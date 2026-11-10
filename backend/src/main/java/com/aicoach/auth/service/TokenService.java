package com.aicoach.auth.service;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.security.JwtConfig;
import com.aicoach.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens. The role is read from the database at issue time, never trusted from input. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties.Jwt config;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, AppProperties properties, Clock clock) {
        this.encoder = encoder;
        this.config = properties.auth().jwt();
        this.clock = clock;
    }

    public record AccessToken(String value, long expiresInSeconds) {
        @Override
        public String toString() {
            return "AccessToken[***, expiresIn=" + expiresInSeconds + "]";
        }
    }

    public AccessToken issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .audience(List.of(config.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(config.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(JwtConfig.ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(config.keyId()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, config.accessTokenTtl().toSeconds());
    }
}
