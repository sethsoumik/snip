package in.soumikseth.links.dto;

import java.time.Instant;

/** A single line in the live activity log. */
public record ActivityEntry(
        Instant timestamp,
        String level,
        String message
) {
}
