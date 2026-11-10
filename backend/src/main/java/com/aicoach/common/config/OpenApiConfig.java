package com.aicoach.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String NULLABLE_EXTENSION = "x-nullable";

    @Bean
    OpenAPI aiCoachOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        String version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
        return new OpenAPI()
                .info(new Info()
                        .title("AI Interview Coach API")
                        .version(version)
                        .description("Mock interviews with an AI interviewer and rubric-based feedback. "
                                + "Errors use RFC 7807 problem+json with `code` and `requestId` extension members.")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token from /api/v1/auth/login, /register or /refresh")));
    }

    /**
     * Marks record components annotated {@code @Nullable} with an extension. The final {@link OpenApiCustomizer}
     * pass turns it into OpenAPI 3.1 nullability ({@code anyOf: [schema, {type: "null"}]}) and leaves the property
     * out of {@code required}. (OpenAPI 3.1 has no {@code nullable} keyword, and springdoc rebuilds property schemas
     * after property customizers run, so the marker is applied at the very end.)
     */
    @Bean
    PropertyCustomizer nullableProperties() {
        return (property, type) -> {
            Annotation[] annotations = type.getCtxAnnotations();
            boolean nullable = annotations != null && Arrays.stream(annotations)
                    .anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"));
            if (nullable) {
                property.addExtension(NULLABLE_EXTENSION, true);
            }
            return property;
        };
    }

    /**
     * Marks every property of response schemas as required unless it is explicitly {@code nullable}.
     * Java records always serialise every component, so this makes the generated TypeScript types precise
     * ({@code name: string} instead of {@code name?: string}).
     */
    @Bean
    OpenApiCustomizer requiredResponseProperties() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (!name.endsWith("Response") || schema.getProperties() == null) {
                    return;
                }
                List<String> required = new ArrayList<>();
                Map<String, Schema> properties = (Map<String, Schema>) schema.getProperties();
                for (Map.Entry<String, Schema> property : properties.entrySet()) {
                    Schema<?> value = property.getValue();
                    if (value.getExtensions() != null && value.getExtensions().remove(NULLABLE_EXTENSION) != null) {
                        property.setValue(nullableVariant(value));
                    } else {
                        required.add(property.getKey());
                    }
                }
                schema.setRequired(required);
            });
        };
    }

    /** {@code anyOf: [<original without description>, {type: "null"}]}, keeping the description on the wrapper. */
    private static Schema<?> nullableVariant(Schema<?> original) {
        String description = original.getDescription();
        original.setDescription(null);
        if (original.getExtensions() != null && original.getExtensions().isEmpty()) {
            original.setExtensions(null);
        }
        Schema<?> target = original;
        if (original.getAllOf() != null && original.getAllOf().size() == 1 && original.get$ref() == null
                && original.getProperties() == null) {
            target = original.getAllOf().getFirst();
        }
        JsonSchema wrapper = new JsonSchema();
        wrapper.setAnyOf(List.of(target, new JsonSchema().types(Set.of("null"))));
        wrapper.setDescription(description);
        return wrapper;
    }
}
