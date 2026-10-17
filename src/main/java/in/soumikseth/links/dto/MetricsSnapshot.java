package in.soumikseth.links.dto;

/** Aggregated operational metrics for the admin dashboard. */
public record MetricsSnapshot(
        long totalLinks,
        long totalClicks,
        long cacheHits,
        long cacheMisses,
        long creates,
        long notFound,
        long uptimeSeconds,
        double cacheHitRate,
        String dbStatus,
        String redisStatus
) {
}
