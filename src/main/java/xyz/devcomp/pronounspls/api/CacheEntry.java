package xyz.devcomp.pronounspls.api;

import java.time.Instant;

/**
 * Private wrapper for a cached value with an expiry time.
 */
record CacheEntry<T>(T value, Instant expiresAt) {
    boolean isNotExpired() {
        return !Instant.now().isAfter(expiresAt);
    }
}
