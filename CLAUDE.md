# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Playbot is a Discord bot with a web admin panel that allows users to roll for random colored name roles once per day. Built with Java 21 + Spring Boot backend and React 19 + TypeScript frontend.

**Core Features:**
- Discord bot with slash commands for color role gacha system
- Question of the Day (QOTD) system with per-channel configuration
- Web-based admin panel with Discord OAuth2 authentication
- H2 database persistence for cooldowns, QOTD questions, and submissions

## Build and Development Commands

### Build Commands

```bash
# Build both backend and frontend
./build.sh

# Build with options
./build.sh --skip-tests      # Skip running tests
./build.sh --clean           # Clean before building
./build.sh --production      # Production build with optimization

# Backend only
mvn clean package            # With tests
mvn clean package -DskipTests  # Skip tests

# Frontend only
cd frontend
npm install
npm run build

# Run tests
mvn test                     # Backend only
cd frontend && npm test      # Frontend only (watch mode)
cd frontend && npm run test:unit  # Frontend once
cd frontend && npm run test:coverage  # Frontend with coverage
cd frontend && npm run test:e2e      # Frontend E2E tests
```

**Note:** The `./build.sh` script runs both backend and frontend tests automatically unless `--skip-tests` is specified.

### Run Commands

```bash
# Start both services (recommended)
./start.sh

# Backend only (runs on port 8080)
java -jar target/playbot-1.0.0.jar

# Frontend only (runs on port 3000)
cd frontend
npm run dev
```

### Frontend-Specific Commands

```bash
cd frontend

# Development
npm run dev              # Start Vite dev server

# Generate legal documents from templates
npm run generate:legal   # Creates PrivacyPolicy.tsx and TermsOfService.tsx

# Linting
npm run lint            # Run ESLint
```

## Architecture

### Backend Structure

**Main Entry Point:** `src/main/java/com/discordbot/Bot.java`
- Spring Boot application that initializes both the Discord bot (JDA) and web server
- Loads `.env` file and sets environment variables as system properties
- Creates JDA bean with required gateway intents and event listeners

**Discord Bot Layer:**
- `SlashCommandHandler.java` - Handles all slash command interactions (/roll, /d20, /mycolor, /colors, /help, /testroll, /qotd commands)
- `ColorGachaHandler.java` - Legacy message command handler (deprecated, kept for compatibility)

**Web Layer:**
- `web/controller/` - REST API controllers for admin panel
  - `ServerController.java` - List and manage Discord servers
  - `RoleController.java` - CRUD operations for gacha roles
  - `QotdController.java` - QOTD management and submission handling
  - `HealthController.java` - Bot health check endpoint
  - `AuthRedirectController.java` - OAuth2 redirect handling
- `web/service/` - Business logic
  - `AdminService.java` - Core admin operations and permission validation
  - `QotdService.java` - QOTD scheduling and management
  - `QotdSubmissionService.java` - User submission handling
  - `RateLimitService.java` - API rate limiting with Bucket4j
  - `GuildsCache.java` - Caches Discord guild data
  - `WebSocketNotificationService.java` - Real-time updates via WebSocket
- `web/config/` - Configuration classes for WebSocket, etc.
- `SecurityConfig.java` - Spring Security configuration with Discord OAuth2

**Data Layer:**
- `entity/` - JPA entities
  - `UserCooldown` - Tracks user roll cooldowns per guild
  - `QotdConfig` - Per-channel QOTD configuration
  - `QotdQuestion` - Question bank for QOTD
  - `QotdSubmission` - User answers to QOTD
- `repository/` - Spring Data JPA repositories for entities

**DTOs:** `web/dto/` - Data Transfer Objects for API responses (using Java Records)

### Frontend Structure

**Main Files:**
- `src/main.tsx` - App entry point with React Router
- `src/App.tsx` - Main component with routing logic
- `src/App.css` - All application styles (centralized)

**Components:**
- `Login.tsx` - Discord OAuth2 login page
- `ServerList.tsx` - Grid view of user's Discord servers
- `RoleManager.tsx` - Manage gacha roles (create, delete, bulk operations, CSV import)
- `QotdManager.tsx` - Configure QOTD per channel (schedule, question bank, CSV import)
- `Navbar.tsx` - Top navigation bar
- `Footer.tsx` - Footer with legal links
- `PrivacyPolicy.tsx` / `TermsOfService.tsx` - Legal documents (generated from .template.tsx files)

**API Client:** `src/api/api.ts` - Axios instance with CSRF token handling and base URL configuration

**State Management:** TanStack React Query for server state, local state for UI

**Proxy Configuration:** Vite dev server proxies `/api`, `/oauth2`, `/login`, and `/ws` to backend (port 8080)

### Database

- **H2 file-based database** at `./data/playbot`
- **Schema auto-updated** via JPA (ddl-auto=update)
- **Spring Session JDBC** stores HTTP sessions (supports OAuth2 persistence across restarts)
- **Initial schema:** `src/main/resources/schema.sql` creates oauth2_authorized_client table

### Authentication & Security

- **Discord OAuth2** for web admin panel authentication
- **Permission Model:** User must have ADMINISTRATOR or MANAGE_SERVER permission in a Discord server AND bot must be present in that server to manage it
- **CSRF Protection:** Enabled for state-changing operations
- **Rate Limiting:** Applied to API endpoints via `RateLimitService` (Bucket4j + Caffeine cache)
- **Session Persistence:** 30-day sessions stored in H2 database

### Real-Time Updates

- **WebSocket** endpoint at `/ws` using STOMP over SockJS
- Sends notifications when:
  - New QOTD submissions are posted
  - Role operations complete
- Frontend subscribes to topics like `/topic/submissions/{guildId}/{channelId}`

## Key Patterns and Conventions

### Role Naming Convention

Gacha roles must follow this naming format:
```
gacha:rarity:ColorName
```
or
```
gacha:ColorName
```

Valid rarities: `legendary`, `epic`, `rare`, `uncommon`, `common`

**Example:** `gacha:legendary:Rainbow` or `gacha:epic:Gold`

### Rarity Weights

Defined in `ColorGachaHandler.java`:
- LEGENDARY: 0.25 (0.5% drop rate)
- EPIC: 1.25 (2.5% drop rate)
- RARE: 2.33 (7% drop rate)
- UNCOMMON: 4 (20% drop rate)
- COMMON: 10 (70% drop rate)

### Role Hierarchy Requirement

**Critical:** The bot's Discord role MUST be positioned ABOVE all gacha roles in the server's role hierarchy, otherwise the bot cannot assign/remove roles.

### Slash Command Registration

Commands are registered per-guild when:
1. Bot starts up (for all guilds)
2. Bot joins a new guild

Commands are defined in `SlashCommandHandler.onGuildReady()`

### Cooldown System

- User can roll once per day (disabled in testing mode)
- Cooldown stored in `UserCooldown` entity (per user, per guild)
- Testing mode check in `SlashCommandHandler` (search for "TESTING MODE")

### D20 Roll Mechanic

- **Optional risk/reward system** - Users can roll a d20 after using `/roll` for bonuses or penalties
- **Availability**: Only when server has 3+ Epic or Legendary gacha roles configured
- **60-minute window**: Must use `/d20` within 60 minutes of using `/roll`
- **One-time use**: Can only use `/d20` once per roll cycle
- **Outcomes**:
  - **Nat 20 (5%)**: Grants "Lucky Streak" buff - next roll guaranteed Epic or Legendary
  - **Nat 1 (5%)**: "Critical Failure" - cooldown extended to 48 hours instead of 24
  - **2-19 (90%)**: No effect
- **State tracking**: `d20Used`, `guaranteedEpicPlus`, and `extendedCooldown` flags in `UserCooldown` entity (auto-created via JPA)
- **Visual presentation**: Animated d20 GIF at `/images/d20-roll.gif` with progressive text reveal (6 frames, 500ms each)
- **Status display**: `/mycolor` shows active buffs, d20 window availability, and extended cooldowns
- **No admin configuration needed**: Feature automatically enables when 3+ Epic/Legendary roles exist

### QOTD System

- **Multi-streams per channel**: Up to 5 independent QOTD streams per Discord channel
- **Per-stream configuration**: Each stream has its own schedule, timezone, question bank, banner, and settings
- **Scheduled posting** via `QotdScheduler` using Spring's `@Scheduled`
- **Question bank** with approval workflow (pending → approved → posted)
- **CSV import** for bulk question upload with optional author attribution
- **User submissions**: `/qotd-submit` command with optional stream targeting via autocomplete
  - Users can optionally select which stream their question is for (autocomplete shows banner text)
  - If stream has `autoApprove` enabled, question is automatically added to that stream
  - If no stream specified and multiple streams have `autoApprove`, question is added to all
  - Otherwise, admin manually approves and routes to appropriate stream
- **Submissions** stored in `QotdSubmission` entity with optional `targetStreamId`
- **Real-time updates** via WebSocket notifications

## Environment Variables

Required in `.env` file:
```env
DISCORD_TOKEN=your_bot_token_here
DISCORD_CLIENT_ID=your_client_id_here
DISCORD_CLIENT_SECRET=your_client_secret_here
ADMIN_PANEL_URL=http://localhost:8080
```

Optional for production:
```env
COOKIE_SECURE=true           # HTTPS-only cookies
COOKIE_SAME_SITE=strict      # Strict SameSite policy
```

## Testing

- **Unit tests** in `src/test/java/com/discordbot/`
- **Key test files:**
  - `SlashCommandHandlerTest.java` - Slash command logic
  - `RateLimitServiceTest.java` - Rate limiting
  - `GradientProbabilityTest.java` - Gacha probability distribution
- **Run tests:** `mvn test`
- **Coverage:** Tests use JUnit 5 and Mockito

## Common Development Tasks

### Adding a New Slash Command

1. Add command definition in `SlashCommandHandler.onGuildReady()`
2. Handle command in `SlashCommandHandler.onSlashCommandInteraction()`
3. Use `event.reply()` with `.setEphemeral(true)` for private responses
4. Update help text in `handleHelp()` method

### Adding a New API Endpoint

1. Create controller in `web/controller/` package
2. Create DTOs in `web/dto/` package (use Java Records)
3. Add service logic in `web/service/` if needed
4. Secure endpoint with `@PreAuthorize` or manual permission checks
5. Update `WEB_API_README.md` documentation

### Modifying Gacha Probabilities

Edit rarity weights in `ColorGachaHandler.java` in the `Rarity` enum, then rebuild.

### Enabling/Disabling Daily Cooldown

In `SlashCommandHandler.java`, find the "TESTING MODE" comment and toggle the cooldown check code.

### Customizing Legal Documents

1. Edit `frontend/src/components/PrivacyPolicy.template.tsx` and `TermsOfService.template.tsx`
2. Run `npm run generate:legal` in frontend directory
3. Or set env vars during build: `ORGANIZATION_NAME`, `CONTACT_EMAIL`, `WEBSITE_URL`

## Deployment Notes

- **JAR location:** `target/playbot-1.0.0.jar`
- **Frontend build:** `frontend/dist/` (served by Spring Boot)
- **Database:** Automatically created at `./data/playbot.mv.db`
- **Logs:** Written to `logs/` directory by startup script
- **OAuth2 Redirect URI:** Must match in Discord Developer Portal (e.g., `https://your-domain.com/login/oauth2/code/discord`)
- **Reverse proxy:** Configure headers for `X-Forwarded-For`, `X-Forwarded-Proto` (already supported in `application.properties`)

## Discord Bot Permissions

Required permissions when inviting bot:
- **Manage Roles** - Assign/remove color roles
- **Send Messages** - Respond to commands and post QOTD
- **View Channels** - See channels for slash commands and QOTD
- **Embed Links** - Send rich embeds for QOTD

OAuth2 scopes for invite: `bot`, `applications.commands`

## Code Style Notes

### Backend (Java)
- Java 21 features used (Records, pattern matching, modern syntax)
- 4 spaces for indentation
- SLF4J for logging (never System.out)
- JavaDoc for public methods
- Spring dependency injection via constructor (not field injection)

### Frontend (TypeScript/React)
- TypeScript strict mode enabled
- Functional components with hooks
- Props interfaces for all components
- 2 spaces for indentation
- React Query for data fetching
- Axios for HTTP with CSRF token support

## Troubleshooting

### Bot won't assign roles
- Check role hierarchy (bot role must be ABOVE gacha roles)
- Verify bot has "Manage Roles" permission
- Check logs for detailed error messages

### OAuth2 login fails
- Verify redirect URI matches exactly in Discord Developer Portal
- Check `DISCORD_CLIENT_ID` and `DISCORD_CLIENT_SECRET` are correct
- Ensure `ADMIN_PANEL_URL` points to backend

### Frontend can't reach API
- Check Vite proxy configuration in `vite.config.ts`
- Verify backend is running on port 8080
- Check browser console for CORS/CSRF errors

### Database not persisting
- Check `./data/` directory exists and is writable
- Verify H2 database file created (`data/playbot.mv.db`)
- Check logs for JPA/Hibernate errors
