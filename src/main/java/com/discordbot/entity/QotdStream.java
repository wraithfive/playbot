package com.discordbot.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing a QOTD stream.
 * Multiple streams can exist per channel, each with independent schedules,
 * question lists, banners, and mention targets.
 */
@Entity
@Table(name = "qotd_streams", indexes = {
    @Index(name = "idx_guild_channel", columnList = "guildId,channelId"),
    @Index(name = "idx_enabled_schedule", columnList = "enabled,lastPostedAt")
})
public class QotdStream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String guildId;

    @Column(nullable = false, length = 32)
    private String channelId;

    @Column(nullable = false, length = 64)
    private String streamName;

    // Scheduling
    @Column(length = 128)
    private String scheduleCron;

    @Column(length = 64)
    private String timezone = "UTC";

    @Column
    private Instant lastPostedAt;

    // Rotation
    @Column(nullable = false)
    private Boolean randomize = false;

    @Column(nullable = false)
    private Integer nextIndex = 0;

    // Banner/Appearance
    @Column(length = 160)
    private String bannerText = "❓❓ Question of the Day ❓❓";

    @Column
    private Integer embedColor;  // RGB as int (e.g., 0x9B59B6)

    @Column(length = 64)
    private String mentionTarget;

    // Flags
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Boolean autoApprove = false;

    // Metadata
    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getScheduleCron() {
        return scheduleCron;
    }

    public void setScheduleCron(String scheduleCron) {
        this.scheduleCron = scheduleCron;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Instant getLastPostedAt() {
        return lastPostedAt;
    }

    public void setLastPostedAt(Instant lastPostedAt) {
        this.lastPostedAt = lastPostedAt;
    }

    public Boolean getRandomize() {
        return randomize;
    }

    public void setRandomize(Boolean randomize) {
        this.randomize = randomize;
    }

    public Integer getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(Integer nextIndex) {
        this.nextIndex = nextIndex;
    }

    public String getBannerText() {
        return bannerText;
    }

    public void setBannerText(String bannerText) {
        this.bannerText = bannerText;
    }

    public Integer getEmbedColor() {
        return embedColor;
    }

    public void setEmbedColor(Integer embedColor) {
        this.embedColor = embedColor;
    }

    public String getMentionTarget() {
        return mentionTarget;
    }

    public void setMentionTarget(String mentionTarget) {
        this.mentionTarget = mentionTarget;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getAutoApprove() {
        return autoApprove;
    }

    public void setAutoApprove(Boolean autoApprove) {
        this.autoApprove = autoApprove;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
