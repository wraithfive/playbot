package com.discordbot.web.service;

import com.discordbot.entity.QotdConfig;
import com.discordbot.entity.QotdQuestion;
import com.discordbot.repository.QotdConfigRepository;
import com.discordbot.repository.QotdQuestionRepository;
import com.discordbot.web.dto.qotd.QotdDtos;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("deprecation") // Uses legacy QotdConfig and QotdBanner for backward compatibility
public class QotdService {
    private static final Logger logger = LoggerFactory.getLogger(QotdService.class);
    private final QotdQuestionRepository questionRepo;
    private final QotdConfigRepository configRepo;
    private final JDA jda;
    private final WebSocketNotificationService wsNotificationService;
    private final com.discordbot.repository.QotdBannerRepository bannerRepo;
    private static final String DEFAULT_BANNER = "❓❓ Question of the Day ❓❓";

    public QotdService(QotdQuestionRepository questionRepo, QotdConfigRepository configRepo, JDA jda, 
                       WebSocketNotificationService wsNotificationService,
                       com.discordbot.repository.QotdBannerRepository bannerRepo) {
        this.questionRepo = questionRepo;
        this.configRepo = configRepo;
        this.jda = jda;
        this.wsNotificationService = wsNotificationService;
        this.bannerRepo = bannerRepo;
    }

    public String getBanner(String channelId) {
        return bannerRepo.findByChannelId(channelId)
                .map(com.discordbot.entity.QotdBanner::getBannerText)
                .orElse(DEFAULT_BANNER);
    }

    public Integer getBannerColor(String channelId) {
        return bannerRepo.findByChannelId(channelId)
                .map(com.discordbot.entity.QotdBanner::getEmbedColor)
                .orElse(null);
    }

    public void setBanner(String channelId, String bannerText) {
        com.discordbot.entity.QotdBanner banner = bannerRepo.findByChannelId(channelId)
                .orElse(new com.discordbot.entity.QotdBanner(channelId, DEFAULT_BANNER));
        banner.setBannerText(bannerText);
        bannerRepo.save(banner);
    }

    public void setBannerColor(String channelId, Integer color) {
        com.discordbot.entity.QotdBanner banner = bannerRepo.findByChannelId(channelId)
                .orElse(new com.discordbot.entity.QotdBanner(channelId, DEFAULT_BANNER));
        banner.setEmbedColor(color);
        bannerRepo.save(banner);
    }

        public String getBannerMentionTarget(String channelId) {
            return bannerRepo.findByChannelId(channelId)
                    .map(com.discordbot.entity.QotdBanner::getMentionTarget)
                    .orElse(null);
        }

        public void setBannerMentionTarget(String channelId, String mentionTarget) {
            com.discordbot.entity.QotdBanner banner = bannerRepo.findByChannelId(channelId)
                    .orElse(new com.discordbot.entity.QotdBanner(channelId, DEFAULT_BANNER));
            banner.setMentionTarget(mentionTarget);
            bannerRepo.save(banner);
        }

    public void resetBanner(String channelId) {
        com.discordbot.entity.QotdBanner banner = bannerRepo.findByChannelId(channelId)
                .orElse(new com.discordbot.entity.QotdBanner(channelId, DEFAULT_BANNER));
        banner.setBannerText(DEFAULT_BANNER);
        banner.setEmbedColor(0x9B59B6);
        bannerRepo.save(banner);
    }
    /**
     * Validates that the given channelId belongs to the specified guildId.
     * Throws IllegalArgumentException if validation fails.
     */
    public void validateChannelBelongsToGuild(String guildId, String channelId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Guild not found: " + guildId);
        }
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found or does not belong to this guild");
        }
    }

    public List<QotdDtos.QotdQuestionDto> listQuestions(String guildId, String channelId) {
        return questionRepo.findByGuildIdAndChannelIdOrderByDisplayOrderAsc(guildId, channelId).stream()
                .map(q -> new QotdDtos.QotdQuestionDto(q.getId(), q.getText(), q.getCreatedAt(), 
                    q.getAuthorUserId(), q.getAuthorUsername()))
                .toList();
    }

    public QotdDtos.QotdQuestionDto addQuestion(String guildId, String channelId, String text) {
        // Get the max display_order and add 1 for new question
        List<QotdQuestion> existing = questionRepo.findByGuildIdAndChannelIdOrderByDisplayOrderAsc(guildId, channelId);
        int nextOrder = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getDisplayOrder() + 1;
        
        QotdQuestion newQuestion = new QotdQuestion(guildId, channelId, text.trim());
        newQuestion.setDisplayOrder(nextOrder);
        
        QotdQuestion saved = questionRepo.save(newQuestion);
        wsNotificationService.notifyQotdQuestionsChanged(guildId, channelId, "added");
        return new QotdDtos.QotdQuestionDto(saved.getId(), saved.getText(), saved.getCreatedAt(),
            saved.getAuthorUserId(), saved.getAuthorUsername());
    }

    @Transactional
    public void deleteQuestion(String guildId, String channelId, Long id) {
        questionRepo.deleteByIdAndGuildIdAndChannelId(id, guildId, channelId);
        wsNotificationService.notifyQotdQuestionsChanged(guildId, channelId, "deleted");
    }

    @Transactional
    public void reorderQuestions(String guildId, String channelId, List<Long> orderedIds) {
        // Fetch all questions for this channel
        List<QotdQuestion> questions = questionRepo.findByGuildIdAndChannelIdOrderByDisplayOrderAsc(guildId, channelId);
        
        // Create a map of id -> question for quick lookup
        Map<Long, QotdQuestion> questionMap = questions.stream()
            .collect(java.util.stream.Collectors.toMap(QotdQuestion::getId, q -> q));
        
        // Update displayOrder based on the new order
        for (int i = 0; i < orderedIds.size(); i++) {
            Long questionId = orderedIds.get(i);
            QotdQuestion question = questionMap.get(questionId);
            if (question != null) {
                question.setDisplayOrder(i + 1);
                questionRepo.save(question);
            }
        }
        
        wsNotificationService.notifyQotdQuestionsChanged(guildId, channelId, "reordered");
    }

    public QotdDtos.UploadCsvResult uploadCsv(String guildId, String channelId, String csvContent) {
        String[] lines = csvContent.split("\r?\n");
        int success = 0, failure = 0; List<String> errors = new ArrayList<>();
        
        // Get current max displayOrder for this channel
        List<QotdQuestion> existing = questionRepo.findByGuildIdAndChannelIdOrderByDisplayOrderAsc(guildId, channelId);
        int nextOrder = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getDisplayOrder() + 1;
        
        // Check if first line is a header
        int startLine = 0;
        if (lines.length > 0) {
            String firstLine = lines[0].toLowerCase().trim();
            if (firstLine.startsWith("question") || firstLine.startsWith("text") || 
                firstLine.contains("author") || firstLine.contains("username") || firstLine.contains("userid")) {
                startLine = 1; // Skip header
            }
        }
        
        for (int i = startLine; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            try {
                // Parse CSV line (supports quoted fields with commas)
                String[] fields = parseCsvLine(line);
                String questionText = fields[0].trim();
                String authorUsername = fields.length > 1 ? fields[1].trim() : null;
                String authorUserId = fields.length > 2 ? fields[2].trim() : null;
                
                if (questionText.isEmpty()) {
                    throw new IllegalArgumentException("Question text cannot be empty");
                }
                
                // Create question with optional author info and next displayOrder
                QotdQuestion question = new QotdQuestion(guildId, channelId, questionText);
                question.setDisplayOrder(nextOrder++);
                if (authorUsername != null && !authorUsername.isEmpty()) {
                    question.setAuthorUsername(authorUsername);
                }
                if (authorUserId != null && !authorUserId.isEmpty()) {
                    question.setAuthorUserId(authorUserId);
                }
                questionRepo.save(question);
                success++;
            } catch (Exception e) {
                failure++;
                errors.add("Line " + (i + 1) + ": " + e.getMessage());
            }
        }
        
        if (success > 0) {
            wsNotificationService.notifyQotdQuestionsChanged(guildId, channelId, "uploaded");
        }
        
        return new QotdDtos.UploadCsvResult(success, failure, errors);
    }
    
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        
        return fields.toArray(new String[0]);
    }

    public QotdDtos.QotdConfigDto getConfig(String guildId, String channelId) {
        QotdConfig.QotdConfigId id = new QotdConfig.QotdConfigId(guildId, channelId);
        QotdConfig cfg = configRepo.findById(id).orElseGet(() -> {
            QotdConfig c = new QotdConfig(guildId, channelId);
            return configRepo.save(c);
        });
        List<String> nextRuns = computeNextRuns(cfg, 5);
        return new QotdDtos.QotdConfigDto(
                cfg.getChannelId(), cfg.isEnabled(), cfg.getTimezone(), cfg.getScheduleCron(), 
                cfg.isRandomize(), cfg.isAutoApprove(), cfg.getLastPostedAt(), cfg.getNextIndex(), nextRuns
        );
    }

    public List<QotdDtos.QotdConfigDto> listGuildConfigs(String guildId) {
        return configRepo.findByGuildId(guildId).stream()
                .map(cfg -> new QotdDtos.QotdConfigDto(
                        cfg.getChannelId(), cfg.isEnabled(), cfg.getTimezone(), cfg.getScheduleCron(), 
                        cfg.isRandomize(), cfg.isAutoApprove(), cfg.getLastPostedAt(), cfg.getNextIndex(), computeNextRuns(cfg, 5)
                ))
                .toList();
    }

    public QotdDtos.QotdConfigDto updateConfig(String guildId, String channelId, QotdDtos.UpdateConfigRequest req) {
        QotdConfig.QotdConfigId id = new QotdConfig.QotdConfigId(guildId, channelId);
        QotdConfig cfg = configRepo.findById(id).orElse(new QotdConfig(guildId, channelId));
        cfg.setEnabled(req.enabled());
        cfg.setTimezone(Optional.ofNullable(req.timezone()).orElse("UTC"));
        cfg.setRandomize(req.randomize());
        cfg.setAutoApprove(req.autoApprove());

        String cron = req.advancedCron();
        if (cron == null || cron.isBlank()) {
            cron = buildCronFromWeekly(req.daysOfWeek(), req.timeOfDay());
        }
        cfg.setScheduleCron(cron);
        configRepo.save(cfg);
        return getConfig(guildId, channelId);
    }

    private String buildCronFromWeekly(List<String> days, String timeOfDay) {
        // Default to daily at 09:00 UTC if not provided
        String tod = (timeOfDay == null || timeOfDay.isBlank()) ? "09:00" : timeOfDay;
        String[] parts = tod.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);
        List<String> d = (days == null || days.isEmpty()) ? Arrays.asList("MON","TUE","WED","THU","FRI") : days;
        // Quartz-style: sec min hour day-of-month month day-of-week
        return String.format("0 %d %d ? * %s", mm, hh, String.join(",", d));
    }

    private List<String> computeNextRuns(QotdConfig cfg, int n) {
        try {
            if (cfg.getScheduleCron() == null || cfg.getScheduleCron().isBlank()) return List.of();
            CronExpression expr = CronExpression.parse(cfg.getScheduleCron());
            ZoneId zone = ZoneId.of(cfg.getTimezone() == null ? "UTC" : cfg.getTimezone());
            ZonedDateTime now = ZonedDateTime.now(zone);
            List<String> out = new ArrayList<>();
            for (int i=0;i<n;i++) {
                now = expr.next(now);
                if (now == null) break;
                out.add(now.toString());
            }
            return out;
        } catch (Exception e) {
            logger.warn("Failed to compute next runs: {}", e.getMessage());
            return List.of();
        }
    }

    public List<QotdDtos.TextChannelInfo> listTextChannels(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return List.of();
        return guild.getTextChannels().stream()
                .map(ch -> new QotdDtos.TextChannelInfo(ch.getId(), "#" + ch.getName()))
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean postNextQuestion(String guildId, String channelId) {
        QotdConfig.QotdConfigId id = new QotdConfig.QotdConfigId(guildId, channelId);
        QotdConfig cfg = configRepo.findById(id).orElse(null);
        if (cfg == null || !cfg.isEnabled()) return false;

        List<QotdQuestion> questions = questionRepo.findByGuildIdAndChannelIdOrderByDisplayOrderAsc(guildId, channelId);
        if (questions.isEmpty()) return false;

        int index = cfg.getNextIndex();
        if (cfg.isRandomize()) {
            index = new Random().nextInt(questions.size());
        } else {
            if (index >= questions.size()) index = 0;
        }

        QotdQuestion next = questions.get(index);

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return false;

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel != null) {
            boolean sent = false;
            try {
                // Create embed with QOTD formatting
                net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
                String banner = getBanner(channelId);
                embed.setTitle(banner);
                String mention = getBannerMentionTarget(channelId);
                String description = next.getText();
                if (mention != null && !mention.isEmpty()) {
                    description = mention + " " + description;
                }
                embed.setDescription(description);
                Integer color = getBannerColor(channelId);
                embed.setColor(color != null ? color : 0x9B59B6); // default purple

                // Add author info if available
                if (next.getAuthorUsername() != null && !next.getAuthorUsername().isEmpty()) {
                    String authorText = "Author: " + next.getAuthorUsername();
                    if (next.getAuthorUserId() != null && !next.getAuthorUserId().isEmpty()) {
                        authorText += " -- Card: " + next.getAuthorUserId();
                    }
                    embed.setFooter(authorText);
                }

                channel.sendMessageEmbeds(embed.build()).complete();
                sent = true;
            } catch (Exception e) {
                logger.warn("Failed to send QOTD embed to channel {}: {}. Falling back to plain text.", channelId, e.getMessage());
                try {
                    StringBuilder sb = new StringBuilder();
                    String mention = getBannerMentionTarget(channelId);
                    if (mention != null && !mention.isEmpty()) {
                        sb.append(mention).append(" ");
                    }
                    String banner = getBanner(channelId);
                    sb.append(banner).append("\n\n");
                    sb.append(next.getText());
                    if (next.getAuthorUsername() != null && !next.getAuthorUsername().isEmpty()) {
                        sb.append("\n\nAuthor: ").append(next.getAuthorUsername());
                        if (next.getAuthorUserId() != null && !next.getAuthorUserId().isEmpty()) {
                            sb.append(" -- Card: ").append(next.getAuthorUserId());
                        }
                    }
                    channel.sendMessage(sb.toString()).complete();
                    sent = true;
                } catch (Exception e2) {
                    logger.warn("Failed to send QOTD plain text to channel {}: {}", channelId, e2.getMessage());
                    return false;
                }
            }
            if (!sent) return false;
        } else {
            return false;
        }

        // remove the used question from the queue and adjust pointer
        try {
            questionRepo.deleteByIdAndGuildIdAndChannelId(next.getId(), guildId, channelId);
        } catch (Exception e) {
            logger.warn("Failed to delete posted QOTD id={} for channel {}: {}", next.getId(), channelId, e.getMessage());
        }

        int newSize = Math.max(questions.size() - 1, 0);
        if (!cfg.isRandomize()) {
            if (newSize <= 0) {
                cfg.setNextIndex(0);
            } else {
                // If we removed the last element, wrap to 0; otherwise keep the same index (next item shifted into this spot)
                cfg.setNextIndex(index >= newSize ? 0 : index);
            }
        }
        cfg.setLastPostedAt(Instant.now());
        configRepo.save(cfg);
        return true;
    }
}
