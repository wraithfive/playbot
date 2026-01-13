# Battle System Package

This package contains the battle system implementation, completely decoupled from gacha mechanics.

## Structure
- `config/` - Configuration properties and Spring beans
- `controller/` - Discord slash command handlers
- `entity/` - JPA entities (PlayerCharacter, BattleSession, etc.)
- `exception/` - Custom exception types
- `repository/` - Spring Data repositories
- `service/` - Business logic services

## Feature Flag
The entire battle system is gated by `battle.enabled` in `application.properties`.
Set to `true` to activate all battle commands.

## Documentation
See `/docs/battle-design.md` for complete design specifications.
