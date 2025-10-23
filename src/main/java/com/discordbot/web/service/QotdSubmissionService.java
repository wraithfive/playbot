package com.discordbot.web.service;

import com.discordbot.entity.QotdQuestion;
import com.discordbot.entity.QotdSubmission;
import com.discordbot.repository.QotdQuestionRepository;
import com.discordbot.repository.QotdSubmissionRepository;
import com.discordbot.web.dto.qotd.QotdDtos;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QotdSubmissionService {
    private final QotdSubmissionRepository submissionRepo;
    private final QotdQuestionRepository questionRepo;

    // Simple per guild:user rate limiter: 3 submissions per hour
    private final Cache<String, Bucket> buckets;

    public QotdSubmissionService(QotdSubmissionRepository submissionRepo, QotdQuestionRepository questionRepo) {
        this.submissionRepo = submissionRepo;
        this.questionRepo = questionRepo;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterAccess(Duration.ofHours(2))
                .build();
    }

    public QotdDtos.QotdSubmissionDto submit(String guildId, String userId, String username, String text) {
        String trimmed = Optional.ofNullable(text).orElse("").trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Question cannot be empty");
        if (trimmed.length() > 300) throw new IllegalArgumentException("Question is too long (max 300 chars)");

        String key = guildId + ":" + userId;
        Bucket bucket = buckets.get(key, k -> buildBucket());
        if (!bucket.tryConsume(1)) {
            throw new IllegalStateException("Rate limit exceeded. Try again later.");
        }

        QotdSubmission sub = new QotdSubmission(guildId, userId, username, trimmed);
        sub = submissionRepo.save(sub);
        return toDto(sub);
    }

    public List<QotdDtos.QotdSubmissionDto> listPending(String guildId) {
        return submissionRepo.findByGuildIdAndStatusOrderByCreatedAtAsc(guildId, QotdSubmission.Status.PENDING)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public QotdDtos.QotdSubmissionDto approve(String guildId, String channelId, Long id, String approverId, String approverUsername) {
        QotdSubmission sub = submissionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Submission not found"));
        if (!sub.getGuildId().equals(guildId)) throw new IllegalArgumentException("Invalid guild");
        if (sub.getStatus() != QotdSubmission.Status.PENDING) throw new IllegalStateException("Already processed");

        // Create question for the specified channel with author info
        questionRepo.save(new QotdQuestion(guildId, channelId, sub.getText(), sub.getUserId(), sub.getUsername()));

        // Mark approved
        sub.setStatus(QotdSubmission.Status.APPROVED);
        sub.setApprovedByUserId(approverId);
        sub.setApprovedByUsername(approverUsername);
        sub.setApprovedAt(Instant.now());
        submissionRepo.save(sub);
        return toDto(sub);
    }

    public QotdDtos.BulkActionResult approveBulk(String guildId, String channelId, List<Long> ids, String approverId, String approverUsername) {
        int success = 0; int failure = 0; List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                approve(guildId, channelId, id, approverId, approverUsername);
                success++;
            } catch (Exception e) {
                failure++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }
        return new QotdDtos.BulkActionResult(success, failure, errors);
    }

    public QotdDtos.QotdSubmissionDto reject(String guildId, Long id, String approverId, String approverUsername) {
        QotdSubmission sub = submissionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Submission not found"));
        if (!sub.getGuildId().equals(guildId)) throw new IllegalArgumentException("Invalid guild");
        if (sub.getStatus() != QotdSubmission.Status.PENDING) throw new IllegalStateException("Already processed");

        sub.setStatus(QotdSubmission.Status.REJECTED);
        sub.setApprovedByUserId(approverId);
        sub.setApprovedByUsername(approverUsername);
        sub.setApprovedAt(Instant.now());
        submissionRepo.save(sub);
        return toDto(sub);
    }

    public QotdDtos.BulkActionResult rejectBulk(String guildId, List<Long> ids, String approverId, String approverUsername) {
        int success = 0; int failure = 0; List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                reject(guildId, id, approverId, approverUsername);
                success++;
            } catch (Exception e) {
                failure++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }
        return new QotdDtos.BulkActionResult(success, failure, errors);
    }

    private QotdDtos.QotdSubmissionDto toDto(QotdSubmission s) {
        return new QotdDtos.QotdSubmissionDto(
                s.getId(), s.getText(), s.getUserId(), s.getUsername(),
                QotdDtos.SubmissionStatus.valueOf(s.getStatus().name()), s.getCreatedAt()
        );
    }

    private Bucket buildBucket() {
        Bandwidth limit = Bandwidth.builder().capacity(3).refillIntervally(3, Duration.ofHours(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }
}
