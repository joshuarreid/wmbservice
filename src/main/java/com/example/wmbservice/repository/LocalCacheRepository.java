package com.example.wmbservice.repository;

import com.example.wmbservice.model.LocalCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository for LocalCache CRUD and queries.
 */
@Repository
public interface LocalCacheRepository extends JpaRepository<LocalCache, Long> {

    /**
     * Find cache entry by cacheKey.
     */
    Optional<LocalCache> findByCacheKey(String cacheKey);

    /**
     * Query for cache entry by cacheKey.
     */
    @Query("SELECT lc FROM LocalCache lc WHERE lc.cacheKey = :cacheKey")
    Optional<LocalCache> getByCacheKey(@Param("cacheKey") String cacheKey);

    /**
     * Delete cache entry by cacheKey.
     */
    void deleteByCacheKey(String cacheKey);
}