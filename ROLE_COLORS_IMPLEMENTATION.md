# Role Colors API Implementation

**Status:** ⏸️ On Hold - Waiting for JDA Support  
**Branch:** `feature/role-colors-api`  
**Created:** November 2025  
**Author:** Development Team

## Overview

This branch implements Discord's Role Colors Object API (gradients and holographic roles) using a custom REST client while JDA doesn't yet support it natively. The implementation is feature-complete, tested, and ready to deploy once JDA adds official support.

## Why On Hold?

Discord's Role Colors Object (supporting gradients and holographic effects) is not yet supported by JDA (Java Discord API). We've built a working implementation using direct HTTP calls to Discord's API, but we're keeping it local until JDA releases official support to:

1. **Avoid maintenance burden** - Custom REST clients require updates when Discord changes their API
2. **Ensure long-term stability** - JDA handles rate limiting, retries, and edge cases better than custom code
3. **Simplify the codebase** - Once JDA supports it, we can remove our custom HTTP client and use their typed API

## What's Implemented

### Backend

#### 1. Discord API Client (`DiscordApiClient.java`)
- **GET guild role colors** - Fetches all roles with their color objects (primary/secondary/tertiary)
- **POST create role with colors** - Creates gradient/holographic roles via Discord HTTP API
- **Guild capability detection** - Checks if a guild supports enhanced role colors via guild features
- **10-minute TTL cache** - Reduces API calls for capability checks

#### 2. AdminService Enhancements
- **Capability gating** - Automatically falls back to solid colors if guild doesn't support enhanced colors
- **Graceful degradation** - If REST call fails, creates solid color role via JDA instead
- **CSV parsing** - Supports optional `secondaryColorHex` and `tertiaryColorHex` columns

#### 3. API Endpoints
- `GET /api/servers` - Includes `supportsEnhancedRoleColors` flag per guild
- `GET /api/servers/{guildId}` - Server details with capability flag
- `POST /api/servers/{guildId}/roles/upload-csv` - Bulk create with enhanced colors
- `GET /api/servers/{guildId}/roles/download-example` - Updated example CSV

### Frontend

#### 1. Type Updates (`types/index.ts`)
```typescript
export interface GuildInfo {
  id: string;
  name: string;
  iconUrl: string | null;
  userIsAdmin: boolean;
  botIsPresent: boolean;
  supportsEnhancedRoleColors?: boolean; // NEW
}
```

#### 2. UI Enhancements (`RoleManager.tsx`)
- **Capability banner** - Shows if server supports enhanced colors
- **Dynamic messaging** - Explains CSV format for gradients/holographic when supported
- **Graceful fallback messaging** - Informs users when enhanced colors aren't available

### Documentation

#### CSV Format (`WEB_API_README.md`)
```csv
name,rarity,colorHex,secondaryColorHex,tertiaryColorHex
# Solid color roles (secondary/tertiary left blank)
Sunset Glow,legendary,#FF6B35,,

# Gradient role (primary + secondary)
Aurora Wave,epic,#7F00FF,#E100FF,

# Holographic role (primary + secondary + tertiary)
Prism Shift,legendary,#00C6FF,#0072FF,#FF00FF
```

**Behavior:**
- Empty optional fields → solid color
- `secondaryColorHex` only → gradient
- Both secondary + tertiary → holographic
- Unsupported guild → automatic fallback to solid color

### Tests

- ✅ `AdminServiceRoleColorsGatingTest` - Verifies capability gating (unsupported/supported paths)
- ✅ Updated all existing tests for new `GuildInfo` signature
- ✅ CSV parsing tests cover 3-5 column formats
- ✅ All 291 backend tests pass
- ✅ All 81 frontend tests pass

## How It Works

### Role Creation Flow

```
1. User requests role (API or CSV)
   ↓
2. Parse colors (primary, secondary?, tertiary?)
   ↓
3. If enhanced colors requested:
   ├─→ Check guild capability (cached for 10m)
   │   ↓
   ├─→ Unsupported? → Create solid via JDA
   │   ↓
   └─→ Supported? → Attempt REST creation
       ├─→ Success? → Fetch role via JDA
       └─→ Failed? → Fallback to solid via JDA
4. Notify UI via WebSocket
```

### Key Files Changed

**Backend:**
- `DiscordApiClient.java` - Custom REST client + capability cache
- `AdminService.java` - Capability gating + fallback logic
- `GuildInfo.java` - Added `supportsEnhancedRoleColors`
- `CreateRoleRequest.java` - Added optional secondary/tertiary colors
- `RoleController.java` - Extended CSV parser to 5 columns

**Frontend:**
- `types/index.ts` - Extended `GuildInfo` interface
- `RoleManager.tsx` - Capability banner

**Resources:**
- `example-roles.csv` - Updated with gradient/holographic examples

**Tests:**
- `AdminServiceRoleColorsGatingTest.java` - New capability gating tests
- Updated test constructors for `GuildInfo`

## Migration Path (When JDA Supports It)

### 1. Update JDA Dependency
```xml
<!-- pom.xml -->
<dependency>
    <groupId>net.dv8tion</groupId>
    <artifactId>JDA</artifactId>
    <version><!-- VERSION WITH ROLE COLORS SUPPORT --></version>
</dependency>
```

### 2. Replace Custom REST with JDA API

**Before (our custom implementation):**
```java
// Custom REST call
String roleId = discordApiClient.createRoleWithColors(
    guildId, fullName, primaryColor.getRGB(),
    secondaryColorInt, tertiaryColorInt,
    false, false
);
```

**After (with JDA support - hypothetical):**
```java
// JDA native support (check actual JDA API when released)
Role createdRole = guild.createRole()
    .setName(fullName)
    .setRoleColors(new RoleColors()
        .setPrimaryColor(primaryColor)
        .setSecondaryColor(secondaryColor)
        .setTertiaryColor(tertiaryColor))
    .setMentionable(false)
    .setHoisted(false)
    .complete();
```

### 3. Simplify Capability Detection

Once JDA supports Role Colors, capability detection may be built into JDA's Guild object:

```java
// Hypothetical JDA API
if (guild.getFeatures().contains(GuildFeature.ROLE_COLORS)) {
    // Use enhanced colors
}
```

### 4. Remove Custom HTTP Client

Delete or archive `DiscordApiClient.java` and its tests if JDA provides equivalent functionality.

### 5. Keep CSV Format & UI

The CSV format, frontend types, and capability banner can stay as-is - they'll work with JDA's implementation.

## Testing Checklist (Before Merging in Future)

When JDA support arrives and you're ready to merge:

- [ ] Update JDA to version with Role Colors support
- [ ] Replace custom REST calls with JDA API
- [ ] Run full test suite (`mvn test`)
- [ ] Manual test: Create gradient role via CSV
- [ ] Manual test: Create holographic role via CSV
- [ ] Manual test: Verify fallback on unsupported guild
- [ ] Manual test: Check capability banner in UI
- [ ] Update `CHANGELOG.md`
- [ ] Update `README.md` if needed
- [ ] Merge to `main` and tag release

## Local Development

To work on this branch:

```bash
# Switch to the branch
git checkout feature/role-colors-api

# Build and test
./build.sh

# Run locally
./start.sh
```

To keep it updated with main:

```bash
# Fetch latest from main
git checkout main
git pull origin main

# Rebase feature branch
git checkout feature/role-colors-api
git rebase main

# Force push if already pushed remotely (or keep local only)
git push --force-with-lease origin feature/role-colors-api
```

## Commits on This Branch

1. `feat: implement Discord Role Colors API with REST client`
   - Add DiscordApiClient for guild role colors and role creation
   - Update DTOs for secondary/tertiary colors
   - Extend CSV parser to support enhanced colors

2. `feat: add capability gating for enhanced role colors`
   - Implement guild feature detection
   - Add automatic fallback to solid colors
   - Create comprehensive tests for both paths

3. `feat: expose enhanced role color capability and cache guild support`
   - Add TTL cache to reduce API calls
   - Extend GuildInfo with capability flag
   - Add frontend capability banner
   - Update documentation

## Notes

- **All features work** - This is production-ready code, just waiting for JDA
- **No breaking changes** - Everything degrades gracefully
- **Backward compatible** - Existing 3-column CSV still works
- **Well tested** - 291 backend + 81 frontend tests pass
- **Documented** - CSV format and API behavior fully documented

## When to Reconsider

Monitor JDA releases for Role Colors support:
- Check: https://github.com/discord-jda/JDA/releases
- Discord API changelog: https://discord.com/developers/docs/change-log

Once JDA announces support, revisit this branch and follow the migration path above.

---

**Last Updated:** November 4, 2025  
**JDA Version When Implemented:** 6.1.0 (no Role Colors support)  
**Waiting For:** JDA to implement Discord's Role Colors Object
