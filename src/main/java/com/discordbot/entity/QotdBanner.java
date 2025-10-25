package com.discordbot.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "qotd_banner")
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

    public QotdBanner() {}

    public QotdBanner(String channelId, String bannerText) {
        this.channelId = channelId;
        this.bannerText = bannerText;
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
