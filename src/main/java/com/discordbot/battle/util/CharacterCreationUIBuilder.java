package com.discordbot.battle.util;

import com.discordbot.battle.config.BattleProperties;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.springframework.stereotype.Component;

import java.awt.Color;

/**
 * Spring component to build character creation UI components.
 * Provides a consistent interface for creating characters from both
 * /create-character command and battle acceptance flows.
 */
@Component
public class CharacterCreationUIBuilder {
    
    private static final int DEFAULT_STAT = 10;
    
    private final BattleProperties battleProperties;
    
    public CharacterCreationUIBuilder(BattleProperties battleProperties) {
        this.battleProperties = battleProperties;
    }
    
    /**
     * Builds the complete character creation embed with menus and buttons.
     * @param userId The Discord user ID to bind components to
     * @param includeStatDescriptions Whether to include detailed stat descriptions (for command flow)
     * @return MessageCreateData containing the embed and action rows
     */
    public MessageCreateData buildCharacterCreationMessage(String userId, boolean includeStatDescriptions) {
        MessageCreateBuilder builder = new MessageCreateBuilder();
        builder.setEmbeds(buildCharacterCreationEmbed(includeStatDescriptions));
        builder.addComponents(
            buildClassMenu(userId),
            buildRaceMenu(userId),
            buildStatButtonRow1(userId),
            buildStatButtonRow2(userId),
            buildStatButtonRow3(userId)
        );
        
        return builder.build();
    }
    
    /**
     * Builds just the character creation embed without interactive components.
     * Useful for displaying character creation instructions or information without action rows.
     * @param includeStatDescriptions Whether to include detailed stat descriptions
     * @return MessageEmbed containing the character creation information
     */
    public MessageEmbed buildCharacterCreationEmbed(boolean includeStatDescriptions) {
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚔️ Create Your Character");
        
        // Build description with optional stat descriptions
        StringBuilder description = new StringBuilder();
        description.append(String.format(
            "Build your character using the point-buy system.\n\n" +
            "**Available Points:** %d\n" +
            "**Stat Range:** %d - %d\n\n",
            pointBuy.getTotalPoints(),
            pointBuy.getMinScore(),
            pointBuy.getMaxScore()
        ));
        
        if (includeStatDescriptions) {
            description.append(
                "**Stat Descriptions:**\n" +
                "💪 **STR** (Strength) - Physical power and melee damage\n" +
                "🏃 **DEX** (Dexterity) - Agility, reflexes, and ranged accuracy\n" +
                "❤️ **CON** (Constitution) - Health, stamina, and resilience\n" +
                "🧠 **INT** (Intelligence) - Knowledge, magic power, and reasoning\n" +
                "🦉 **WIS** (Wisdom) - Perception, intuition, and willpower\n" +
                "✨ **CHA** (Charisma) - Personality, leadership, and persuasion\n\n"
            );
        }
        
        description.append(
            "Select your class and race, then adjust stats using the +/- buttons.\n" +
            "Use `/battle-help` to see the point-buy cost table."
        );
        
        embed.setDescription(description.toString());
        embed.setColor(Color.BLUE);
        
        // Initial stats display (all at default)
        embed.addField("Current Stats", String.format(
            "💪 **STR:** %d | 🏃 **DEX:** %d | ❤️ **CON:** %d\n" +
            "🧠 **INT:** %d | 🦉 **WIS:** %d | ✨ **CHA:** %d",
            DEFAULT_STAT, DEFAULT_STAT, DEFAULT_STAT, DEFAULT_STAT, DEFAULT_STAT, DEFAULT_STAT
        ), false);
        
        embed.setFooter(String.format("Points Used: 0/%d", pointBuy.getTotalPoints()));
        
        return embed.build();
    }
    
    /**
     * Builds the class selection dropdown.
     */
    private ActionRow buildClassMenu(String userId) {
        StringSelectMenu classMenu = StringSelectMenu.create("char-create:" + userId + ":class")
            .setPlaceholder("Select a Class")
            .addOption("Warrior", "Warrior", "Front-line fighter with high strength")
            .addOption("Rogue", "Rogue", "Agile combatant with high dexterity")
            .addOption("Mage", "Mage", "Spellcaster with high intelligence")
            .addOption("Cleric", "Cleric", "Divine caster with high wisdom")
            .build();
        return ActionRow.of(classMenu);
    }
    
    /**
     * Builds the race selection dropdown.
     */
    private ActionRow buildRaceMenu(String userId) {
        StringSelectMenu raceMenu = StringSelectMenu.create("char-create:" + userId + ":race")
            .setPlaceholder("Select a Race")
            .addOption("Human", "Human", "Versatile and adaptable")
            .addOption("Elf", "Elf", "Graceful and perceptive")
            .addOption("Dwarf", "Dwarf", "Sturdy and resilient")
            .addOption("Halfling", "Halfling", "Small and lucky")
            .build();
        return ActionRow.of(raceMenu);
    }
    
    /**
     * Builds the first row of stat adjustment buttons (STR, DEX).
     */
    private ActionRow buildStatButtonRow1(String userId) {
        return ActionRow.of(
            Button.danger("char-create:" + userId + ":stat-:str", "STR −").withDisabled(true),
            Button.success("char-create:" + userId + ":stat+:str", "STR +"),
            Button.danger("char-create:" + userId + ":stat-:dex", "DEX −").withDisabled(true),
            Button.success("char-create:" + userId + ":stat+:dex", "DEX +")
        );
    }
    
    /**
     * Builds the second row of stat adjustment buttons (CON, INT).
     */
    private ActionRow buildStatButtonRow2(String userId) {
        return ActionRow.of(
            Button.danger("char-create:" + userId + ":stat-:con", "CON −").withDisabled(true),
            Button.success("char-create:" + userId + ":stat+:con", "CON +"),
            Button.danger("char-create:" + userId + ":stat-:int", "INT −").withDisabled(true),
            Button.success("char-create:" + userId + ":stat+:int", "INT +")
        );
    }
    
    /**
     * Builds the third row of stat adjustment buttons (WIS, CHA) and submit button.
     */
    private ActionRow buildStatButtonRow3(String userId) {
        return ActionRow.of(
            Button.danger("char-create:" + userId + ":stat-:wis", "WIS −").withDisabled(true),
            Button.success("char-create:" + userId + ":stat+:wis", "WIS +"),
            Button.danger("char-create:" + userId + ":stat-:cha", "CHA −").withDisabled(true),
            Button.success("char-create:" + userId + ":stat+:cha", "CHA +"),
            Button.primary("char-create:" + userId + ":submit", "✓ Submit").withDisabled(true)
        );
    }
}
