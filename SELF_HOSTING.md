# Self-Hosting Guide

This guide covers configuration and deployment options for running Playbot yourself.

## Prerequisites
- Java 21+
- Maven 3.6+
- Node.js 18+ (for building the frontend)
- Discord Application with a Bot and OAuth2 credentials
- Reverse proxy (recommended): Nginx, Caddy, or similar

## Required Environment Variables
Create a `.env` file at the project root or otherwise provide these values:

```env
DISCORD_TOKEN=your_bot_token
DISCORD_CLIENT_ID=your_oauth_client_id
DISCORD_CLIENT_SECRET=your_oauth_client_secret
# Frontend origin for redirects/CORS (change in production)
ADMIN_PANEL_URL=http://localhost:3000

# Optional cookie flags (enable in production)
COOKIE_SECURE=true
COOKIE_SAME_SITE=strict
```

OAuth2 redirect URIs to register in Discord Developer Portal:
- Development: `http://localhost:8080/login/oauth2/code/discord`
- Production: `https://your-domain.com/login/oauth2/code/discord`

## Build and Run (local)

```bash
# Backend + Frontend build
chmod +x build.sh
./build.sh

# Run both services for local dev
chmod +x start.sh
./start.sh
```

Backend: http://localhost:8080  
Frontend: http://localhost:3000

## Reverse Proxy
- Terminate TLS and set `X-Forwarded-Proto` and `X-Forwarded-Host`
- Restrict CORS to your frontend origin
- Enable HSTS in production

## Database and Migrations
- Uses H2 file DB by default for persistence: `./data/playbot`
- Liquibase applies migrations automatically on startup
- For a managed DB (e.g., Postgres), update `spring.datasource.*` and dialect settings accordingly

## Production Hardening

Apply these recommendations when running Playbot in production:

### AuthZ/AuthN
- Restrict admin UI/API to Discord users with Admin or Manage Server in the target guild
- Keep Discord Client Secret and Bot Token in a secret manager or environment variables

### CSRF/CORS
- CSRF enabled for REST via cookie token; verify clients echo `XSRF-TOKEN` in `X-XSRF-TOKEN`
- Exempt only `/ws/**` to allow WebSocket upgrades
- CORS: set allowed origin to your frontend origin (`ADMIN_PANEL_URL`), not `*`

### Cookies/Sessions
- Set `server.servlet.session.cookie.secure=true` and `server.servlet.session.cookie.same-site=strict` (or `lax` if redirects require)
- Persist sessions and OAuth clients via JDBC (Liquibase tables); back up DB

### TLS/Proxy
- Terminate TLS at a reverse proxy (e.g., Nginx) with modern ciphers
- Enable HSTS at the proxy
- Ensure proxy sets `X-Forwarded-Proto` and `X-Forwarded-Host`; Spring will respect via `ForwardedHeaderFilter`

### Abuse controls
- Add rate limiting at the proxy/API gateway (per-IP and per-session)
- Consider WAF or fail2ban for brute-force patterns

### Logging/Monitoring
- Use INFO in production; avoid logging secrets or PII
- Centralize logs and alert on spikes of 401/403/5xx

### Migrations and Upgrades
- Liquibase is authoritative; Hibernate should be `validate`
- Forward-only changesets; use preconditions with `onFail="CONTINUE"`
- Use the nullable → backfill → not-null pattern for existing data
- Back up DB prior to migration

### Dependencies/OS
- Keep Java, Spring Boot, and libraries updated; scan dependencies
- Patch the OS/container base images regularly

## Troubleshooting
- OAuth2 redirect mismatch: verify Discord redirect URI matches exactly
- CSRF errors on POST/PUT/DELETE: ensure frontend echoes `XSRF-TOKEN` cookie in `X-XSRF-TOKEN` header
- Bot permissions errors: check role hierarchy and required permissions

## Next Steps
- Review the Production Hardening section above for deployment best practices
- See [WEB_API_README.md](WEB_API_README.md) for detailed API and WebSocket docs
- See [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md) for schema changes and migration patterns
