package com.aicoach.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private static final List<String> VALID = List.of(
            "app.cors.allowed-origins=http://localhost:5173,https://coach.example.com",
            "app.cors.max-age-seconds=600",
            "app.http.max-body-bytes=1024",
            "app.web.base-url=http://localhost:5173",
            "app.auth.jwt.secret=c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldC1zZWNyZXQ=",
            "app.auth.jwt.issuer=iss",
            "app.auth.jwt.audience=aud",
            "app.auth.jwt.access-token-ttl=15m",
            "app.auth.jwt.key-id=k1",
            "app.auth.refresh.ttl=7d",
            "app.auth.refresh.reuse-grace=30s",
            "app.auth.cookie.name=aic_rt",
            "app.auth.cookie.secure=true",
            "app.auth.login.max-failures=5",
            "app.auth.login.lockout=15m",
            "app.auth.google.authorization-uri=https://accounts.google.com/o/oauth2/v2/auth",
            "app.auth.google.token-uri=https://oauth2.googleapis.com/token",
            "app.auth.google.jwk-set-uri=https://www.googleapis.com/oauth2/v3/certs",
            "app.rate-limit.enabled=true",
            "app.rate-limit.auth.capacity=10", "app.rate-limit.auth.period=1m",
            "app.rate-limit.refresh.capacity=30", "app.rate-limit.refresh.period=1m",
            "app.rate-limit.api.capacity=120", "app.rate-limit.api.period=1m",
            "app.seed.enabled=false");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    private ApplicationContextRunner with(String... overrides) {
        List<String> props = new ArrayList<>(VALID);
        props.addAll(List.of(overrides));
        return runner.withPropertyValues(props.toArray(String[]::new));
    }

    @Test
    void bindsValidConfigurationIncludingCommaSeparatedOrigins() {
        with().run(context -> {
            assertThat(context).hasNotFailed();
            AppProperties props = context.getBean(AppProperties.class);
            assertThat(props.cors().allowedOrigins())
                    .containsExactly("http://localhost:5173", "https://coach.example.com");
            assertThat(props.http().maxBodyBytes()).isEqualTo(1024);
            assertThat(props.auth().google().enabled()).isFalse();
            assertThat(props.rateLimit().api().capacity()).isEqualTo(120);
        });
    }

    @Test
    void googleIsEnabledOnlyWhenClientIdSecretAndRedirectAreAllSet() {
        with("app.auth.google.client-id=id", "app.auth.google.client-secret=secret")
                .run(context -> assertThat(context.getBean(AppProperties.class).auth().google().enabled()).isFalse());
        with("app.auth.google.client-id=id", "app.auth.google.client-secret=secret",
                "app.auth.google.redirect-uri=http://localhost:8088/api/v1/auth/google/callback")
                .run(context -> assertThat(context.getBean(AppProperties.class).auth().google().enabled()).isTrue());
    }

    @Test
    void rejectsOriginWithoutScheme() {
        with("app.cors.allowed-origins=localhost:5173").run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("must be an exact origin"));
    }

    @Test
    void rejectsWildcardOrigin() {
        with("app.cors.allowed-origins=*").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveBodyLimit() {
        with("app.http.max-body-bytes=0").run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("maxBodyBytes"));
    }

    @Test
    void requiresJwtSecret() {
        with("app.auth.jwt.secret=").run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @Test
    void rejectsWebBaseUrlWithPath() {
        with("app.web.base-url=http://localhost:5173/app").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingSections() {
        runner.run(context -> assertThat(context).hasFailed());
    }
}
