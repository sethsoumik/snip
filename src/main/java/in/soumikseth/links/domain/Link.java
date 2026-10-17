package in.soumikseth.links.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single shortened link.
 *
 * The numeric id is the source of truth for the short code: a Postgres sequence
 * assigns it, then ShortCodeGenerator encodes it into the slug. Keeping a numeric
 * id lets the sequence grow contiguously, which is what keeps codes minimal.
 */
@Entity
@Table(name = "links")
public class Link {

    @Id
    private Long id;

    /** The short code, e.g. "Kf7", derived from id. */
    @Column(nullable = false, unique = true, length = 16)
    private String slug;

    /** The original (long) URL. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    /** sha256(url), unique-indexed so creates are idempotent without indexing the full URL. */
    @Column(name = "url_hash", nullable = false, unique = true, length = 64)
    private String urlHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private long clicks;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public void setUrlHash(String urlHash) {
        this.urlHash = urlHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getClicks() {
        return clicks;
    }

    public void setClicks(long clicks) {
        this.clicks = clicks;
    }
}
