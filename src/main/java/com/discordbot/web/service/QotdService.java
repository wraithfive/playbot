package com.discordbot.web.service;

import com.discordbot.entity.QotdQuestion;
import com.discordbot.repository.QotdQuestionRepository;
import com.discordbot.web.dto.qotd.QotdDtos;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QotdService {
    private final QotdQuestionRepository questionRepo;
    private final JDA jda;
    private final WebSocketNotificationService wsNotificationService;

    public QotdService(QotdQuestionRepository questionRepo, JDA jda, 
                       WebSocketNotificationService wsNotificationService) {
        this.questionRepo = questionRepo;
        this.jda = jda;
        this.wsNotificationService = wsNotificationService;
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

    public List<QotdDtos.TextChannelInfo> listTextChannels(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return List.of();
        return guild.getTextChannels().stream()
                .map(ch -> new QotdDtos.TextChannelInfo(ch.getId(), "#" + ch.getName()))
                .collect(Collectors.toList());
    }
}
