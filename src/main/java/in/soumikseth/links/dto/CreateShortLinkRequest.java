package in.soumikseth.links.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for creating a short link. */
public record CreateShortLinkRequest(
        @NotBlank @Size(max = 2048, message = "url must not exceed 2048 characters")
        String url,

        Instant expiresAt
) {
}
