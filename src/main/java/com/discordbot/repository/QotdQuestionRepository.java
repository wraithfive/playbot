package com.discordbot.repository;

import com.discordbot.entity.QotdQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QotdQuestionRepository extends JpaRepository<QotdQuestion, Long> {
    List<QotdQuestion> findByGuildIdAndChannelIdOrderByDisplayOrderAsc(String guildId, String channelId);
    long countByGuildIdAndChannelId(String guildId, String channelId);
    void deleteByIdAndGuildIdAndChannelId(Long id, String guildId, String channelId);
}
