package com.inbook.service;

import com.inbook.dto.BookLookupResult;
import com.inbook.repository.BookLookupCacheRepository;
import com.inbook.repository.entity.BookLookupCache;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AieBookLookupService {
    private static final URI AIE_SEARCH_PAGE = URI.create("https://www.adozioniaie.it/ricerca.html");
    private static final String AIE_SEARCH_URL = "https://www.adozioniaie.it/Ricerca.cgi";
    private static final String SOURCE = "AIE";

    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset=([^;]+)");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d+(?:[.,]\\d{1,2})?");

    private final HttpClient httpClient;
    private final BookLookupCacheRepository cacheRepository;
    private final ConcurrentMap<String, Object> lookupLocks = new ConcurrentHashMap<>();

    public AieBookLookupService(BookLookupCacheRepository cacheRepository) {
        this(cacheRepository, defaultHttpClient());
    }

    AieBookLookupService() {
        this(null, defaultHttpClient());
    }

    private AieBookLookupService(BookLookupCacheRepository cacheRepository, HttpClient httpClient) {
        this.cacheRepository = cacheRepository;
        this.httpClient = httpClient;
    }

    private static HttpClient defaultHttpClient() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(cookieManager)
                .build();
    }

    public BookLookupResult lookupByIsbn(String isbn) {
        String cleanIsbn = normalizeLookupIsbn(isbn);

        Optional<BookLookupResult> cached = lookupCachedBook(cleanIsbn);
        if (cached.isPresent()) {
            return cached.get();
        }

        Object lock = lookupLocks.computeIfAbsent(cleanIsbn, key -> new Object());
        synchronized (lock) {
            try {
                return lookupCachedBook(cleanIsbn).orElseGet(() -> {
                    BookLookupResult result = fetchBookFromAie(cleanIsbn);
                    cacheLookupResult(cleanIsbn, result);
                    return result;
                });
            } finally {
                lookupLocks.remove(cleanIsbn, lock);
            }
        }
    }

    private BookLookupResult fetchBookFromAie(String cleanIsbn) {
        try {
            warmUpAieSession();
            HttpResponse<byte[]> response = httpClient.send(buildLookupRequest(cleanIsbn), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AieLookupUnavailableException("AIE ha risposto con stato HTTP " + response.statusCode());
            }
            String html = decodeBody(response);
            return parseAieResponse(cleanIsbn, html);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AieLookupUnavailableException("Ricerca AIE interrotta");
        } catch (IOException e) {
            throw new AieLookupUnavailableException("AIE non raggiungibile: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new AieLookupUnavailableException(normalizeAieClientError(e));
        }
    }

    private String normalizeAieClientError(IllegalArgumentException e) {
        String message = e.getMessage();
        String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ITALIAN);
        if (normalizedMessage.contains("troppi tentativi")) {
            return "AIE ha bloccato temporaneamente la ricerca. Riprova piu tardi.";
        }
        if (normalizedMessage.contains("illegal character")) {
            return "AIE ha risposto con un redirect di errore non valido. Probabile blocco temporaneo della ricerca.";
        }
        return "AIE ha risposto con una richiesta non valida: " + (message == null ? "errore sconosciuto" : message);
    }

    private Optional<BookLookupResult> lookupCachedBook(String isbn) {
        if (cacheRepository == null) {
            return Optional.empty();
        }
        return cacheRepository.findById(isbn).map(this::toLookupResult);
    }

    private void cacheLookupResult(String isbn, BookLookupResult result) {
        if (cacheRepository == null || result == null) {
            return;
        }

        long now = System.currentTimeMillis();
        BookLookupCache cache = cacheRepository.findById(isbn).orElseGet(BookLookupCache::new);
        cache.setIsbn(isbn);
        cache.setAutore(result.getAutore());
        cache.setTitolo(result.getTitolo());
        cache.setVolume(result.getVolume());
        cache.setCasaEditrice(result.getCasaEditrice());
        cache.setPrezzo(result.getPrezzo());
        cache.setSource(result.getSource());
        if (cache.getCreated_at() == null) {
            cache.setCreated_at(now);
        }
        cache.setUpdated_at(now);

        cacheRepository.save(cache);
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

    private void warmUpAieSession() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(AIE_SEARCH_PAGE)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", userAgent())
                .GET()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private HttpRequest buildLookupRequest(String isbn) {
        String query = "FormName=ISBN_Search&FormAction=search&CODVOL="
                + URLEncoder.encode(isbn, StandardCharsets.UTF_8);
        return HttpRequest.newBuilder(URI.create(AIE_SEARCH_URL + "?" + query))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Referer", AIE_SEARCH_PAGE.toString())
                .header("User-Agent", userAgent())
                .GET()
                .build();
    }

    private String userAgent() {
        return "Mozilla/5.0 (compatible; InBook/1.0; +https://www.adozioniaie.it/)";
    }

    private String decodeBody(HttpResponse<byte[]> response) {
        Charset charset = response.headers()
                .firstValue("Content-Type")
                .flatMap(this::charsetFromContentType)
                .orElse(StandardCharsets.ISO_8859_1);
        return new String(response.body(), charset);
    }

    private Optional<Charset> charsetFromContentType(String contentType) {
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Charset.forName(matcher.group(1).trim()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private BookLookupResult parseAieResponse(String requestedIsbn, String html) {
        String pageText = plainText(html);
        String pageTextLower = pageText.toLowerCase(Locale.ITALIAN);

        if (pageTextLower.contains("troppi tentativi")) {
            throw new AieLookupUnavailableException("AIE ha bloccato temporaneamente la ricerca. Riprova piu tardi.");
        }
        if (pageTextLower.contains("captcha") && !textContainsIsbn(pageText, requestedIsbn)) {
            throw new AieLookupUnavailableException("AIE richiede una verifica anti-bot per questa ricerca.");
        }
        if (pageTextLower.contains("nessun") && (pageTextLower.contains("risult") || pageTextLower.contains("trov"))) {
            throw new AieBookNotFoundException("Nessun libro trovato su AIE per questo ISBN.");
        }

        List<List<String>> rows = extractRows(html);
        Map<String, String> fields = new HashMap<>();
        fields.putAll(extractFromHeaderTable(rows, requestedIsbn));
        fields.putAll(extractFromKeyValueRows(rows));

        String isbn = firstNonBlank(fields.get("isbn"), requestedIsbn);
        String autore = cleanField(fields.get("autore"));
        String titolo = cleanField(fields.get("titolo"));
        String casaEditrice = cleanField(fields.get("casaEditrice"));
        Integer volume = parseVolume(fields.get("volume"));
        Double prezzo = parsePrice(fields.get("prezzo"));

        if (!textContainsIsbn(pageText, requestedIsbn) && titolo == null && autore == null && casaEditrice == null) {
            throw new AieBookNotFoundException("Nessun libro trovato su AIE per questo ISBN.");
        }
        if (titolo == null && autore == null && casaEditrice == null && prezzo == null) {
            throw new AieLookupUnavailableException("Risposta AIE ricevuta, ma formato non riconosciuto.");
        }

        return new BookLookupResult(isbn, autore, titolo, volume, casaEditrice, prezzo, SOURCE);
    }

    private List<List<String>> extractRows(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = CELL_PATTERN.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                String value = plainText(cellMatcher.group(1));
                if (!value.isBlank()) {
                    cells.add(value);
                }
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private Map<String, String> extractFromKeyValueRows(List<List<String>> rows) {
        Map<String, String> fields = new HashMap<>();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size() - 1; i++) {
                String key = canonicalKey(row.get(i));
                String value = row.get(i + 1);
                if (key != null && value != null && !value.isBlank() && canonicalKey(value) == null) {
                    fields.putIfAbsent(key, value);
                }
            }
        }
        return fields;
    }

    private Map<String, String> extractFromHeaderTable(List<List<String>> rows, String requestedIsbn) {
        Map<String, String> fields = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> header = rows.get(i);
            Map<Integer, String> columns = new HashMap<>();
            for (int column = 0; column < header.size(); column++) {
                String key = canonicalKey(header.get(column));
                if (key != null) {
                    columns.put(column, key);
                }
            }
            if (columns.size() < 2) {
                continue;
            }
            for (int j = i + 1; j < rows.size(); j++) {
                List<String> values = rows.get(j);
                String joinedValues = String.join(" ", values);
                if (!textContainsIsbn(joinedValues, requestedIsbn) && values.size() < columns.size()) {
                    continue;
                }
                for (Map.Entry<Integer, String> entry : columns.entrySet()) {
                    int column = entry.getKey();
                    if (column < values.size()) {
                        String value = values.get(column);
                        if (value != null && !value.isBlank() && canonicalKey(value) == null) {
                            fields.putIfAbsent(entry.getValue(), value);
                        }
                    }
                }
                if (!fields.isEmpty()) {
                    return fields;
                }
            }
        }
        return fields;
    }

    private String canonicalKey(String label) {
        if (label == null) {
            return null;
        }
        String normalized = normalizeSpaces(label).toLowerCase(Locale.ITALIAN);
        if (normalized.contains("isbn") || normalized.contains("codice volume") || normalized.equals("codvol")) {
            return "isbn";
        }
        if (normalized.contains("autore") || normalized.contains("autori")) {
            return "autore";
        }
        if (normalized.contains("titolo")) {
            return "titolo";
        }
        if (normalized.contains("casa editrice") || normalized.contains("editore")) {
            return "casaEditrice";
        }
        if (normalized.contains("prezzo") || normalized.equals("euro")) {
            return "prezzo";
        }
        if (normalized.equals("vol") || normalized.equals("vol.") || normalized.contains("volume")) {
            return "volume";
        }
        return null;
    }

    private String plainText(String html) {
        if (html == null) {
            return "";
        }
        String withoutScripts = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll(" ");
        String withoutTags = TAG_PATTERN.matcher(withoutScripts).replaceAll(" ");
        return normalizeSpaces(HtmlUtils.htmlUnescape(withoutTags));
    }

    private String normalizeSpaces(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanField(String value) {
        String cleaned = normalizeSpaces(value);
        if (cleaned.isBlank()) {
            return null;
        }
        return cleaned;
    }

    private String firstNonBlank(String first, String second) {
        String cleanFirst = cleanField(first);
        return cleanFirst != null ? cleanFirst : cleanField(second);
    }

    private Integer parseVolume(String value) {
        String cleaned = cleanField(value);
        if (cleaned == null) {
            return null;
        }
        Matcher matcher = INTEGER_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(matcher.group());
            return parsed >= 0 && parsed <= 99 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double parsePrice(String value) {
        String cleaned = cleanField(value);
        if (cleaned == null) {
            return null;
        }
        Matcher matcher = PRICE_PATTERN.matcher(cleaned.replace(" ", ""));
        if (!matcher.find()) {
            return null;
        }
        String numeric = matcher.group();
        int comma = numeric.lastIndexOf(',');
        int dot = numeric.lastIndexOf('.');
        if (comma > -1 && dot > -1) {
            numeric = comma > dot ? numeric.replace(".", "").replace(',', '.') : numeric.replace(",", "");
        } else if (comma > -1) {
            numeric = numeric.replace(',', '.');
        }
        try {
            return Double.parseDouble(numeric);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private boolean textContainsIsbn(String text, String isbn) {
        if (text == null || isbn == null) {
            return false;
        }
        return text.replaceAll("\\D", "").contains(isbn);
    }

    public static class AieBookNotFoundException extends RuntimeException {
        public AieBookNotFoundException(String message) {
            super(message);
        }
    }

    public static class AieLookupUnavailableException extends RuntimeException {
        public AieLookupUnavailableException(String message) {
            super(message);
        }
    }
}
