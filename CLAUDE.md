# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Playbot is a Discord bot with a web admin panel that allows users to roll for random colored name roles once per day. Built with Java 21 + Spring Boot 3.4 backend (JDA for Discord) and React 19 + TypeScript frontend.

**Core Features:**
- Discord bot with slash commands for a color role gacha system (with optional d20 risk/reward mechanic)
- Question of the Day (QOTD) system with multiple independent streams per channel
- Web-based admin panel with Discord OAuth2 authentication
- H2 file-based database with Liquibase-managed schema

## Build and Development Commands

```bash
# Build both backend and frontend (runs ALL tests by default)
./build.sh
./build.sh --skip-tests      # Skip tests
./build.sh --clean           # Clean before building
./build.sh --production      # Production build

# Backend only
mvn clean package            # With tests
mvn test                     # Backend tests only
mvn test -Dtest=ClassName            # Single test class
mvn test -Dtest=ClassName#methodName # Single test method

# Frontend (from frontend/ directory)
npm run dev              # Vite dev server on port 3000
npm run build            # Production build (runs generate:legal + tsc first)
npm run lint             # ESLint
npm test                 # Vitest watch mode
npm run test:unit        # Run once (CI mode)
npm run test:coverage    # With coverage
npm run test:e2e         # Playwright E2E tests

# Run the app
./start.sh               # Both services (recommended)
java -jar target/playbot-1.0.0.jar   # Backend only (port 8080)
```

Notes:
- `build.sh` auto-detects Java 21 on macOS via `/usr/libexec/java_home -v 21`.
- `Bot.java` and `SlashCommandHandler*.class` are excluded from JaCoCo coverage (Discord bootstrap code), configured in `pom.xml`.

## Database & Migrations (CRITICAL)

**Liquibase owns ALL schema changes.** `spring.jpa.hibernate.ddl-auto=validate` — Hibernate only validates, never creates or alters tables.

- **Never modify a JPA entity expecting DDL to follow.** Instead create a Liquibase changeset in `src/main/resources/db/changelog/changes/` (numbered `XXX-description.xml`) and add an `<include>` for it in `db/changelog/db.changelog-master.xml`.
- **Never modify a deployed changeset** — always create a new one to fix issues.
- **NOT NULL columns** require the 3-step process (add nullable → backfill → add constraint). See `DATABASE_MIGRATIONS.md`.
- Liquibase also manages the `SPRING_SESSION*` and `oauth2_authorized_client` tables (`spring.session.jdbc.initialize-schema=never`, `spring.sql.init.mode=never`).
- H2 database file lives at `./data/playbot.mv.db`.

## Architecture

### Backend Structure (`src/main/java/com/discordbot/`)

**Main Entry Point:** `Bot.java` — Spring Boot application that initializes both the Discord bot (JDA) and web server. Loads `.env` and sets entries as system properties.

**Discord Bot Layer:**
- `SlashCommandHandler.java` — All slash commands: `/roll`, `/d20`, `/testroll` (admin, no cooldown), `/mycolor`, `/colors`, `/help`, `/qotd-submit`. Commands are registered per-guild in `onGuildReady()` and on guild join.
- `ColorGachaHandler.java` — Gacha core logic (rarity enum/weights, role name parsing) plus legacy message command handling.

**Web Layer:**
- `web/controller/` — REST controllers: `ServerController`, `RoleController`, `QotdController`, `QotdStreamController`, `CsrfController`, `DiagnosticsController`, `HealthController`, `AuthRedirectController`
- `web/service/` — Business logic: `AdminService` (permission validation), `QotdService`, `QotdStreamService`, `QotdScheduler` (Spring `@Scheduled` posting), `QotdSubmissionService`, `RateLimitService` (Bucket4j), `GuildsCache`, `WebSocketNotificationService`
- `SecurityConfig.java` — Spring Security with Discord OAuth2

**Data Layer:**
- `entity/` — JPA entities: `UserCooldown` (roll cooldowns + d20 state), `QotdStream` (per-stream config), `QotdQuestion`, `QotdSubmission`
- `repository/` — Spring Data JPA repositories
- `web/dto/` — API DTOs as Java Records

### Frontend Structure (`frontend/src/`)

- `main.tsx` / `App.tsx` — Entry point and routing; all styles centralized in `App.css`
- `components/` — `Login`, `ServerList`, `RoleManager`, `QotdManager`, `Navbar`, `Footer`, plus `PrivacyPolicy.tsx` / `TermsOfService.tsx` (generated — edit the `.template.tsx` files and run `npm run generate:legal`)
- `api/client.ts` — Axios instance with CSRF token handling
- State management: TanStack React Query for server state

### Authentication & Security

- **Discord OAuth2** for the admin panel. Permission model: user must have ADMINISTRATOR or MANAGE_SERVER in a Discord server AND the bot must be present in that server to manage it.
- **CSRF (non-standard flow):** the frontend must call `/api/csrf` BEFORE the first mutating request to receive the `XSRF-TOKEN` cookie, then echo it in the `X-XSRF-TOKEN` header (handled in `api/client.ts`). CSRF is disabled for `/ws/**`.
- **Sessions:** Spring Session JDBC in H2, 30-day timeout, survives restarts.
- **Rate limiting** on API endpoints via `RateLimitService` (Bucket4j + Caffeine).

### Real-Time Updates

WebSocket at `/ws` using STOMP over SockJS (session-based auth, no CSRF). Notifications for new QOTD submissions and role operations. Frontend subscribes to topics like `/topic/submissions/{guildId}/{channelId}`.

### Dev Server Quirks

- Vite proxies `/api`, `/oauth2`, `/login`, and `/ws` to the backend (port 8080) — see `vite.config.ts`.
- OAuth2 login in dev must use the full backend URL: `http://localhost:8080/oauth2/authorization/discord`.
- `global: 'window'` is defined in `vite.config.ts` to fix sockjs-client expecting Node globals.

## Key Patterns and Conventions

### Gacha Role Naming Convention

Roles must be named `gacha:rarity:ColorName` or `gacha:ColorName` — the `gacha:` prefix is required for the bot to recognize them. Valid rarities: `legendary`, `epic`, `rare`, `uncommon`, `common`.

### Rarity Weights

Defined in the `Rarity` enum in `ColorGachaHandler.java`:
- LEGENDARY: 0.25 (0.5%), EPIC: 1.25 (2.5%), RARE: 2.33 (7%), UNCOMMON: 4 (20%), COMMON: 10 (70%)

### Role Hierarchy Requirement

**Critical:** The bot's Discord role MUST be positioned ABOVE all gacha roles in the server's role hierarchy, otherwise role assignment fails. This is the #1 cause of "Failed to assign role" errors.

### Cooldown System

Users roll once per day; cooldown stored in `UserCooldown` (per user, per guild). Admins can use `/testroll` to bypass the cooldown for testing.

### D20 Roll Mechanic

- Auto-enables when a server has 3+ Epic or Legendary gacha roles — no admin configuration.
- Users may roll `/d20` once, within 60 minutes of `/roll`: nat 20 grants a guaranteed Epic+ next roll ("Lucky Streak"), nat 1 extends the cooldown to 48 hours, 2–19 has no effect.
- State tracked on `UserCooldown`: `d20Used`, `guaranteedEpicPlus`, `extendedCooldown`.

### QOTD Multi-Stream System

- Up to 5 independent streams per Discord channel; each stream (`QotdStream` entity) has its own cron schedule, timezone, question bank, banner, mentions, and auto-approve setting.
- Posting handled by `QotdScheduler`; question bank uses an approval workflow (pending → approved → posted); CSV import supported with author attribution.
- `/qotd-submit` lets users optionally target a stream via autocomplete (shows banner text). Auto-approve streams bypass moderation; with no target and multiple auto-approve streams, the question goes to all of them. Submissions stored in `QotdSubmission` with optional `targetStreamId`.

## Environment Variables

Required in `.env`:
```env
DISCORD_TOKEN=your_bot_token_here
DISCORD_CLIENT_ID=your_client_id_here
DISCORD_CLIENT_SECRET=your_client_secret_here
ADMIN_PANEL_URL=http://localhost:8080
```

Optional for production: `COOKIE_SECURE=true`, `COOKIE_SAME_SITE=strict`.

## Common Development Tasks

### Adding a New Slash Command

1. Add the command definition in `SlashCommandHandler.onGuildReady()` (and the guild-join registration block)
2. Handle it in `SlashCommandHandler.onSlashCommandInteraction()`
3. Use `event.reply()` with `.setEphemeral(true)` for private responses
4. Update help text in `handleHelp()`

### Adding a New API Endpoint

1. Create controller in `web/controller/`, DTOs as Records in `web/dto/`, service logic in `web/service/`
2. Secure with `@PreAuthorize` or manual permission checks (see `AdminService`)
3. Update `WEB_API_README.md`

### Modifying Gacha Probabilities

Edit the `Rarity` enum weights in `ColorGachaHandler.java`, then rebuild.

## Code Style

### Backend (Java)
- Java 21 features (Records, pattern matching); 4-space indentation
- SLF4J for logging (never System.out); JavaDoc for public methods
- Constructor-based dependency injection (not field injection)

### Frontend (TypeScript/React)
- TypeScript strict mode plus `noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`
- Functional components with hooks; props interfaces for all components; 2-space indentation
- React Query for data fetching; Axios with CSRF support
- Test files (`__tests__`, `*.test.ts(x)`, `e2e/`) are excluded from the app build

## Deployment Notes

- JAR at `target/playbot-1.0.0.jar`; frontend build in `frontend/dist/` (served by Spring Boot)
- OAuth2 redirect URI must match the Discord Developer Portal exactly (e.g., `https://your-domain.com/login/oauth2/code/discord`)
- Reverse proxy: `X-Forwarded-For` / `X-Forwarded-Proto` headers already supported in `application.properties`
- Bot invite requires: Manage Roles, Send Messages, View Channels, Embed Links; OAuth2 scopes `bot`, `applications.commands`

## Troubleshooting

- **Bot won't assign roles:** check role hierarchy (bot role above gacha roles), verify Manage Roles permission, check logs
- **OAuth2 login fails:** verify redirect URI, client ID/secret, and `ADMIN_PANEL_URL`
- **Frontend can't reach API:** check Vite proxy in `vite.config.ts`, confirm backend on 8080, check browser console for CORS/CSRF errors
- **Schema validation errors on startup:** an entity changed without a matching Liquibase changeset — write the changeset, don't switch ddl-auto
