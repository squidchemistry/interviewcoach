package com.aicoach.auth.web;

import com.aicoach.auth.service.GoogleOAuthService;
import com.aicoach.auth.service.GoogleOAuthService.GoogleLogin;
import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.AppException;
import com.aicoach.common.security.RefreshTokenCookies;
import com.aicoach.common.web.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Browser-navigation endpoints for Google sign-in. They answer with redirects, not JSON: the callback sets the
 * refresh cookie and sends the browser to the web app, which then calls {@code POST /auth/refresh} to obtain an
 * access token (so no token ever appears in a URL).
 */
@RestController
@RequestMapping("/api/v1/auth/google")
@Tag(name = "Auth")
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    private final GoogleOAuthService googleOAuth;
    private final RefreshTokenCookies cookies;
    private final String webBaseUrl;

    public GoogleAuthController(GoogleOAuthService googleOAuth, RefreshTokenCookies cookies, AppProperties properties) {
        this.googleOAuth = googleOAuth;
        this.cookies = cookies;
        this.webBaseUrl = properties.web().baseUrl();
    }

    @GetMapping
    @Operation(summary = "Start Google sign-in (redirects to Google)")
    @ApiResponse(responseCode = "302", description = "Redirect to Google's consent screen")
    @ApiResponse(responseCode = "503", description = "FEATURE_DISABLED when Google is not configured")
    public ResponseEntity<Void> start(@RequestParam(name = "redirect", required = false) String redirect) {
        return ResponseEntity.status(HttpStatus.FOUND).location(googleOAuth.authorizationRedirect(redirect)).build();
    }

    @GetMapping("/callback")
    @Operation(summary = "Google redirects here; signs the user in and redirects to the web app")
    @ApiResponse(responseCode = "302", description = "To /auth/complete on success, /login?error=google on failure")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest http) {
        if (error != null) {
            return redirectToLogin("google_" + ("access_denied".equals(error) ? "cancelled" : "failed"));
        }
        try {
            GoogleLogin login = googleOAuth.complete(code, state, ClientInfo.from(http));
            URI target = UriComponentsBuilder.fromUriString(webBaseUrl).path("/auth/complete")
                    .queryParam("redirect", login.redirectPath()).encode().build().toUri();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, cookies.issue(login.result().refreshToken().value(),
                            login.result().refreshToken().maxAgeSeconds()))
                    .location(target)
                    .build();
        } catch (AppException e) {
            log.info("Google sign-in failed: {}", e.getMessage());
            return redirectToLogin("google_failed");
        }
    }

    private ResponseEntity<Void> redirectToLogin(String errorCode) {
        URI target = UriComponentsBuilder.fromUriString(webBaseUrl).path("/login")
                .queryParam("error", errorCode).encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
