# Battle System Design Document

Status: Draft
Last Updated: 2025-11-09
Owner: (wraithfive)
Feature Flag: `battle.enabled`

## 1. Executive Summary
Introduce a self-contained turn-based battle subsystem (duels first, optionally parties later) with character creation, spells/abilities, progression, and leaderboards. Completely decoupled from the existing gacha/role color mechanics. Rollout will be phased to minimize risk and allow iterative balancing.

## 2. Goals
- Provide an engaging interactive PvP/PvE style experience inside Discord via slash commands and component interactions.
- Enable players to create persistent characters (class, stats) and evolve them via XP/ELO without external dependencies.
- Support future party battles and cooperative modes with minimal architectural churn.
- Maintain low operational overhead: safe defaults, limited per-user concurrent sessions, robust timeout handling.
- Observability: structured logging + optional debug, metrics for active sessions & latency.

## 3. Non-Goals
- No linkage to gacha role colors, rarity, gradients, or holographic effects.
- No real-money or economy system in initial phases.
- No complex equipment or inventory at MVP stage.
- No cross-guild battling (guild boundary enforced).

## 4. High-Level Phases
1. Foundation & Feature Flag (Phase 0)
2. Character Creation & Persistence (Phases 1 / 1a / 1b)
3. Spells & Resources (Phases 2 / 2a)
4. Duel Combat MVP (Phase 3 + anti-abuse 3b + persistence 3a)
5. Expanded Actions & Status Effects (Phase 4)
6. Party System & Targeting (Phase 5 / 5a) [Optional]
7. Progression & Leaderboards (Phase 6 / 6a)
8. Edge Cases & Recovery (Phase 7)
9. Monitoring & Logging (Phase 8)
10. Performance & Load (Phase 9)
11. Documentation & Help (Phase 10)
12. Security & Permissions (Phase 11)
13. Config & Tuning (Phase 12)
14. Test Suite Build-Out (Phase 13)
15. Deployment & Rollout (Phase 14)

### 4.1 Phase status (as of 2025-11-14)
- Phase 0 — Foundation & feature flag: DONE
  - `battle.enabled` flag wired in `application.properties` with sane defaults.
- Phases 1 / 1a / 1b — Character creation & persistence: CORE IMPLEMENTED (1a/1b enhancements pending)
  - Delivered: `player_character` table (Liquibase 011 + 013), JPA entity, repository, `/create-character` command + interactive flow, basic validation, help text, tests.
  - Pending (1a/1b): extended stat/respec flow, additional edge-case tests.
  - Optional later: cosmetic fields (nickname/avatar/bio) for additional color.

  | Phase 1 scope | Delivered | Notes |
  | --- | --- | --- |
  | Class selection | Yes | Slash + interactive menus |
  | Race selection | Yes | Validation via `CharacterValidationService` |
  | Point-buy stats | Yes | 27-point validation enforced |
  | Persistence | Yes | `player_character` table + JPA repository |
  | Basic `/character` view | Yes | Implemented (stats, modifiers, HP, AC; assumes level 1) |
  | Respec flow | Deferred | Track under 1a |
  | Cosmetics (nickname/avatar/bio) | Optional later | Not planned for core |
- Phases 2 / 2a — Spells & resources: CORE IMPLEMENTED
  - Static ability/spell definitions seeded for all classes (Warrior, Rogue, Mage, Cleric) plus universal feats.
  - Effect parser (`EffectParser`, `AbilityEffect`), character stats calculator with ability bonuses integrated.
  - `/abilities` command for viewing and learning abilities interactively.
  - Combat system applies ability modifiers (DAMAGE, AC, CRIT_DAMAGE, MAX_HP, etc.).
- Phase 2b — Resource costs & spell casting: COMPLETED
  - Database schema: 3 new migrations (018, 019, 020) for spell slots, cooldowns, charges.
  - Entities: `RestType` enum, extended `Ability` with resource costs, `CharacterSpellSlot`, `CharacterAbilityCooldown`.
  - Repositories: `CharacterSpellSlotRepository`, `CharacterAbilityCooldownRepository` with @Transactional.
  - Validation: Triple-layer (setter checks + Jakarta annotations + database CHECK constraints).
  - Comprehensive unit tests: 38 new tests (CharacterSpellSlotTest, CharacterAbilityCooldownTest, AbilityTest).
  - All 460 tests passing.
- Phase 2c — Resource management service layer: COMPLETED
  - Implemented `SpellResourceService` for spell slots, cooldowns, and utility getters.
  - Added unit tests (`SpellResourceServiceTest`): 26 tests covering initialization, consumption, restoration, cooldowns, cleanup.
  - Note: Level-based slot tables are stubbed to level 1 for MVP (no level field yet on `PlayerCharacter`).
  - All 486 tests passing.
- Phase 3 — Duel combat MVP: COMPLETED + CRITICAL FIXES APPLIED
  - **Commands:** `/duel`, `/accept`, `/forfeit` fully implemented
  - **Combat actions:** Attack, Defend, Forfeit with interactive button UI
  - **Turn persistence:** Migration 021 creates `battle_turn_log` table with full audit trail
  - **Anti-abuse:** Battle cooldowns, turn timeout detection, busy state validation
  - **Critical fixes applied (2025-11-14):**
    - Battle cooldown enforcement in challenge creation (prevents spam)
    - Race condition prevention on accept (prevents duplicate battles)
    - Full spell resource integration (slots, cooldowns, ability validation)
    - Timeout handling mechanism (timeoutTurn method)
    - Audit trail enrichment (status effects in turn logs)
    - Defend AC lifecycle clarity (comment fixes)
  - **Status:** Production-ready, all high/medium priority issues resolved
- Phase 3b — Anti-abuse enhancements: COMPLETED
  - **Scheduled timeout checker:** `BattleTimeoutScheduler` with automatic stale battle detection
  - **Configuration:** Properties for timeout check intervals and feature toggle
  - **Test coverage:** Comprehensive unit tests for scheduler behavior
  - **Status:** Core scheduler complete, optional analytics/IP limiting deferred
- Phase 4 — Expanded Actions & Status Effects: IN PROGRESS (Part 1/2 complete)
  - **Base infrastructure (completed):**
    - Database migration 022: battle_status_effect table with indexes
    - StatusEffectType enum: 12 effect types (STUN, BURN, POISON, REGEN, SHIELD, HASTE, SLOW, BLEED, WEAKNESS, STRENGTH, PROTECTION, VULNERABILITY)
    - BattleStatusEffect entity with business logic (stacking, duration, display)
    - BattleStatusEffectRepository with comprehensive query methods
    - StatusEffectService: Complete effect management (apply, tick, modifiers, shield absorption)
    - ActiveBattle extended with maxHP tracking
  - **Pending:** Combat integration, UI indicators, spell extensions, tests

### 4.2 Recent progress (2025-11-14)
- **Phase 3 — Duel combat MVP: COMPLETED + CRITICAL FIXES APPLIED**
  - **Initial Implementation (commit 537519a):**
    - Database: `battle_turn_log` table (migration 021) with full audit trail
    - Entities: `BattleTurn` (JPA entity), `ActiveBattle` extended with turnNumber, tempAcBonus, lastActionAt
    - Commands: `/accept`, `/forfeit` with command handlers
    - Combat actions: `performDefend` (grants +2 AC for one turn), basic `performSpell` (INT-based damage)
    - Turn persistence: All actions logged to database
    - Anti-abuse: In-memory cooldown tracking, turn timeout detection
    - UI: Interactive buttons (⚔️ Attack / 🛡️ Defend / 🏳️ Forfeit)

  - **Critical Fixes (commit 7be2cd4):**
    - **Battle cooldown enforcement:** createChallenge now checks both challenger and opponent for cooldowns; rejects with remaining time
    - **Race condition fix:** acceptChallenge re-validates busy state for both participants; prevents duplicate battles
    - **Spell resource integration:** Full integration with SpellResourceService
      - Verifies caster knows ability and it's a SPELL type
      - Checks spell slot availability and consumes slots when cast
      - Enforces per-ability cooldowns
      - Uses proper spellDamageBonus() from stats calculator
      - Includes ability name in combat logs and turn persistence
    - **Timeout handling:** New timeoutTurn() method to end battles that exceed turn timeout
    - **Audit trail enrichment:** Status effects in turn logs ("AC+2 (defend)", "Spell: {abilityKey}")
    - **Code clarity:** Updated defend AC lifecycle comments

  - **Architecture:** BattleService now depends on SpellResourceService and AbilityRepository
  - **Status:** Production-ready, all code review issues resolved

- **Phase 3b — Anti-abuse enhancements: IN PROGRESS**
  - **Scheduled timeout checker (implemented):**
    - `BattleTimeoutScheduler.java`: Spring @Scheduled component for periodic battle monitoring
    - `checkAndTimeoutStaleBattles()`: Runs every 30 seconds (configurable), checks all active battles for turn timeouts
    - `cleanupExpiredChallenges()`: Runs every 2 minutes (configurable), removes expired pending challenges
    - Feature toggle: `@ConditionalOnProperty` respects `battle.scheduler.timeout.enabled`
    - Error handling: Individual battle timeout failures don't block processing of remaining battles
    - Extended `BattleService` with `getAllActiveBattles()` method for scheduler access
  - **Configuration properties (completed):**
    - `battle.scheduler.timeout.enabled=true`: Enable/disable automatic timeout checking
    - `battle.scheduler.timeout.checkIntervalMs=30000`: Timeout check interval (30 seconds default)
    - `battle.scheduler.cleanup.checkIntervalMs=120000`: Challenge cleanup interval (2 minutes default)
  - **Test coverage (completed):**
    - `BattleTimeoutSchedulerTest.java`: 11 unit tests covering all scheduler behavior
    - Tests: Feature flag disabled, empty battles, single/multiple timeouts, error handling, cleanup
    - All tests use Mockito for dependency mocking and verify correct service method invocations
  - **Pending (optional):**
    - Battle history analytics dashboard (optional enhancement)
    - IP-based rate limiting for challenge creation (optional anti-abuse measure)

### 4.2 Recent progress (2025-11-10)
- **Phase 2b — Resource costs & spell casting: COMPLETED**
  - Database migrations:
    - `018-add-ability-resource-costs.xml`: Extended ability table with spell_slot_level (0-9), cooldown_seconds, charges_max, rest_type with CHECK constraints.
    - `019-create-character-spell-slots-table.xml`: Per-character spell slot tracking with max_slots, current_slots, last_rest timestamp.
    - `020-create-character-ability-cooldown-table.xml`: Per-character, per-ability cooldown tracking with last_used, available_at timestamps.
  - Entities:
    - `RestType` enum: NONE, SHORT_REST, LONG_REST (D&D 5e mechanics).
    - `Ability`: Extended with spellSlotLevel, cooldownSeconds, chargesMax, restType fields with validation.
    - `CharacterSpellSlot`: Spell slot management with consumeSlot(), restoreSlots(), validation in setters.
    - `CharacterAbilityCooldown`: Cooldown tracking with isAvailable(), resetCooldown() methods.
  - Repositories:
    - `CharacterSpellSlotRepository`: CRUD + findByCharacterAndSlotLevel, @Transactional deletes.
    - `CharacterAbilityCooldownRepository`: CRUD + findByAvailableAtBefore for cleanup, @Transactional deletes.
  - Validation: Triple-layer approach (explicit setter validation + Jakarta annotations + database CHECK constraints).
  - Tests: 38 new unit tests covering all entity business logic, validation, edge cases. All 460 tests passing.
  - Status: Schema and entities complete. Ready for SpellResourceService implementation.

- Previous progress (2025-11-09):
  - **Phase 2a — Spells & resources: CORE IMPLEMENTED**
  - Database migrations:
    - Added `017-expand-class-abilities.xml` covering Cleric (complete), and expanded Warrior/Rogue/Mage/Universal abilities.
    - Fixed Liquibase preconditions to be idempotent and reliable; escaped XML entities.
  - Update summary on startup: Run: 5, Previously run: 32, Total change sets: 37; Rows affected: 49.
  - Content seeded:
    - Cleric: 10 abilities (Life Domain talents and core spell kit).
    - Warrior: +7; Rogue: +8; Mage: +11; Universal feats: +8.
    - **Note:** All abilities currently set to `required_level=1` for MVP simplicity. Some spells (e.g., Time Stop, Disintegrate) would require level 9 in full D&D 5e but are accessible now for testing/balancing. Level gating will be refined in Phase 6 (Progression).
  - Verification:
    - Bot restarted successfully; Liquibase applied all 5 new changesets without error.
    - Interactive class & race selection flow is live in `/create-character`.

- Phase 2a — Spells & resources: CORE IMPLEMENTED (resource costs pending)
  - Delivered: Effect parser (`EffectParser`, `AbilityEffect`), character stats calculator with ability bonuses integrated into combat resolution.
  - `/abilities` command for viewing and learning abilities interactively (filters by type, class-appropriate abilities).
  - Combat system now applies ability modifiers (DAMAGE, AC, CRIT_DAMAGE, MAX_HP, etc.) from learned talents/skills/spells.
  - Comprehensive test coverage for effect parsing and combat integration.
  - Pending: Resource costs (spell slots, charges), casting pipeline for active spell use, cooldowns.

### 4.3 Next up
- Phase 3b (Anti-abuse enhancements - IN PROGRESS)
  - ✅ Scheduled timeout checker job to automatically end stale battles
  - ⏳ IP-based rate limiting for challenge creation (prevent multi-account abuse) - Optional
  - ⏳ Battle history analytics dashboard - Optional
- Phase 4 (Expanded Actions & Status Effects)
  - Implement status effect system (stun, burn, shield, haste, slow, poison)
  - Status effect entity and tracking (duration, stacks, application/expiry)
  - Extend spell system with status effect application
  - UI indicators for active status effects
- Phase 5 (Party System & Targeting - Optional)
  - Party entity and member management
  - Multi-target spell system
  - Party turn order resolution (initiative-based)
  - Party vs Party battles
- Phase 6 (Progression & Leaderboards)
  - Add level field to PlayerCharacter
  - XP gain and leveling system
  - ELO rating system for competitive ranking
  - Proficiency bonuses based on level
  - Battle leaderboards (by ELO, wins, kill/death ratio)
  - Level-based spell slot progression

## 5. Domain Glossary
- Character: Player-created persistent entity with D&D 5e ability scores (STR, DEX, CON, INT, WIS, CHA) & class.
- Class: Archetype defining stat priorities, spell slots, possible spells (e.g., Warrior, Rogue, Mage, Cleric).
- Ability Scores: Six core D&D 5e stats (Strength, Dexterity, Constitution, Intelligence, Wisdom, Charisma) with modifiers.
- Combat Stats: Derived values from ability scores: HP (from CON), Initiative (from DEX), Attack/Defense (from class primary stats), Save DCs (from casting stat).
- Spell: Action with resource cost, cooldown and effect (damage, heal, buff, debuff, DoT, shield).
- BattleSession: Active duel (later party battle) state, tracking participants, turn order, status effects.
- Turn Log: Immutable record of each action resolution.
- ELO: Rating system for relative player skill progression.
- XP: Experience points for leveling characters (optional early or later depending on balancing comfort).
- Status Effect: Temporary modifier applied to a character (stun, burn, shield, haste, slow).

## 6. Architecture Overview
Layered domain under package `com.discordbot.battle`:
- `entity`: JPA entities for Character, BattleSession, BattleTurn, (later Party, Spell).
- `repository`: Spring Data repositories.
- `service`: Stateless services (CharacterService, BattleService, SpellService, ProgressionService, RecoveryService).
- `web.controller`: Slash command + interaction handlers.
- `config`: BattleProperties mapping values from `application.properties` (prefixed `battle.`).

Flow:
1. User invokes `/create-character` → CharacterService validates class + stat allocation → persists.
2. User invokes `/duel @user` → BattleService creates pending challenge.
3. Target invokes `/accept` → instantiate BattleSession, produce interactive message with buttons.
4. Each button press triggers turn resolution pipeline.
5. On win/draw/forfeit/time-out, finalize session, update ELO/XP, log metrics.

## 7. Data Model (Initial + Incremental)
### 7.1 Tables (Liquibase)
`player_character`:
- `id` (PK, UUID or bigserial)
- `guild_id` (STRING)
- `user_id` (STRING)
- `class` (ENUM or VARCHAR: Warrior, Rogue, Mage, Cleric)
- `race` (VARCHAR: Human, Elf, Dwarf, Halfling, etc.)
- `level` (INT, default 1)
- `xp` (BIGINT, default 0)
- **D&D 5e Ability Scores** (INT, 8-15 base range via point-buy):
  - `strength`
  - `dexterity`
  - `constitution`
  - `intelligence`
  - `wisdom`
  - `charisma`
- **Derived Combat Stats** (computed on load):
  - HP = 10 + (CON modifier × level) + class base HP
  - Initiative = DEX modifier
  - Attack bonus = proficiency + primary stat modifier (STR for Warrior, DEX for Rogue, INT for Mage, WIS for Cleric)
  - Armor Class (AC) = 10 + DEX modifier (+ equipment later)
- Cosmetic: `nickname` (VARCHAR), `avatar_url` (VARCHAR), `bio` (TEXT) [Phase 1b]
- Timestamps: `created_at`, `updated_at`
- Unique constraint: (guild_id, user_id)

`battle_session`:
- `id` (PK)
- `guild_id`
- `status` (PENDING, ACTIVE, COMPLETED, ABORTED)
- `initiator_user_id`
- `opponent_user_id`
- `winner_user_id` (nullable)
- `start_time`, `end_time`
- Snapshot stats (denormalized for audit): `init_hp_start`, `opp_hp_start`
- Turn pointer / current actor: `current_actor_user_id`
- Timeouts: `last_action_at`

`battle_turn_log`:
- `id` (PK)
- `battle_id` (FK → battle_session.id)
- `turn_number` (INT)
- `actor_user_id`
- `action_type` (ATTACK, DEFEND, SPELL, SPECIAL, FORFEIT, TIMEOUT)
- `target_user_id` (nullable)
- `damage_dealt` (INT)
- `crit` (BOOLEAN)
- `hp_actor_after`, `hp_target_after` (INT)
- `status_effects_applied` (JSON or TEXT)
- Timestamp: `created_at`

Future: `spell_def` (static definitions), `party`, `party_member`, `character_spell_prepared`.

### 7.2 Indexing
- player_character: idx on (guild_id, user_id)
- battle_session: idx on (guild_id, status), idx on (initiator_user_id), idx on (opponent_user_id)
- battle_turn_log: idx on (battle_id, turn_number)

## 8. Config Parameters (application.properties)
```
battle.enabled=false

# D&D 5e Character Creation (Point-Buy)
battle.character.pointBuy.totalPoints=27
battle.character.pointBuy.minScore=8
battle.character.pointBuy.maxScore=15
# Point costs: 8=0, 9=1, 10=2, 11=3, 12=4, 13=5, 14=7, 15=9
battle.character.pointBuy.costs=0,1,2,3,4,5,7,9

# Class Base HP (added to CON-derived HP)
battle.class.warrior.baseHp=12
battle.class.rogue.baseHp=8
battle.class.mage.baseHp=6
battle.class.cleric.baseHp=8

# Combat & Duel Settings
battle.crit.threshold=20
battle.crit.multiplier=2.0
battle.turn.timeoutSeconds=45
battle.challenge.expireSeconds=120
battle.cooldown.seconds=60
battle.max.concurrentPerGuild=50

# Progression
battle.proficiency.byLevel=2,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,6,6,6,6
battle.elo.k=32
battle.xp.levelCurve=0,300,900,2700,6500,14000,23000,34000,48000,64000

# Logging
battle.debug=false
```
Reload command later: `/battle-config-reload` (Phase 12).

## 9. Command Surface (Incremental)
Phase 1:
- `/create-character` (interactive UI with menus; point-buy validated: 27 points total)
- `/character [user]` (view stats, modifiers, HP, AC; level assumed 1 until leveling added)
- `/abilities` (view learned & available, learn interactively)
- `/character-edit nickname:<str> avatar_url:<url> bio:<text>` (Optional, later)

Phase 3:
- `/duel target:@user`
- `/accept`
- `/forfeit`

Phase 6:
- `/battle-stats [user]`
- `/battle-leaderboard [page]`

Admin/Security (Phase 11):
- `/battle-cancel battle_id:<id>`
- `/battle-config-reload`

Future (Spells/Party):
- `/spells`
- `/prepare-spells`
- `/party-create`
- `/party-invite user:@user`
- `/party-leave`

## 10. Interaction Components
Each active duel message: Buttons
- Attack
- Defend
- Spell (dropdown or button group; appears after Phase 2)
- Special (future)
- Forfeit
Disable buttons when not the user's turn. Auto-disable entire panel on completion.

## 11. Turn Resolution Pipeline
1. Validate actor matches `current_actor_user_id`.
2. Check timeout → if exceeded, auto-forfeit or pass turn (design choice: forfeit for MVP).
3. Apply action (attack/defend/spell):
   - **Attack**: Roll d20 + attack bonus vs target AC
     - If hit: roll damage die + ability modifier
     - Natural 20 (crit): double damage dice
   - **Defend**: Gain temporary AC bonus or resistance for one turn
   - **Spell**: Check spell slot availability, roll spell attack or force saving throw
4. Update target HP; record log entry.
5. Check win/draw conditions:
   - If target HP <= 0 and actor HP > 0 → actor wins.
   - If both <= 0 → draw.
6. Advance turn: set `current_actor_user_id` to other participant.
7. Emit updated embed + ephemeral ack.
8. Persist changes; commit log.

### D&D 5e Formulas
- **Ability Modifier**: `floor((score - 10) / 2)`
- **Attack Roll**: `d20 + proficiency + ability modifier`
- **Damage**: weapon/spell dice + ability modifier
- **Armor Class (AC)**: `10 + DEX modifier` (base, no armor)
- **HP**: `class base HP + (CON modifier × level)`
- **Initiative**: `d20 + DEX modifier`

## 12. State Machine (battle_session.status)
- PENDING: challenge sent, awaiting accept.
- ACTIVE: accepted, turn loop running.
- COMPLETED: winner decided or draw.
- ABORTED: system-driven termination (restart, cancellation, invalid state).

## 13. ELO & XP (Phase 6/6a)
ELO Update (simplified):
```
expected = 1 / (1 + 10^((ELO_opp - ELO_actor)/400))
newELO = oldELO + K * (score - expected)
score = 1 (win), 0 (loss), 0.5 (draw)
```
XP Gain: base + bonus (e.g., `baseXP = 25`, win bonus 15, draw 5). Level thresholds from config.

## 14. Security & Permissions
- All character & battle commands require same guild context.
- Validate target user is not a bot and has a character (for duel).
- Admin-only for cancel & config reload (check discord roles or permission flags). No linking to role colors—pure permission check.

## 15. Anti-Abuse & Limits
- Per-user duel cooldown: `battle.cooldown.seconds` after completing/forfeiting a duel.
- Max active duels per guild: `battle.max.concurrentPerGuild`.
- Challenge expiration: pending duel auto-cancel after `battle.challenge.expireSeconds`.
- Turn timeout enforcement per actor.

## 16. Monitoring & Logging
Structured event log patterns:
- `battle.challenge.created` {battleId, guildId, initiator, opponent}
- `battle.challenge.accepted`
- `battle.turn.resolved` {battleId, turn, actor, action, damage, crit, hpActor, hpTarget}
- `battle.timeout` {battleId, actor}
- `battle.completed` {battleId, winner, draw}
- `battle.aborted` {battleId, reason}
Metrics (phase 8+): counters & gauges
- ActiveSessions
- AvgTurnLatency
- TimeoutsCount
- ForfeitsCount

## 17. Testing Strategy
Phase 3: unit tests for damage formula, crit probability, turn order, timeout logic.
Phase 3a: persistence tests verifying session + turn log creation & restart recovery.
Phase 6: ELO correctness with deterministic cases.
Phase 13: property-based tests on damage ensuring non-negative and within bounds; concurrency tests for simultaneous button presses (ensure single turn advancement).

## 18. Performance Considerations
- Keep sessions minimal in memory (IDs + current HP + actor pointer). Full historical data in turn_log table.
- Avoid heavy JSON parsing each turn; store status effects as compressed string or enum list.
- Scheduled cleanup: end sessions stuck in ACTIVE beyond a max duration.

## 19. Failure & Recovery
- On bot startup, scan `battle_session` where status=ACTIVE and `last_action_at` older than threshold → mark ABORTED and optionally DM participants.
- Graceful abort path ensures ELO/XP not updated.

## 20. Rollout Plan
1. Implement behind `battle.enabled=false` default.
2. Deploy Phase 3 MVP to staging; gather latency & error logs.
3. Enable for a test guild only (whitelist check optional) before global enable.
4. Gradually introduce spells & progression after stability.

## 21. Risks & Mitigations
| Risk | Mitigation |
|------|------------|
| Button spam causing race conditions | Atomic turn resolution; lock per battle (synchronized or optimistic checks). |
| Unbalanced stat allocations | Caps + validation + test harness. |
| Persistence errors during high load | Use transactions & index tuning; monitor slow queries. |
| User confusion on creating characters | Clear `/battle-help` embed with examples. |
| Stale sessions after restart | Recovery sweep on startup. |

## 22. Open Questions
- Should spells have global cooldown categories? (Pending Phase 2 design.)
- Do we allow re-spec of stats? Possibly limited (e.g., once per 24h) with a command `/character-respec` later.
- Draw resolution awarding partial XP/ELO? Current plan: ELO draw (0.5 score), XP minimal.
- Party initiative ordering algorithm: sum SPD vs average vs individual queue? To decide before Phase 5.

## 23. Implementation Sequence (Immediate)
1. Create `battle.enabled` flag and `BattleProperties`.
2. Add packages skeleton.
3. Character entity + Liquibase changeSet.
4. Minimal `/create-character` & `/character`.
5. In-memory duel MVP (no persistence) to validate loop.

---
End of Document.
