package com.aicoach.common.security;

import com.aicoach.common.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Builds the production JWT encoder/decoder (same code paths as {@link JwtConfig}) for unit tests. */
public final class JwtTestSupport {

    public static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=";
    public static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci1zZWNyZXQtb3RoZXI=";

    private JwtTestSupport() {
    }

    public static AppProperties properties(AppProperties.Jwt jwt) {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173"), 600),
                new AppProperties.Http(1024),
                new AppProperties.Web("http://localhost:5173"),
                new AppProperties.Auth(jwt,
                        new AppProperties.Refresh(Duration.ofDays(7), Duration.ofSeconds(30)),
                        new AppProperties.Cookie("aic_rt", true),
                        new AppProperties.Login(5, Duration.ofMinutes(15)),
                        new AppProperties.Google(null, null, null, "a", "t", "j")),
                new AppProperties.RateLimit(true, new AppProperties.Bucket(10, Duration.ofMinutes(1)),
                        new AppProperties.Bucket(30, Duration.ofMinutes(1)), new AppProperties.Bucket(120, Duration.ofMinutes(1))),
                new AppProperties.Seed(false, null, null));
    }

    public static JwtEncoder encoder(AppProperties.Jwt jwt) {
        return new JwtConfig().jwtEncoder(JwtConfig.signingKey(jwt.secret()), properties(jwt));
    }

    public static JwtDecoder decoder(AppProperties.Jwt jwt, Clock clock) {
        return new JwtConfig().jwtDecoder(JwtConfig.signingKey(jwt.secret()), properties(jwt), clock);
    }
}
