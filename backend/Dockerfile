# Sprint 0 skeleton image — infrastructure only, no business logic.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/brika-backend-*.jar app.jar
# Sprint 39 audit (D39-3): eclipse-temurin:21-jre-alpine is a floating tag — its baked-in Alpine
# packages lag behind Alpine's own v3.23 repo the moment new CVEs are disclosed and a fixed build
# is published upstream. A first Trivy pass found libexpat/p11-kit/p11-kit-trust/sqlite-libs
# behind fixes already published in this image's own configured repo; a same-day re-scan (after
# Trivy's vulnerability DB itself refreshed) then surfaced a freshly-disclosed HIGH in
# libssl3/libcrypto3/openssl (CVE-2026-14456, QUIC-server DoS — not exploitable here, this JVM app
# uses its own TLS stack, no native OpenSSL calls, no QUIC server anywhere in this image) with its
# own fix already published in the same repo. Rather than keep extending an explicit package list
# every time the CVE feed moves, upgrade everything already available from the same trusted repo —
# same minimal-risk category as the targeted fix (no base-image swap, no architecture change),
# just not chasing a moving target package-by-package.
RUN apk update && apk upgrade --no-cache
# Sprint 38 audit (D38-5): Trivy config scan (DS-0002) flagged this image for running as root —
# nothing here needs root (no port <1024 to bind, no privileged operation), so drop to a
# dedicated non-root user, the standard minimal fix for that finding.
RUN addgroup -S brika && adduser -S brika -G brika
USER brika
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
