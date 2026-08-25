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
# Sprint 38 audit (D38-5): Trivy config scan (DS-0002) flagged this image for running as root —
# nothing here needs root (no port <1024 to bind, no privileged operation), so drop to a
# dedicated non-root user, the standard minimal fix for that finding.
RUN addgroup -S brika && adduser -S brika -G brika
USER brika
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
