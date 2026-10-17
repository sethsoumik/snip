package in.soumikseth.links.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import in.soumikseth.links.dto.CreateShortLinkRequest;
import in.soumikseth.links.dto.ShortLinkResponse;
import in.soumikseth.links.service.ShortLinkService;
import jakarta.validation.Valid;

/** REST API for creating and inspecting short links. */
@RestController
@RequestMapping("/api/v1/links")
public class ShortLinkController {

    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShortLinkResponse> create(@Valid @RequestBody CreateShortLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ShortLinkResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(service.get(slug));
    }
}
