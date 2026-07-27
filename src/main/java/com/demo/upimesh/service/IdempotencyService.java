package com.demo.upimesh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency cache. Uses Redis SETNX + TTL when available (for production multi-instance),
 * with fallback to JVM-local ConcurrentHashMap for standalone dev & testing.
 *
 * The contract:
 *   - claim(hash) returns true on first call, false on every call after that
 *     (within the TTL window)
 *   - the operation is atomic — even if 100 threads call claim(hash) at the
 *     same instant, exactly one returns true
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Value("${upi.mesh.idempotency-ttl-seconds:86400}")
    private long ttlSeconds;

    /**
     * Try to claim a hash. Returns true if this caller is the first; false if
     * someone else already claimed it (i.e. the packet is a duplicate).
     */
    public boolean claim(String packetHash) {
        if (redisTemplate != null) {
            try {
                Boolean success = redisTemplate.opsForValue()
                        .setIfAbsent("upi:idempotency:" + packetHash, "claimed", Duration.ofSeconds(ttlSeconds));
                if (success != null) {
                    return success;
                }
            } catch (Exception e) {
                log.warn("Redis claim error ({}), failing over to in-memory idempotency cache", e.getMessage());
            }
        }

        Instant now = Instant.now();
        Instant prev = seen.putIfAbsent(packetHash, now);
        return prev == null;
    }

    public int size() {
        if (redisTemplate != null) {
            try {
                var keys = redisTemplate.keys("upi:idempotency:*");
                if (keys != null) return keys.size();
            } catch (Exception ignored) {}
        }
        return seen.size();
    }

    /** Periodically evict entries past their TTL so the in-memory map doesn't grow forever. */
    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
        seen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    /** Test/demo helper. */
    public void clear() {
        if (redisTemplate != null) {
            try {
                var keys = redisTemplate.keys("upi:idempotency:*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception ignored) {}
        }
        seen.clear();
    }
}
