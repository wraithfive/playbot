package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;

/**
 * Handles all battle-related slash commands.
 * Commands are only active when battle.enabled=true.
 */
@Component
public class BattleCommandHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(BattleCommandHandler.class);
    
    private final BattleProperties battleProperties;

    public BattleCommandHandler(BattleProperties battleProperties) {
        this.battleProperties = battleProperties;
    }

    @Override
    public void onReady(ReadyEvent event) {
        if (!battleProperties.isEnabled()) {
            logger.info("Battle system is disabled (battle.enabled=false). Commands will not be registered.");
            return;
        }

        logger.info("Battle system enabled. Registering battle commands...");
        
        event.getJDA().updateCommands().addCommands(
            Commands.slash("battle-help", "Show information about the battle system")
        ).queue(
            success -> logger.info("Battle commands registered successfully"),
            error -> logger.error("Failed to register battle commands", error)
        );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!battleProperties.isEnabled()) {
            return; // Silently ignore if disabled
        }

        String commandName = event.getName();
        
        try {
            switch (commandName) {
                case "battle-help" -> handleBattleHelp(event);
                default -> {
                    // Not a battle command
                }
            }
        } catch (Exception e) {
            logger.error("Error handling battle command: {}", commandName, e);
            event.reply("❌ An error occurred while processing your command.")
                .setEphemeral(true)
                .queue();
        }
    }

    private void handleBattleHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚔️ Battle System Guide");
        embed.setColor(Color.BLUE);
        
        embed.setDescription(
            "Welcome to the D&D 5e inspired battle system! Create a character, challenge others to duels, " +
            "and climb the leaderboards.\n\n" +
            "**Status:** " + (battleProperties.isEnabled() ? "✅ Enabled" : "❌ Disabled")
        );

        embed.addField(
            "📊 Character Stats",
            "Characters use D&D 5e ability scores:\n" +
            "• **STR** (Strength) - Physical power\n" +
            "• **DEX** (Dexterity) - Agility and reflexes\n" +
            "• **CON** (Constitution) - Endurance and HP\n" +
            "• **INT** (Intelligence) - Knowledge and reasoning\n" +
            "• **WIS** (Wisdom) - Awareness and insight\n" +
            "• **CHA** (Charisma) - Force of personality",
            false
        );

        embed.addField(
            "🎲 Point-Buy System",
            String.format(
                "You have **%d points** to allocate across your stats.\n" +
                "• Base score range: **%d-%d**\n" +
                "• Higher scores cost more points\n" +
                "• Point costs: 8=0, 9=1, 10=2, 11=3, 12=4, 13=5, 14=7, 15=9",
                battleProperties.getCharacter().getPointBuy().getTotalPoints(),
                battleProperties.getCharacter().getPointBuy().getMinScore(),
                battleProperties.getCharacter().getPointBuy().getMaxScore()
            ),
            false
        );

        embed.addField(
            "🛡️ Classes",
            "• **Warrior** - High HP, melee focused (STR primary)\n" +
            "• **Rogue** - Quick and deadly (DEX primary)\n" +
            "• **Mage** - Arcane spellcaster (INT primary)\n" +
            "• **Cleric** - Divine magic and support (WIS primary)",
            false
        );

        embed.addField(
            "⚡ Combat Mechanics",
            "• **Attack rolls:** d20 + proficiency + ability modifier vs AC\n" +
            "• **Critical hits:** Natural 20 doubles damage dice\n" +
            "• **Initiative:** DEX modifier determines turn order\n" +
            "• **HP:** Class base + (CON modifier × level)\n" +
            "• **AC:** 10 + DEX modifier (base)",
            false
        );

        embed.addField(
            "📜 Available Commands (Coming Soon)",
            "`/create-character` - Create your battle character\n" +
            "`/character` - View your character sheet\n" +
            "`/duel @user` - Challenge someone to a duel\n" +
            "`/battle-stats` - View your battle statistics\n" +
            "`/battle-leaderboard` - See top fighters",
            false
        );

        embed.addField(
            "📖 Learn More",
            "Full design documentation: `docs/battle-design.md`\n" +
            "Based on D&D 5e rules (simplified for Discord)",
            false
        );

        embed.setFooter("Battle System v0.1.0 | Phase 0");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        
        if (battleProperties.isDebug()) {
            logger.debug("battle-help displayed for user {} in guild {}", 
                event.getUser().getId(), 
                event.getGuild() != null ? event.getGuild().getId() : "DM");
        }
    }
}
