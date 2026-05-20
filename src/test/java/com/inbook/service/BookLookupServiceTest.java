package com.inbook.service;

import com.inbook.dto.BookLookupResult;
import com.inbook.repository.BookLookupCacheRepository;
import com.inbook.repository.entity.BookLookupCache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookLookupServiceTest {
    private final BookLookupService service = new BookLookupService(null, List.of(), false);

    @Test
    void keepsValidThirteenDigitIsbn() {
        assertEquals("9788836021215", service.normalizeLookupIsbn("9788836021215"));
    }

    @Test
    void normalizesFourteenDigitIsbnWithRepeatedExtraDigit() {
        assertEquals("9788836021215", service.normalizeLookupIsbn("97888836021215"));
    }

    @Test
    void rejectsTooShortIsbn() {
        assertThrows(IllegalArgumentException.class, () -> service.normalizeLookupIsbn("978888360212"));
    }

    @Test
    void returnsCachedLookup() {
        BookLookupCache cachedBook = new BookLookupCache();
        cachedBook.setIsbn("9788836021215");
        cachedBook.setAutore("Autore cache");
        cachedBook.setTitolo("Titolo cache");
        cachedBook.setVolume(1);
        cachedBook.setCasaEditrice("Editore cache");
        cachedBook.setPrezzo(12.50);
        cachedBook.setSource("MIM_OPEN_DATA");
        cachedBook.setCreated_at(1L);
        cachedBook.setUpdated_at(1L);

        AtomicInteger findCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();
        AtomicInteger remoteCount = new AtomicInteger();
        BookLookupCacheRepository cacheRepository = cacheRepositoryReturning(cachedBook, findCount, saveCount, null);

        BookLookupService cachedService = new BookLookupService(cacheRepository, List.of(isbn -> {
            remoteCount.incrementAndGet();
            return Optional.empty();
        }), true);
        BookLookupResult result = cachedService.lookupByIsbn("97888836021215");

        assertEquals("9788836021215", result.getIsbn());
        assertEquals("Titolo cache", result.getTitolo());
        assertEquals("Autore cache", result.getAutore());
        assertEquals(1, findCount.get());
        assertEquals(0, saveCount.get());
        assertEquals(0, remoteCount.get());
    }

    @Test
    void missingCachedLookupWithoutRemoteReturnsNotFound() {
        AtomicInteger findCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();
        BookLookupCacheRepository cacheRepository = emptyCacheRepository(findCount, saveCount, null);

        BookLookupService cachedService = new BookLookupService(cacheRepository, List.of(), false);

        assertThrows(BookLookupService.BookNotFoundException.class,
                () -> cachedService.lookupByIsbn("9788836021215"));
        assertEquals(1, findCount.get());
        assertEquals(0, saveCount.get());
    }

    @Test
    void googleFallbackHitSavesResult() {
        AtomicInteger findCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();
        AtomicReference<BookLookupCache> savedBook = new AtomicReference<>();
        BookLookupCacheRepository cacheRepository = emptyCacheRepository(findCount, saveCount, savedBook);

        BookLookupService lookupService = new BookLookupService(cacheRepository, List.of(isbn -> Optional.of(
                new BookLookupResult(isbn, "Autore Google", "Titolo Google", null, "Editore Google", null, "GOOGLE_BOOKS")
        )), true);

        BookLookupResult result = lookupService.lookupByIsbn("9788836021215");

        assertEquals("9788836021215", result.getIsbn());
        assertEquals("Titolo Google", result.getTitolo());
        assertEquals("GOOGLE_BOOKS", result.getSource());
        assertEquals(1, findCount.get());
        assertEquals(1, saveCount.get());
        assertEquals("GOOGLE_BOOKS", savedBook.get().getSource());
    }

    @Test
    void manualBookIsCachedWhenMissing() {
        AtomicInteger findCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();
        AtomicReference<BookLookupCache> savedBook = new AtomicReference<>();
        BookLookupCacheRepository cacheRepository = emptyCacheRepository(findCount, saveCount, savedBook);
        BookLookupService lookupService = new BookLookupService(cacheRepository, List.of(), false);

        boolean cached = lookupService.cacheManualBookIfAbsent(
                "978-88-360-2121-5",
                "Autore manuale",
                "Titolo manuale",
                2,
                "Editore manuale",
                19.90
        );

        assertTrue(cached);
        assertEquals(1, findCount.get());
        assertEquals(1, saveCount.get());
        assertEquals("9788836021215", savedBook.get().getIsbn());
        assertEquals("Titolo manuale", savedBook.get().getTitolo());
        assertEquals("USER_MANUAL", savedBook.get().getSource());
    }

    @Test
    void manualBookDoesNotOverwriteExistingCacheEntry() {
        BookLookupCache cachedBook = new BookLookupCache();
        cachedBook.setIsbn("9788836021215");
        cachedBook.setAutore("Autore cache");
        cachedBook.setTitolo("Titolo cache");
        cachedBook.setVolume(1);
        cachedBook.setCasaEditrice("Editore cache");
        cachedBook.setPrezzo(12.50);
        cachedBook.setSource("MIM_OPEN_DATA");
        cachedBook.setCreated_at(1L);
        cachedBook.setUpdated_at(1L);

        AtomicInteger findCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();
        BookLookupCacheRepository cacheRepository = cacheRepositoryReturning(cachedBook, findCount, saveCount, null);
        BookLookupService lookupService = new BookLookupService(cacheRepository, List.of(), false);

        boolean cached = lookupService.cacheManualBookIfAbsent(
                "9788836021215",
                "Nuovo autore",
                "Nuovo titolo",
                1,
                "Nuovo editore",
                10.00
        );

        assertFalse(cached);
        assertEquals(1, findCount.get());
        assertEquals(0, saveCount.get());
    }

    @Test
    void invalidManualIsbnIsNotCached() {
        AtomicInteger findCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();
        BookLookupCacheRepository cacheRepository = emptyCacheRepository(findCount, saveCount, null);
        BookLookupService lookupService = new BookLookupService(cacheRepository, List.of(), false);

        boolean cached = lookupService.cacheManualBookIfAbsent(
                "123",
                "Autore",
                "Titolo",
                1,
                "Editore",
                9.99
        );

        assertFalse(cached);
        assertEquals(0, findCount.get());
        assertEquals(0, saveCount.get());
    }

    @Test
    void openLibraryFallbackRunsAfterGoogleMiss() {
        AtomicInteger googleCount = new AtomicInteger();
        AtomicInteger openLibraryCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();
        BookLookupCacheRepository cacheRepository = emptyCacheRepository(new AtomicInteger(), saveCount, null);

        BookLookupService lookupService = new BookLookupService(cacheRepository, List.of(
                isbn -> {
                    googleCount.incrementAndGet();
                    return Optional.empty();
                },
                isbn -> {
                    openLibraryCount.incrementAndGet();
                    return Optional.of(new BookLookupResult(isbn, "Autore OL", "Titolo OL", null, "Editore OL", null, "OPEN_LIBRARY"));
                }
        ), true);

        BookLookupResult result = lookupService.lookupByIsbn("9788836021215");

        assertEquals("OPEN_LIBRARY", result.getSource());
        assertEquals(1, googleCount.get());
        assertEquals(1, openLibraryCount.get());
        assertEquals(1, saveCount.get());
    }

    @Test
    void remoteResultWithWrongIsbnIsDiscarded() {
        AtomicInteger saveCount = new AtomicInteger();
        BookLookupCacheRepository cacheRepository = emptyCacheRepository(new AtomicInteger(), saveCount, null);

        BookLookupService lookupService = new BookLookupService(cacheRepository, List.of(
                isbn -> Optional.of(new BookLookupResult("9780000000002", "Autore", "Titolo", null, "Editore", null, "GOOGLE_BOOKS")),
                isbn -> Optional.empty()
        ), true);

        assertThrows(BookLookupService.BookNotFoundException.class,
                () -> lookupService.lookupByIsbn("9788836021215"));
        assertEquals(0, saveCount.get());
    }

    @Test
    void allRemoteMissesReturnNotFound() {
        BookLookupCacheRepository cacheRepository = emptyCacheRepository(new AtomicInteger(), new AtomicInteger(), null);
        BookLookupService lookupService = new BookLookupService(cacheRepository, List.of(
                isbn -> Optional.empty(),
                isbn -> Optional.empty()
        ), true);

        assertThrows(BookLookupService.BookNotFoundException.class,
                () -> lookupService.lookupByIsbn("9788836021215"));
    }

    @Test
    void allRemoteTechnicalFailuresReturnUnavailable() {
        BookLookupCacheRepository cacheRepository = emptyCacheRepository(new AtomicInteger(), new AtomicInteger(), null);
        BookLookupService lookupService = new BookLookupService(cacheRepository, List.of(
                isbn -> {
                    throw new BookLookupService.RemoteLookupException("google down");
                },
                isbn -> {
                    throw new BookLookupService.RemoteLookupException("open library down");
                }
        ), true);

        assertThrows(BookLookupService.BookLookupUnavailableException.class,
                () -> lookupService.lookupByIsbn("9788836021215"));
    }

    private BookLookupCacheRepository cacheRepositoryReturning(BookLookupCache cachedBook,
                                                              AtomicInteger findCount,
                                                              AtomicInteger saveCount,
                                                              AtomicReference<BookLookupCache> savedBook) {
        return (BookLookupCacheRepository) Proxy.newProxyInstance(
                BookLookupCacheRepository.class.getClassLoader(),
                new Class<?>[]{BookLookupCacheRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        findCount.incrementAndGet();
                        assertEquals(cachedBook.getIsbn(), args[0]);
                        return Optional.of(cachedBook);
                    }
                    if ("save".equals(method.getName())) {
                        saveCount.incrementAndGet();
                        if (savedBook != null) {
                            savedBook.set((BookLookupCache) args[0]);
                        }
                        return args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "BookLookupCacheRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private BookLookupCacheRepository emptyCacheRepository(AtomicInteger findCount,
                                                           AtomicInteger saveCount,
                                                           AtomicReference<BookLookupCache> savedBook) {
        return (BookLookupCacheRepository) Proxy.newProxyInstance(
                BookLookupCacheRepository.class.getClassLoader(),
                new Class<?>[]{BookLookupCacheRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        findCount.incrementAndGet();
                        return Optional.empty();
                    }
                    if ("save".equals(method.getName())) {
                        saveCount.incrementAndGet();
                        if (savedBook != null) {
                            savedBook.set((BookLookupCache) args[0]);
                        }
                        return args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "BookLookupCacheRepository empty test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
