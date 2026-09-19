# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy application files
COPY --from=build /app/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/target/quarkus-app/*.jar ./
COPY --from=build /app/target/quarkus-app/app/ ./app/
COPY --from=build /app/target/quarkus-app/quarkus/ ./quarkus/

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8181

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8181/q/health || exit 1

# JVM optimization for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-jar", "/app/quarkus-run.jar"]
