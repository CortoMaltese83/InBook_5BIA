package com.inbook.repository;

import com.inbook.repository.entity.BookLookupCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookLookupCacheRepository extends JpaRepository<BookLookupCache, String> {
}
