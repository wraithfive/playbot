package com.discordbot.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "qotd_configs")
@IdClass(QotdConfig.QotdConfigId.class)
public class QotdConfig {

    @Id
    @Column(nullable = false)
    private String guildId;

    @Id
    @Column(nullable = false)
    private String channelId;

    @Column(nullable = false)
    private boolean enabled = false;

    // Stored cron expression for schedule (server will compute from UI selection)
    @Column(nullable = true, length = 255)
    private String scheduleCron;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private boolean randomize = false;

    @Column
    private Instant lastPostedAt;

    @Column(nullable = false)
    private int nextIndex = 0;

    public QotdConfig() {}

    public QotdConfig(String guildId, String channelId) { 
        this.guildId = guildId;
        this.channelId = channelId;
    }

    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public boolean isRandomize() { return randomize; }
    public void setRandomize(boolean randomize) { this.randomize = randomize; }

    public Instant getLastPostedAt() { return lastPostedAt; }
    public void setLastPostedAt(Instant lastPostedAt) { this.lastPostedAt = lastPostedAt; }

    public int getNextIndex() { return nextIndex; }
    public void setNextIndex(int nextIndex) { this.nextIndex = nextIndex; }

    // Composite key class
    public static class QotdConfigId implements Serializable {
        private String guildId;
        private String channelId;

        public QotdConfigId() {}

        public QotdConfigId(String guildId, String channelId) {
            this.guildId = guildId;
            this.channelId = channelId;
        }

        public String getGuildId() { return guildId; }
        public void setGuildId(String guildId) { this.guildId = guildId; }

        public String getChannelId() { return channelId; }
        public void setChannelId(String channelId) { this.channelId = channelId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QotdConfigId that = (QotdConfigId) o;
            return Objects.equals(guildId, that.guildId) && Objects.equals(channelId, that.channelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(guildId, channelId);
        }
    }
}
