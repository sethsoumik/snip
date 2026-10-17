package in.soumikseth.links.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache-aside layer for the hot redirect path.
 *
 * Redis is optional here: if it's down, reads just fall through to Postgres and
 * writes are skipped, so the service works with or without it.
 */
@Component
public class UrlCache {

    private static final Logger log = LoggerFactory.getLogger(UrlCache.class);
    private static final String PREFIX = "link:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    public UrlCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String get(String slug) {
        try {
            return redis.opsForValue().get(PREFIX + slug);
        } catch (Exception ex) {
            log.warn("Redis get failed (falling back to DB): {}", ex.getMessage());
            return null;
        }
    }

    public void put(String slug, String url) {
        try {
            redis.opsForValue().set(PREFIX + slug, url, TTL);
        } catch (Exception ex) {
            log.warn("Redis put failed: {}", ex.getMessage());
        }
    }

    /** Connectivity check for the admin dashboard (a PING). */
    public boolean isUp() {
        try {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception ex) {
            return false;
        }
    }
}
