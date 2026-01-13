package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;

/**
 * Comprehensive help command for the battle system.
 * Phase 10: Documentation & Help
 */
@Component
public class BattleCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(BattleCommandHandler.class);

    private final BattleProperties battleProperties;

    public BattleCommandHandler(BattleProperties battleProperties) {
        this.battleProperties = battleProperties;
    }

    @Override
    public boolean canHandle(String commandName) {
        // Only handle if battle system is enabled
        if (!battleProperties.isEnabled()) {
            return false;
        }

        return "battle-help".equals(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        if ("battle-help".equals(commandName)) {
            String topic = event.getOption("topic") != null
                ? event.getOption("topic").getAsString()
                : "overview";
            handleBattleHelp(event, topic);
        }
    }

    private void handleBattleHelp(SlashCommandInteractionEvent event, String topic) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        switch (topic.toLowerCase()) {
            case "commands" -> buildCommandsHelp(embed);
            case "character" -> buildCharacterHelp(embed);
            case "combat" -> buildCombatHelp(embed);
            case "abilities" -> buildAbilitiesHelp(embed);
            case "status" -> buildStatusEffectsHelp(embed);
            case "progression" -> buildProgressionHelp(embed);
            default -> buildOverviewHelp(embed);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();

        if (battleProperties.isDebug()) {
            logger.debug("battle-help topic={} displayed for user {} in guild {}",
                topic, event.getUser().getId(),
                event.getGuild() != null ? event.getGuild().getId() : "DM");
        }
    }

    private void buildOverviewHelp(EmbedBuilder embed) {
        embed.setTitle("⚔️ Battle System - Overview");

        embed.setDescription(
            "Welcome to the D&D 5e inspired battle system! Create characters, duel other players, " +
            "and climb the leaderboards through combat and chat participation.\n\n" +
            "**Status:** " + (battleProperties.isEnabled() ? "✅ Enabled" : "❌ Disabled")
        );

        embed.addField(
            "📚 Help Topics",
            "`/battle-help topic:commands` - All available commands\n" +
            "`/battle-help topic:character` - Character creation & stats\n" +
            "`/battle-help topic:combat` - Combat mechanics & duels\n" +
            "`/battle-help topic:abilities` - Spells, skills & abilities\n" +
            "`/battle-help topic:status` - Status effects explained\n" +
            "`/battle-help topic:progression` - XP, levels & leaderboards",
            false
        );

        embed.addField(
            "⚡ Quick Start",
            "1️⃣ Create a character: `/create-character`\n" +
            "2️⃣ View your stats: `/character`\n" +
            "3️⃣ Learn abilities: `/abilities`\n" +
            "4️⃣ Challenge someone: `/duel @user`\n" +
            "5️⃣ Gain XP by chatting and battling!",
            false
        );

        embed.addField(
            "🎯 Core Features",
            "• **Auto-character creation** - Start earning XP from chat immediately\n" +
            "• **Turn-based PvP duels** - Strategic combat with abilities\n" +
            "• **12 status effects** - Stun, burn, poison, shields & more\n" +
            "• **Progression system** - Level up to 20, earn ELO rankings\n" +
            "• **4 classes** - Warrior, Rogue, Mage, Cleric\n" +
            "• **Leaderboards** - Compete for top ELO, wins, and levels",
            false
        );

        embed.setFooter("Battle System v1.0 | Phases 0-9 Complete | Based on D&D 5e");
    }

    private void buildCommandsHelp(EmbedBuilder embed) {
        embed.setTitle("⚔️ Battle System - Commands");

        embed.addField(
            "👤 Character Commands",
            "`/create-character` - Create your battle character (interactive)\n" +
            "`/character [user]` - View character sheet\n" +
            "`/abilities [type] [page]` - Browse and learn abilities",
            false
        );

        embed.addField(
            "⚔️ Combat Commands",
            "`/duel @user` - Challenge someone to a 1v1 duel\n" +
            "`/forfeit` - Surrender current battle (awards opponent win)",
            false
        );

        embed.addField(
            "📊 Stats & Leaderboards",
            "`/leaderboard [type] [limit]` - View rankings\n" +
            "  • `type:elo` - Top ELO rankings (default)\n" +
            "  • `type:wins` - Most victories\n" +
            "  • `type:level` - Highest levels\n" +
            "  • `type:activity` - Most battles fought\n" +
            "`/battle-stats` - View global battle statistics",
            false
        );

        embed.addField(
            "ℹ️ Help & Info",
            "`/battle-help [topic]` - This help system\n" +
            "  • Topics: `commands`, `character`, `combat`, `abilities`, `status`, `progression`",
            false
        );

        embed.addField(
            "💬 Chat XP System",
            "• Earn **10-15 XP** per message (60s cooldown)\n" +
            "• Characters auto-created on first message\n" +
            "• Level-up reactions (⭐) notify progress\n" +
            "• Primary progression path (battles give bonus XP)",
            false
        );

        embed.setFooter("Use /battle-help topic:<name> for detailed guides");
    }

    private void buildCharacterHelp(EmbedBuilder embed) {
        embed.setTitle("⚔️ Battle System - Characters");

        embed.setDescription(
            "Characters use D&D 5e ability scores and point-buy allocation. " +
            "Your stats determine combat effectiveness and available abilities."
        );

        embed.addField(
            "📊 Ability Scores",
            "• **STR** (Strength) - Melee attack & damage (Warrior)\n" +
            "• **DEX** (Dexterity) - AC, initiative, ranged attacks (Rogue)\n" +
            "• **CON** (Constitution) - Hit points & durability\n" +
            "• **INT** (Intelligence) - Arcane spells & knowledge (Mage)\n" +
            "• **WIS** (Wisdom) - Divine magic & perception (Cleric)\n" +
            "• **CHA** (Charisma) - Social skills & presence",
            false
        );

        embed.addField(
            "🎲 Point-Buy System",
            String.format(
                "Allocate **%d points** across your stats (range %d-%d):\n" +
                "```\n" +
                "Score  8  9  10  11  12  13  14  15\n" +
                "Cost   0  1   2   3   4   5   7   9\n" +
                "```\n" +
                "**Example:** 15,14,13,12,10,8 uses exactly 27 points",
                battleProperties.getCharacter().getPointBuy().getTotalPoints(),
                battleProperties.getCharacter().getPointBuy().getMinScore(),
                battleProperties.getCharacter().getPointBuy().getMaxScore()
            ),
            false
        );

        embed.addField(
            "🛡️ Classes & Roles",
            "**Warrior** - Tank/Damage (High HP, STR abilities)\n" +
            "  • Base HP: High | Primary: STR | Armor: Heavy\n" +
            "  • Abilities: Power Attack, Rending Strike, Sunder Armor\n\n" +
            "**Rogue** - DPS/Mobility (Crits, DEX abilities)\n" +
            "  • Base HP: Medium | Primary: DEX | Armor: Light\n" +
            "  • Abilities: Sneak Attack, Poison Strike, Evasion\n\n" +
            "**Mage** - Caster/Control (INT spells, status effects)\n" +
            "  • Base HP: Low | Primary: INT | Armor: None\n" +
            "  • Abilities: Fireball, Shocking Grasp, Haste, Slow\n\n" +
            "**Cleric** - Support/Healer (WIS spells, buffs)\n" +
            "  • Base HP: Medium-High | Primary: WIS | Armor: Medium\n" +
            "  • Abilities: Regeneration, Shield of Faith, Bless",
            false
        );

        embed.addField(
            "🧮 Derived Stats",
            "• **HP** = Class base + (CON modifier × level)\n" +
            "• **AC** = 10 + DEX modifier\n" +
            "• **Ability Modifier** = (Score - 10) ÷ 2 (rounded down)\n" +
            "• **Proficiency Bonus** = Based on level (starts at +2)",
            false
        );

        embed.setFooter("Use /create-character to build your character");
    }

    private void buildCombatHelp(EmbedBuilder embed) {
        embed.setTitle("⚔️ Battle System - Combat");

        embed.setDescription(
            "Turn-based duels using D&D 5e combat mechanics. " +
            "Use strategy, abilities, and status effects to defeat opponents."
        );

        embed.addField(
            "⚡ Combat Flow",
            "1️⃣ `/duel @user` - Issue challenge\n" +
            "2️⃣ Opponent clicks **Accept** or **Decline**\n" +
            "3️⃣ Initiative determines who goes first (DEX + d20)\n" +
            "4️⃣ On your turn: **⚔️ Attack**, **🛡️ Defend**, or use **Ability**\n" +
            "5️⃣ Battle ends when HP reaches 0 or someone forfeits\n" +
            "6️⃣ Winner gains XP & ELO, loser gets participation XP",
            false
        );

        embed.addField(
            "🎲 Attack Mechanics",
            "**Attack Roll:** d20 + proficiency + ability modifier\n" +
            "• Must meet or exceed target's AC to hit\n" +
            "• Natural 20 = **Critical Hit** (double damage)\n" +
            "• Natural 1 = automatic miss\n\n" +
            "**Damage:** Weapon/spell dice + ability modifier\n" +
            "• Warriors/Rogues use STR/DEX for melee\n" +
            "• Mages use INT for spell damage\n" +
            "• Clerics use WIS for divine spells",
            false
        );

        embed.addField(
            "🛡️ Defend Action",
            "• Grants **+2 AC** until your next turn\n" +
            "• Useful when low HP or facing strong attacks\n" +
            "• Defensive positioning in dangerous situations",
            false
        );

        embed.addField(
            "⏱️ Battle Limits",
            "• **Turn timeout:** 5 minutes per action\n" +
            "• **Challenge expiration:** 10 minutes to accept\n" +
            "• **Cooldown:** 24 hours between battles (per user pair)\n" +
            "• **Forfeit:** Available anytime (counts as loss)",
            false
        );

        embed.addField(
            "🏆 Victory Conditions",
            "• Reduce opponent's HP to 0\n" +
            "• Opponent forfeits\n" +
            "• Opponent times out (5 min inactive)\n" +
            "• **Draw:** Both reach 0 HP simultaneously",
            false
        );

        embed.setFooter("Use /battle-help topic:abilities for spell/skill details");
    }

    private void buildAbilitiesHelp(EmbedBuilder embed) {
        embed.setTitle("⚔️ Battle System - Abilities");

        embed.setDescription(
            "Learn and use spells, skills, and feats to enhance your combat effectiveness. " +
            "Each class has unique abilities based on their primary stat."
        );

        embed.addField(
            "📜 Ability Types",
            "**TALENT** - Passive bonuses (always active)\n" +
            "  • Examples: Weapon Focus (+1 attack), Tough (+HP)\n\n" +
            "**SKILL** - Active combat abilities (no resource cost)\n" +
            "  • Examples: Power Attack, Sneak Attack, Cleave\n\n" +
            "**SPELL** - Magical effects (uses spell slots)\n" +
            "  • Examples: Fireball, Cure Wounds, Shield\n\n" +
            "**FEAT** - Universal improvements (any class)\n" +
            "  • Examples: Lucky, Alert, Resilient",
            false
        );

        embed.addField(
            "✨ Learning Abilities",
            "1. Use `/abilities` to browse available abilities\n" +
            "2. Filter by type: `TALENT`, `SKILL`, `SPELL`, `FEAT`\n" +
            "3. Click **Learn** button to acquire\n" +
            "4. Requirements: appropriate class, level, stats\n" +
            "5. Use in battle via **Ability** button dropdown",
            false
        );

        embed.addField(
            "⚡ Spell Resources",
            "**Spell Slots** - Limited uses per rest\n" +
            "  • Levels 1-9 (higher = more powerful)\n" +
            "  • Restore via `/rest` command (not yet implemented)\n\n" +
            "**Cooldowns** - Time-based restrictions\n" +
            "  • Powerful abilities have cooldowns\n" +
            "  • Tracked per-character, per-ability\n\n" +
            "**Charges** - Multi-use abilities\n" +
            "  • Some abilities have limited charges\n" +
            "  • Restore on short or long rest",
            false
        );

        embed.addField(
            "🎯 Ability Effects",
            "Abilities can apply various effects:\n" +
            "• **DAMAGE** - Increased damage output\n" +
            "• **AC** - Improved armor class\n" +
            "• **MAX_HP** - Higher hit points\n" +
            "• **CRIT_DAMAGE** - Bonus critical hit damage\n" +
            "• **STATUS** - Apply burn, stun, poison, etc.",
            false
        );

        embed.setFooter("Use /abilities to explore class-specific spells & skills");
    }

    private void buildStatusEffectsHelp(EmbedBuilder embed) {
        embed.setTitle("⚔️ Battle System - Status Effects");

        embed.setDescription(
            "Status effects alter combat dynamics through buffs, debuffs, and damage-over-time. " +
            "Effects stack, have durations, and can turn the tide of battle."
        );

        embed.addField(
            "💥 Damage Over Time (DoT)",
            "**🔥 BURN** - Fire damage each turn\n" +
            "  • Source: Fireball, Flame Strike\n" +
            "  • Stacks for increased damage\n\n" +
            "**☠️ POISON** - Toxic damage each turn\n" +
            "  • Source: Poison Strike, Venomous Touch\n" +
            "  • Bypasses some defenses\n\n" +
            "**🩸 BLEED** - Physical damage over time\n" +
            "  • Source: Rending Strike, Deep Wounds\n" +
            "  • Prevents healing (not yet implemented)",
            false
        );

        embed.addField(
            "🛡️ Defensive Effects",
            "**💙 SHIELD** - Absorbs damage before HP\n" +
            "  • Source: Shield of Faith, Mage Armor\n" +
            "  • Magnitude = damage absorbed\n\n" +
            "**🙏 PROTECTION** - Reduces incoming damage\n" +
            "  • Source: Protection from Evil, Stoneskin\n" +
            "  • Percentage damage reduction\n\n" +
            "**💚 REGEN** - Healing over time\n" +
            "  • Source: Regeneration, Troll Blood\n" +
            "  • Restores HP each turn",
            false
        );

        embed.addField(
            "⚡ Offensive Buffs/Debuffs",
            "**💪 STRENGTH** - Increased damage output\n" +
            "  • Source: Bless, Bull's Strength\n" +
            "  • Bonus damage per attack\n\n" +
            "**💔 WEAKNESS** - Reduced damage output\n" +
            "  • Source: Crippling Blow, Ray of Enfeeblement\n" +
            "  • Penalty to damage dealt\n\n" +
            "**🎯 VULNERABILITY** - Increases damage taken\n" +
            "  • Source: Sunder Armor, Expose Weakness\n" +
            "  • Amplifies incoming damage",
            false
        );

        embed.addField(
            "🌀 Control Effects",
            "**😵 STUN** - Skip your next turn\n" +
            "  • Source: Shocking Grasp, Stunning Strike\n" +
            "  • Most powerful control effect\n\n" +
            "**⚡ HASTE** - Extra actions (future)\n" +
            "  • Source: Haste spell\n" +
            "  • Not yet fully implemented\n\n" +
            "**🐌 SLOW** - Reduced effectiveness (future)\n" +
            "  • Source: Slow spell\n" +
            "  • Planned for future phases",
            false
        );

        embed.addField(
            "📊 Effect Mechanics",
            "• **Duration:** Measured in turns (decrements after your turn)\n" +
            "• **Stacks:** Multiple applications increase magnitude\n" +
            "• **Refresh:** Reapplying extends duration to maximum\n" +
            "• **Display:** Active effects shown in HP field\n" +
            "• **Cleanup:** All effects removed when battle ends",
            false
        );

        embed.setFooter("Status effects add strategic depth to combat");
    }

    private void buildProgressionHelp(EmbedBuilder embed) {
        embed.setTitle("⚔️ Battle System - Progression");

        embed.setDescription(
            "Progress your character through chat participation and combat victories. " +
            "Earn XP to level up, gain ELO for competitive ranking."
        );

        embed.addField(
            "💬 Chat XP (Primary)",
            "**How it works:**\n" +
            "• Earn **10-15 XP** per message\n" +
            "• **60-second cooldown** per user\n" +
            "• Auto-creates character on first message\n" +
            "• Level-up notification: ⭐ reaction\n\n" +
            "**Progression rate:**\n" +
            "• Level 2: ~20-30 messages\n" +
            "• Level 10: ~5,100 messages (2-3 months active)\n" +
            "• Level 20: ~24,000 messages (6-12 months)",
            false
        );

        embed.addField(
            "⚔️ Battle XP (Bonus)",
            "**XP Rewards:**\n" +
            "• **Victory:** 50 XP (20 base + 30 win bonus)\n" +
            "• **Draw:** 30 XP (20 base + 10 draw bonus)\n" +
            "• **Loss:** 20 XP (participation only)\n\n" +
            "Battles provide bonus XP but aren't required for progression. " +
            "A single battle win = 3-5 chat messages worth of XP.",
            false
        );

        embed.addField(
            "📈 Leveling System",
            "**Level 1-20** (D&D 5e XP curve):\n" +
            "• Level 1: 0 XP (starting point)\n" +
            "• Level 2: 300 XP\n" +
            "• Level 5: 6,500 XP\n" +
            "• Level 10: 64,000 XP\n" +
            "• Level 20: 355,000 XP (max level)\n\n" +
            "**Benefits per level:**\n" +
            "• Increased HP (CON modifier per level)\n" +
            "• Proficiency bonus (+2 to +6)\n" +
            "• Access to higher-level abilities",
            false
        );

        embed.addField(
            "🏆 ELO Ranking System",
            "**How ELO works:**\n" +
            "• Competitive skill rating (starts at 1000)\n" +
            "• Gains/losses based on opponent's ELO\n" +
            "• Beating higher ELO = bigger gain\n" +
            "• Losing to lower ELO = bigger loss\n\n" +
            "**Formula:** `ΔE = K × (Score - Expected)`\n" +
            "• K = 32 (change rate)\n" +
            "• Score: 1.0 (win), 0.5 (draw), 0.0 (loss)\n" +
            "• Expected based on 400-point difference",
            false
        );

        embed.addField(
            "📊 Battle Statistics",
            "**Tracked stats:**\n" +
            "• **Wins** - Total victories\n" +
            "• **Losses** - Total defeats\n" +
            "• **Draws** - Simultaneous defeats\n" +
            "• **Win Rate** - Wins ÷ Total battles\n" +
            "• **Total Battles** - Win + Loss + Draw\n" +
            "• **Current ELO** - Competitive ranking\n\n" +
            "View your stats with `/character`",
            false
        );

        embed.addField(
            "🥇 Leaderboards",
            "**Ranking types:**\n" +
            "• `/leaderboard type:elo` - Top ELO players\n" +
            "• `/leaderboard type:wins` - Most victories\n" +
            "• `/leaderboard type:level` - Highest levels\n" +
            "• `/leaderboard type:activity` - Most battles\n\n" +
            "**Medals:** 🥇 (1st) 🥈 (2nd) 🥉 (3rd)\n" +
            "Guild-specific rankings only",
            false
        );

        embed.setFooter("Chat to level up | Battle to climb ELO rankings");
    }
}
