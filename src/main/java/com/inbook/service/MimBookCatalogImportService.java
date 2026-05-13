package com.inbook.service;

import com.inbook.repository.BookLookupCacheRepository;
import com.inbook.repository.BookImportRunItemRepository;
import com.inbook.repository.BookImportRunRepository;
import com.inbook.repository.BookImportRunSourceRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.BookLookupCache;
import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookImportRunItem;
import com.inbook.repository.entity.BookImportRunSource;
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
    private static final String RUN_TYPE = "MIM_OPEN_DATA_IMPORT";
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d+(?:[.,]\\d{1,2})?");

    private final BookLookupCacheRepository cacheRepository;
    private final BookImportRunRepository runRepository;
    private final BookImportRunSourceRepository sourceRepository;
    private final BookImportRunItemRepository itemRepository;
    private final BookImportRunErrorGroupService errorGroupService;
    private final HttpClient httpClient;
    private final List<String> configuredSources;
    private final boolean scheduledImportEnabled;
    private final int maxDiscardedItems;

    public MimBookCatalogImportService(BookLookupCacheRepository cacheRepository,
                                       BookImportRunRepository runRepository,
                                       BookImportRunSourceRepository sourceRepository,
                                       BookImportRunItemRepository itemRepository,
                                       BookImportRunErrorGroupService errorGroupService,
                                       @Value("${inbook.mim-books.csv-urls:}") String csvUrls,
                                       @Value("${inbook.mim-books.scheduled-enabled:false}") boolean scheduledImportEnabled,
                                       @Value("${inbook.import-report.max-discarded-items:500}") int maxDiscardedItems) {
        this.cacheRepository = cacheRepository;
        this.runRepository = runRepository;
        this.sourceRepository = sourceRepository;
        this.itemRepository = itemRepository;
        this.errorGroupService = errorGroupService;
        this.configuredSources = parseConfiguredSources(csvUrls);
        this.scheduledImportEnabled = scheduledImportEnabled;
        this.maxDiscardedItems = Math.max(0, maxDiscardedItems);
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
        BookImportRun run = importConfiguredSourcesWithRun(null);
        log.info("Import catalogo MIM completato. Sorgenti={}, righe={}, salvati={}, saltati={}",
                run.getSourceCount(), run.getRowsRead(), run.getSaved(), run.getSkipped());
    }

    public ImportSummary importConfiguredSources() {
        BookImportRun run = importConfiguredSourcesWithRun(null);
        return new ImportSummary(run.getSourceCount(), run.getRowsRead(), run.getSaved(), run.getSkipped());
    }

    public BookImportRun importConfiguredSourcesWithRun(AppUser actor) {
        if (configuredSources.isEmpty()) {
            throw new IllegalStateException("Nessun CSV MIM configurato in inbook.mim-books.csv-urls");
        }

        BookImportRun run = startRun(actor, RUN_TYPE);
        run.setSourceCount(configuredSources.size());
        runRepository.save(run);
        Set<String> seenIsbns = new HashSet<>();
        DetailLimit detailLimit = new DetailLimit();
        boolean hasFailures = false;
        for (String source : configuredSources) {
            try {
                SourceImportSummary sourceSummary = importSource(source, seenIsbns, run, detailLimit);
                saveSourceRun(run, sourceSummary, "COMPLETED", null);
                addToRun(run, sourceSummary);
            } catch (RuntimeException e) {
                hasFailures = true;
                run.setFailed(run.getFailed() + 1);
                saveSourceRun(run, new SourceImportSummary(source, 0, 0, 0), "FAILED", e.getMessage());
            }
        }
        run.setStatus(hasFailures ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
        run.setFinished_at(System.currentTimeMillis());
        return runRepository.save(run);
    }

    public ImportSummary importSource(String source) {
        SourceImportSummary summary = importSource(source, new HashSet<>(), null, null);
        return summary.toImportSummary();
    }

    public List<String> getConfiguredSources() {
        return configuredSources;
    }

    private SourceImportSummary importSource(String source, Set<String> seenIsbns,
                                             BookImportRun run, DetailLimit detailLimit) {
        if (source == null || source.isBlank()) {
            return SourceImportSummary.empty();
        }

        String cleanSource = source.trim();
        try (InputStream inputStream = openSource(cleanSource)) {
            return importCsv(inputStream, cleanSource, seenIsbns, run, detailLimit);
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

    private SourceImportSummary importCsv(InputStream inputStream, String source, Set<String> seenIsbns,
                                          BookImportRun run, DetailLimit detailLimit) throws IOException {
        long rowsRead = 0;
        long saved = 0;
        long skipped = 0;
        List<BookLookupCache> batch = new ArrayList<>(BATCH_SIZE);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new SourceImportSummary(source, 0, 0, 0);
            }
            headerLine = stripUtf8Bom(headerLine);
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = parseCsvLine(headerLine, delimiter);
            Map<String, Integer> columns = indexColumns(headers);

            String line;
            while ((line = reader.readLine()) != null) {
                rowsRead++;
                List<String> values = parseCsvLine(line, delimiter);
                CacheRow row = toCache(values, columns);
                if (row.cache() == null) {
                    skipped++;
                    saveDiscardedItem(run, detailLimit, source, rowsRead, row.rawIsbn(), row.normalizedIsbn(), row.discardReason(), null);
                    continue;
                }

                BookLookupCache cache = row.cache();
                if (!seenIsbns.add(cache.getIsbn())) {
                    skipped++;
                    saveDiscardedItem(run, detailLimit, source, rowsRead, row.rawIsbn(), cache.getIsbn(),
                            "ISBN duplicato nel run o in una sorgente gia letta.", cache.getTitolo());
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
        return new SourceImportSummary(source, rowsRead, saved, skipped);
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

    private CacheRow toCache(List<String> values, Map<String, Integer> columns) {
        String rawIsbn = value(values, columns, "codiceisbn", "isbn");
        String isbn = normalizeIsbn(rawIsbn);
        if (isbn.isBlank()) {
            return new CacheRow(null, clean(rawIsbn), null, "ISBN mancante.");
        }
        if (isbn.length() != 13) {
            return new CacheRow(null, clean(rawIsbn), null, "ISBN normalizzato non a 13 cifre: " + isbn.length() + ".");
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
        return new CacheRow(cache, clean(rawIsbn), isbn, null);
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

    private BookImportRun startRun(AppUser actor, String type) {
        BookImportRun run = new BookImportRun();
        run.setType(type);
        run.setStatus("RUNNING");
        run.setActor(actor);
        run.setStarted_at(System.currentTimeMillis());
        return runRepository.save(run);
    }

    private void addToRun(BookImportRun run, SourceImportSummary summary) {
        run.setRowsRead(run.getRowsRead() + summary.rowsRead());
        run.setTotalItems(run.getTotalItems() + summary.rowsRead());
        run.setProcessed(run.getProcessed() + summary.rowsRead());
        run.setSaved(run.getSaved() + summary.saved());
        run.setSkipped(run.getSkipped() + summary.skipped());
        runRepository.save(run);
    }

    private void saveSourceRun(BookImportRun run, SourceImportSummary summary, String status, String errorMessage) {
        BookImportRunSource source = new BookImportRunSource();
        source.setRun(run);
        source.setSourceUrl(truncate(summary.source(), 1000));
        source.setStatus(status);
        source.setRowsRead(summary.rowsRead());
        source.setSaved(summary.saved());
        source.setSkipped(summary.skipped());
        source.setErrorMessage(truncate(errorMessage, 1000));
        source.setCreated_at(System.currentTimeMillis());
        sourceRepository.save(source);
    }

    private void saveDiscardedItem(BookImportRun run, DetailLimit detailLimit, String sourceUrl, long rowNumber,
                                   String rawIsbn, String normalizedIsbn, String reason, String title) {
        if (run == null) {
            return;
        }
        errorGroupService.increment(run, "DISCARDED", "MIM_CSV", reason);

        if (detailLimit == null || detailLimit.count >= maxDiscardedItems) {
            return;
        }

        detailLimit.count++;
        BookImportRunItem item = new BookImportRunItem();
        item.setRun(run);
        item.setSourceUrl(truncate(sourceUrl, 1000));
        item.setRowNumber(rowNumber);
        item.setIsbn(truncate(rawIsbn, 20));
        item.setNormalizedIsbn(truncate(normalizedIsbn, 13));
        item.setStatus("DISCARDED");
        item.setFallbackStep("MIM_CSV");
        item.setReason(truncate(reason, 1000));
        item.setTitle(truncate(title, 300));
        item.setCreated_at(System.currentTimeMillis());
        itemRepository.save(item);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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

    private record CacheRow(BookLookupCache cache, String rawIsbn, String normalizedIsbn, String discardReason) {
    }

    private record SourceImportSummary(String source, long rowsRead, long saved, long skipped) {
        static SourceImportSummary empty() {
            return new SourceImportSummary("-", 0, 0, 0);
        }

        ImportSummary toImportSummary() {
            return new ImportSummary(source == null || source.isBlank() || "-".equals(source) ? 0 : 1, rowsRead, saved, skipped);
        }
    }

    private static class DetailLimit {
        private int count;
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
