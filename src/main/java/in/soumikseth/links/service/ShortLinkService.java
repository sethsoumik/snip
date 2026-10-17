package in.soumikseth.links.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.soumikseth.links.admin.OpsMetrics;
import in.soumikseth.links.core.ShortCodeGenerator;
import in.soumikseth.links.domain.Link;
import in.soumikseth.links.dto.CreateShortLinkRequest;
import in.soumikseth.links.dto.ShortLinkResponse;
import in.soumikseth.links.exception.NotFoundException;
import in.soumikseth.links.repository.LinkRepository;

/**
 * Core use cases: create a short link and resolve a short code back to a URL.
 */
@Service
public class ShortLinkService {

    private final LinkRepository repository;
    private final UrlCache cache;
    private final ClickCounter clickCounter;
    private final ShortCodeGenerator codec;
    private final OpsMetrics metrics;
    private final String baseUrl;

    public ShortLinkService(LinkRepository repository,
                            UrlCache cache,
                            ClickCounter clickCounter,
                            ShortCodeGenerator codec,
                            OpsMetrics metrics,
                            @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.cache = cache;
        this.clickCounter = clickCounter;
        this.codec = codec;
        this.metrics = metrics;
        this.baseUrl = baseUrl;
    }

    /** Whitelist for short codes — the base64url alphabet the codec emits. */
    private static final Pattern SLUG_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    /**
     * Creates (or returns an existing) short link for the given URL.
     *
     * Idempotency comes from the unique index on url_hash: the same normalized URL
     * always maps back to the same code, so repeat submits don't create duplicate
     * rows.
     */
    @Transactional
    public ShortLinkResponse create(CreateShortLinkRequest request) {
        String url = normalizeUrl(request.url());
        String hash = sha256(url);

        return repository.findByUrlHash(hash)
                .map(this::toResponse)
                .orElseGet(() -> {
                    // Fetch the next id up front so the slug can be derived and set
                    // BEFORE the row is persisted (slug is NOT NULL).
                    long id = repository.nextId();

                    Link link = new Link();
                    link.setId(id);
                    link.setSlug(codec.encode(id));
                    link.setUrl(url);
                    link.setUrlHash(hash);
                    link.setCreatedAt(Instant.now());
                    link.setExpiresAt(request.expiresAt());

                    repository.save(link);

                    cache.put(link.getSlug(), url);
                    metrics.recordCreate(link.getSlug());
                    return toResponse(link);
                });
    }

    /**
     * Resolves a short code to its target URL (the redirect hot path).
     *
     * Cache-aside: hit Redis first, fall back to Postgres, then backfill the cache.
     * Click counting is fire-and-forget so it never blocks the redirect.
     */
    public String resolve(String slug) {
        validateSlug(slug);

        String cached = cache.get(slug);
        if (cached != null) {
            metrics.recordCacheHit(slug);
            clickCounter.record(slug);
            return cached;
        }

        Link link = repository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("No link found for code: " + slug));

        metrics.recordCacheMiss(slug);
        cache.put(slug, link.getUrl());
        clickCounter.record(slug);
        return link.getUrl();
    }

    /** Returns metadata (including click count) for a short code. */
    public ShortLinkResponse get(String slug) {
        validateSlug(slug);

        Link link = repository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("No link found for code: " + slug));
        return toResponse(link);
    }

    /**
     * Rejects anything outside the base64url alphabet. It's a second layer on top
     * of parameterized queries — those already prevent injection, this just keeps
     * obviously-bad input from ever reaching the DB.
     */
    private void validateSlug(String slug) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("invalid short code");
        }
    }

    private ShortLinkResponse toResponse(Link link) {
        return new ShortLinkResponse(
                link.getSlug(),
                baseUrl + "/" + link.getSlug(),
                link.getUrl(),
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.getClicks()
        );
    }

    /** Validates the scheme and trims the URL so javascript: / data: can't sneak in. */
    private String normalizeUrl(String url) {
        String trimmed = url.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("url is malformed");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("url must start with http:// or https://");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("url must include a host");
        }
        return trimmed;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
