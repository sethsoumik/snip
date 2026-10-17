package in.soumikseth.links.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import in.soumikseth.links.dto.ActivityEntry;
import in.soumikseth.links.dto.MetricsSnapshot;

/**
 * Process-local counters plus a bounded activity log.
 *
 * The counters reset on restart; the persisted totals (links, clicks) are read
 * from Postgres by AdminController and merged into the snapshot here.
 */
@Component
public class OpsMetrics {

    private static final int MAX_LOG_ENTRIES = 200;

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong creates = new AtomicLong();
    private final AtomicLong notFound = new AtomicLong();
    private final Instant startedAt = Instant.now();
    private final Deque<ActivityEntry> log = new ConcurrentLinkedDeque<>();

    public void recordCacheHit(String slug) {
        cacheHits.incrementAndGet();
        push("CACHE", "hit " + slug);
    }

    public void recordCacheMiss(String slug) {
        cacheMisses.incrementAndGet();
        push("DB", "miss " + slug);
    }

    public void recordCreate(String slug) {
        creates.incrementAndGet();
        push("CREATE", "created " + slug);
    }

    public void recordNotFound(String slug) {
        notFound.incrementAndGet();
        push("404", slug + " not found");
    }

    public void recordClick(String slug) {
        push("CLICK", "click " + slug);
    }

    public List<ActivityEntry> recent() {
        return new ArrayList<>(log);
    }

    public MetricsSnapshot snapshot(long totalLinks, long totalClicks, String dbStatus, String redisStatus) {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long lookups = hits + misses;
        double hitRate = lookups == 0 ? 0.0 : (double) hits / lookups;
        long uptime = Duration.between(startedAt, Instant.now()).getSeconds();
        return new MetricsSnapshot(totalLinks, totalClicks, hits, misses,
                creates.get(), notFound.get(), uptime, hitRate, dbStatus, redisStatus);
    }

    private void push(String level, String message) {
        log.addLast(new ActivityEntry(Instant.now(), level, message));
        while (log.size() > MAX_LOG_ENTRIES) {
            log.removeFirst();
        }
    }
}
