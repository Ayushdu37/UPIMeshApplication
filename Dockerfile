# ==============================================================================
# Build Stage: Maven compilation & artifact packaging
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production JAR
COPY src ./src
RUN mvn package -DskipTests -B

# ==============================================================================
# Production Runtime Stage: Lightweight JRE execution
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Create non-root app user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy built JAR artifact from builder stage
COPY --from=builder /app/target/upi-offline-mesh-*.jar app.jar

# Expose Spring Boot default port
EXPOSE 8080

# Environment defaults
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8080

# Start Application
ENTRYPOINT ["java", "-jar", "app.jar"]
