# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately for faster incremental builds
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# application.yml is gitignored (it can hold real credentials), so use the
# committed example as the default config. Secrets come from env vars at runtime.
RUN cp src/main/resources/application-example.yml src/main/resources/application.yml

RUN mvn -B package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
# Force IPv4: Render free tier has no IPv6 egress, and Supabase resolves to IPv6
# first, causing "Network is unreachable" on connection.
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
