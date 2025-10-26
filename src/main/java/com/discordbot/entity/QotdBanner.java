package com.discordbot.entity;

import jakarta.persistence.*;

/**
 * DEPRECATED: This entity is kept for backward compatibility with legacy QOTD system.
 * Banner data has been migrated to qotd_streams table.
 * This table was renamed to qotd_banner_deprecated in migration 008.
 * TODO: Remove this entity after all channels are migrated to streams and verified.
 */
@Deprecated
@Entity
@Table(name = "qotd_banner_deprecated")
public class QotdBanner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false, length = 64)
    private String channelId;

    @Column(name = "banner_text", nullable = false, length = 160)
    private String bannerText;

    @Column(name = "embed_color")
    private Integer embedColor; // RGB integer (e.g., 0x5865F2)

        @Column(name = "mention_target", length = 64)
        private String mentionTarget;

    public QotdBanner() {}

    public QotdBanner(String channelId, String bannerText) {
        this.channelId = channelId;
        this.bannerText = bannerText;
    }

    public String getMentionTarget() {
        return mentionTarget;
    }

    public void setMentionTarget(String mentionTarget) {
        this.mentionTarget = mentionTarget;
    }

    public Long getId() {
        return id;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
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
}
