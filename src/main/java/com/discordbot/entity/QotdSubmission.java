package com.discordbot.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "qotd_submissions")
public class QotdSubmission {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String guildId;

    @Column(nullable = false, length = 32)
    private String userId;

    @Column(nullable = false, length = 128)
    private String username;

    @Column(nullable = false, length = 500)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 32)
    private String approvedByUserId;

    @Column(length = 128)
    private String approvedByUsername;

    private Instant approvedAt;

    public QotdSubmission() {}

    public QotdSubmission(String guildId, String userId, String username, String text) {
        this.guildId = guildId;
        this.userId = userId;
        this.username = username;
        this.text = text;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getApprovedByUserId() { return approvedByUserId; }
    public void setApprovedByUserId(String approvedByUserId) { this.approvedByUserId = approvedByUserId; }
    public String getApprovedByUsername() { return approvedByUsername; }
    public void setApprovedByUsername(String approvedByUsername) { this.approvedByUsername = approvedByUsername; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
}
