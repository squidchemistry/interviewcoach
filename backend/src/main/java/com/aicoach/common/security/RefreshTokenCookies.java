package com.aicoach.common.security;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token lives only in an httpOnly cookie: JavaScript (and therefore XSS) cannot read it.
 * {@code SameSite=Strict} plus the path scope {@code /api/v1/auth} mean the browser sends it only on same-site
 * requests to the auth endpoints. Endpoints that act on the cookie also verify the {@code Origin} header.
 */
@Component
public class RefreshTokenCookies {

    public static final String PATH = "/api/v1/auth";

    private final AppProperties.Cookie config;
    private final List<String> allowedOrigins;

    public RefreshTokenCookies(AppProperties properties) {
        this.config = properties.auth().cookie();
        this.allowedOrigins = properties.cors().allowedOrigins();
    }

    /** {@code Set-Cookie} header value carrying a new refresh token. */
    public String issue(String tokenValue, long maxAgeSeconds) {
        return base(tokenValue).maxAge(Duration.ofSeconds(maxAgeSeconds)).build().toString();
    }

    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (config.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * CSRF defence in depth for cookie-authenticated endpoints: browsers always send {@code Origin} on POST; if it
     * is present it must be whitelisted. (Non-browser clients without the header are not a CSRF vector.)
     */
    public void assertTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin) && !sameOrigin(request, origin)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Request origin is not allowed");
        }
    }

    private static boolean sameOrigin(HttpServletRequest request, String origin) {
        String self = request.getScheme() + "://" + request.getServerName()
                + (isDefaultPort(request) ? "" : ":" + request.getServerPort());
        return self.equalsIgnoreCase(origin);
    }

    private static boolean isDefaultPort(HttpServletRequest request) {
        int port = request.getServerPort();
        return ("http".equals(request.getScheme()) && port == 80) || ("https".equals(request.getScheme()) && port == 443);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(config.name(), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite("Strict")
                .path(PATH);
    }
}
