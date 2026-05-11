package com.inbook.service;

import com.inbook.repository.BookLookupCacheRepository;
import com.inbook.repository.entity.BookLookupCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MimBookCatalogImportService {
    private static final Logger log = LoggerFactory.getLogger(MimBookCatalogImportService.class);
    private static final int BATCH_SIZE = 500;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d+(?:[.,]\\d{1,2})?");

    private final BookLookupCacheRepository cacheRepository;
    private final HttpClient httpClient;
    private final List<String> configuredSources;
    private final boolean scheduledImportEnabled;

    public MimBookCatalogImportService(BookLookupCacheRepository cacheRepository,
                                       @Value("${inbook.mim-books.csv-urls:}") String csvUrls,
                                       @Value("${inbook.mim-books.scheduled-enabled:false}") boolean scheduledImportEnabled) {
        this.cacheRepository = cacheRepository;
        this.configuredSources = parseConfiguredSources(csvUrls);
        this.scheduledImportEnabled = scheduledImportEnabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Scheduled(cron = "${inbook.mim-books.schedule-cron:0 0 3 * * SUN}")
    public void scheduledImport() {
        if (!scheduledImportEnabled || configuredSources.isEmpty()) {
            return;
        }
        ImportSummary summary = importConfiguredSources();
        log.info("Import catalogo MIM completato. Sorgenti={}, righe={}, salvati={}, saltati={}",
                summary.sources(), summary.rowsRead(), summary.saved(), summary.skipped());
    }

    public ImportSummary importConfiguredSources() {
        if (configuredSources.isEmpty()) {
            throw new IllegalStateException("Nessun CSV MIM configurato in inbook.mim-books.csv-urls");
        }

        ImportSummary total = ImportSummary.empty();
        Set<String> seenIsbns = new HashSet<>();
        for (String source : configuredSources) {
            total = total.plus(importSource(source, seenIsbns));
        }
        return total;
    }

    public ImportSummary importSource(String source) {
        return importSource(source, new HashSet<>());
    }

    private ImportSummary importSource(String source, Set<String> seenIsbns) {
        if (source == null || source.isBlank()) {
            return ImportSummary.empty();
        }

        String cleanSource = source.trim();
        try (InputStream inputStream = openSource(cleanSource)) {
            return importCsv(inputStream, cleanSource, seenIsbns);
        } catch (IOException e) {
            throw new IllegalStateException("Import CSV MIM non riuscito per " + cleanSource + ": " + e.getMessage(), e);
        }
    }

    private InputStream openSource(String source) throws IOException {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(source))
                        .timeout(Duration.ofMinutes(5))
                        .header("Accept", "text/csv,*/*")
                        .header("User-Agent", "InBook/1.0")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("download interrotto", e);
            }
        }
        return Files.newInputStream(Path.of(source));
    }

    private ImportSummary importCsv(InputStream inputStream, String source, Set<String> seenIsbns) throws IOException {
        long rowsRead = 0;
        long saved = 0;
        long skipped = 0;
        List<BookLookupCache> batch = new ArrayList<>(BATCH_SIZE);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new ImportSummary(1, 0, 0, 0);
            }
            headerLine = stripUtf8Bom(headerLine);
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = parseCsvLine(headerLine, delimiter);
            Map<String, Integer> columns = indexColumns(headers);

            String line;
            while ((line = reader.readLine()) != null) {
                rowsRead++;
                List<String> values = parseCsvLine(line, delimiter);
                BookLookupCache cache = toCache(values, columns);
                if (cache == null || !seenIsbns.add(cache.getIsbn())) {
                    skipped++;
                    continue;
                }

                batch.add(cache);
                if (batch.size() >= BATCH_SIZE) {
                    saved += saveBatch(batch);
                }
            }
        }

        saved += saveBatch(batch);
        log.info("Import CSV MIM sorgente={} righe={} salvati={} saltati={}", source, rowsRead, saved, skipped);
        return new ImportSummary(1, rowsRead, saved, skipped);
    }

    private long saveBatch(List<BookLookupCache> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        int size = batch.size();
        cacheRepository.saveAll(batch);
        cacheRepository.flush();
        batch.clear();
        return size;
    }

    private BookLookupCache toCache(List<String> values, Map<String, Integer> columns) {
        String isbn = normalizeIsbn(value(values, columns, "codiceisbn", "isbn"));
        if (isbn.length() != 13) {
            return null;
        }

        long now = System.currentTimeMillis();
        BookLookupCache cache = new BookLookupCache();
        cache.setIsbn(isbn);
        cache.setAutore(clean(value(values, columns, "autori", "autore"), 100));
        cache.setTitolo(clean(joinTitle(value(values, columns, "titolo"), value(values, columns, "sottotitolo")), 300));
        cache.setVolume(parseVolume(value(values, columns, "volume")));
        cache.setCasaEditrice(clean(value(values, columns, "editore", "casaeditrice"), 100));
        cache.setPrezzo(parsePrice(value(values, columns, "prezzo")));
        cache.setSource("MIM_OPEN_DATA");
        cache.setCreated_at(now);
        cache.setUpdated_at(now);
        return cache;
    }

    private String value(List<String> values, Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(name);
            if (index != null && index >= 0 && index < values.size()) {
                return values.get(index);
            }
        }
        return null;
    }

    private Map<String, Integer> indexColumns(List<String> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            columns.put(normalizeHeader(headers.get(i)), i);
        }
        return columns;
    }

    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private char detectDelimiter(String headerLine) {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        counts.put(';', count(headerLine, ';'));
        counts.put(',', count(headerLine, ','));
        counts.put('\t', count(headerLine, '\t'));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(';');
    }

    private int count(String value, char needle) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private String stripUtf8Bom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim()
                .toLowerCase(Locale.ITALIAN)
                .replaceAll("[^a-z0-9]", "");
    }

    private String normalizeIsbn(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String clean(String value, int maxLength) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength);
    }

    private String joinTitle(String title, String subtitle) {
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

    private Integer parseVolume(String value) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.equalsIgnoreCase("U")) {
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
        String cleaned = clean(value);
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

    private List<String> parseConfiguredSources(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("[,\\n]")).stream()
                .map(String::trim)
                .filter(source -> !source.isBlank())
                .toList();
    }

    public record ImportSummary(int sources, long rowsRead, long saved, long skipped) {
        static ImportSummary empty() {
            return new ImportSummary(0, 0, 0, 0);
        }

        ImportSummary plus(ImportSummary other) {
            return new ImportSummary(
                    sources + other.sources,
                    rowsRead + other.rowsRead,
                    saved + other.saved,
                    skipped + other.skipped
            );
        }
    }
}
