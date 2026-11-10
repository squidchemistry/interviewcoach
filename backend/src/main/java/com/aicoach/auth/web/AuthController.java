package com.aicoach.auth.web;

import com.aicoach.auth.dto.AuthResponse;
import com.aicoach.auth.dto.LoginRequest;
import com.aicoach.auth.dto.ProvidersResponse;
import com.aicoach.auth.dto.RegisterRequest;
import com.aicoach.auth.service.AuthService;
import com.aicoach.auth.service.AuthService.AuthResult;
import com.aicoach.auth.service.GoogleOAuthService;
import com.aicoach.common.security.RefreshTokenCookies;
import com.aicoach.common.web.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuthService googleOAuth;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, GoogleOAuthService googleOAuth, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.googleOAuth = googleOAuth;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account with email and password; signs the user in")
    @ApiResponse(responseCode = "201", description = "Created; refresh token set as httpOnly cookie")
    @ApiResponse(responseCode = "409", description = "EMAIL_TAKEN")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return withCookie(HttpStatus.CREATED, authService.register(request, ClientInfo.from(http)));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED after repeated failures (Retry-After header)")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withCookie(HttpStatus.OK, authService.login(request, ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a new access token (the cookie is rotated)")
    @ApiResponse(responseCode = "401", description = "Missing, expired, revoked or reused refresh token")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        cookies.assertTrustedOrigin(http);
        return withCookie(HttpStatus.OK, authService.refresh(cookies.read(http), ClientInfo.from(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current session (refresh-token family) and clear the cookie")
    @ApiResponse(responseCode = "204", description = "Logged out (idempotent)")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        cookies.assertTrustedOrigin(http);
        authService.logout(cookies.read(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @GetMapping("/providers")
    @Operation(summary = "Sign-in methods available on this server")
    public ProvidersResponse providers() {
        return new ProvidersResponse(true, googleOAuth.enabled());
    }

    private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.refreshToken().value(), result.refreshToken().maxAgeSeconds()))
                .body(result.response());
    }
}
