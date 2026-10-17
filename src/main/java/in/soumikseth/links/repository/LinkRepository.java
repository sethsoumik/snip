package in.soumikseth.links.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.soumikseth.links.domain.Link;

public interface LinkRepository extends JpaRepository<Link, Long> {

    /** Fetches the next id from the Postgres sequence (used to pre-compute the slug). */
    @Query(value = "SELECT nextval('link_seq')", nativeQuery = true)
    long nextId();

    /** Sum of all recorded clicks (for the admin dashboard). */
    @Query("SELECT COALESCE(SUM(l.clicks), 0) FROM Link l")
    long totalClicks();

    Optional<Link> findBySlug(String slug);

    Optional<Link> findByUrlHash(String urlHash);

    /** Atomic click increment — safe under concurrency and avoids a read-modify-write. */
    @Modifying
    @Query("UPDATE Link l SET l.clicks = l.clicks + 1 WHERE l.slug = :slug")
    int incrementClicks(@Param("slug") String slug);
}
