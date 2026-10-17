package in.soumikseth.links.controller;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import in.soumikseth.links.admin.OpsMetrics;
import in.soumikseth.links.exception.NotFoundException;
import in.soumikseth.links.service.ShortLinkService;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles the public redirect path: GET /{slug} -> 302 to the target URL.
 */
@RestController
public class RedirectController {

    private final ShortLinkService service;
    private final OpsMetrics metrics;

    public RedirectController(ShortLinkService service, OpsMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    // Constrain the slug to base64url chars so static assets (style.css, app.js,
    // favicon.svg) fall through to Spring's static resource handler instead of
    // being treated as short codes.
    @GetMapping("/{slug:[A-Za-z0-9_-]+}")
    public void redirect(@PathVariable String slug, HttpServletResponse response) throws IOException {
        try {
            String url = service.resolve(slug);
            // 302 (temporary) so browsers/clients keep hitting us and we keep counting clicks.
            response.setStatus(HttpStatus.FOUND.value());
            response.setHeader(HttpHeaders.LOCATION, url);
        } catch (NotFoundException ex) {
            metrics.recordNotFound(slug);
            // Hand off to the container so Spring serves our custom error/404.html
            // for browser users instead of the API's JSON error body.
            response.sendError(HttpStatus.NOT_FOUND.value());
        }
    }
}
