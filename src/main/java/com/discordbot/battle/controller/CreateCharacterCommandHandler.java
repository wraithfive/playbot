package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Optional;

/**
 * Handles the /create-character slash command.
 * Displays an interactive embed with dropdowns and buttons for character creation.
 */
@Component
public class CreateCharacterCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateCharacterCommandHandler.class);

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository repository;

    public CreateCharacterCommandHandler(BattleProperties battleProperties, 
                                        PlayerCharacterRepository repository) {
        this.battleProperties = battleProperties;
        this.repository = repository;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "create-character".equals(commandName);
    }

    /**
     * Handles character creation: displays interactive embed with class/race selection menus
     * and point-buy rules explanation.
     */
    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            // Extract Discord context
            String userId = event.getUser().getId();
            String guildId = event.getGuild().getId();
            
            // Check if character already exists
            Optional<PlayerCharacter> existing = repository.findByUserIdAndGuildId(userId, guildId);
            if (existing.isPresent()) {
                logger.debug("Character creation attempted for userId={} in guildId={} but character already exists", userId, guildId);
                event.reply("❌ You already have a character in this server!")
                    .setEphemeral(true)
                    .queue();
                return;
            }
            
            // Show interactive character creation embed
            showCharacterCreationEmbed(event);

        } catch (Exception e) {
            logger.error("Error handling create-character command for user={}", event.getUser().getId(), e);
            event.reply("❌ An error occurred while starting character creation.")
                .setEphemeral(true)
                .queue();
        }
    }
    
    /**
     * Displays the interactive character creation embed with dropdowns and stat buttons.
     * Uses component IDs with format: char-create:{userId}:{componentType}:{data}
     */
    private void showCharacterCreationEmbed(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        int defaultStat = 10;
        
        // Build the embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚔️ Create Your Character");
        embed.setDescription(String.format(
            "Build your character using the point-buy system.\n\n" +
            "**Available Points:** %d\n" +
            "**Stat Range:** %d - %d\n\n" +
            "**Stat Descriptions:**\n" +
            "💪 **STR** (Strength) - Physical power and melee damage\n" +
            "🏃 **DEX** (Dexterity) - Agility, reflexes, and ranged accuracy\n" +
            "❤️ **CON** (Constitution) - Health, stamina, and resilience\n" +
            "🧠 **INT** (Intelligence) - Knowledge, magic power, and reasoning\n" +
            "🦉 **WIS** (Wisdom) - Perception, intuition, and willpower\n" +
            "✨ **CHA** (Charisma) - Personality, leadership, and persuasion\n\n" +
            "Select your class and race, then adjust stats using the +/- buttons.\n" +
            "Use `/battle-help` to see the point-buy cost table.",
            pointBuy.getTotalPoints(),
            pointBuy.getMinScore(),
            pointBuy.getMaxScore()
        ));
        embed.setColor(Color.BLUE);
        
        // Initial stats display (all at default)
        embed.addField("Current Stats", String.format(
            "💪 **STR:** %d | 🏃 **DEX:** %d | ❤️ **CON:** %d\n" +
            "🧠 **INT:** %d | 🦉 **WIS:** %d | ✨ **CHA:** %d",
            defaultStat, defaultStat, defaultStat, defaultStat, defaultStat, defaultStat
        ), false);
        
        embed.setFooter(String.format("Points Used: 0/%d", pointBuy.getTotalPoints()));
        
        // Row 1: Class selection
        StringSelectMenu classMenu = StringSelectMenu.create("char-create:" + userId + ":class")
            .setPlaceholder("Select a Class")
            .addOption("Warrior", "Warrior", "Front-line fighter with high strength")
            .addOption("Rogue", "Rogue", "Agile combatant with high dexterity")
            .addOption("Mage", "Mage", "Spellcaster with high intelligence")
            .addOption("Cleric", "Cleric", "Divine caster with high wisdom")
            .build();
        
        // Row 2: Race selection
        StringSelectMenu raceMenu = StringSelectMenu.create("char-create:" + userId + ":race")
            .setPlaceholder("Select a Race")
            .addOption("Human", "Human", "Versatile and adaptable")
            .addOption("Elf", "Elf", "Graceful and perceptive")
            .addOption("Dwarf", "Dwarf", "Sturdy and resilient")
            .addOption("Halfling", "Halfling", "Small and lucky")
            .build();
        
        // Rows 3-5: Stat adjustment buttons
        // We need to fit 6 stats with +/- buttons in 3 rows (max 5 buttons per row)
        // Layout: Each button shows "STAT +" or "STAT −" 
        
        event.replyEmbeds(embed.build())
            .addComponents(
                ActionRow.of(classMenu),
                ActionRow.of(raceMenu),
                ActionRow.of(
                    Button.danger("char-create:" + userId + ":stat-:str", "STR −").withDisabled(true),
                    Button.success("char-create:" + userId + ":stat+:str", "STR +"),
                    Button.danger("char-create:" + userId + ":stat-:dex", "DEX −").withDisabled(true),
                    Button.success("char-create:" + userId + ":stat+:dex", "DEX +")
                ),
                ActionRow.of(
                    Button.danger("char-create:" + userId + ":stat-:con", "CON −").withDisabled(true),
                    Button.success("char-create:" + userId + ":stat+:con", "CON +"),
                    Button.danger("char-create:" + userId + ":stat-:int", "INT −").withDisabled(true),
                    Button.success("char-create:" + userId + ":stat+:int", "INT +")
                ),
                ActionRow.of(
                    Button.danger("char-create:" + userId + ":stat-:wis", "WIS −").withDisabled(true),
                    Button.success("char-create:" + userId + ":stat+:wis", "WIS +"),
                    Button.danger("char-create:" + userId + ":stat-:cha", "CHA −").withDisabled(true),
                    Button.success("char-create:" + userId + ":stat+:cha", "CHA +"),
                    Button.primary("char-create:" + userId + ":submit", "✓ Submit").withDisabled(true)
                )
            )
            .setEphemeral(true)
            .queue();
        
        // User can reference /battle-help for detailed rules explanation
    }
}
