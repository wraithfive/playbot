package com.discordbot.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "qotd_questions")
public class QotdQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String guildId;

    @Column(nullable = false)
    private String channelId;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(length = 32)
    private String authorUserId;

    @Column(length = 128)
    private String authorUsername;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public QotdQuestion() {}

    public QotdQuestion(String guildId, String channelId, String text) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public QotdQuestion(String guildId, String channelId, String text, String authorUserId, String authorUsername) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.text = text;
        this.authorUserId = authorUserId;
        this.authorUsername = authorUsername;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(String authorUserId) { this.authorUserId = authorUserId; }

    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
