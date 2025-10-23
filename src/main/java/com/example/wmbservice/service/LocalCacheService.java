package com.example.wmbservice.service;

import com.example.wmbservice.model.LocalCache;
import com.example.wmbservice.repository.LocalCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for LocalCache CRUD operations.
 * All methods log entry, error, and key state for traceability.
 */
@Service
public class LocalCacheService {

    private static final Logger logger = LoggerFactory.getLogger(LocalCacheService.class);
    private final LocalCacheRepository repository;

    public LocalCacheService(LocalCacheRepository repository) {
        this.repository = repository;
    }

    /**
     * Get all cache entries.
     * @return list of LocalCache entries
     */
    public List<LocalCache> getAll() {
        logger.info("getAll entered");
        List<LocalCache> results = repository.findAll();
        logger.info("getAll successful, resultCount={}", results.size());
        return results;
    }

    /**
     * Get a cache entry by cacheKey.
     * @param cacheKey cache key
     * @return Optional containing LocalCache if found
     */
    public Optional<LocalCache> getByCacheKey(String cacheKey) {
        logger.info("getByCacheKey entered. cacheKey={}", cacheKey);
        Optional<LocalCache> result = repository.findByCacheKey(cacheKey);
        if (result.isPresent()) {
            logger.info("Cache entry found for cacheKey={}", cacheKey);
        } else {
            logger.warn("Cache entry not found for cacheKey={}", cacheKey);
        }
        return result;
    }

    /**
     * Create or update a cache entry with the given key/value.
     * @param cacheKey   cache key
     * @param cacheValue cache value
     * @return saved LocalCache entry
     */
    @Transactional
    public LocalCache saveOrUpdate(String cacheKey, String cacheValue) {
        logger.info("saveOrUpdate entered. cacheKey={}", cacheKey);
        Optional<LocalCache> existing = repository.findByCacheKey(cacheKey);
        LocalCache cache = existing.orElseGet(() -> new LocalCache(cacheKey, cacheValue));
        cache.setCacheValue(cacheValue);
        cache.setUpdatedAt(LocalDateTime.now());
        LocalCache saved = repository.save(cache);
        logger.info("saveOrUpdate successful. cacheKey={}, id={}", saved.getCacheKey(), saved.getId());
        return saved;
    }

    /**
     * Delete a cache entry by cacheKey.
     * @param cacheKey cache key
     */
    @Transactional
    public void deleteByCacheKey(String cacheKey) {
        logger.info("deleteByCacheKey entered. cacheKey={}", cacheKey);
        repository.deleteByCacheKey(cacheKey);
        logger.info("deleteByCacheKey successful. cacheKey={}", cacheKey);
    }
}