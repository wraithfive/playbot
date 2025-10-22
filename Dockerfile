# Use Eclipse Temurin as base image with Java 21
# Explicitly specify amd64 platform for x86_64 servers
FROM --platform=linux/amd64 eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Create logs and data directories
RUN mkdir -p /app/logs /app/data

# Copy the JAR file
COPY target/playbot-1.0.0.jar /app/playbot.jar

# Copy logback configuration
COPY src/main/resources/logback.xml /app/logback.xml

# Create a non-root user to run the application
RUN addgroup -S playbot && adduser -S playbot -G playbot

# Change ownership of the app directory
RUN chown -R playbot:playbot /app

# Switch to non-root user
USER playbot

# Environment variables (will be provided at runtime via .env or docker-compose)
ENV DISCORD_TOKEN=""
ENV DISCORD_CLIENT_ID=""
ENV DISCORD_CLIENT_SECRET=""
ENV ADMIN_PANEL_URL=""

# Expose port 8080 for the web API and admin panel
EXPOSE 8080

# Health check - verify the Spring Boot actuator health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

# Run the application
CMD ["java", "-Xms512m", "-Xmx1g", "-jar", "/app/playbot.jar"]
