package com.example.wmbservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a cache entry for workspace state.
 */
@Getter
@Setter
@Entity
@Table(
        name = "local_cache",
        indexes = {
                @Index(name = "idx_cache_key", columnList = "cache_key")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uniq_cache_key", columnNames = {"cache_key"})
        }
)
public class LocalCache {

    private static final Logger logger = LoggerFactory.getLogger(LocalCache.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 128)
    private String cacheKey;

    @Column(name = "cache_value", columnDefinition = "TEXT")
    private String cacheValue;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Default constructor.
     */
    public LocalCache() {
        logger.debug("LocalCache default constructor called");
    }

    /**
     * All-args constructor.
     */
    public LocalCache(String cacheKey, String cacheValue) {
        logger.info("LocalCache instantiated with cacheKey={}", cacheKey);
        this.cacheKey = cacheKey;
        this.cacheValue = cacheValue;
    }

    @Override
    public boolean equals(Object o) {
        logger.debug("equals called");
        if (this == o) return true;
        if (!(o instanceof LocalCache that)) return false;
        return Objects.equals(cacheKey, that.cacheKey);
    }

    @Override
    public int hashCode() {
        logger.debug("hashCode called");
        return Objects.hash(cacheKey);
    }

    @Override
    public String toString() {
        logger.debug("toString called");
        return "LocalCache{" +
                "id=" + id +
                ", cacheKey='" + cacheKey + '\'' +
                ", cacheValue='" + (cacheValue != null ? "[PROTECTED]" : null) + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }
}