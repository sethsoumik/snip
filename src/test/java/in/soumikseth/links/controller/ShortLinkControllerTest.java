package in.soumikseth.links.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import in.soumikseth.links.dto.ShortLinkResponse;
import in.soumikseth.links.service.ShortLinkService;

@WebMvcTest(ShortLinkController.class)
class ShortLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShortLinkService service;

    @Test
    void create_returns201WithShortLink() throws Exception {
        ShortLinkResponse response = new ShortLinkResponse(
                "abc", "http://localhost:8080/abc", "https://example.com",
                Instant.now(), null, 0);
        given(service.create(any())).willReturn(response);

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("abc"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc"));
    }

    @Test
    void create_rejectsBlankUrl() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_returnsMetadata() throws Exception {
        ShortLinkResponse response = new ShortLinkResponse(
                "abc", "http://localhost:8080/abc", "https://example.com",
                Instant.now(), null, 5);
        given(service.get("abc")).willReturn(response);

        mockMvc.perform(get("/api/v1/links/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("abc"))
                .andExpect(jsonPath("$.clicks").value(5));
    }
}
