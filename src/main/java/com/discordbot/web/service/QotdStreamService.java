package com.discordbot.web.service;

import com.discordbot.entity.QotdQuestion;
import com.discordbot.entity.QotdStream;
import com.discordbot.repository.QotdQuestionRepository;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.web.dto.qotd.QotdDtos.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing QOTD streams.
 * Each stream represents an independent QOTD configuration within a channel.
 */
@Service
public class QotdStreamService {
    private static final Logger log = LoggerFactory.getLogger(QotdStreamService.class);

    private final QotdStreamRepository streamRepository;
    private final QotdQuestionRepository questionRepository;
    private final JDA jda;
    private final WebSocketNotificationService wsNotificationService;

    private static final String DEFAULT_BANNER = "❓❓ Question of the Day ❓❓";
    private static final int DEFAULT_COLOR = 0x9B59B6; // Discord purple
    private static final int MAX_STREAMS_PER_CHANNEL = 5;

    public QotdStreamService(
            QotdStreamRepository streamRepository,
            QotdQuestionRepository questionRepository,
            JDA jda,
            WebSocketNotificationService wsNotificationService) {
        this.streamRepository = streamRepository;
        this.questionRepository = questionRepository;
        this.jda = jda;
        this.wsNotificationService = wsNotificationService;
    }

    // ==================== Stream Management ====================

    /**
     * List all streams for a channel.
     */
    public List<QotdStreamDto> listStreams(String guildId, String channelId) {
        validateChannelBelongsToGuild(guildId, channelId);
        return streamRepository.findByGuildIdAndChannelIdOrderByIdAsc(guildId, channelId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a single stream by ID.
     */
    public QotdStreamDto getStream(Long streamId) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        return toDto(stream);
    }

    /**
     * Create a new stream in a channel.
     * Enforces 5-stream limit per channel.
     */
    @Transactional
    public QotdStreamDto createStream(String guildId, String channelId, CreateStreamRequest request) {
        validateChannelBelongsToGuild(guildId, channelId);

        // Enforce 5-stream limit
        long count = streamRepository.countByGuildIdAndChannelId(guildId, channelId);
        if (count >= MAX_STREAMS_PER_CHANNEL) {
            throw new IllegalArgumentException("Maximum " + MAX_STREAMS_PER_CHANNEL + " streams per channel");
        }

        // Check for duplicate names in same channel (optional uniqueness)
        Optional<QotdStream> existing = streamRepository.findByGuildIdAndChannelIdAndStreamName(
                guildId, channelId, request.streamName());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Stream name already exists in this channel: " + request.streamName());
        }

        QotdStream stream = new QotdStream();
        stream.setGuildId(guildId);
        stream.setChannelId(channelId);
        stream.setStreamName(request.streamName());
        stream.setEnabled(request.enabled());
        stream.setTimezone(request.timezone() != null ? request.timezone() : "UTC");
        stream.setScheduleCron(buildCronExpression(request.advancedCron(), request.daysOfWeek(), request.timeOfDay()));
        stream.setRandomize(request.randomize());
        stream.setAutoApprove(request.autoApprove());
        stream.setBannerText(request.bannerText() != null ? request.bannerText() : DEFAULT_BANNER);
        stream.setEmbedColor(request.embedColor());
        stream.setMentionTarget(request.mentionTarget());
        stream.setCreatedAt(Instant.now());

        QotdStream saved = streamRepository.save(stream);
        wsNotificationService.notifyQotdStreamChanged(guildId, channelId, saved.getId(), "created");

        return toDto(saved);
    }

    /**
     * Update an existing stream.
     */
    @Transactional
    public QotdStreamDto updateStream(Long streamId, UpdateStreamRequest request) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));

        // Check for name conflicts if name changed
        if (!stream.getStreamName().equals(request.streamName())) {
            Optional<QotdStream> existing = streamRepository.findByGuildIdAndChannelIdAndStreamName(
                    stream.getGuildId(), stream.getChannelId(), request.streamName());
            if (existing.isPresent() && !existing.get().getId().equals(streamId)) {
                throw new IllegalArgumentException("Stream name already exists in this channel: " + request.streamName());
            }
        }

        stream.setStreamName(request.streamName());
        stream.setEnabled(request.enabled());
        stream.setTimezone(request.timezone() != null ? request.timezone() : "UTC");
        stream.setScheduleCron(buildCronExpression(request.advancedCron(), request.daysOfWeek(), request.timeOfDay()));
        stream.setRandomize(request.randomize());
        stream.setAutoApprove(request.autoApprove());
        stream.setUpdatedAt(Instant.now());

        QotdStream saved = streamRepository.save(stream);
        wsNotificationService.notifyQotdStreamChanged(stream.getGuildId(), stream.getChannelId(), streamId, "updated");

        return toDto(saved);
    }

    /**
     * Delete a stream and all its questions (cascade).
     */
    @Transactional
    public void deleteStream(Long streamId) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));

        String guildId = stream.getGuildId();
        String channelId = stream.getChannelId();

        streamRepository.deleteById(streamId);  // CASCADE will delete questions
        wsNotificationService.notifyQotdStreamChanged(guildId, channelId, streamId, "deleted");
    }

    // ==================== Question Management (Stream-Scoped) ====================

    /**
     * List questions for a stream.
     */
    public List<QotdQuestionDto> listQuestions(Long streamId) {
        return questionRepository.findByStreamIdOrderByDisplayOrderAsc(streamId)
                .stream()
                .map(q -> new QotdQuestionDto(q.getId(), q.getText(), q.getCreatedAt(),
                        q.getAuthorUserId(), q.getAuthorUsername()))
                .collect(Collectors.toList());
    }

    /**
     * Add a question to a stream.
     */
    @Transactional
    public QotdQuestionDto addQuestion(Long streamId, String text) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));

        List<QotdQuestion> existing = questionRepository.findByStreamIdOrderByDisplayOrderAsc(streamId);
        int nextOrder = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getDisplayOrder() + 1;

        QotdQuestion question = new QotdQuestion();
        question.setStreamId(streamId);
        question.setGuildId(stream.getGuildId());  // Keep for migration compatibility
        question.setChannelId(stream.getChannelId());
        question.setText(text.trim());
        question.setDisplayOrder(nextOrder);
        question.setCreatedAt(Instant.now());

        QotdQuestion saved = questionRepository.save(question);
        wsNotificationService.notifyQotdQuestionsChanged(stream.getGuildId(), stream.getChannelId(), "added");

        return new QotdQuestionDto(saved.getId(), saved.getText(), saved.getCreatedAt(),
                saved.getAuthorUserId(), saved.getAuthorUsername());
    }

    /**
     * Delete a question from a stream.
     */
    @Transactional
    public void deleteQuestion(Long streamId, Long questionId) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));

        questionRepository.deleteByIdAndStreamId(questionId, streamId);
        wsNotificationService.notifyQotdQuestionsChanged(stream.getGuildId(), stream.getChannelId(), "deleted");
    }

    /**
     * Reorder questions in a stream.
     */
    @Transactional
    public void reorderQuestions(Long streamId, List<Long> orderedIds) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));

        List<QotdQuestion> questions = questionRepository.findByStreamIdOrderByDisplayOrderAsc(streamId);
        Map<Long, QotdQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(QotdQuestion::getId, q -> q));

        for (int i = 0; i < orderedIds.size(); i++) {
            Long questionId = orderedIds.get(i);
            QotdQuestion question = questionMap.get(questionId);
            if (question != null) {
                question.setDisplayOrder(i + 1);
                questionRepository.save(question);
            }
        }

        wsNotificationService.notifyQotdQuestionsChanged(stream.getGuildId(), stream.getChannelId(), "reordered");
    }

    /**
     * Upload CSV of questions to a stream.
     */
    @Transactional
    public UploadCsvResult uploadCsv(Long streamId, MultipartFile file) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));

        List<QotdQuestion> existing = questionRepository.findByStreamIdOrderByDisplayOrderAsc(streamId);
        int nextOrder = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getDisplayOrder() + 1;

        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty() || lineNumber == 1) continue; // Skip header or empty lines

                try {
                    String[] parts = parseCsvLine(line);
                    if (parts.length == 0) continue;

                    String questionText = parts[0].trim();
                    if (questionText.isEmpty() || questionText.length() > 2000) {
                        failureCount++;
                        errors.add("Line " + lineNumber + ": Invalid question length");
                        continue;
                    }

                    QotdQuestion question = new QotdQuestion();
                    question.setStreamId(streamId);
                    question.setGuildId(stream.getGuildId());
                    question.setChannelId(stream.getChannelId());
                    question.setText(questionText);
                    question.setDisplayOrder(nextOrder++);
                    question.setCreatedAt(Instant.now());

                    // Optional author info (if CSV has 2+ columns)
                    if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                        question.setAuthorUsername(parts[1].trim());
                    }
                    if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                        question.setAuthorUserId(parts[2].trim());
                    }

                    questionRepository.save(question);
                    successCount++;

                } catch (Exception e) {
                    failureCount++;
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to upload CSV", e);
            throw new RuntimeException("Failed to upload CSV: " + e.getMessage());
        }

        if (successCount > 0) {
            wsNotificationService.notifyQotdQuestionsChanged(stream.getGuildId(), stream.getChannelId(), "uploaded");
        }

        return new UploadCsvResult(successCount, failureCount, errors);
    }

    // ==================== Banner Management (Stream-Scoped) ====================

    public String getBanner(Long streamId) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        return stream.getBannerText() != null ? stream.getBannerText() : DEFAULT_BANNER;
    }

    @Transactional
    public void setBanner(Long streamId, String bannerText) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        stream.setBannerText(bannerText);
        streamRepository.save(stream);
    }

    public Integer getBannerColor(Long streamId) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        return stream.getEmbedColor();
    }

    @Transactional
    public void setBannerColor(Long streamId, Integer color) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        stream.setEmbedColor(color);
        streamRepository.save(stream);
    }

    public String getBannerMention(Long streamId) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        return stream.getMentionTarget();
    }

    @Transactional
    public void setBannerMention(Long streamId, String mention) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        stream.setMentionTarget(mention);
        streamRepository.save(stream);
    }

    @Transactional
    public void resetBanner(Long streamId) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        stream.setBannerText(DEFAULT_BANNER);
        stream.setEmbedColor(DEFAULT_COLOR);
        streamRepository.save(stream);
    }

    // ==================== Posting Logic ====================

    /**
     * Post the next question for a stream to Discord.
     * Called by scheduler or manual post-now action.
     */
    @Transactional
    public void postNextQuestion(Long streamId) {
        QotdStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));

        List<QotdQuestion> questions = questionRepository.findByStreamIdOrderByDisplayOrderAsc(streamId);

        if (questions.isEmpty()) {
            log.warn("No questions available for stream {} ({})", streamId, stream.getStreamName());
            return;
        }

        // Select question based on randomize flag
        QotdQuestion selectedQuestion;
        int nextIdx;

        if (stream.getRandomize()) {
            int randomIdx = new Random().nextInt(questions.size());
            selectedQuestion = questions.get(randomIdx);
            nextIdx = stream.getNextIndex(); // Don't increment in random mode
        } else {
            int currentIdx = stream.getNextIndex();
            if (currentIdx >= questions.size()) {
                currentIdx = 0; // Wrap around
            }
            selectedQuestion = questions.get(currentIdx);
            nextIdx = (currentIdx + 1) % questions.size();
        }

        // Post to Discord
        try {
            Guild guild = jda.getGuildById(stream.getGuildId());
            if (guild == null) {
                log.error("Guild not found: {}", stream.getGuildId());
                return;
            }

            TextChannel channel = guild.getTextChannelById(stream.getChannelId());
            if (channel == null) {
                log.error("Channel not found: {}", stream.getChannelId());
                return;
            }

            // Build embed
            String bannerText = stream.getBannerText() != null ? stream.getBannerText() : DEFAULT_BANNER;
            Integer embedColor = stream.getEmbedColor() != null ? stream.getEmbedColor() : DEFAULT_COLOR;
            String mention = stream.getMentionTarget() != null ? stream.getMentionTarget() : "";

            String description = mention.isEmpty() ? selectedQuestion.getText() : mention + " " + selectedQuestion.getText();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(bannerText)
                    .setDescription(description)
                    .setColor(embedColor);

            if (selectedQuestion.getAuthorUsername() != null) {
                embed.setFooter("Suggested by " + selectedQuestion.getAuthorUsername());
            }

            // Try embed first, fallback to plain text
            try {
                channel.sendMessageEmbeds(embed.build()).queue();
            } catch (Exception e) {
                log.warn("Failed to send embed, falling back to plain text", e);
                String plainMessage = bannerText + "\n\n" + description;
                channel.sendMessage(plainMessage).queue();
            }

            // Update stream state
            stream.setLastPostedAt(Instant.now());
            stream.setNextIndex(nextIdx);
            streamRepository.save(stream);

            // Delete the question after posting
            questionRepository.deleteById(selectedQuestion.getId());

            log.info("Posted QOTD for stream {} ({}) in channel {}", streamId, stream.getStreamName(), channel.getName());

        } catch (Exception e) {
            log.error("Failed to post QOTD for stream " + streamId, e);
        }
    }

    // ==================== Helper Methods ====================

    private void validateChannelBelongsToGuild(String guildId, String channelId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Guild not found: " + guildId);
        }
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found or does not belong to this guild");
        }
    }

    private QotdStreamDto toDto(QotdStream stream) {
        List<String> nextRuns = computeNextRuns(stream, 5);
        return new QotdStreamDto(
                stream.getId(),
                stream.getGuildId(),
                stream.getChannelId(),
                stream.getStreamName(),
                stream.getEnabled(),
                stream.getTimezone(),
                stream.getScheduleCron(),
                stream.getRandomize(),
                stream.getAutoApprove(),
                stream.getLastPostedAt(),
                stream.getNextIndex(),
                stream.getBannerText(),
                stream.getEmbedColor(),
                stream.getMentionTarget(),
                stream.getCreatedAt(),
                nextRuns
        );
    }

    private List<String> computeNextRuns(QotdStream stream, int count) {
        if (stream.getScheduleCron() == null || stream.getScheduleCron().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            CronExpression expr = CronExpression.parse(stream.getScheduleCron());
            ZoneId zone = ZoneId.of(stream.getTimezone() != null ? stream.getTimezone() : "UTC");
            ZonedDateTime current = ZonedDateTime.now(zone);

            List<String> runs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                current = expr.next(current);
                if (current == null) break;
                runs.add(current.toInstant().toString());
            }
            return runs;
        } catch (Exception e) {
            log.warn("Failed to compute next runs for stream {}", stream.getId(), e);
            return Collections.emptyList();
        }
    }

    private String buildCronExpression(String advancedCron, List<String> daysOfWeek, String timeOfDay) {
        if (advancedCron != null && !advancedCron.isEmpty()) {
            return advancedCron;
        }

        if (daysOfWeek == null || daysOfWeek.isEmpty() || timeOfDay == null) {
            return null;
        }

        String[] timeParts = timeOfDay.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        String days = String.join(",", daysOfWeek);
        return String.format("0 %d %d ? * %s", minute, hour, days);
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }
}
