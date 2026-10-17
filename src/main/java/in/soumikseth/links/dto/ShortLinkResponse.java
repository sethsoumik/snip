package in.soumikseth.links.dto;

import java.time.Instant;

/** Response returned when a short link is created or fetched. */
public record ShortLinkResponse(
        String slug,
        String shortUrl,
        String url,
        Instant createdAt,
        Instant expiresAt,
        long clicks
) {
}
