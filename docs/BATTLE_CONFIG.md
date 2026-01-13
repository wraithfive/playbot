# Battle System Configuration Guide

**Phase 12: Config & Tuning**

This document provides comprehensive guidance for configuring and tuning the battle system in `application.properties`.

## Table of Contents

1. [System Control](#system-control)
2. [Character Creation](#character-creation)
3. [Class Configuration](#class-configuration)
4. [Combat Settings](#combat-settings)
5. [Challenge Settings](#challenge-settings)
6. [Progression & XP](#progression--xp)
7. [Scheduler Settings](#scheduler-settings)
8. [Tuning Guidelines](#tuning-guidelines)
9. [Troubleshooting](#troubleshooting)

---

## System Control

### `battle.enabled`
- **Type:** Boolean
- **Default:** `false`
- **Description:** Master switch to enable/disable the entire battle system
- **Values:** `true` | `false`
- **Notes:** When disabled, all battle commands are hidden and unavailable

### `battle.debug`
- **Type:** Boolean
- **Default:** `false`
- **Description:** Enable verbose debug logging for battle operations
- **Values:** `true` | `false`
- **Notes:** Only enable in development or when troubleshooting. Generates significant log volume.

---

## Character Creation

### Point-Buy System

#### `battle.character.pointBuy.totalPoints`
- **Type:** Integer
- **Default:** `27`
- **Range:** 15-40 (recommended)
- **Description:** Total points available for ability score allocation
- **D&D 5e Standard:** 27 points
- **Tuning:**
  - Lower (15-20): Weaker characters, emphasizes teamwork
  - Higher (30-40): Stronger characters, faster progression

#### `battle.character.pointBuy.minScore`
- **Type:** Integer
- **Default:** `8`
- **Range:** 3-10 (D&D 5e standard: 8)
- **Description:** Minimum allowed ability score
- **Notes:** Lower values allow dump stats but create unbalanced characters

#### `battle.character.pointBuy.maxScore`
- **Type:** Integer
- **Default:** `15`
- **Range:** 15-18 (D&D 5e standard: 15)
- **Description:** Maximum ability score during character creation (before racial bonuses)
- **Notes:** Higher values create overpowered characters at low levels

#### `battle.character.pointBuy.defaultScore`
- **Type:** Integer
- **Default:** `10`
- **Range:** 8-12
- **Description:** Default score for abilities if not manually set
- **Notes:** Score of 10 provides a +0 modifier (balanced starting point)

#### `battle.character.pointBuy.costs`
- **Type:** Comma-separated integers
- **Default:** `0,1,2,3,4,5,7,9`
- **Description:** Point costs for each score (indexed from minScore)
- **Format:** Costs for scores 8,9,10,11,12,13,14,15
- **D&D 5e Standard:** 0,1,2,3,4,5,7,9 (exponential cost for high scores)
- **Notes:** Modify to adjust difficulty of obtaining high scores

---

## Class Configuration

### Base HP Values

Each class has a base HP value based on their D&D 5e hit die maximum.

#### `battle.classConfig.warrior.baseHp`
- **Type:** Integer
- **Default:** `10`
- **Range:** 8-14 (recommended)
- **D&D 5e:** 10 (d10 hit die)
- **Description:** Warrior starting HP (before CON modifier)

#### `battle.classConfig.rogue.baseHp`
- **Type:** Integer
- **Default:** `8`
- **Range:** 6-10 (recommended)
- **D&D 5e:** 8 (d8 hit die)
- **Description:** Rogue starting HP (before CON modifier)

#### `battle.classConfig.mage.baseHp`
- **Type:** Integer
- **Default:** `6`
- **Range:** 4-8 (recommended)
- **D&D 5e:** 6 (d6 hit die)
- **Description:** Mage starting HP (before CON modifier)

#### `battle.classConfig.cleric.baseHp`
- **Type:** Integer
- **Default:** `8`
- **Range:** 6-10 (recommended)
- **D&D 5e:** 8 (d8 hit die)
- **Description:** Cleric starting HP (before CON modifier)

**HP Calculation:** `Total HP = baseHp + CON modifier`

**Tuning Notes:**
- **Balanced:** Use D&D 5e defaults (10/8/6/8)
- **Tank Meta:** Increase all by 2-4
- **Glass Cannon:** Keep defaults, emphasizes tactical play

---

## Combat Settings

### Critical Hits

#### `battle.combat.crit.threshold`
- **Type:** Integer
- **Default:** `20`
- **Range:** 18-20
- **Description:** Minimum d20 roll required for a critical hit
- **D&D 5e Standard:** 20 (5% chance)
- **Tuning:**
  - 20: Standard (5% crit chance)
  - 19: High variance (10% crit chance)
  - 18: Very swingy (15% crit chance)

#### `battle.combat.crit.multiplier`
- **Type:** Double
- **Default:** `2.0`
- **Range:** 1.5-3.0 (recommended)
- **Description:** Damage multiplier on critical hits
- **D&D 5e Standard:** 2.0 (double damage)
- **Tuning:**
  - 1.5: Lower burst damage
  - 2.0: Standard
  - 2.5-3.0: High risk/reward gameplay

### Turn & Timing

#### `battle.combat.turn.timeoutSeconds`
- **Type:** Integer
- **Default:** `45`
- **Range:** 30-300 (recommended)
- **Description:** Seconds before a turn times out (player forfeits)
- **Tuning:**
  - 30s: Fast-paced, requires quick decisions
  - 45s: Balanced (default)
  - 60-120s: Allows strategic planning
  - 300s: Very casual, good for slow servers

### Cooldowns & Limits

#### `battle.combat.cooldownSeconds`
- **Type:** Integer
- **Default:** `60`
- **Range:** 0-3600 (recommended)
- **Description:** Cooldown after completing a battle before starting a new one
- **Anti-Spam:** Prevents rapid battle grinding
- **Tuning:**
  - 0: No cooldown (testing only)
  - 30-60: Active communities
  - 120-300: Moderate pace
  - 600+: Discourage grinding, emphasize chat XP

#### `battle.combat.maxConcurrentPerGuild`
- **Type:** Integer
- **Default:** `50`
- **Range:** 10-500
- **Description:** Maximum simultaneous active battles per guild
- **Resource Control:** Prevents memory/DB overload
- **Tuning:**
  - 10-25: Small servers (<100 members)
  - 50: Medium servers (100-500 members)
  - 100-200: Large servers (500-5000 members)
  - 300-500: Huge servers (5000+ members)

#### `battle.combat.defendAcBonus`
- **Type:** Integer
- **Default:** `2`
- **Range:** 1-5
- **Description:** Temporary AC bonus granted by the Defend action
- **D&D 5e Standard:** +2 (dodge action equivalent)
- **Tuning:**
  - 1: Defend is weak, encourages aggressive play
  - 2: Balanced (default)
  - 3-5: Strong defense, rewards defensive play

---

## Challenge Settings

#### `battle.challenge.expireSeconds`
- **Type:** Integer
- **Default:** `120`
- **Range:** 30-600 (recommended)
- **Description:** Seconds until a pending challenge expires
- **Special:** Set to 0 for testing (challenges expire immediately)
- **Tuning:**
  - 30-60: Fast-paced servers, clean up spam quickly
  - 120: Balanced (default)
  - 300-600: Casual servers, allows offline users to respond

---

## Progression & XP

### Proficiency Bonus

#### `battle.progression.proficiencyByLevel`
- **Type:** Comma-separated integers (20 values)
- **Default:** `2,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,6,6,6,6`
- **Description:** Proficiency bonus for each level (1-20)
- **D&D 5e Standard:** +2 (1-4), +3 (5-8), +4 (9-12), +5 (13-16), +6 (17-20)
- **Notes:** DO NOT MODIFY unless you understand the combat math implications

### ELO Rating

#### `battle.progression.elo.k`
- **Type:** Integer
- **Default:** `32`
- **Range:** 16-64 (recommended)
- **Description:** ELO K-factor (rating volatility)
- **Chess Standard:** 32 for active players
- **Tuning:**
  - 16: Slow, stable ratings (competitive)
  - 32: Standard (balanced)
  - 48-64: Fast rating changes (casual)

### XP Curve & Level Progression

#### `battle.progression.xp.levelCurve`
- **Type:** Comma-separated integers (20 values)
- **Default:** `0,300,900,2700,6500,14000,23000,34000,48000,64000,85000,100000,120000,140000,165000,195000,225000,265000,305000,355000`
- **Description:** XP required to reach each level (1-20)
- **D&D 5e Standard:** Uses official XP thresholds
- **Notes:** Modifying this changes entire progression pace

### Battle XP Rewards

Battle XP is a **bonus reward** - chat XP is the primary progression source.

#### `battle.progression.xp.baseXp`
- **Type:** Long
- **Default:** `20`
- **Range:** 10-100 (recommended)
- **Description:** Base XP for participating in a battle (win/loss/draw)

#### `battle.progression.xp.winBonus`
- **Type:** Long
- **Default:** `30`
- **Range:** 20-150 (recommended)
- **Description:** Additional XP for winning a battle
- **Total Win XP:** baseXp + winBonus = 50 XP

#### `battle.progression.xp.drawBonus`
- **Type:** Long
- **Default:** `10`
- **Range:** 5-50 (recommended)
- **Description:** Additional XP for a draw
- **Total Draw XP:** baseXp + drawBonus = 30 XP

**Progression Balance:**
- Level 2 (300 XP): ~6 battle wins OR ~20-30 chat messages
- Level 10 (64,000 XP): ~1,280 battle wins OR ~5,120 messages
- **Chat XP is intentionally the primary source** to reward server participation

### Chat XP (Primary Progression)

#### `battle.progression.chatXp.enabled`
- **Type:** Boolean
- **Default:** `true`
- **Description:** Enable chat-based XP rewards
- **Notes:** Disabling removes primary progression source

#### `battle.progression.chatXp.baseXp`
- **Type:** Integer
- **Default:** `10`
- **Range:** 5-25 (recommended)
- **Description:** Base XP per chat message

#### `battle.progression.chatXp.bonusXpMax`
- **Type:** Integer
- **Default:** `5`
- **Range:** 0-10 (recommended)
- **Description:** Maximum random bonus XP per message (0 to bonusXpMax)
- **Total Range:** baseXp to (baseXp + bonusXpMax) = 10-15 XP per message

#### `battle.progression.chatXp.cooldownSeconds`
- **Type:** Integer
- **Default:** `60`
- **Range:** 30-300 (recommended)
- **Description:** Cooldown between XP-awarding messages per user
- **Anti-Spam:** Prevents message spam for XP
- **Tuning:**
  - 30s: Fast-paced, active servers
  - 60s: Balanced (default)
  - 120-300s: Slow progression, casual servers

#### `battle.progression.chatXp.levelUpNotification`
- **Type:** Boolean
- **Default:** `true`
- **Description:** React with ⭐ emoji when user levels up
- **Notes:** Provides visible feedback, encourages engagement

#### `battle.progression.chatXp.autoCreateCharacter`
- **Type:** Boolean
- **Default:** `true`
- **Description:** Automatically create a character on first message
- **Notes:** Seamless onboarding - users don't need to run `/create-character` first
- **Class Selection:** Auto-assigns class based on first message hash

---

## Scheduler Settings

### Timeout Checking

#### `battle.scheduler.timeout.enabled`
- **Type:** Boolean
- **Default:** `true`
- **Description:** Enable automatic timeout checking for stale battles
- **Notes:** Disabling may leave abandoned battles in memory

#### `battle.scheduler.timeout.checkIntervalMs`
- **Type:** Long (milliseconds)
- **Default:** `30000` (30 seconds)
- **Range:** 10000-300000 (recommended)
- **Description:** How often to scan for timed-out battles
- **Resource Impact:** Lower values = more CPU usage
- **Tuning:**
  - 10-30s: Responsive timeout handling
  - 60-120s: Balanced for larger servers
  - 300s+: Low resource usage, slow timeout response

### Challenge Cleanup

#### `battle.scheduler.cleanup.checkIntervalMs`
- **Type:** Long (milliseconds)
- **Default:** `120000` (2 minutes)
- **Range:** 60000-600000 (recommended)
- **Description:** How often to cleanup expired challenges
- **Tuning:**
  - 60s: Fast cleanup, higher resource usage
  - 120s: Balanced (default)
  - 300-600s: Slower cleanup, lower resource usage

---

## Tuning Guidelines

### Small Servers (<100 members)

```properties
battle.enabled=true
battle.combat.cooldownSeconds=30
battle.combat.maxConcurrentPerGuild=10
battle.challenge.expireSeconds=60
battle.progression.chatXp.cooldownSeconds=30
battle.scheduler.timeout.checkIntervalMs=60000
```

**Profile:** Fast-paced, responsive, encourages frequent battles

### Medium Servers (100-500 members)

```properties
battle.enabled=true
battle.combat.cooldownSeconds=60
battle.combat.maxConcurrentPerGuild=50
battle.challenge.expireSeconds=120
battle.progression.chatXp.cooldownSeconds=60
battle.scheduler.timeout.checkIntervalMs=30000
```

**Profile:** Balanced defaults (recommended for most servers)

### Large Servers (500-5000 members)

```properties
battle.enabled=true
battle.combat.cooldownSeconds=120
battle.combat.maxConcurrentPerGuild=100
battle.challenge.expireSeconds=180
battle.progression.chatXp.cooldownSeconds=90
battle.scheduler.timeout.checkIntervalMs=60000
```

**Profile:** Moderate pace, prevents spam, emphasizes chat XP

### Huge Servers (5000+ members)

```properties
battle.enabled=true
battle.combat.cooldownSeconds=300
battle.combat.maxConcurrentPerGuild=200
battle.challenge.expireSeconds=300
battle.progression.chatXp.cooldownSeconds=120
battle.scheduler.timeout.checkIntervalMs=120000
battle.scheduler.cleanup.checkIntervalMs=300000
```

**Profile:** Resource-optimized, chat XP primary source, battles are rare events

### Competitive/PvP Focus

```properties
battle.combat.crit.threshold=20
battle.combat.crit.multiplier=2.0
battle.progression.xp.baseXp=50
battle.progression.xp.winBonus=100
battle.progression.chatXp.baseXp=5
battle.progression.chatXp.bonusXpMax=2
```

**Profile:** Battles are primary XP source, emphasizes combat skill

### Casual/Social Focus

```properties
battle.combat.turn.timeoutSeconds=120
battle.combat.cooldownSeconds=300
battle.progression.xp.baseXp=20
battle.progression.xp.winBonus=30
battle.progression.chatXp.baseXp=15
battle.progression.chatXp.bonusXpMax=10
```

**Profile:** Chat is primary XP source, battles are optional side content

---

## Troubleshooting

### Issue: Characters progress too slowly

**Solution:** Increase chat XP or decrease cooldown
```properties
battle.progression.chatXp.baseXp=15
battle.progression.chatXp.bonusXpMax=10
battle.progression.chatXp.cooldownSeconds=45
```

### Issue: Too many simultaneous battles

**Solution:** Reduce max concurrent or increase cooldown
```properties
battle.combat.maxConcurrentPerGuild=25
battle.combat.cooldownSeconds=120
```

### Issue: Battles feel too slow

**Solution:** Reduce turn timeout
```properties
battle.combat.turn.timeoutSeconds=30
```

### Issue: Crits are too rare/common

**Solution:** Adjust threshold (lower = more crits)
```properties
battle.combat.crit.threshold=19  # 10% chance instead of 5%
```

### Issue: Users spam challenges

**Solution:** Reduce expire time to auto-cleanup faster
```properties
battle.challenge.expireSeconds=60
```

### Issue: Memory/performance problems

**Solution:** Reduce concurrent battles and increase scheduler intervals
```properties
battle.combat.maxConcurrentPerGuild=25
battle.scheduler.timeout.checkIntervalMs=120000
battle.scheduler.cleanup.checkIntervalMs=300000
```

---

## Viewing Current Configuration

Use the admin command `/battle-config-reload` to view current configuration in Discord (requires Administrator or Manage Server permission).

---

## Configuration Validation

The battle system validates configuration at startup:

1. **Required Values:** All class base HP values must be > 0
2. **Type Safety:** Spring Boot validates types automatically
3. **Range Validation:** BattleProperties.java validates critical ranges

Check startup logs for configuration warnings or errors.

---

## Notes

- **All changes require bot restart** - Configuration uses `@ConfigurationProperties` which loads at startup
- **Test changes in dev first** - Invalid config can prevent bot startup
- **Backup before tuning** - Keep a copy of working configuration
- **Monitor metrics** - Use `/battle-stats` to see impact of config changes

---

**Phase 12 Implementation Date:** 2025-11-16
**Last Updated:** 2025-11-16
