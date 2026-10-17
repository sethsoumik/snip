package in.soumikseth.links.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import in.soumikseth.links.admin.OpsMetrics;
import in.soumikseth.links.repository.LinkRepository;

/**
 * Records click events asynchronously so redirect latency is never impacted by
 * analytics writes.
 */
@Component
public class ClickCounter {

    private final LinkRepository repository;
    private final OpsMetrics metrics;

    public ClickCounter(LinkRepository repository, OpsMetrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    @Async
    @Transactional
    public void record(String slug) {
        repository.incrementClicks(slug);
        metrics.recordClick(slug);
    }
}
