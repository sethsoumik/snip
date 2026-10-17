package in.soumikseth.links.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import in.soumikseth.links.dto.LoginRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Login page and session endpoints for the admin area.
 */
@RestController
public class AdminAuthController {

    private static final String USERNAME = "admin";

    private final byte[] password;

    public AdminAuthController(@Value("${app.admin-password:admin}") String password) {
        this.password = password.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/admin/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("static/admin/login.html")) {
            if (in == null) {
                throw new IllegalStateException("login.html not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/admin/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body, HttpSession session) {
        byte[] given = body.password() == null
                ? new byte[0]
                : body.password().getBytes(StandardCharsets.UTF_8);
        if (USERNAME.equals(body.username()) && MessageDigest.isEqual(password, given)) {
            session.setAttribute("admin", Boolean.TRUE);
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "invalid credentials"));
    }

    @PostMapping("/admin/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
