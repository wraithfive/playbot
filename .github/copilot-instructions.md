# Playbot - AI Coding Agent Instructions

## Project Overview
Discord bot with web admin panel combining Java 21 Spring Boot backend and React 19 TypeScript frontend. Provides daily color gacha system, optional D20 risk/reward mechanic, turn-based battle system, and Question of the Day (QOTD) streams.

## Architecture

### Dual Runtime Model
- **Bot Process**: JDA (Java Discord API) and Spring Boot run in single process
- **Frontend**: React dev server (port 3000) proxies to backend (port 8080)
- **Entry Point**: `src/main/java/com/discordbot/Bot.java` - loads `.env`, creates JDA bean, starts Spring Boot
- **Feature Flags**: Battle system gated by `battle.enabled` in `application.properties`

### Command Routing Pattern
Commands use **CommandRouter + CommandHandler pattern** to prevent "interaction already acknowledged" errors:
- `CommandRouter` receives all slash commands, delegates to first matching handler
- Handlers implement `CommandHandler` interface with `canHandle()` and `handle()` methods
- **Critical**: Each command handled by exactly ONE handler - no multiple listeners on same command
- Registration: `CommandRegistrar` registers commands per-guild on startup and join events

### Database Strategy
- **Liquibase for ALL schema changes** - Hibernate set to `validate` mode (never `update`/`create`)
- **Migration Location**: `src/main/resources/db/changelog/` with master XML + numbered changesets
- **Naming**: `XXX-description.xml` (e.g., `001-add-feature.xml`)
- **Critical**: Use preconditions for idempotency when adding to baseline
- **Spring-managed tables**: `SPRING_SESSION*`, `oauth2_authorized_client` also in Liquibase baseline
- See `DATABASE_MIGRATIONS.md` for safe migration patterns (especially NOT NULL columns)

### Permission Model
Web admin uses **custom permission validation** (not Spring Security annotations):
- User must have ADMINISTRATOR or MANAGE_SERVER permission in Discord guild
- Bot must be present in guild
- Validation in `AdminService.java` via Discord REST API + JDA guild cache
- OAuth2 session persists 30 days in H2 database

### Real-Time Updates
- **WebSocket/STOMP** at `/ws` for QOTD submissions and role operation updates
- Frontend subscribes to `/topic/submissions/{guildId}/{channelId}`
- `WebSocketNotificationService` sends notifications from backend services

## Critical Patterns

### Role Naming Convention
Gacha roles MUST match regex: `gacha:(legendary|epic|rare|uncommon|common):ColorName` or `gacha:ColorName`
- Example: `gacha:legendary:Rainbow`, `gacha:Gold`
- Rarity weights in `ColorGachaHandler.java` Rarity enum

### D20 Risk/Reward System
- Available ONLY if server has 3+ Epic/Legendary roles
- 60-minute window after `/roll` to use `/d20`
- Tracking: `d20Used`, `guaranteedEpicPlus`, `extendedCooldown` flags in `UserCooldown` entity
- See `D20_FEATURE_DESIGN.md` for complete mechanics

### QOTD Multi-Stream Architecture
- Up to 5 independent streams per channel (`QotdStream` entity with `streamOrder` 0-4)
- Each stream: own schedule (cron), timezone, question bank, banner, auto-approve setting
- User submissions via `/qotd-submit` can target specific stream (autocomplete by banner text)
- Auto-approve logic: if stream specified + has auto-approve → direct add; if no stream + multiple auto-approve → add to all

### Battle System Design
- **Completely decoupled** from gacha mechanics - separate package `com.discordbot.battle`
- **Turn persistence**: `battle_turn_log` table with full audit trail (migration 021)
- **Resource management**: `CharacterSpellSlot`, `CharacterAbilityCooldown` entities for spell casting
- **Status effects**: 12 types (STUN, BURN, POISON, REGEN, SHIELD, etc.) with stacking/duration logic
- **Anti-abuse**: Battle cooldowns, turn timeouts, scheduled checker (`BattleTimeoutScheduler`)
- See `docs/battle-design.md` for phase-by-phase implementation status

## Development Workflow

### Build & Run
```bash
./build.sh                    # Build both (runs tests by default)
./build.sh --skip-tests       # Fast build
./build.sh --production       # Optimized frontend build
./start.sh                    # Start both services
```

### Testing
```bash
mvn test                             # Backend unit tests
cd frontend && npm run test:unit     # Frontend unit tests
cd frontend && npm run test:coverage # Frontend with coverage
cd frontend && npm run test:e2e      # Playwright E2E tests
```

### Adding Slash Commands
1. Define in `CommandRegistrar.onGuildReady()` using JDA's `Commands.slash()`
2. Create handler class implementing `CommandHandler` in `com.discordbot.command` or feature package
3. Use `@Component` to auto-register with `CommandRouter`
4. Return command names from `canHandle()` - router finds first match

### Adding API Endpoints
1. Create controller in `web/controller/` with `@RestController`
2. Use **Java Records** for DTOs in `web/dto/` (immutable by default)
3. Validate permissions via `AdminService.validateUserHasPermission()` or `hasAdminAccess()`
4. Add CSRF token handling if mutating (already in frontend `api/client.ts`)
5. Update `WEB_API_README.md`

### Frontend API Calls
- Use TanStack React Query (`useQuery`, `useMutation`) for all data fetching
- CSRF token auto-injected by `api/client.ts` interceptor
- Import from `api/client.ts` typed API functions (e.g., `serverApi.getRoles()`)

### Database Changes
1. Create `XXX-description.xml` in `src/main/resources/db/changelog/changes/`
2. Add `<include>` to `db.changelog-master.xml`
3. Test locally: `./build.sh && java -jar target/playbot-*.jar`
4. For NOT NULL columns on existing tables: add nullable → populate → add constraint (see `DATABASE_MIGRATIONS.md`)

## Code Style & Conventions

### Backend (Java 21)
- **Constructor injection** (no `@Autowired` field injection)
- **SLF4J logging** (`logger.info()`, never `System.out.println()`)
- **Java Records** for DTOs and immutable data
- **JPA entity validation**: Jakarta `@NotNull`, `@Size`, etc. + `@PrePersist`/`@PreUpdate`
- **4 spaces** indentation
- **Null safety**: Use `@NonNullByDefault` (Eclipse JDT annotations) at package/class level

### Frontend (TypeScript/React)
- **Functional components** with hooks (no class components)
- **TypeScript strict mode** - all props/state typed with interfaces
- **2 spaces** indentation
- **Centralized styles** in `src/App.css` (not per-component CSS modules)
- **React Query** for server state, local `useState` for UI-only state

## Testing Notes
- **Backend**: JUnit 5 + Mockito, repository tests use `@DataJpaTest` with `repository-test` profile
- **Frontend**: Vitest + React Testing Library for unit, Playwright for E2E
- **Profile isolation**: `@Profile("!repository-test")` on JDA bean to avoid Discord connection in tests

## Common Pitfalls
- **Never use Hibernate DDL auto** - Liquibase owns schema, Hibernate validates only
- **Role hierarchy**: Bot role must be ABOVE gacha roles in Discord or assignment fails
- **Command acknowledgment**: Always check `event.isAcknowledged()` before replying in error handlers
- **OAuth2 redirect URI**: Must match exactly in Discord Developer Portal (e.g., `{baseUrl}/login/oauth2/code/discord`)
- **Vite proxy**: `/api`, `/oauth2`, `/login`, `/ws` proxied to port 8080 - don't hardcode URLs
- **Legal document generation**: Run `npm run generate:legal` after editing `.template.tsx` files

## Key Files to Reference
- `CLAUDE.md` - Detailed project documentation (commands, architecture, troubleshooting)
- `README.md` - User-facing setup and features
- `DATABASE_MIGRATIONS.md` - Liquibase migration patterns
- `D20_FEATURE_DESIGN.md` - D20 mechanic specification
- `docs/battle-design.md` - Battle system implementation phases
- `WEB_API_README.md` - REST API documentation
- `src/main/java/com/discordbot/Bot.java` - Application entry point
- `src/main/java/com/discordbot/command/CommandRouter.java` - Command dispatch pattern
- `frontend/src/api/client.ts` - API client with CSRF handling
- `frontend/vite.config.ts` - Dev proxy configuration
