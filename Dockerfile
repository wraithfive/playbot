# Use Eclipse Temurin (formerly AdoptOpenJDK) as base image
# Explicitly specify amd64 platform for x86_64 servers
FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Create logs directory
RUN mkdir -p /app/logs

# Copy the JAR file
COPY target/discord-bot-1.0.0.jar /app/discord-bot.jar

# Copy logback configuration
COPY src/main/resources/logback.xml /app/logback.xml

# Create a non-root user to run the application
RUN addgroup -S discordbot && adduser -S discordbot -G discordbot

# Change ownership of the app directory
RUN chown -R discordbot:discordbot /app

# Switch to non-root user
USER discordbot

# Environment variable for the bot token (will be provided at runtime)
ENV DISCORD_TOKEN=""

# Expose no ports (Discord bot only makes outbound connections)

# Health check (optional - checks if process is running)
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD pgrep -f discord-bot.jar || exit 1

# Run the bot
CMD ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/discord-bot.jar"]
