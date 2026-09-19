# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy dependency descriptors first for better layer caching
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -q

# Copy source and build the fat jar
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Run ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy only the fat jar from the build stage
COPY --from=build /app/target/agent-0.0.1-SNAPSHOT.jar app.jar

# Render injects PORT env var — Spring Boot reads SERVER_PORT
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
