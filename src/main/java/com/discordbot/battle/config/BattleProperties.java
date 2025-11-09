package com.discordbot.battle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for the Battle System.
 * Binds all battle.* properties from application.properties.
 */
@Configuration
@ConfigurationProperties(prefix = "battle")
public class BattleProperties {

    private boolean enabled = false;
    private boolean debug = false;

    private CharacterConfig character = new CharacterConfig();
    private ClassConfig classConfig = new ClassConfig();
    private CombatConfig combat = new CombatConfig();
    private ChallengeConfig challenge = new ChallengeConfig();
    private ProgressionConfig progression = new ProgressionConfig();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public CharacterConfig getCharacter() {
        return character;
    }

    public void setCharacter(CharacterConfig character) {
        this.character = character;
    }

    public ClassConfig getClassConfig() {
        return classConfig;
    }

    public void setClassConfig(ClassConfig classConfig) {
        this.classConfig = classConfig;
    }

    public CombatConfig getCombat() {
        return combat;
    }

    public void setCombat(CombatConfig combat) {
        this.combat = combat;
    }

    public ChallengeConfig getChallenge() {
        return challenge;
    }

    public void setChallenge(ChallengeConfig challenge) {
        this.challenge = challenge;
    }

    public ProgressionConfig getProgression() {
        return progression;
    }

    public void setProgression(ProgressionConfig progression) {
        this.progression = progression;
    }

    // Nested Configuration Classes
    public static class CharacterConfig {
        private PointBuyConfig pointBuy = new PointBuyConfig();

        public PointBuyConfig getPointBuy() {
            return pointBuy;
        }

        public void setPointBuy(PointBuyConfig pointBuy) {
            this.pointBuy = pointBuy;
        }

        public static class PointBuyConfig {
            // Default values - overridden by battle.character.pointBuy.* properties from application.properties
            private int totalPoints = 27;
            private int minScore = 8;
            private int maxScore = 15;
            private int defaultScore = 10;
            // Point costs indexed by (score - minScore): 8=0pts, 9=1pt, 10=2pts, 11=3pts, 12=4pts, 13=5pts, 14=7pts, 15=9pts
            private List<Integer> costs = List.of(0, 1, 2, 3, 4, 5, 7, 9);

            public int getTotalPoints() {
                return totalPoints;
            }

            public void setTotalPoints(int totalPoints) {
                this.totalPoints = totalPoints;
            }

            public int getMinScore() {
                return minScore;
            }

            public void setMinScore(int minScore) {
                this.minScore = minScore;
            }

            public int getMaxScore() {
                return maxScore;
            }

            public void setMaxScore(int maxScore) {
                this.maxScore = maxScore;
            }

            public int getDefaultScore() {
                return defaultScore;
            }

            public void setDefaultScore(int defaultScore) {
                this.defaultScore = defaultScore;
            }

            public List<Integer> getCosts() {
                return costs;
            }

            public void setCosts(List<Integer> costs) {
                this.costs = costs;
            }

            public int getCostForScore(int score) {
                if (score < minScore || score > maxScore) {
                    return -1;
                }
                return costs.get(score - minScore);
            }
        }
    }

    public static class ClassConfig {
    // Default values - overridden by battle.classConfig.*.baseHp properties from application.properties
    private ClassStats warrior = new ClassStats(12);
    private ClassStats rogue = new ClassStats(8);
    private ClassStats mage = new ClassStats(6);
    private ClassStats cleric = new ClassStats(8);

        public ClassStats getWarrior() {
            return warrior;
        }

        public void setWarrior(ClassStats warrior) {
            this.warrior = warrior;
        }

        public ClassStats getRogue() {
            return rogue;
        }

        public void setRogue(ClassStats rogue) {
            this.rogue = rogue;
        }

        public ClassStats getMage() {
            return mage;
        }

        public void setMage(ClassStats mage) {
            this.mage = mage;
        }

        public ClassStats getCleric() {
            return cleric;
        }

        public void setCleric(ClassStats cleric) {
            this.cleric = cleric;
        }

        public static class ClassStats {
            private int baseHp;

            public ClassStats() {}

            public ClassStats(int baseHp) {
                this.baseHp = baseHp;
            }

            public int getBaseHp() {
                return baseHp;
            }

            public void setBaseHp(int baseHp) {
                this.baseHp = baseHp;
            }
        }
    }

    public static class CombatConfig {
        private CritConfig crit = new CritConfig();
        private TurnConfig turn = new TurnConfig();
    // Default values - overridden by battle.combat.* properties from application.properties
    private int cooldownSeconds = 60;
    private int maxConcurrentPerGuild = 50;

        public CritConfig getCrit() {
            return crit;
        }

        public void setCrit(CritConfig crit) {
            this.crit = crit;
        }

        public TurnConfig getTurn() {
            return turn;
        }

        public void setTurn(TurnConfig turn) {
            this.turn = turn;
        }

        public int getCooldownSeconds() {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(int cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }

        public int getMaxConcurrentPerGuild() {
            return maxConcurrentPerGuild;
        }

        public void setMaxConcurrentPerGuild(int maxConcurrentPerGuild) {
            this.maxConcurrentPerGuild = maxConcurrentPerGuild;
        }

        public static class CritConfig {
            // Default values - overridden by battle.combat.crit.* properties from application.properties
            private int threshold = 20;
            private double multiplier = 2.0;

            public int getThreshold() {
                return threshold;
            }

            public void setThreshold(int threshold) {
                this.threshold = threshold;
            }

            public double getMultiplier() {
                return multiplier;
            }

            public void setMultiplier(double multiplier) {
                this.multiplier = multiplier;
            }
        }

        public static class TurnConfig {
            // Default value - overridden by battle.combat.turn.timeoutSeconds property from application.properties
            private int timeoutSeconds = 45;

            public int getTimeoutSeconds() {
                return timeoutSeconds;
            }

            public void setTimeoutSeconds(int timeoutSeconds) {
                this.timeoutSeconds = timeoutSeconds;
            }
        }
    }

    public static class ChallengeConfig {
    // Default value - overridden by battle.challenge.expireSeconds property from application.properties
    private int expireSeconds = 120;

        public int getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(int expireSeconds) {
            this.expireSeconds = expireSeconds;
        }
    }

    public static class ProgressionConfig {
    // Default values - overridden by battle.progression.* properties from application.properties
    // Proficiency bonuses by level (D&D 5e standard): levels 1-4=+2, 5-8=+3, 9-12=+4, 13-16=+5, 17-20=+6
    private List<Integer> proficiencyByLevel = List.of(2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6);
    private EloConfig elo = new EloConfig();
    private XpConfig xp = new XpConfig();

        public List<Integer> getProficiencyByLevel() {
            return proficiencyByLevel;
        }

        public void setProficiencyByLevel(List<Integer> proficiencyByLevel) {
            this.proficiencyByLevel = proficiencyByLevel;
        }

        public EloConfig getElo() {
            return elo;
        }

        public void setElo(EloConfig elo) {
            this.elo = elo;
        }

        public XpConfig getXp() {
            return xp;
        }

        public void setXp(XpConfig xp) {
            this.xp = xp;
        }

        public int getProficiencyBonus(int level) {
            if (level < 1 || level > proficiencyByLevel.size()) {
                return 2; // Default
            }
            return proficiencyByLevel.get(level - 1);
        }

        public static class EloConfig {
            // Default value - overridden by battle.progression.elo.k property from application.properties
            private int k = 32;

            public int getK() {
                return k;
            }

            public void setK(int k) {
                this.k = k;
            }
        }

        public static class XpConfig {
            // Default values - overridden by battle.progression.xp.levelCurve property from application.properties
            // XP thresholds for levels 1-10 (D&D 5e standard progression)
            private List<Integer> levelCurve = List.of(0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000);

            public List<Integer> getLevelCurve() {
                return levelCurve;
            }

            public void setLevelCurve(List<Integer> levelCurve) {
                this.levelCurve = levelCurve;
            }

            public int getLevelForXp(int xp) {
                for (int i = levelCurve.size() - 1; i >= 0; i--) {
                    if (xp >= levelCurve.get(i)) {
                        return i + 1;
                    }
                }
                return 1;
            }
        }
    }
}
