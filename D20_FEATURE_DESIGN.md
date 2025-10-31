# D20 Roll Mechanic - Feature Design

## Overview
Add an optional `/d20` command that allows users to "double down" on their roll for a chance at rewards or penalties. This is completely opt-in and only available as a follow-up to `/roll`.

## Core Concept
Players use `/roll` normally, then have a **60-minute window** to optionally use `/d20` to roll a virtual d20 for bonus effects or penalties.

---

## Commands

### `/roll` - Standard Roll (Unchanged)
- Normal color gacha roll
- Starts 24-hour cooldown
- Opens 60-minute `/d20` window (if conditions met)
- Shows hint about `/d20` availability

### `/d20` - Risk Roll (New!)
- Only available for 60 minutes after using `/roll`
- Rolls a virtual d20 for bonus/penalty effects
- Shows animated d20 GIF with suspenseful text reveal
- Can only be used once per roll cycle
- Closes the 60-minute window after use

---

## D20 Mechanics

### Nat 20 (5% chance) - "Lucky Streak" 🎉
- **Effect**: Next roll (either `/roll` or `/d20`) is guaranteed Epic or Legendary rarity
- **Duration**: Buff persists until used (even across multiple days)
- **Cooldown**: Standard 24-hour cooldown applies
- **Display**: Shown in `/mycolor` as "🎲 Next roll guaranteed Epic+!"

### Nat 1 (5% chance) - "Critical Failure" 💀
- **Effect**: Cooldown extended from 24 hours to 48 hours
- **Note**: You still keep the color you rolled with `/roll`
- **Applies to**: Both `/roll` and `/d20` commands locked for 48 hours
- **Display**: Shown in `/mycolor` as "⏳ 36 hours remaining (critical failure)"

### 2-19 (90% chance) - Normal Roll
- **Effect**: No special effect
- **Cooldown**: Standard 24-hour cooldown continues normally

---

## Availability Conditions

The `/d20` command is **only available** when:
1. ✅ Server has **3 or more** Epic or Legendary gacha roles configured
2. ✅ User has used `/roll` within the last 60 minutes
3. ✅ User has NOT already used `/d20` this cycle
4. ✅ User is NOT on cooldown (applies to `/roll` too)

If conditions are not met, appropriate error messages guide the user.

---

## User Flow Examples

### Scenario 1: Normal Flow (Taking the Risk)
```
1. User: /roll
   Bot: You rolled... Rare Green!
        🎲 Feeling lucky? Use /d20 within the next 60 minutes to roll for a bonus or penalty!

2. User: /d20 (within 60 minutes)
   Bot: [Animated GIF of rolling d20]
        🎲 Rolling... 7... 15... 20!
        🎉 NAT 20! Your next roll is guaranteed to be Epic or Legendary!

3. User: /roll (after 24 hours)
   Bot: You rolled... Legendary Rainbow! (guaranteed Epic+)
        🎲 Feeling lucky? Use /d20 within the next 60 minutes to roll for a bonus or penalty!
```

### Scenario 2: Playing It Safe
```
1. User: /roll
   Bot: You rolled... Common Blue!
        🎲 Feeling lucky? Use /d20 within the next 60 minutes to roll for a bonus or penalty!

2. User: (ignores /d20, waits 24 hours)

3. User: /roll (after 24 hours)
   Bot: You rolled... Uncommon Purple!
        🎲 Feeling lucky? Use /d20 within the next 60 minutes to roll for a bonus or penalty!
```

### Scenario 3: Critical Failure
```
1. User: /roll
   Bot: You rolled... Epic Gold!
        🎲 Feeling lucky? Use /d20 within the next 60 minutes to roll for a bonus or penalty!

2. User: /d20
   Bot: [Animated GIF of rolling d20]
        🎲 Rolling... 12... 4... 1!
        💀 NAT 1! Critical failure! Your next cooldown is extended to 48 hours.

3. User: /roll (tries after 24 hours)
   Bot: ⏳ You can roll again in 24 hours.

4. User: /roll (after 48 hours total)
   Bot: You rolled... Rare Red!
```

---

## Error Messages

### `/d20` with no active window
```
⏳ You must use /roll first!
The /d20 command is only available for 60 minutes after using /roll.
```

### `/d20` already used this cycle
```
🎲 You've already used /d20 for this roll!
Wait for your cooldown to reset, then use /roll to start a new cycle.
```

### `/d20` when server has insufficient Epic+ roles
```
🎲 The /d20 feature requires at least 3 Epic or Legendary roles to be configured.
Ask your server admin to add more high-tier roles!
```

### `/d20` after window expired
```
⏱️ The /d20 window has expired!
You have 60 minutes after using /roll to use /d20.
```

---

## Visual Presentation

### D20 Animation
- Display animated d20 GIF (hosted locally at `/images/d20-roll.gif`)
- Text reveals result progressively: "Rolling... 7... 15... **20!**"
- Timing: ~600ms between text updates for suspense
- Special visual treatment for nat 1 and nat 20 (different embed colors, emojis)

### `/mycolor` Command Updates
Show active buffs and penalties:
- **With Epic+ buff**: "🎲 Next roll guaranteed Epic+!"
- **With extended cooldown**: "⏳ 36 hours remaining (critical failure)"
- **With active d20 window**: "🎲 /d20 available for 45 more minutes"

### `/roll` Response Hint
After successful roll, if conditions met:
```
🎲 Feeling lucky? Use /d20 within the next 60 minutes to roll for a bonus or penalty!
```

Only show if:
- Server has 3+ Epic/Legendary roles
- User hasn't seen it recently (don't spam every roll)

---

## Database Schema Changes

### Updates to `UserCooldown` Entity
Add new fields:
```java
private Instant lastRollTime;        // When /roll was used
private boolean d20Used;             // Whether /d20 used this cycle
private boolean guaranteedEpicPlus;  // Nat 20 buff active
```

### Migration Notes
- Add columns with default values (null/false)
- Existing cooldowns continue working normally
- New fields only used when `/d20` feature is active

---

## Edge Cases & Handling

### 1. User has Epic+ buff and uses `/d20`
- ✅ The buff applies to the color roll from `/roll`
- ✅ The `/d20` can potentially grant another buff (stacking for next-next roll)
- ✅ Or could trigger nat 1 penalty despite getting good color this time

### 2. Admin deletes Epic roles mid-cycle
- ✅ Check role count dynamically on `/d20` invocation
- ✅ If <3 Epic+ roles, show error message immediately
- ✅ Existing buffs still work (honor commitments)

### 3. 48-hour cooldown from nat 1
- ✅ Locks both `/roll` and `/d20` for full 48 hours
- ✅ Cooldown message shows remaining time
- ✅ After expiry, user can `/roll` normally and get new 60min window

### 4. Timing precision at 60:00 boundary
- ✅ Store exact `Instant` timestamp of `/roll`
- ✅ Check: `Duration.between(lastRollTime, now).toMinutes() < 60`
- ✅ Fails gracefully if exactly at or past 60 minutes

### 5. User has multiple buffs/penalties across guilds
- ✅ All state is per-guild (stored in `UserCooldown` with guild ID)
- ✅ Buff in Server A doesn't affect Server B
- ✅ Penalty in Server A doesn't affect Server B

### 6. Server has no Epic/Legendary roles at all
- ✅ `/d20` hint doesn't show in `/roll` response
- ✅ If user tries `/d20` anyway, shows error about needing 3+ roles
- ✅ No admin configuration needed - automatic based on roles

### 7. Role hierarchy issues with guaranteed Epic+
- ✅ Use existing role assignment logic
- ✅ If bot can't assign Epic role, falls back to error handling
- ✅ Buff is NOT consumed if assignment fails (retry next roll)

---

## Testing Checklist

### Unit Tests
- [ ] D20 roll probability distribution (should be uniform 1-20)
- [ ] 60-minute window calculation accuracy
- [ ] Epic+ role counting logic
- [ ] Buff application and clearing
- [ ] Cooldown extension on nat 1
- [ ] Window closure after `/d20` use

### Integration Tests
- [ ] `/roll` followed by `/d20` within window
- [ ] `/d20` after window expires (should fail)
- [ ] `/d20` without prior `/roll` (should fail)
- [ ] Nat 20 buff carries to next roll
- [ ] Nat 1 extends cooldown to 48 hours
- [ ] Multiple guilds with independent state
- [ ] Role count check on `/d20` invocation

### Manual Testing
- [ ] GIF displays correctly in Discord
- [ ] Text animation timing feels good
- [ ] Error messages are clear and helpful
- [ ] `/mycolor` shows buff status correctly
- [ ] Cooldown countdown accurate
- [ ] Works on mobile Discord client

---

## Implementation Checklist

### Backend
- [ ] Download and host d20 GIF in `src/main/resources/static/images/`
- [ ] Update `UserCooldown` entity with new fields
- [ ] Add migration/schema update for new columns
- [ ] Implement `/d20` command handler in `SlashCommandHandler`
- [ ] Add d20 roll logic with nat 1/20 special handling
- [ ] Implement 60-minute window validation
- [ ] Add Epic+ role counting utility method
- [ ] Update color selection logic for guaranteed Epic+ buff
- [ ] Implement cooldown extension for nat 1
- [ ] Add animated response with GIF embed and text edits
- [ ] Update `/mycolor` to show buff/penalty status
- [ ] Update `/roll` response to show `/d20` hint (conditional)
- [ ] Write unit tests for all new logic
- [ ] Write integration tests for command flow

### No Frontend Changes Needed
- ✅ No admin panel configuration required
- ✅ Feature is automatic based on role configuration
- ✅ All interaction happens via Discord slash commands

### Documentation
- [ ] Update `CLAUDE.md` with `/d20` command documentation
- [ ] Update slash command help text (in `/help` response)
- [ ] Add to `WEB_API_README.md` if any API changes needed
- [ ] Update this design doc with any implementation learnings

---

## Future Enhancements (Out of Scope)

These are NOT part of the initial implementation but could be considered later:

- **Different dice types**: `/d12`, `/d100`, etc. with different risk/reward profiles
- **Stacking buffs**: Allow multiple nat 20s to stack (e.g., guaranteed Legendary specifically)
- **Penalty mitigation**: Items or commands to reduce 48hr cooldown
- **Statistics tracking**: Show user's d20 roll history and luck stats
- **Achievements**: Badges for rolling multiple nat 20s, surviving nat 1s, etc.
- **Custom GIFs**: Allow admins to upload custom d20 animations
- **Sound effects**: Play dice rolling sound (if Discord adds audio support to bots)

---

## Open Questions for Feedback

1. **Is 60 minutes the right window duration?** Too short? Too long?
2. **Should the hint show every time or only occasionally?** Could get spammy.
3. **Is 48 hours too harsh for nat 1?** Or just right for the risk?
4. **Should we allow stacking buffs?** (Nat 20 while already having Epic+ buff)
5. **Any other rewards/penalties for other d20 results?** Or keep it simple with just 1 and 20?

---

## Approval & Sign-off

- [ ] Feature requester approval (9ofOrange, W* P* S*)
- [ ] Server admin feedback
- [ ] Developer sign-off on technical feasibility
- [ ] UX review of messages and flow
- [ ] Ready to implement

---

**Version**: 1.0
**Last Updated**: 2025-10-30
**Status**: Pending Feedback
