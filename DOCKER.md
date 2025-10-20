# Docker Deployment Guide

Quick guide to run the Color Gacha Bot using Docker.

## Prerequisites

- Docker installed on your host
- Docker Compose (usually comes with Docker)
- Your Discord bot token

## Quick Start

### 1. Build the JAR (if not already built)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean package -DskipTests
```

### 2. Make sure .env file exists with your bot token

```bash
# Create .env file
cat > .env << EOF
DISCORD_TOKEN=your_bot_token_here
EOF
```

### 3. Build and Run with Docker Compose

```bash
# Build the Docker image
docker-compose build

# Start the bot
docker-compose up -d

# View logs
docker-compose logs -f
```

## Manual Docker Commands (Alternative)

If you prefer not to use docker-compose:

```bash
# Build the image
docker build -t color-gacha-bot:latest .

# Run the container
docker run -d \
  --name discord-bot \
  --restart unless-stopped \
  -e DISCORD_TOKEN="your_bot_token_here" \
  -v $(pwd)/logs:/app/logs \
  color-gacha-bot:latest
```

## Management Commands

```bash
# View logs
docker-compose logs -f discord-bot

# Stop the bot
docker-compose stop

# Start the bot
docker-compose start

# Restart the bot
docker-compose restart

# Stop and remove container
docker-compose down

# View bot status
docker-compose ps

# Execute commands in the container
docker-compose exec discord-bot sh
```

## Updating the Bot

```bash
# 1. Stop the bot
docker-compose down

# 2. Rebuild the JAR (if you made code changes)
mvn clean package -DskipTests

# 3. Rebuild the Docker image
docker-compose build

# 4. Start the bot
docker-compose up -d
```

## Logs

Logs are stored in two places:

1. **Container logs** (Docker's logging):
   ```bash
   docker-compose logs -f
   ```

2. **Application logs** (mounted volume at `./logs/`):
   ```bash
   tail -f logs/color-gacha-bot.log
   tail -f logs/color-gacha-bot-error.log
   tail -f logs/color-gacha-bot-activity.log
   ```

## Resource Limits

The default `docker-compose.yml` sets:
- CPU limit: 1 core
- Memory limit: 512 MB
- Memory reservation: 256 MB

Adjust in `docker-compose.yml` if needed:

```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'      # Increase CPU
      memory: 1G       # Increase RAM
    reservations:
      memory: 512M
```

## Troubleshooting

### Bot won't start

```bash
# Check container status
docker-compose ps

# View detailed logs
docker-compose logs discord-bot

# Check if token is set
docker-compose exec discord-bot env | grep DISCORD_TOKEN
```

### Out of Memory

If the bot crashes with OOM:

1. Edit `docker-compose.yml` - increase memory limit
2. Edit `Dockerfile` - reduce Java heap: `-Xmx256m`
3. Rebuild and restart

### Permission Issues with Logs

```bash
# Fix log directory permissions
chmod 755 logs
```

### Container keeps restarting

```bash
# Check logs for errors
docker-compose logs --tail=50 discord-bot

# Common causes:
# - Invalid bot token
# - Network issues
# - Out of memory
```

## Running on Different Architectures

The image uses `eclipse-temurin:17-jre-alpine` which supports:
- x86_64 (AMD64)
- ARM64 (Apple Silicon, Raspberry Pi 4)
- ARMv7 (Raspberry Pi 3)

Docker will automatically pull the correct architecture.

## Deploying to Your Docker Host

### Option 1: Copy files via SCP

```bash
# From your local machine
scp -r ~/projects/playbot user@dockerhost:/path/to/deploy/

# SSH to Docker host
ssh user@dockerhost

# Navigate to directory
cd /path/to/deploy/playbot

# Start the bot
docker-compose up -d
```

### Option 2: Use Git

```bash
# On your Docker host
git clone https://github.com/yourusername/playbot.git
cd playbot

# Create .env file
nano .env
# Add: DISCORD_TOKEN=your_token_here

# Start the bot
docker-compose up -d
```

### Option 3: Build locally, transfer image

```bash
# Build on local machine
docker build -t color-gacha-bot:latest .

# Save image to tar
docker save color-gacha-bot:latest > color-gacha-bot.tar

# Transfer to Docker host
scp color-gacha-bot.tar user@dockerhost:/tmp/

# On Docker host, load the image
docker load < /tmp/color-gacha-bot.tar

# Run with docker-compose or docker run
```

## Docker Compose with Portainer

If you use Portainer for Docker management:

1. Create a new Stack
2. Paste the contents of `docker-compose.yml`
3. Add environment variable: `DISCORD_TOKEN=your_token`
4. Deploy the stack

## Health Monitoring

The Docker image includes a health check:

```bash
# View health status
docker inspect discord-bot | grep -A 10 Health

# Health check runs every 30 seconds
# Marks unhealthy after 3 failed checks
```

## Auto-restart on System Boot

The `restart: unless-stopped` policy ensures:
- Bot restarts if it crashes
- Bot starts automatically on system boot
- Bot stays stopped if you manually stop it

## Backup

Backup your bot configuration:

```bash
# Backup .env and logs
tar -czf discord-bot-backup-$(date +%Y%m%d).tar.gz .env logs/

# Transfer to safe location
scp discord-bot-backup-*.tar.gz user@backup-server:/backups/
```

## Next Steps

- Set up automated backups
- Configure monitoring/alerting
- Set up log rotation (already configured in logback.xml)
- Consider using Docker secrets for the bot token (for production)
