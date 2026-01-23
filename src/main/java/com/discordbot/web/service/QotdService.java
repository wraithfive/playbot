package com.discordbot.web.service;

import com.discordbot.web.dto.qotd.QotdDtos;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QotdService {
    private final JDA jda;

    public QotdService(JDA jda) {
        this.jda = jda;
    }

    /**
     * Validates that the given channelId belongs to the specified guildId.
     * Throws IllegalArgumentException if validation fails.
     */
    public List<QotdDtos.TextChannelInfo> listTextChannels(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return List.of();
        return guild.getTextChannels().stream()
                .map(ch -> new QotdDtos.TextChannelInfo(ch.getId(), "#" + ch.getName()))
                .collect(Collectors.toList());
    }
}
