package com.inbook.service;

import com.inbook.dto.BookLookupResult;
import com.inbook.repository.BookLookupCacheRepository;
import com.inbook.repository.entity.BookLookupCache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AieBookLookupServiceTest {
    private final AieBookLookupService service = new AieBookLookupService();

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
    void returnsCachedLookupBeforeRemoteCall() {
        BookLookupCache cachedBook = new BookLookupCache();
        cachedBook.setIsbn("9788836021215");
        cachedBook.setAutore("Autore cache");
        cachedBook.setTitolo("Titolo cache");
        cachedBook.setVolume(1);
        cachedBook.setCasaEditrice("Editore cache");
        cachedBook.setPrezzo(12.50);
        cachedBook.setSource("AIE");
        cachedBook.setCreated_at(1L);
        cachedBook.setUpdated_at(1L);

        AtomicInteger findCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();
        BookLookupCacheRepository cacheRepository = cacheRepositoryReturning(cachedBook, findCount, saveCount);

        AieBookLookupService cachedService = new AieBookLookupService(cacheRepository);
        BookLookupResult result = cachedService.lookupByIsbn("97888836021215");

        assertEquals("9788836021215", result.getIsbn());
        assertEquals("Titolo cache", result.getTitolo());
        assertEquals("Autore cache", result.getAutore());
        assertEquals(1, findCount.get());
        assertEquals(0, saveCount.get());
    }

    private BookLookupCacheRepository cacheRepositoryReturning(BookLookupCache cachedBook,
                                                              AtomicInteger findCount,
                                                              AtomicInteger saveCount) {
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
                        return args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "BookLookupCacheRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
