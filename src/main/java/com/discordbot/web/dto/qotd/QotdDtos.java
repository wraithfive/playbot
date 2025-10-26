package com.discordbot.web.dto.qotd;

import java.time.Instant;
import java.util.List;

public class QotdDtos {
    public record QotdQuestionDto(Long id, String text, Instant createdAt, String authorUserId, String authorUsername) {}

    public record QotdConfigDto(
            String channelId,
            boolean enabled,
            String timezone,
            String scheduleCron,
            boolean randomize,
            boolean autoApprove,
            Instant lastPostedAt,
            int nextIndex,
            List<String> nextRuns // ISO strings for next 5 runs
    ) {}

    public record UpsertQuestionRequest(String text) {}

    public record ReorderQuestionsRequest(List<Long> orderedIds) {}

    public record UploadCsvResult(int successCount, int failureCount, List<String> errors) {}

    public record UpdateConfigRequest(
            boolean enabled,
            String timezone,
            // Either supply advancedCron OR provide daysOfWeek + timeOfDay
            String advancedCron,
            List<String> daysOfWeek, // MON..SUN
            String timeOfDay, // HH:mm
            boolean randomize,
            boolean autoApprove
    ) {}

    public record TextChannelInfo(String id, String name) {}

    // Submissions
    public enum SubmissionStatus { PENDING, APPROVED, REJECTED }

    public record QotdSubmissionDto(Long id, String text, String userId, String username, SubmissionStatus status, Instant createdAt) {}

    public record BulkIdsRequest(List<Long> ids) {}

    public record BulkActionResult(int successCount, int failureCount, List<String> errors) {}

    // Stream DTOs
    public record QotdStreamDto(
            Long id,
            String guildId,
            String channelId,
            String streamName,
            boolean enabled,
            String timezone,
            String scheduleCron,
            boolean randomize,
            boolean autoApprove,
            Instant lastPostedAt,
            int nextIndex,
            String bannerText,
            Integer embedColor,
            String mentionTarget,
            Instant createdAt,
            List<String> nextRuns  // ISO strings for next 5 runs
    ) {}

    public record CreateStreamRequest(
            String streamName,
            boolean enabled,
            String timezone,
            String advancedCron,  // Optional cron expression
            List<String> daysOfWeek,  // Optional: MON, TUE, etc.
            String timeOfDay,  // Optional: HH:mm
            boolean randomize,
            boolean autoApprove,
            String bannerText,
            Integer embedColor,
            String mentionTarget
    ) {}

    public record UpdateStreamRequest(
            String streamName,
            boolean enabled,
            String timezone,
            String advancedCron,
            List<String> daysOfWeek,
            String timeOfDay,
            boolean randomize,
            boolean autoApprove
    ) {}
}
