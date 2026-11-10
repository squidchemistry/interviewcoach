# syntax=docker/dockerfile:1.7

# ---- Build: compile and package (tests run in CI / `mvnw verify`, not in the image build) ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests package \
    && java -Djarmode=tools -jar target/ai-interview-coach-backend-*.jar \
       extract --layers --launcher --destination target/extracted

# ---- Runtime: JRE only, non-root, layered for small rebuilds ----
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER app
# 8080: API · 8081: management (health/metrics) — keep 8081 on the internal network only.
EXPOSE 8080 8081
# --sun-misc-unsafe-memory-access=allow: Netty (Lettuce) still uses sun.misc.Unsafe; allow it explicitly on JDK 25.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError --sun-misc-unsafe-memory-access=allow"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
