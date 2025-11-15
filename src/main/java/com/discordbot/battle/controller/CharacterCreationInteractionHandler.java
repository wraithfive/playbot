package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.CharacterValidationService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.concurrent.TimeUnit;

/**
 * Handles button and select menu interactions for character creation.
 * 
 * <p>This handler manages the interactive character creation flow using Discord UI components:
 * <ul>
 *   <li>String select menus for class and race selection</li>
 *   <li>Buttons for stat adjustment (increase/decrease)</li>
 *   <li>Submit and cancel buttons</li>
 * </ul>
 * 
 * <p><b>Component ID Format:</b> {@code char-create:{userId}:{action}[:{data}]}
 * <ul>
 *   <li>{@code char-create:123456:class} - Class selection menu</li>
 *   <li>{@code char-create:123456:race} - Race selection menu</li>
 *   <li>{@code char-create:123456:stat+:str} - Increase STR button</li>
 *   <li>{@code char-create:123456:stat-:dex} - Decrease DEX button</li>
 *   <li>{@code char-create:123456:submit} - Submit character button</li>
 *   <li>{@code char-create:123456:cancel} - Cancel creation button</li>
 * </ul>
 * 
 * <p><b>Point-Buy Budget Enforcement:</b>
 * <br>The handler enforces D&D 5e point-buy rules by:
 * <ol>
 *   <li>Tracking total points spent as stats are adjusted</li>
 *   <li>Dynamically disabling "+" buttons when increasing a stat would exceed the budget</li>
 *   <li>Testing stat changes in a copy of the state before applying (see {@link #canAffordIncrease})</li>
 *   <li>Validating the final character uses exactly the allowed points on submission</li>
 * </ol>
 * 
 * <p><b>State Management:</b>
 * <br>Character creation state is stored in-memory using Caffeine cache with:
 * <ul>
 *   <li>Key format: {@code userId:guildId}</li>
 *   <li>30-minute expiry after last interaction</li>
 *   <li>Maximum 1000 entries</li>
 * </ul>
 * 
 * <p>Only active in non-test profiles (excludes repository-test profile).
 * 
 * @see CharacterState
 * @see CreateCharacterCommandHandler
 */
@Component
@Profile("!repository-test")
public class CharacterCreationInteractionHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CharacterCreationInteractionHandler.class);

    // Component ID constants
    private static final String COMPONENT_PREFIX = "char-create:";
    private static final String SEPARATOR = ":";
    private static final String ACTION_CLASS = "class";
    private static final String ACTION_RACE = "race";
    private static final String ACTION_STAT_INC = "stat+";
    private static final String ACTION_STAT_DEC = "stat-";
    private static final String ACTION_SUBMIT = "submit";
    private static final String ACTION_CANCEL = "cancel";

    // Stat name constants
    private static final String STAT_STR = "str";
    private static final String STAT_DEX = "dex";
    private static final String STAT_CON = "con";
    private static final String STAT_INT = "int";
    private static final String STAT_WIS = "wis";
    private static final String STAT_CHA = "cha";

    // Cache configuration
    private static final int CACHE_EXPIRY_MINUTES = 30;
    private static final int CACHE_MAX_SIZE = 1000;

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository repository;
    private final CharacterValidationService validationService;

    // Temporary state storage for character creation in progress
    // Key: userId:guildId, Value: CharacterState
    // Automatically expires after 30 minutes of inactivity
    private final Cache<String, CharacterState> activeCreations;

    public CharacterCreationInteractionHandler(BattleProperties battleProperties,
                                             PlayerCharacterRepository repository,
                                             CharacterValidationService validationService) {
        this.battleProperties = battleProperties;
        this.repository = repository;
        this.validationService = validationService;
        this.activeCreations = Caffeine.newBuilder()
            .expireAfterAccess(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
            .maximumSize(CACHE_MAX_SIZE)
            .build();
    }

    /**
     * Inner class to track character creation state for a user.
     * Each user's state is stored temporarily in the cache and expires after 30 minutes of inactivity.
     * State includes class, race, and all six ability scores (STR, DEX, CON, INT, WIS, CHA).
     */
    private static class CharacterState {
        String characterClass;
        String race;
        int str;
        int dex;
        int con;
        int intel;
        int wis;
        int cha;
        long lastInteractionTime = System.currentTimeMillis();

        CharacterState(int defaultScore) {
            this.str = defaultScore;
            this.dex = defaultScore;
            this.con = defaultScore;
            this.intel = defaultScore;
            this.wis = defaultScore;
            this.cha = defaultScore;
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();

        // Check if this is a character creation select menu
        if (!componentId.startsWith(COMPONENT_PREFIX)) {
            return;
        }

        // Validate user ID
        String[] parts = componentId.split(SEPARATOR);
        if (parts.length < 3) {
            logger.warn("Invalid component ID format: {}", componentId);
            return;
        }

        String expectedUserId = parts[1];
        if (!expectedUserId.equals(event.getUser().getId())) {
            logger.warn("User {} attempted to interact with component for user {}",
                event.getUser().getId(), expectedUserId);
            event.reply("❌ This character creation is not for you!")
                .setEphemeral(true)
                .queue();
            return;
        }

        // Get the selected value
        String selectedValue = event.getValues().get(0);
        String selectionType = parts[2]; // "class" or "race"

        // Get or create state
        String stateKey = event.getUser().getId() + SEPARATOR + event.getGuild().getId();
        int defaultScore = battleProperties.getCharacter().getPointBuy().getDefaultScore();
        CharacterState state = activeCreations.get(stateKey, k -> new CharacterState(defaultScore));
        state.lastInteractionTime = System.currentTimeMillis();

        // Update state
        if (selectionType.equals(ACTION_CLASS)) {
            state.characterClass = selectedValue;
            logger.info("User {} in guild {} selected class: {}",
                event.getUser().getId(), event.getGuild().getId(), selectedValue);
        } else if (selectionType.equals(ACTION_RACE)) {
            state.race = selectedValue;
            logger.info("User {} in guild {} selected race: {}",
                event.getUser().getId(), event.getGuild().getId(), selectedValue);
        }

        // Save updated state
        activeCreations.put(stateKey, state);

        // Update the embed AND components to show current selections
        event.deferEdit().queue(hook -> {
            hook.editOriginalEmbeds(buildCharacterCreationEmbed(state, event.getUser().getId()))
                .setComponents(buildCharacterCreationComponents(state, event.getUser().getId()))
                .queue();
        });
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        // Check if this is a character creation button
        if (!componentId.startsWith(COMPONENT_PREFIX)) {
            return;
        }

        // Validate user ID
        String[] parts = componentId.split(SEPARATOR);
        if (parts.length < 3) {
            logger.warn("Invalid component ID format: {}", componentId);
            return;
        }

        String expectedUserId = parts[1];
        if (!expectedUserId.equals(event.getUser().getId())) {
            logger.warn("User {} attempted to interact with component for user {}",
                event.getUser().getId(), expectedUserId);
            event.reply("❌ This character creation is not for you!")
                .setEphemeral(true)
                .queue();
            return;
        }

        String action = parts[2];

        // Handle different button actions
        if (action.equals(ACTION_SUBMIT)) {
            handleSubmitButton(event);
        } else if (action.equals(ACTION_CANCEL)) {
            handleCancelButton(event);
        } else if (action.equals(ACTION_STAT_INC) || action.equals(ACTION_STAT_DEC)) {
            if (parts.length < 4) {
                logger.warn("Invalid stat button component ID: {}", componentId);
                return;
            }
            String stat = parts[3];
            handleStatButton(event, action, stat);
        }
    }

    /**
     * Handles the submit button - creates the character directly using Discord username.
     */
    private void handleSubmitButton(ButtonInteractionEvent event) {
        String stateKey = event.getUser().getId() + SEPARATOR + event.getGuild().getId();
        CharacterState state = activeCreations.getIfPresent(stateKey);

        if (state == null) {
            logger.warn("User {} attempted to submit but no character state found", event.getUser().getId());
            event.reply("❌ Character creation session expired. Please start over with `/create-character`.")
                .setEphemeral(true)
                .queue();
            return;
        }

        // Validate the character is ready to submit
        if (state.characterClass == null || state.race == null) {
            event.reply("❌ Please select both a class and race before submitting!")
                .setEphemeral(true)
                .queue();
            return;
        }

        // Check point-buy total
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        int pointsUsed = calculatePointsUsed(state, pointBuy);
        int maxPoints = pointBuy.getTotalPoints();
        int minScore = pointBuy.getMinScore();
        int maxScore = pointBuy.getMaxScore();

        if (pointsUsed != maxPoints) {
            event.reply(String.format("❌ You must use exactly %d points! (Currently using %d)",
                    maxPoints, pointsUsed))
                .setEphemeral(true)
                .queue();
            return;
        }

        // Validate individual stat ranges as a safety check
        int[] stats = {state.str, state.dex, state.con, state.intel, state.wis, state.cha};
        String[] statNames = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
        for (int i = 0; i < stats.length; i++) {
            if (stats[i] < minScore || stats[i] > maxScore) {
                logger.error("Stat {} out of range for userId={} in guildId={}: {} (range: {}-{})",
                    statNames[i], event.getUser().getId(), event.getGuild().getId(), 
                    stats[i], minScore, maxScore);
                event.reply(String.format("❌ Invalid character state detected! %s score (%d) is out of range. Please contact an administrator.",
                        statNames[i], stats[i]))
                    .setEphemeral(true)
                    .queue();
                return;
            }
        }

        // Use Discord username as character name
        String characterName = event.getUser().getName();
        String userId = event.getUser().getId();
        String guildId = event.getGuild().getId();

        // Create character entity
        PlayerCharacter character = new PlayerCharacter(
            userId, guildId,
            state.characterClass, state.race,
            state.str, state.dex, state.con,
            state.intel, state.wis, state.cha
        );

        // Validate
        if (!validationService.isValid(character)) {
            logger.error("Character validation failed for userId={} in guildId={}", userId, guildId);
            event.reply("❌ Character validation failed! Please contact an administrator.")
                .setEphemeral(true)
                .queue();
            return;
        }

        // Save to database
        try {
            PlayerCharacter saved = repository.save(character);
            logger.info("Character created successfully: id={}, userId={}, guildId={}, class={}, race={}, name={}",
                saved.getId(), userId, guildId, state.characterClass, state.race, characterName);

            // Clean up state
            activeCreations.invalidate(stateKey);

            // Send success embed
            sendSuccessEmbed(event, saved, characterName);

        } catch (DataIntegrityViolationException e) {
            logger.warn("Duplicate character creation attempt for userId={} in guildId={}", userId, guildId);
            event.reply("❌ You already have a character in this server!")
                .setEphemeral(true)
                .queue();
        } catch (Exception e) {
            logger.error("Error saving character for userId={} in guildId={}", userId, guildId, e);
            event.reply("❌ An error occurred while creating your character. Please try again.")
                .setEphemeral(true)
                .queue();
        }
    }

    /**
     * Handles the cancel button - removes state and shows cancellation message.
     */
    private void handleCancelButton(ButtonInteractionEvent event) {
        String stateKey = event.getUser().getId() + SEPARATOR + event.getGuild().getId();
        activeCreations.invalidate(stateKey);

        logger.info("User {} in guild {} cancelled character creation",
            event.getUser().getId(), event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("❌ Character Creation Cancelled");
        embed.setDescription("Your character creation has been cancelled.\n\nUse `/create-character` to start over.");
        embed.setColor(Color.RED);

        event.editMessageEmbeds(embed.build())
            .setComponents() // Remove all components
            .queue();
    }

    /**
     * Handles stat increase/decrease buttons.
     */
    private void handleStatButton(ButtonInteractionEvent event, String action, String stat) {
        String stateKey = event.getUser().getId() + SEPARATOR + event.getGuild().getId();
        int defaultScore = battleProperties.getCharacter().getPointBuy().getDefaultScore();
        CharacterState state = activeCreations.get(stateKey, k -> new CharacterState(defaultScore));
        state.lastInteractionTime = System.currentTimeMillis();

        boolean isIncrease = action.equals(ACTION_STAT_INC);
        int change = isIncrease ? 1 : -1;
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        int minScore = pointBuy.getMinScore();
        int maxScore = pointBuy.getMaxScore();

        // Calculate current stat value
        int currentValue = getStatValue(state, stat);
        int newValue = Math.max(minScore, Math.min(maxScore, currentValue + change));

        // If no change would occur, just acknowledge
        if (newValue == currentValue) {
            event.deferEdit().queue();
            return;
        }

        // Check point affordability
        CharacterState testState = copyState(state);
        setStatValue(testState, stat, newValue);
        int newPointsUsed = calculatePointsUsed(testState, pointBuy);
        int maxPoints = pointBuy.getTotalPoints();
        int currentPointsUsed = calculatePointsUsed(state, pointBuy);

        if (newPointsUsed > maxPoints) {
            int costOfIncrease = newPointsUsed - currentPointsUsed;
            int pointsAvailable = maxPoints - currentPointsUsed;
            logger.debug("User {} in guild {} cannot afford to increase {} from {} to {} (costs {} points, only {} available, {}/{} used)",
                event.getUser().getId(), event.getGuild().getId(), stat, currentValue, newValue, 
                costOfIncrease, pointsAvailable, currentPointsUsed, maxPoints);
            event.reply(String.format("❌ You don't have enough points! This increase costs **%d** points, but you only have **%d** available. (%d/%d used)",
                    costOfIncrease, pointsAvailable, currentPointsUsed, maxPoints))
                .setEphemeral(true)
                .queue();
            return;
        }

        // Apply the change
        setStatValue(state, stat, newValue);
        activeCreations.put(stateKey, state);

        logger.info("User {} in guild {} changed {} from {} to {} ({} points used)",
            event.getUser().getId(), event.getGuild().getId(), stat.toUpperCase(),
            currentValue, newValue, newPointsUsed);

        // Update the embed AND components to show new stat values
        event.deferEdit().queue(hook -> {
            hook.editOriginalEmbeds(buildCharacterCreationEmbed(state, event.getUser().getId()))
                .setComponents(buildCharacterCreationComponents(state, event.getUser().getId()))
                .queue();
        });
    }

    /**
     * Builds the character creation embed showing current selections and stats.
     */
    private net.dv8tion.jda.api.entities.MessageEmbed buildCharacterCreationEmbed(CharacterState state, String userId) {
        var pointBuy = battleProperties.getCharacter().getPointBuy();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚔️ Create Your Character");
        embed.setDescription(String.format(
            "Build your character using the point-buy system.\n\n" +
            "**Available Points:** %d\n" +
            "**Stat Range:** %d - %d\n\n" +
            "**Class:** %s\n" +
            "**Race:** %s\n\n" +
            "**Stat Descriptions:**\n" +
            "💪 **STR** (Strength) - Physical power and melee damage\n" +
            "🏃 **DEX** (Dexterity) - Agility, reflexes, and ranged accuracy\n" +
            "❤️ **CON** (Constitution) - Health, stamina, and resilience\n" +
            "🧠 **INT** (Intelligence) - Knowledge, magic power, and reasoning\n" +
            "🦉 **WIS** (Wisdom) - Perception, intuition, and willpower\n" +
            "✨ **CHA** (Charisma) - Personality, leadership, and persuasion\n\n" +
            "Use the +/- buttons to adjust your stats.",
            pointBuy.getTotalPoints(),
            pointBuy.getMinScore(),
            pointBuy.getMaxScore(),
            state.characterClass != null ? state.characterClass : "_Not selected_",
            state.race != null ? state.race : "_Not selected_"
        ));
        embed.setColor(Color.BLUE);

        // Current stats display
        String statsDisplay = String.format(
            "💪 **STR:** %d | 🏃 **DEX:** %d | ❤️ **CON:** %d\n" +
            "🧠 **INT:** %d | 🦉 **WIS:** %d | ✨ **CHA:** %d",
            state.str, state.dex, state.con, state.intel, state.wis, state.cha
        );
        embed.addField("Current Stats", statsDisplay, false);

        // Calculate points used
        int pointsUsed = calculatePointsUsed(state, pointBuy);
        int maxPoints = pointBuy.getTotalPoints();
        embed.setFooter(String.format("Points Used: %d/%d", pointsUsed, maxPoints));

        return embed.build();
    }

    /**
     * Builds the character creation components (select menus and buttons) with proper enabled/disabled states.
     */
    private java.util.List<net.dv8tion.jda.api.components.actionrow.ActionRow> buildCharacterCreationComponents(CharacterState state, String userId) {
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        int minScore = pointBuy.getMinScore();
        int maxScore = pointBuy.getMaxScore();
        int maxPoints = pointBuy.getTotalPoints();
        int currentPoints = calculatePointsUsed(state, pointBuy);

        // Row 1: Class selection
        var classMenu = net.dv8tion.jda.api.components.selections.StringSelectMenu.create(
                COMPONENT_PREFIX + userId + SEPARATOR + ACTION_CLASS)
            .setPlaceholder(state.characterClass != null ? state.characterClass : "Select a Class")
            .addOption("Warrior", "Warrior", "Front-line fighter with high strength")
            .addOption("Rogue", "Rogue", "Agile combatant with high dexterity")
            .addOption("Mage", "Mage", "Spellcaster with high intelligence")
            .addOption("Cleric", "Cleric", "Divine caster with high wisdom")
            .build();

        // Row 2: Race selection
        var raceMenu = net.dv8tion.jda.api.components.selections.StringSelectMenu.create(
                COMPONENT_PREFIX + userId + SEPARATOR + ACTION_RACE)
            .setPlaceholder(state.race != null ? state.race : "Select a Race")
            .addOption("Human", "Human", "Versatile and adaptable")
            .addOption("Elf", "Elf", "Graceful and perceptive")
            .addOption("Dwarf", "Dwarf", "Sturdy and resilient")
            .addOption("Halfling", "Halfling", "Small and lucky")
            .build();

        // Row 3: STR and DEX buttons
        var row3 = net.dv8tion.jda.api.components.actionrow.ActionRow.of(
            net.dv8tion.jda.api.components.buttons.Button.danger(
                buildStatButtonId(userId, ACTION_STAT_DEC, STAT_STR), "STR −")
                .withDisabled(state.str <= minScore),
            net.dv8tion.jda.api.components.buttons.Button.success(
                buildStatButtonId(userId, ACTION_STAT_INC, STAT_STR), "STR +")
                .withDisabled(state.str >= maxScore || !canAffordIncrease(state, STAT_STR, pointBuy)),
            net.dv8tion.jda.api.components.buttons.Button.danger(
                buildStatButtonId(userId, ACTION_STAT_DEC, STAT_DEX), "DEX −")
                .withDisabled(state.dex <= minScore),
            net.dv8tion.jda.api.components.buttons.Button.success(
                buildStatButtonId(userId, ACTION_STAT_INC, STAT_DEX), "DEX +")
                .withDisabled(state.dex >= maxScore || !canAffordIncrease(state, STAT_DEX, pointBuy))
        );

        // Row 4: CON and INT buttons
        var row4 = net.dv8tion.jda.api.components.actionrow.ActionRow.of(
            net.dv8tion.jda.api.components.buttons.Button.danger(
                buildStatButtonId(userId, ACTION_STAT_DEC, STAT_CON), "CON −")
                .withDisabled(state.con <= minScore),
            net.dv8tion.jda.api.components.buttons.Button.success(
                buildStatButtonId(userId, ACTION_STAT_INC, STAT_CON), "CON +")
                .withDisabled(state.con >= maxScore || !canAffordIncrease(state, STAT_CON, pointBuy)),
            net.dv8tion.jda.api.components.buttons.Button.danger(
                buildStatButtonId(userId, ACTION_STAT_DEC, STAT_INT), "INT −")
                .withDisabled(state.intel <= minScore),
            net.dv8tion.jda.api.components.buttons.Button.success(
                buildStatButtonId(userId, ACTION_STAT_INC, STAT_INT), "INT +")
                .withDisabled(state.intel >= maxScore || !canAffordIncrease(state, STAT_INT, pointBuy))
        );

        // Row 5: WIS, CHA, Submit, and Cancel buttons (combined to stay within 5 row limit)
        boolean canSubmit = state.characterClass != null && state.race != null && currentPoints == maxPoints;
        var row5 = net.dv8tion.jda.api.components.actionrow.ActionRow.of(
            net.dv8tion.jda.api.components.buttons.Button.danger(
                buildStatButtonId(userId, ACTION_STAT_DEC, STAT_WIS), "WIS −")
                .withDisabled(state.wis <= minScore),
            net.dv8tion.jda.api.components.buttons.Button.success(
                buildStatButtonId(userId, ACTION_STAT_INC, STAT_WIS), "WIS +")
                .withDisabled(state.wis >= maxScore || !canAffordIncrease(state, STAT_WIS, pointBuy)),
            net.dv8tion.jda.api.components.buttons.Button.danger(
                buildStatButtonId(userId, ACTION_STAT_DEC, STAT_CHA), "CHA −")
                .withDisabled(state.cha <= minScore),
            net.dv8tion.jda.api.components.buttons.Button.success(
                buildStatButtonId(userId, ACTION_STAT_INC, STAT_CHA), "CHA +")
                .withDisabled(state.cha >= maxScore || !canAffordIncrease(state, STAT_CHA, pointBuy)),
            net.dv8tion.jda.api.components.buttons.Button.primary(
                COMPONENT_PREFIX + userId + SEPARATOR + ACTION_SUBMIT, "✓ Submit")
                .withDisabled(!canSubmit)
        );

        return java.util.Arrays.asList(
            net.dv8tion.jda.api.components.actionrow.ActionRow.of(classMenu),
            net.dv8tion.jda.api.components.actionrow.ActionRow.of(raceMenu),
            row3,
            row4,
            row5
        );
    }

    /**
     * Builds a stat button component ID.
     */
    private String buildStatButtonId(String userId, String action, String stat) {
        return COMPONENT_PREFIX + userId + SEPARATOR + action + SEPARATOR + stat;
    }

    /**
     * Gets a stat value from the character state.
     */
    private int getStatValue(CharacterState state, String stat) {
        return switch (stat.toLowerCase()) {
            case STAT_STR -> state.str;
            case STAT_DEX -> state.dex;
            case STAT_CON -> state.con;
            case STAT_INT -> state.intel;
            case STAT_WIS -> state.wis;
            case STAT_CHA -> state.cha;
            default -> 10;
        };
    }

    /**
     * Sets a stat value in the character state.
     */
    private void setStatValue(CharacterState state, String stat, int value) {
        switch (stat.toLowerCase()) {
            case STAT_STR -> state.str = value;
            case STAT_DEX -> state.dex = value;
            case STAT_CON -> state.con = value;
            case STAT_INT -> state.intel = value;
            case STAT_WIS -> state.wis = value;
            case STAT_CHA -> state.cha = value;
        }
    }

    /**
     * Calculates points used based on current stat allocations.
     */
    private int calculatePointsUsed(CharacterState state, BattleProperties.CharacterConfig.PointBuyConfig pointBuy) {
        int[] stats = {state.str, state.dex, state.con, state.intel, state.wis, state.cha};
        int totalCost = 0;

        for (int stat : stats) {
            // Costs are indexed by (score - minScore)
            int index = stat - pointBuy.getMinScore();
            if (index >= 0 && index < pointBuy.getCosts().size()) {
                totalCost += pointBuy.getCosts().get(index);
            }
        }

        return totalCost;
    }

    /**
     * Creates a copy of the character state for testing changes.
     */
    private CharacterState copyState(CharacterState original) {
        int defaultScore = battleProperties.getCharacter().getPointBuy().getDefaultScore();
        CharacterState copy = new CharacterState(defaultScore);
        copy.characterClass = original.characterClass;
        copy.race = original.race;
        copy.str = original.str;
        copy.dex = original.dex;
        copy.con = original.con;
        copy.intel = original.intel;
        copy.wis = original.wis;
        copy.cha = original.cha;
        copy.lastInteractionTime = original.lastInteractionTime;
        return copy;
    }

    /**
     * Checks if we can afford to increase a stat.
     */
    private boolean canAffordIncrease(CharacterState state, String stat, BattleProperties.CharacterConfig.PointBuyConfig pointBuy) {
        int maxPoints = pointBuy.getTotalPoints();
        int maxScore = pointBuy.getMaxScore();

        // Create a test state with the stat increased
        CharacterState testState = copyState(state);
        int currentValue = getStatValue(testState, stat);
        if (currentValue >= maxScore) {
            return false; // Already at max
        }

        setStatValue(testState, stat, currentValue + 1);
        int newPoints = calculatePointsUsed(testState, pointBuy);

        return newPoints <= maxPoints;
    }

    /**
     * Sends a success embed after character creation.
     */
    private void sendSuccessEmbed(ButtonInteractionEvent event, PlayerCharacter character, String characterName) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚔️ Character Created!");
        embed.setColor(Color.GREEN);
        embed.setDescription(String.format("Welcome, brave %s **%s**!", character.getCharacterClass(), characterName));

        embed.addField("Class", character.getCharacterClass(), true);
        embed.addField("Race", character.getRace(), true);
        embed.addField("\u200B", "\u200B", true); // Spacer

        // Ability scores with modifiers
        embed.addField("💪 STR", formatStat(character.getStrength()), true);
        embed.addField("🏃 DEX", formatStat(character.getDexterity()), true);
        embed.addField("❤️ CON", formatStat(character.getConstitution()), true);
        embed.addField("🧠 INT", formatStat(character.getIntelligence()), true);
        embed.addField("🦉 WIS", formatStat(character.getWisdom()), true);
        embed.addField("✨ CHA", formatStat(character.getCharisma()), true);

        embed.addField("Next Steps",
            "Your character is ready for battle!",
            false);

        embed.setFooter("Battle System | Character ID: " + character.getId());
        embed.setTimestamp(character.getCreatedAt());

        event.reply(embed.build().getDescription())
            .addEmbeds(embed.build())
            .setEphemeral(false)
            .queue();
    }

    /**
     * Formats a stat with its modifier.
     */
    private String formatStat(int score) {
        int modifier = (score - 10) / 2;
        String modStr = modifier >= 0 ? "+" + modifier : String.valueOf(modifier);
        return String.format("%d (%s)", score, modStr);
    }
}
