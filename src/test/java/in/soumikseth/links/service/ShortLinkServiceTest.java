package in.soumikseth.links.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import in.soumikseth.links.admin.OpsMetrics;
import in.soumikseth.links.core.ShortCodeGenerator;
import in.soumikseth.links.domain.Link;
import in.soumikseth.links.dto.CreateShortLinkRequest;
import in.soumikseth.links.dto.ShortLinkResponse;
import in.soumikseth.links.exception.NotFoundException;
import in.soumikseth.links.repository.LinkRepository;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    private static final String BASE_URL = "http://localhost:8080";

    @Mock
    private LinkRepository repository;
    @Mock
    private UrlCache cache;
    @Mock
    private ClickCounter clickCounter;

    private ShortLinkService service;
    private final ShortCodeGenerator codec = new ShortCodeGenerator(42L);

    @BeforeEach
    void setUp() {
        service = new ShortLinkService(repository, cache, clickCounter, codec, new OpsMetrics(), BASE_URL);
    }

    @Test
    void create_returnsExistingWhenUrlAlreadyShortened() {
        Link existing = link(5L, "abc", "https://example.com");
        given(repository.findByUrlHash(anyString())).willReturn(Optional.of(existing));

        ShortLinkResponse response = service.create(new CreateShortLinkRequest("https://example.com", null));

        assertThat(response.slug()).isEqualTo("abc");
        verify(repository, never()).save(any());
    }

    @Test
    void create_derivesSlugFromGeneratedId() {
        given(repository.findByUrlHash(anyString())).willReturn(Optional.empty());
        given(repository.nextId()).willReturn(7L);

        ShortLinkResponse response = service.create(new CreateShortLinkRequest("https://example.com", null));

        String expectedSlug = codec.encode(7L);
        assertThat(response.slug()).isEqualTo(expectedSlug);
        assertThat(response.shortUrl()).isEqualTo(BASE_URL + "/" + expectedSlug);
        assertThat(response.url()).isEqualTo("https://example.com");
        verify(repository).save(any(Link.class));
    }

    @Test
    void resolve_servesFromCacheWhenHit() {
        given(cache.get("abc")).willReturn("https://example.com");

        String url = service.resolve("abc");

        assertThat(url).isEqualTo("https://example.com");
        verify(clickCounter).record("abc");
        verify(repository, never()).findBySlug(anyString());
    }

    @Test
    void resolve_fallsBackToDatabaseOnCacheMiss() {
        Link link = link(1L, "abc", "https://example.com");
        given(cache.get("abc")).willReturn(null);
        given(repository.findBySlug("abc")).willReturn(Optional.of(link));

        String url = service.resolve("abc");

        assertThat(url).isEqualTo("https://example.com");
        verify(cache).put("abc", "https://example.com");
        verify(clickCounter).record("abc");
    }

    @Test
    void resolve_throwsWhenSlugUnknown() {
        given(cache.get("nope")).willReturn(null);
        given(repository.findBySlug("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("nope"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_rejectsNonHttpScheme() {
        assertThatThrownBy(() -> service.create(new CreateShortLinkRequest("javascript:alert(1)", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    private Link link(long id, String slug, String url) {
        Link link = new Link();
        link.setId(id);
        link.setSlug(slug);
        link.setUrl(url);
        link.setUrlHash("hash-" + url);
        link.setCreatedAt(Instant.now());
        link.setClicks(0);
        return link;
    }
}
