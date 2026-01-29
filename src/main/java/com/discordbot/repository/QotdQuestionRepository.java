package com.discordbot.repository;

import com.discordbot.entity.QotdQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QotdQuestionRepository extends JpaRepository<QotdQuestion, Long> {
    // NEW: Stream-based queries (preferred)
    List<QotdQuestion> findByStreamIdOrderByDisplayOrderAsc(Long streamId);
    long countByStreamId(Long streamId);
    void deleteByIdAndStreamId(Long id, Long streamId);
    long countByStreamIdIsNull();  // For migration verification
    List<QotdQuestion> findByStreamIdIsNull();  // For cleanup of legacy questions
}
