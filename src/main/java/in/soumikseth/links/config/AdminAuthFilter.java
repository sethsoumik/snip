package in.soumikseth.links.config;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Session guard for the admin area.
 *
 * The login page (/admin/login) is public; everything else under /admin and
 * /api/v1/admin needs an authenticated session. Browsers get bounced to the login
 * page, API calls get a 401 JSON body.
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final String SESSION_ATTR = "admin";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!isProtected(path) || "/admin/login".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_ATTR))) {
            chain.doFilter(request, response);
            return;
        }

        if (wantsHtml(request)) {
            response.sendRedirect("/admin/login");
        } else {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"unauthorized\"}");
        }
    }

    private boolean isProtected(String path) {
        return "/admin".equals(path)
                || path.startsWith("/admin/")
                || path.startsWith("/api/v1/admin");
    }

    private boolean wantsHtml(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
