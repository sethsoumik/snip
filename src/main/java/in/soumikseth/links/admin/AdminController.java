package in.soumikseth.links.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import in.soumikseth.links.dto.ActivityEntry;
import in.soumikseth.links.dto.MetricsSnapshot;
import in.soumikseth.links.repository.LinkRepository;
import in.soumikseth.links.service.UrlCache;

/**
 * Admin endpoints (protected by AdminAuthFilter): the dashboard page plus the
 * JSON it polls for metrics and live logs.
 */
@RestController
public class AdminController {

    private final OpsMetrics metrics;
    private final LinkRepository repository;
    private final UrlCache cache;

    public AdminController(OpsMetrics metrics, LinkRepository repository, UrlCache cache) {
        this.metrics = metrics;
        this.repository = repository;
        this.cache = cache;
    }

    @GetMapping(value = "/admin", produces = MediaType.TEXT_HTML_VALUE)
    public String page() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("static/admin/index.html")) {
            if (in == null) {
                throw new IllegalStateException("admin/index.html not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/api/v1/admin/metrics")
    public MetricsSnapshot metrics() {
        long totalLinks = 0;
        long totalClicks = 0;
        String dbStatus = "up";
        try {
            totalLinks = repository.count();
            totalClicks = repository.totalClicks();
        } catch (Exception ex) {
            dbStatus = "down";
        }
        String redisStatus = cache.isUp() ? "up" : "down";
        return metrics.snapshot(totalLinks, totalClicks, dbStatus, redisStatus);
    }

    @GetMapping("/api/v1/admin/logs")
    public List<ActivityEntry> logs() {
        return metrics.recent();
    }
}
