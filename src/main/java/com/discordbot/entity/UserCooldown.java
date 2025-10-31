package com.discordbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing the cooldown state for a user's roll command in a specific guild.
 * Tracks when each user last rolled to enforce the daily cooldown limit.
 */
@Entity
@Table(name = "user_cooldowns", indexes = {
    @Index(name = "idx_user_guild", columnList = "userId,guildId", unique = true)
})
public class UserCooldown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Discord user ID (snowflake)
     */
    @Column(nullable = false, length = 20)
    private String userId;

    /**
     * Discord guild/server ID (snowflake)
     */
    @Column(nullable = false, length = 20)
    private String guildId;

    /**
     * Timestamp of the user's last roll in this guild
     */
    @Column(nullable = false)
    private LocalDateTime lastRollTime;

    /**
     * Username for debugging/logging purposes (not used for logic)
     */
    @Column(length = 100)
    private String username;

    /**
     * Whether the user has used /d20 this roll cycle
     */
    @Column(nullable = false)
    private boolean d20Used = false;

    /**
     * Whether the user has a guaranteed Epic+ buff for their next roll (from nat 20)
     */
    @Column(nullable = false)
    private boolean guaranteedEpicPlus = false;

    /**
     * Default constructor required by JPA
     */
    public UserCooldown() {
    }

    /**
     * Constructor for creating a new cooldown record
     */
    public UserCooldown(String userId, String guildId, LocalDateTime lastRollTime, String username) {
        this.userId = userId;
        this.guildId = guildId;
        this.lastRollTime = lastRollTime;
        this.username = username;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public LocalDateTime getLastRollTime() {
        return lastRollTime;
    }

    public void setLastRollTime(LocalDateTime lastRollTime) {
        this.lastRollTime = lastRollTime;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isD20Used() {
        return d20Used;
    }

    public void setD20Used(boolean d20Used) {
        this.d20Used = d20Used;
    }

    public boolean isGuaranteedEpicPlus() {
        return guaranteedEpicPlus;
    }

    public void setGuaranteedEpicPlus(boolean guaranteedEpicPlus) {
        this.guaranteedEpicPlus = guaranteedEpicPlus;
    }

    @Override
    public String toString() {
        return "UserCooldown{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", guildId='" + guildId + '\'' +
                ", lastRollTime=" + lastRollTime +
                ", username='" + username + '\'' +
                ", d20Used=" + d20Used +
                ", guaranteedEpicPlus=" + guaranteedEpicPlus +
                '}';
    }
}
