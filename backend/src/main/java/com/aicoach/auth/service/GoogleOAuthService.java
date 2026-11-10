package com.aicoach.auth.service;

import com.aicoach.auth.service.AuthService.AuthResult;
import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.security.Hashing;
import com.aicoach.common.web.ClientInfo;
import com.aicoach.user.service.GoogleIdentity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google sign-in via the OpenID Connect authorization-code flow with PKCE (RFC 7636).
 * <ol>
 *   <li>{@link #authorizationRedirect}: creates a one-time {@code state} (CSRF protection) and a PKCE verifier,
 *       stores them in Redis for 10 minutes and returns Google's consent URL.</li>
 *   <li>{@link #complete}: atomically consumes the state (GETDEL, so it cannot be replayed), exchanges the code
 *       with the verifier, verifies the ID token signature (Google JWKS), issuer, audience and expiry, then signs the
 *       user in.</li>
 * </ol>
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);
    static final String STATE_PREFIX = "auth:oauth:google:";
    static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final AppProperties.Google config;
    private final StringRedisTemplate redis;
    private final AuthService authService;
    private final RestClient http;
    private final Clock clock;
    private volatile JwtDecoder idTokenDecoder;

    public GoogleOAuthService(AppProperties properties, StringRedisTemplate redis, AuthService authService,
            RestClient.Builder restClientBuilder, Clock clock) {
        this.config = properties.auth().google();
        this.redis = redis;
        this.authService = authService;
        this.clock = clock;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.http = restClientBuilder.requestFactory(requestFactory).build();
    }

    public boolean enabled() {
        return config.enabled();
    }

    public URI authorizationRedirect(String redirectPath) {
        requireEnabled();
        String state = Hashing.randomToken(32);
        String verifier = Hashing.randomToken(48);
        redis.opsForValue().set(STATE_PREFIX + state, verifier + "|" + SafeRedirects.sanitize(redirectPath), STATE_TTL);
        return UriComponentsBuilder.fromUriString(config.authorizationUri())
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("code_challenge", Hashing.pkceChallenge(verifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("prompt", "select_account")
                .encode()
                .build()
                .toUri();
    }

    /** Outcome of a completed Google sign-in: the session plus where the browser should go next. */
    public record GoogleLogin(AuthResult result, String redirectPath) {
    }

    public GoogleLogin complete(String code, String state, ClientInfo client) {
        requireEnabled();
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw failure("Google did not return an authorization code");
        }
        String stored = redis.opsForValue().getAndDelete(STATE_PREFIX + state);
        if (stored == null) {
            throw failure("The sign-in attempt expired or was already used. Please try again.");
        }
        int separator = stored.indexOf('|');
        String verifier = stored.substring(0, separator);
        String redirectPath = SafeRedirects.sanitize(stored.substring(separator + 1));

        GoogleIdentity identity = verify(exchange(code, verifier));
        return new GoogleLogin(authService.loginWithGoogle(identity, client), redirectPath);
    }

    record TokenResponse(@JsonProperty("id_token") String idToken) {
    }

    private String exchange(String code, String verifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("redirect_uri", config.redirectUri());
        form.add("code_verifier", verifier);
        try {
            TokenResponse response = http.post().uri(config.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || !StringUtils.hasText(response.idToken())) {
                throw failure("Google did not return an ID token");
            }
            return response.idToken();
        } catch (RestClientException e) {
            log.warn("Google token exchange failed: {}", e.toString());
            throw failure("Google sign-in failed. Please try again.");
        }
    }

    GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            log.warn("Rejected Google ID token: {}", e.getMessage());
            throw failure("Google sign-in could not be verified");
        }
        Object verified = jwt.getClaims().get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), emailVerified,
                jwt.getClaimAsString("name"));
    }

    private JwtDecoder decoder() {
        JwtDecoder decoder = idTokenDecoder;
        if (decoder == null) {
            synchronized (this) {
                if (idTokenDecoder == null) {
                    NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(config.jwkSetUri()).build();
                    JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(60));
                    timestamps.setClock(clock);
                    String clientId = config.clientId();
                    nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            timestamps,
                            new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                                    iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString())),
                            new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                                    aud -> aud != null && aud.contains(clientId))));
                    idTokenDecoder = nimbus;
                }
                decoder = idTokenDecoder;
            }
        }
        return decoder;
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new AppException(ErrorCode.FEATURE_DISABLED, "Google sign-in is not configured on this server");
        }
    }

    private static AppException failure(String detail) {
        return new AppException(ErrorCode.UNAUTHENTICATED, detail);
    }
}
