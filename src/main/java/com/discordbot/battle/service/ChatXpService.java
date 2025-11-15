package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;

/**
 * Service for awarding XP from Discord chat messages.
 * Provides the primary progression path for characters through server participation.
 */
@Service
public class ChatXpService {

    private static final Logger logger = LoggerFactory.getLogger(ChatXpService.class);

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository characterRepository;
    private final Random random = new Random();

    public ChatXpService(BattleProperties battleProperties,
                        PlayerCharacterRepository characterRepository) {
        this.battleProperties = battleProperties;
        this.characterRepository = characterRepository;
    }

    /**
     * Award XP to a user for sending a chat message.
     * Handles cooldown checking, auto character creation, and level-up detection.
     *
     * @param userId Discord user ID
     * @param guildId Discord guild ID
     * @return XpAwardResult containing award status and level-up information
     */
    @Transactional
    public XpAwardResult awardChatXp(String userId, String guildId) {
        // Check if chat XP is enabled
        if (!battleProperties.isEnabled() || !battleProperties.getProgression().getChatXp().isEnabled()) {
            return XpAwardResult.disabled();
        }

        BattleProperties.ProgressionConfig.ChatXpConfig config = battleProperties.getProgression().getChatXp();

        // Get or create character
        Optional<PlayerCharacter> characterOpt = characterRepository.findByUserIdAndGuildId(userId, guildId);
        PlayerCharacter character;

        if (characterOpt.isEmpty()) {
            // Auto-create character if enabled
            if (!config.isAutoCreateCharacter()) {
                return XpAwardResult.noCharacter();
            }

            // Create default character (Warrior with balanced stats)
            character = createDefaultCharacter(userId, guildId);
            characterRepository.save(character);
            logger.info("Auto-created character for user {} in guild {} (chat XP)", userId, guildId);
        } else {
            character = characterOpt.get();
        }

        // Check cooldown
        if (isOnCooldown(character, config.getCooldownSeconds())) {
            return XpAwardResult.onCooldown();
        }

        // Calculate XP award
        int baseXp = config.getBaseXp();
        int bonusXp = random.nextInt(config.getBonusXpMax() + 1); // 0 to bonusXpMax inclusive
        int totalXp = baseXp + bonusXp;

        // Award XP and check for level up
        int oldLevel = character.getLevel();
        long[] levelThresholds = battleProperties.getProgression().getXp().getLevelCurve().stream()
            .mapToLong(Integer::longValue).toArray();
        boolean leveledUp = character.addXp(totalXp, levelThresholds);
        int newLevel = character.getLevel();

        // Update last chat XP timestamp
        character.setLastChatXpAt(LocalDateTime.now());
        characterRepository.save(character);

        if (leveledUp) {
            logger.info("User {} leveled up from chat XP! {} → {} (total XP: {})",
                userId, oldLevel, newLevel, character.getXp());
        } else {
            logger.debug("Awarded {} chat XP to user {} (total XP: {})", totalXp, userId, character.getXp());
        }

        return XpAwardResult.awarded(totalXp, oldLevel, newLevel, leveledUp);
    }

    /**
     * Check if user is on cooldown for chat XP
     */
    private boolean isOnCooldown(PlayerCharacter character, int cooldownSeconds) {
        LocalDateTime lastAward = character.getLastChatXpAt();
        if (lastAward == null) {
            return false; // Never awarded, not on cooldown
        }

        long secondsSinceLastAward = ChronoUnit.SECONDS.between(lastAward, LocalDateTime.now());
        return secondsSinceLastAward < cooldownSeconds;
    }

    /**
     * Create a default character for auto-creation feature.
     * Creates a balanced Warrior with 12 in all stats.
     */
    private PlayerCharacter createDefaultCharacter(String userId, String guildId) {
        // Balanced stats (12 across the board = +1 modifier)
        return new PlayerCharacter(
            userId,
            guildId,
            "Warrior",  // Default class
            "Human",    // Default race
            12, 12, 12, 12, 12, 12  // Balanced ability scores
        );
    }

    /**
     * Result of a chat XP award attempt
     */
    public record XpAwardResult(
        XpAwardStatus status,
        int xpAwarded,
        int oldLevel,
        int newLevel,
        boolean leveledUp
    ) {
        public static XpAwardResult disabled() {
            return new XpAwardResult(XpAwardStatus.DISABLED, 0, 0, 0, false);
        }

        public static XpAwardResult noCharacter() {
            return new XpAwardResult(XpAwardStatus.NO_CHARACTER, 0, 0, 0, false);
        }

        public static XpAwardResult onCooldown() {
            return new XpAwardResult(XpAwardStatus.ON_COOLDOWN, 0, 0, 0, false);
        }

        public static XpAwardResult awarded(int xpAwarded, int oldLevel, int newLevel, boolean leveledUp) {
            return new XpAwardResult(XpAwardStatus.AWARDED, xpAwarded, oldLevel, newLevel, leveledUp);
        }
    }

    /**
     * Status of XP award attempt
     */
    public enum XpAwardStatus {
        AWARDED,        // XP was successfully awarded
        DISABLED,       // Chat XP system is disabled
        NO_CHARACTER,   // User has no character and auto-create is disabled
        ON_COOLDOWN     // User is on cooldown
    }
}
