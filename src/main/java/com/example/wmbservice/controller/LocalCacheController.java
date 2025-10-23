package com.example.wmbservice.controller;

import com.example.wmbservice.model.LocalCache;
import com.example.wmbservice.service.LocalCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for LocalCache CRUD operations.
 * Propagates X-Transaction-ID header, logs all actions and errors.
 */
@CrossOrigin(origins = "http://localhost:3000", exposedHeaders = "X-Transaction-ID")
@RestController
@RequestMapping("/api/cache")
public class LocalCacheController {

    private static final Logger logger = LoggerFactory.getLogger(LocalCacheController.class);
    private final LocalCacheService localCacheService;

    public LocalCacheController(LocalCacheService localCacheService) {
        this.localCacheService = localCacheService;
    }

    /**
     * Get all cache entries.
     * @param transactionId request transaction id
     * @return list of cache entries
     */
    @GetMapping
    public ResponseEntity<List<LocalCache>> getAll(
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {
        logger.info("getAll entered. transactionId={}", transactionId);
        try {
            List<LocalCache> result = localCacheService.getAll();
            logger.info("getAll successful. transactionId={}, resultCount={}", transactionId, result.size());
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(result);
        } catch (Exception e) {
            logger.error("Error in getAll. transactionId={}, error={}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(null);
        }
    }

    /**
     * Get a cache entry by cacheKey.
     * @param cacheKey cache key
     * @param transactionId request transaction id
     * @return found cache entry or error
     */
    @GetMapping("/{cacheKey}")
    public ResponseEntity<?> getByCacheKey(
            @PathVariable String cacheKey,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {
        logger.info("getByCacheKey entered. transactionId={}, cacheKey={}", transactionId, cacheKey);
        try {
            Optional<LocalCache> cache = localCacheService.getByCacheKey(cacheKey);
            if (cache.isPresent()) {
                logger.info("getByCacheKey successful. transactionId={}, cacheKey={}", transactionId, cacheKey);
                return ResponseEntity.ok()
                        .header("X-Transaction-ID", transactionId)
                        .body(cache.get());
            } else {
                logger.warn("Cache key not found. transactionId={}, cacheKey={}", transactionId, cacheKey);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .header("X-Transaction-ID", transactionId)
                        .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Cache key not found", transactionId));
            }
        } catch (Exception e) {
            logger.error("Error in getByCacheKey. transactionId={}, cacheKey={}, error={}", transactionId, cacheKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "GET_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Create or update a cache entry.
     * @param cacheKey cache key
     * @param cacheValue cache value
     * @param transactionId request transaction id
     * @return created/updated cache entry or error
     */
    @PostMapping
    public ResponseEntity<?> saveOrUpdate(
            @RequestParam String cacheKey,
            @RequestParam String cacheValue,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {
        logger.info("saveOrUpdate entered. transactionId={}, cacheKey={}", transactionId, cacheKey);
        try {
            LocalCache cache = localCacheService.saveOrUpdate(cacheKey, cacheValue);
            logger.info("saveOrUpdate successful. transactionId={}, cacheKey={}", transactionId, cacheKey);
            return ResponseEntity.ok()
                    .header("X-Transaction-ID", transactionId)
                    .body(cache);
        } catch (Exception e) {
            logger.error("Error in saveOrUpdate. transactionId={}, cacheKey={}, error={}", transactionId, cacheKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "SAVE_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Delete a cache entry by cacheKey.
     * @param cacheKey cache key
     * @param transactionId request transaction id
     * @return success or error response
     */
    @DeleteMapping("/{cacheKey}")
    public ResponseEntity<?> deleteByCacheKey(
            @PathVariable String cacheKey,
            @RequestHeader(value = "X-Transaction-ID", required = false) String transactionId) {
        logger.info("deleteByCacheKey entered. transactionId={}, cacheKey={}", transactionId, cacheKey);
        try {
            localCacheService.deleteByCacheKey(cacheKey);
            logger.info("deleteByCacheKey successful. transactionId={}, cacheKey={}", transactionId, cacheKey);
            return ResponseEntity.noContent()
                    .header("X-Transaction-ID", transactionId)
                    .build();
        } catch (Exception e) {
            logger.error("Error in deleteByCacheKey. transactionId={}, cacheKey={}, error={}", transactionId, cacheKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Transaction-ID", transactionId)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "DELETE_ERROR", "Unexpected error", transactionId));
        }
    }

    /**
     * Error response format for all error cases.
     */
    private static class ErrorResponse {
        public final int status;
        public final String code;
        public final String message;
        public final String transactionId;

        public ErrorResponse(int status, String code, String message, String transactionId) {
            this.status = status;
            this.code = code;
            this.message = message;
            this.transactionId = transactionId;
        }
    }
}