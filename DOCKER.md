# Docker Deployment Guide

Quick guide to run Playbot (Discord bot + web admin panel) using Docker.

## Prerequisites

- Docker installed on your host
- Docker Compose (usually comes with Docker)
- Your Discord bot credentials (token, client ID, client secret)
- A public URL for the admin panel (for production)

## Quick Start

### 1. Build the JAR (if not already built)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn clean package -DskipTests
```

### 2. Configure environment variables

```bash
# Create .env file with all required credentials
cat > .env << EOF
# Discord Bot Configuration
DISCORD_TOKEN=your_bot_token_here
DISCORD_CLIENT_ID=your_client_id_here
DISCORD_CLIENT_SECRET=your_client_secret_here

# Admin Panel URL (use your public domain in production)
ADMIN_PANEL_URL=https://yourdomain.com

# Cookie Security (set to true in production with HTTPS)
COOKIE_SECURE=true
COOKIE_SAME_SITE=Lax
EOF
```

### 3. Build and Run with Docker Compose

```bash
# Build the Docker image
docker-compose build

# Start the bot and web server
docker-compose up -d

# View logs
docker-compose logs -f
```

The web admin panel will be available at http://localhost:8080 (or your ADMIN_PANEL_URL in production).

## Manual Docker Commands (Alternative)

If you prefer not to use docker-compose:

```bash
# Build the image
docker build -t playbot:latest .

# Run the container
docker run -d \
  --name playbot \
  --restart unless-stopped \
  -p 8080:8080 \
  -e DISCORD_TOKEN="your_bot_token_here" \
  -e DISCORD_CLIENT_ID="your_client_id_here" \
  -e DISCORD_CLIENT_SECRET="your_client_secret_here" \
  -e ADMIN_PANEL_URL="https://yourdomain.com" \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/data:/app/data \
  playbot:latest
```

## Management Commands

```bash
# View logs
docker-compose logs -f playbot

# Stop the application
docker-compose stop

# Start the application
docker-compose start

# Restart the application
docker-compose restart

# Stop and remove container
docker-compose down

# View application status
docker-compose ps

# Check health
docker-compose exec playbot wget --no-verbose --tries=1 --spider http://localhost:8080/api/health

# Execute commands in the container
docker-compose exec playbot sh
```

## Updating the Application

```bash
# 1. Stop the application
docker-compose down

# 2. Rebuild the JAR (if you made code changes)
mvn clean package -DskipTests

# 3. Rebuild the Docker image
docker-compose build

# 4. Start the application
docker-compose up -d
```

## Production Deployment

### Reverse Proxy Setup (Nginx)

For production, use a reverse proxy like Nginx with SSL:

```nginx
# /etc/nginx/sites-available/playbot
server {
    listen 443 ssl http2;
    server_name yourdomain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    # WebSocket support
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400;
    }

    # API and admin panel
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Environment Variables for Production

Update your `.env` file:

```bash
DISCORD_TOKEN=your_production_token
DISCORD_CLIENT_ID=your_client_id
DISCORD_CLIENT_SECRET=your_client_secret
ADMIN_PANEL_URL=https://yourdomain.com
COOKIE_SECURE=true
COOKIE_SAME_SITE=Lax
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
