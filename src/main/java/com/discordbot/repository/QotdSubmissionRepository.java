package com.discordbot.repository;

import com.discordbot.entity.QotdSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QotdSubmissionRepository extends JpaRepository<QotdSubmission, Long> {
    List<QotdSubmission> findByGuildIdAndStatusOrderByCreatedAtAsc(String guildId, QotdSubmission.Status status);
    long countByGuildIdAndUserIdAndStatus(String guildId, String userId, QotdSubmission.Status status);
}
