package com.inbook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inbook.dto.BookLookupResult;
import com.inbook.repository.BookLookupCacheRepository;
import com.inbook.repository.entity.BookLookupCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BookLookupService {
    private static final Logger log = LoggerFactory.getLogger(BookLookupService.class);
    private static final Duration REMOTE_TIMEOUT = Duration.ofSeconds(8);
    private static final String SOURCE_GOOGLE_BOOKS = "GOOGLE_BOOKS";
    private static final String SOURCE_OPEN_LIBRARY = "OPEN_LIBRARY";

    private final BookLookupCacheRepository cacheRepository;
    private final List<RemoteBookLookupProvider> remoteProviders;
    private final boolean remoteEnabled;

    @Autowired
    public BookLookupService(BookLookupCacheRepository cacheRepository,
                             ObjectMapper objectMapper,
                             @Value("${inbook.book-lookup.remote-enabled:true}") boolean remoteEnabled,
                             @Value("${inbook.google-books.api-key:}") String googleBooksApiKey) {
        this(cacheRepository, defaultRemoteProviders(objectMapper, googleBooksApiKey), remoteEnabled);
    }

    BookLookupService(BookLookupCacheRepository cacheRepository,
                      List<RemoteBookLookupProvider> remoteProviders,
                      boolean remoteEnabled) {
        this.cacheRepository = cacheRepository;
        this.remoteProviders = remoteProviders == null ? List.of() : List.copyOf(remoteProviders);
        this.remoteEnabled = remoteEnabled;
    }

    public BookLookupResult lookupByIsbn(String isbn) {
        return lookupByIsbnWithTrace(isbn).result();
    }

    public LookupTrace lookupByIsbnWithTrace(String isbn) {
        String cleanIsbn = normalizeLookupIsbn(isbn);
        List<LookupStep> steps = new ArrayList<>();
        Optional<BookLookupResult> cachedBook = lookupCachedBook(cleanIsbn);
        if (cachedBook.isPresent()) {
            steps.add(new LookupStep("LOCAL_CACHE", "FOUND", null));
            return new LookupTrace(cleanIsbn, cachedBook.get(), steps);
        }
        steps.add(new LookupStep("LOCAL_CACHE", "MISS", null));

        if (!remoteEnabled || remoteProviders.isEmpty()) {
            steps.add(new LookupStep("REMOTE_LOOKUP", "SKIPPED", "Fallback remota disabilitata o non configurata."));
            throw notFound(cleanIsbn, steps);
        }

        int technicalFailures = 0;
        for (RemoteBookLookupProvider provider : remoteProviders) {
            try {
                Optional<BookLookupResult> remoteBook = provider.lookup(cleanIsbn);
                if (remoteBook.isPresent()) {
                    BookLookupResult result = remoteBook.get();
                    String resultIsbn = normalizeIsbn(result.getIsbn());
                    if (cleanIsbn.equals(resultIsbn)) {
                        steps.add(new LookupStep(provider.name(), "FOUND", null));
                        return new LookupTrace(cleanIsbn, cacheRemoteResult(cleanIsbn, result), steps);
                    }
                    steps.add(new LookupStep(provider.name(), "DISCARDED",
                            "ISBN restituito diverso: " + (resultIsbn.isBlank() ? "-" : resultIsbn)));
                } else {
                    steps.add(new LookupStep(provider.name(), "MISS", null));
                }
            } catch (RemoteLookupException e) {
                technicalFailures++;
                steps.add(new LookupStep(provider.name(), "ERROR", e.getMessage()));
                log.warn("Catalogo remoto libri non disponibile provider={}: {}", provider.name(), e.getMessage());
            }
        }

        if (technicalFailures == remoteProviders.size()) {
            throw new BookLookupUnavailableException("Cataloghi remoti temporaneamente non disponibili.", cleanIsbn, steps);
        }
        throw notFound(cleanIsbn, steps);
    }

    private Optional<BookLookupResult> lookupCachedBook(String isbn) {
        if (cacheRepository == null) {
            return Optional.empty();
        }
        return cacheRepository.findById(isbn).map(this::toLookupResult);
    }

    private BookLookupResult cacheRemoteResult(String isbn, BookLookupResult result) {
        if (cacheRepository == null) {
            return result;
        }

        long now = System.currentTimeMillis();
        BookLookupCache cache = new BookLookupCache();
        cache.setIsbn(isbn);
        cache.setAutore(clean(result.getAutore(), 100));
        cache.setTitolo(clean(result.getTitolo(), 300));
        cache.setVolume(result.getVolume());
        cache.setCasaEditrice(clean(result.getCasaEditrice(), 100));
        cache.setPrezzo(result.getPrezzo());
        cache.setSource(clean(result.getSource(), 40));
        cache.setCreated_at(now);
        cache.setUpdated_at(now);

        return toLookupResult(cacheRepository.save(cache));
    }

    private BookLookupResult toLookupResult(BookLookupCache cache) {
        return new BookLookupResult(
                cache.getIsbn(),
                cache.getAutore(),
                cache.getTitolo(),
                cache.getVolume(),
                cache.getCasaEditrice(),
                cache.getPrezzo(),
                cache.getSource()
        );
    }

    private BookNotFoundException notFound(String normalizedIsbn, List<LookupStep> steps) {
        return new BookNotFoundException(
                "Libro non trovato nel catalogo locale o nelle fallback gratuite. Compila i dati manualmente.",
                normalizedIsbn,
                steps
        );
    }

    private static List<RemoteBookLookupProvider> defaultRemoteProviders(ObjectMapper objectMapper,
                                                                         String googleBooksApiKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(REMOTE_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return List.of(
                new GoogleBooksProvider(httpClient, objectMapper, googleBooksApiKey),
                new OpenLibraryProvider(httpClient, objectMapper)
        );
    }

    private String normalizeIsbn(String isbn) {
        if (isbn == null) {
            return "";
        }
        return isbn.replaceAll("\\D", "");
    }

    String normalizeLookupIsbn(String isbn) {
        String digits = normalizeIsbn(isbn);
        if (digits.length() < 13) {
            throw new IllegalArgumentException("ISBN deve contenere almeno 13 cifre.");
        }
        if (digits.length() == 13) {
            validateIsbn13(digits);
            return digits;
        }

        List<String> windowCandidates = findValidIsbnWindows(digits);
        if (windowCandidates.size() == 1) {
            return windowCandidates.get(0);
        }

        if (digits.length() == 14) {
            Optional<String> deletionCandidate = findBestSingleDeletionCandidate(digits);
            if (deletionCandidate.isPresent()) {
                return deletionCandidate.get();
            }
        }

        throw new IllegalArgumentException("ISBN non chiaro: inserisci 13 cifre oppure un codice con una sola cifra in piu.");
    }

    private List<String> findValidIsbnWindows(String digits) {
        List<String> candidates = new ArrayList<>();
        for (int start = 0; start <= digits.length() - 13; start++) {
            String candidate = digits.substring(start, start + 13);
            if (isValidIsbn13(candidate) && !candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private Optional<String> findBestSingleDeletionCandidate(String digits) {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        for (int i = 0; i < digits.length(); i++) {
            String candidate = digits.substring(0, i) + digits.substring(i + 1);
            if (isValidIsbn13(candidate)) {
                candidates.merge(candidate, 1, Integer::sum);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.keySet().iterator().next());
        }

        int bestCount = candidates.values().stream().max(Comparator.naturalOrder()).orElse(0);
        List<String> bestCandidates = candidates.entrySet().stream()
                .filter(entry -> entry.getValue() == bestCount)
                .map(Map.Entry::getKey)
                .toList();

        return bestCount > 1 && bestCandidates.size() == 1
                ? Optional.of(bestCandidates.get(0))
                : Optional.empty();
    }

    private void validateIsbn13(String isbn) {
        if (!isValidIsbn13(isbn)) {
            throw new IllegalArgumentException("ISBN formalmente non valido.");
        }
    }

    private boolean isValidIsbn13(String isbn) {
        if (isbn.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = isbn.charAt(i) - '0';
            sum += digit * (i % 2 == 0 ? 1 : 3);
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == isbn.charAt(12) - '0';
    }

    private static HttpRequest jsonRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REMOTE_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "InBook/1.0")
                .GET()
                .build();
    }

    private static URI uri(String value) {
        return URI.create(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean containsIsbn(JsonNode node, String isbn) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsIsbn(item, isbn)) {
                    return true;
                }
            }
            return false;
        }
        return isbn.equals(node.asText("").replaceAll("\\D", ""));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return value.isBlank() ? null : value;
    }

    private static String firstArrayText(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        return text(node.get(0));
    }

    private static String joinArrayText(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = text(item);
            if (value != null) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private static String joinTitle(String title, String subtitle) {
        String cleanTitle = clean(title);
        String cleanSubtitle = clean(subtitle);
        if (cleanTitle == null) {
            return cleanSubtitle;
        }
        if (cleanSubtitle == null) {
            return cleanTitle;
        }
        return cleanTitle + " - " + cleanSubtitle;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String clean(String value, int maxLength) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength);
    }

    public record LookupTrace(String normalizedIsbn, BookLookupResult result, List<LookupStep> steps) {
        public LookupTrace {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    public record LookupStep(String provider, String status, String reason) {
    }

    @FunctionalInterface
    interface RemoteBookLookupProvider {
        Optional<BookLookupResult> lookup(String isbn);

        default String name() {
            return getClass().getSimpleName();
        }
    }

    private static class GoogleBooksProvider implements RemoteBookLookupProvider {
        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;
        private final String apiKey;

        GoogleBooksProvider(HttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
            this.httpClient = httpClient;
            this.objectMapper = objectMapper;
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }

        @Override
        public Optional<BookLookupResult> lookup(String isbn) {
            String url = "https://www.googleapis.com/books/v1/volumes?q=" + encode("isbn:" + isbn)
                    + "&maxResults=1&projection=lite";
            if (!apiKey.isBlank()) {
                url += "&key=" + encode(apiKey);
            }

            JsonNode root = fetchJson(uri(url), "Google Books");
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return Optional.empty();
            }

            JsonNode volumeInfo = items.get(0).path("volumeInfo");
            if (!containsGoogleIsbn(volumeInfo.path("industryIdentifiers"), isbn)) {
                return Optional.empty();
            }

            String title = joinTitle(text(volumeInfo.path("title")), text(volumeInfo.path("subtitle")));
            String author = joinArrayText(volumeInfo.path("authors"));
            String publisher = text(volumeInfo.path("publisher"));
            Double price = price(items.get(0).path("saleInfo").path("listPrice").path("amount"));
            if (title == null && author == null && publisher == null) {
                return Optional.empty();
            }

            return Optional.of(new BookLookupResult(
                    isbn,
                    clean(author, 100),
                    clean(title, 300),
                    null,
                    clean(publisher, 100),
                    price,
                    SOURCE_GOOGLE_BOOKS
            ));
        }

        @Override
        public String name() {
            return SOURCE_GOOGLE_BOOKS;
        }

        private boolean containsGoogleIsbn(JsonNode identifiers, String isbn) {
            if (identifiers == null || !identifiers.isArray()) {
                return false;
            }
            for (JsonNode identifier : identifiers) {
                if (isbn.equals(identifier.path("identifier").asText("").replaceAll("\\D", ""))) {
                    return true;
                }
            }
            return false;
        }

        private Double price(JsonNode node) {
            return node != null && node.isNumber() ? node.asDouble() : null;
        }

        private JsonNode fetchJson(URI uri, String providerName) {
            try {
                HttpResponse<String> response = httpClient.send(jsonRequest(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RemoteLookupException(providerName + " HTTP " + response.statusCode());
                }
                return objectMapper.readTree(response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RemoteLookupException(providerName + " interrotto", e);
            } catch (IOException | IllegalArgumentException e) {
                throw new RemoteLookupException(providerName + " non raggiungibile", e);
            }
        }
    }

    private static class OpenLibraryProvider implements RemoteBookLookupProvider {
        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        OpenLibraryProvider(HttpClient httpClient, ObjectMapper objectMapper) {
            this.httpClient = httpClient;
            this.objectMapper = objectMapper;
        }

        @Override
        public Optional<BookLookupResult> lookup(String isbn) {
            String url = "https://openlibrary.org/search.json?isbn=" + encode(isbn)
                    + "&fields=title,author_name,publisher,isbn&limit=1";

            JsonNode root = fetchJson(uri(url), "Open Library");
            JsonNode docs = root.path("docs");
            if (!docs.isArray() || docs.isEmpty()) {
                return Optional.empty();
            }

            JsonNode doc = docs.get(0);
            if (!containsIsbn(doc.path("isbn"), isbn)) {
                return Optional.empty();
            }

            String title = text(doc.path("title"));
            String author = joinArrayText(doc.path("author_name"));
            String publisher = firstArrayText(doc.path("publisher"));
            if (title == null && author == null && publisher == null) {
                return Optional.empty();
            }

            return Optional.of(new BookLookupResult(
                    isbn,
                    clean(author, 100),
                    clean(title, 300),
                    null,
                    clean(publisher, 100),
                    null,
                    SOURCE_OPEN_LIBRARY
            ));
        }

        @Override
        public String name() {
            return SOURCE_OPEN_LIBRARY;
        }

        private JsonNode fetchJson(URI uri, String providerName) {
            try {
                HttpResponse<String> response = httpClient.send(jsonRequest(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RemoteLookupException(providerName + " HTTP " + response.statusCode());
                }
                return objectMapper.readTree(response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RemoteLookupException(providerName + " interrotto", e);
            } catch (IOException | IllegalArgumentException e) {
                throw new RemoteLookupException(providerName + " non raggiungibile", e);
            }
        }
    }

    public static class BookNotFoundException extends RuntimeException {
        private final String normalizedIsbn;
        private final List<LookupStep> steps;

        public BookNotFoundException(String message) {
            this(message, null, List.of());
        }

        public BookNotFoundException(String message, String normalizedIsbn, List<LookupStep> steps) {
            super(message);
            this.normalizedIsbn = normalizedIsbn;
            this.steps = steps == null ? List.of() : List.copyOf(steps);
        }

        public String getNormalizedIsbn() {
            return normalizedIsbn;
        }

        public List<LookupStep> getSteps() {
            return steps;
        }
    }

    public static class BookLookupUnavailableException extends RuntimeException {
        private final String normalizedIsbn;
        private final List<LookupStep> steps;

        public BookLookupUnavailableException(String message) {
            this(message, null, List.of());
        }

        public BookLookupUnavailableException(String message, String normalizedIsbn, List<LookupStep> steps) {
            super(message);
            this.normalizedIsbn = normalizedIsbn;
            this.steps = steps == null ? List.of() : List.copyOf(steps);
        }

        public String getNormalizedIsbn() {
            return normalizedIsbn;
        }

        public List<LookupStep> getSteps() {
            return steps;
        }
    }

    static class RemoteLookupException extends RuntimeException {
        RemoteLookupException(String message) {
            super(message);
        }

        RemoteLookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
