package com.discordbot;

import com.discordbot.entity.QotdQuestion;
import com.discordbot.entity.QotdSubmission;
import com.discordbot.repository.QotdConfigRepository;
import com.discordbot.repository.QotdQuestionRepository;
import com.discordbot.repository.QotdSubmissionRepository;
import com.discordbot.web.dto.qotd.QotdDtos;
import com.discordbot.web.service.QotdSubmissionService;
import com.discordbot.web.service.WebSocketNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for QotdSubmissionService - focusing on rate limiting and submission workflow
 */
class QotdSubmissionServiceTest {

    private QotdSubmissionService service;
    private QotdSubmissionRepository submissionRepo;
    private QotdQuestionRepository questionRepo;
    private WebSocketNotificationService wsNotificationService;
    private QotdConfigRepository configRepo;

    @BeforeEach
    void setUp() {
        submissionRepo = mock(QotdSubmissionRepository.class);
        questionRepo = mock(QotdQuestionRepository.class);
        wsNotificationService = mock(WebSocketNotificationService.class);
        configRepo = mock(QotdConfigRepository.class);
        service = new QotdSubmissionService(submissionRepo, questionRepo, wsNotificationService, configRepo);
    }

    @Test
    @DisplayName("submit should reject empty text")
    void testSubmit_EmptyText() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.submit("guild123", "user123", "testuser", "");
        }, "Should reject empty question text");

        assertThrows(IllegalArgumentException.class, () -> {
            service.submit("guild123", "user123", "testuser", "   ");
        }, "Should reject whitespace-only question text");

        assertThrows(IllegalArgumentException.class, () -> {
            service.submit("guild123", "user123", "testuser", null);
        }, "Should reject null question text");
    }

    @Test
    @DisplayName("submit should reject text longer than 300 characters")
    void testSubmit_TextTooLong() {
        String longText = "a".repeat(301);

        assertThrows(IllegalArgumentException.class, () -> {
            service.submit("guild123", "user123", "testuser", longText);
        }, "Should reject text longer than 300 characters");
    }

    @Test
    @DisplayName("submit should accept valid text and create submission")
    void testSubmit_ValidText() {
        String questionText = "What is your favorite programming language?";

        when(submissionRepo.save(any(QotdSubmission.class))).thenAnswer(invocation -> {
            QotdSubmission saved = invocation.getArgument(0);
            return createSubmissionWithId(1L, saved.getGuildId(), saved.getUserId(),
                                         saved.getUsername(), saved.getText());
        });

        QotdDtos.QotdSubmissionDto result = service.submit("guild123", "user123", "testuser", questionText);

        assertNotNull(result);
        assertEquals(questionText, result.text());
        assertEquals("user123", result.userId());
        assertEquals("testuser", result.username());

        verify(submissionRepo, times(1)).save(any(QotdSubmission.class));
    }

    @Test
    @DisplayName("submit should enforce rate limit of 3 submissions per hour")
    void testSubmit_RateLimit() {
        String guildId = "guild123";
        String userId = "user123";
        String username = "testuser";

        when(submissionRepo.save(any(QotdSubmission.class))).thenAnswer(invocation -> {
            QotdSubmission saved = invocation.getArgument(0);
            return createSubmissionWithId(1L, saved.getGuildId(), saved.getUserId(),
                                         saved.getUsername(), saved.getText());
        });

        // First 3 submissions should succeed
        assertDoesNotThrow(() -> service.submit(guildId, userId, username, "Question 1"));
        assertDoesNotThrow(() -> service.submit(guildId, userId, username, "Question 2"));
        assertDoesNotThrow(() -> service.submit(guildId, userId, username, "Question 3"));

        // 4th submission should be rate limited
        assertThrows(IllegalStateException.class, () -> {
            service.submit(guildId, userId, username, "Question 4");
        }, "Should enforce rate limit after 3 submissions");
    }

    @Test
    @DisplayName("submit should have separate rate limits for different users")
    void testSubmit_SeparateRateLimitsPerUser() {
        String guildId = "guild123";

        when(submissionRepo.save(any(QotdSubmission.class))).thenAnswer(invocation -> {
            QotdSubmission saved = invocation.getArgument(0);
            return createSubmissionWithId(1L, saved.getGuildId(), saved.getUserId(),
                                         saved.getUsername(), saved.getText());
        });

        // User 1 uses all 3 submissions
        assertDoesNotThrow(() -> service.submit(guildId, "user1", "user1name", "Q1"));
        assertDoesNotThrow(() -> service.submit(guildId, "user1", "user1name", "Q2"));
        assertDoesNotThrow(() -> service.submit(guildId, "user1", "user1name", "Q3"));

        // User 1 is rate limited
        assertThrows(IllegalStateException.class, () -> {
            service.submit(guildId, "user1", "user1name", "Q4");
        });

        // User 2 should still be able to submit
        assertDoesNotThrow(() -> service.submit(guildId, "user2", "user2name", "Q1"));
    }

    @Test
    @DisplayName("approve should create question and update submission status")
    void testApprove_Success() {
        Long submissionId = 1L;
        String guildId = "guild123";
        String channelId = "channel456";

        QotdSubmission submission = createSubmissionWithId(submissionId, guildId, "user123", "testuser", "Great question?");

        when(submissionRepo.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepo.save(any(QotdSubmission.class))).thenReturn(submission);
        when(questionRepo.save(any(QotdQuestion.class))).thenReturn(new QotdQuestion());

        QotdDtos.QotdSubmissionDto result = service.approve(guildId, channelId, submissionId, "admin123", "AdminUser");

        assertNotNull(result);
        verify(questionRepo, times(1)).save(any(QotdQuestion.class));
        verify(submissionRepo, times(1)).save(argThat(sub ->
            sub.getStatus() == QotdSubmission.Status.APPROVED &&
            sub.getApprovedByUserId().equals("admin123") &&
            sub.getApprovedAt() != null
        ));
    }

    @Test
    @DisplayName("approve should reject submission from wrong guild")
    void testApprove_WrongGuild() {
        Long submissionId = 1L;
        QotdSubmission submission = createSubmissionWithId(submissionId, "guild123", "user123", "testuser", "Question");

        when(submissionRepo.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThrows(IllegalArgumentException.class, () -> {
            service.approve("guild999", "channel456", submissionId, "admin123", "AdminUser");
        }, "Should reject approval from wrong guild");
    }

    @Test
    @DisplayName("approve should reject already processed submission")
    void testApprove_AlreadyProcessed() {
        Long submissionId = 1L;
        QotdSubmission submission = createSubmissionWithId(submissionId, "guild123", "user123", "testuser", "Question");
        submission.setStatus(QotdSubmission.Status.APPROVED); // Already approved

        when(submissionRepo.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThrows(IllegalStateException.class, () -> {
            service.approve("guild123", "channel456", submissionId, "admin123", "AdminUser");
        }, "Should reject already processed submission");
    }

    @Test
    @DisplayName("approve should throw when submission not found")
    void testApprove_NotFound() {
        when(submissionRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> {
            service.approve("guild123", "channel456", 99L, "admin123", "AdminUser");
        }, "Should throw NoSuchElementException when submission is missing");
    }

    @Test
    @DisplayName("reject should update submission status")
    void testReject_Success() {
        Long submissionId = 1L;
        String guildId = "guild123";

        QotdSubmission submission = createSubmissionWithId(submissionId, guildId, "user123", "testuser", "Bad question");

        when(submissionRepo.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepo.save(any(QotdSubmission.class))).thenReturn(submission);

        QotdDtos.QotdSubmissionDto result = service.reject(guildId, submissionId, "admin123", "AdminUser");

        assertNotNull(result);
        verify(submissionRepo, times(1)).save(argThat(sub ->
            sub.getStatus() == QotdSubmission.Status.REJECTED &&
            sub.getApprovedByUserId().equals("admin123")
        ));
    }

    @Test
    @DisplayName("reject should throw when submission not found")
    void testReject_NotFound() {
        when(submissionRepo.findById(42L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> {
            service.reject("guild123", 42L, "admin123", "AdminUser");
        }, "Should throw NoSuchElementException when submission is missing");
    }

    @Test
    @DisplayName("reject should reject submission from wrong guild")
    void testReject_WrongGuild() {
        Long submissionId = 1L;
        QotdSubmission submission = createSubmissionWithId(submissionId, "guild123", "user123", "testuser", "Question");
        when(submissionRepo.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThrows(IllegalArgumentException.class, () -> {
            service.reject("guild999", submissionId, "admin123", "AdminUser");
        }, "Should reject wrong guild in reject");
    }

    @Test
    @DisplayName("reject should reject already processed submission")
    void testReject_AlreadyProcessed() {
        Long submissionId = 1L;
        QotdSubmission submission = createSubmissionWithId(submissionId, "guild123", "user123", "testuser", "Question");
        submission.setStatus(QotdSubmission.Status.REJECTED);
        when(submissionRepo.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThrows(IllegalStateException.class, () -> {
            service.reject("guild123", submissionId, "admin123", "AdminUser");
        }, "Should reject already processed submission in reject");
    }

    @Test
    @DisplayName("approveBulk should approve multiple submissions")
    void testApproveBulk_Success() {
        String guildId = "guild123";
        String channelId = "channel456";
        List<Long> ids = List.of(1L, 2L, 3L);

        for (Long id : ids) {
            QotdSubmission submission = createSubmissionWithId(id, guildId, "user" + id, "testuser", "Question " + id);
            when(submissionRepo.findById(id)).thenReturn(Optional.of(submission));
            when(submissionRepo.save(any(QotdSubmission.class))).thenReturn(submission);
        }

        when(questionRepo.save(any(QotdQuestion.class))).thenReturn(new QotdQuestion());

        QotdDtos.BulkActionResult result = service.approveBulk(guildId, channelId, ids, "admin123", "AdminUser");

        assertEquals(3, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("approveBulk should handle partial failures")
    void testApproveBulk_PartialFailure() {
        String guildId = "guild123";
        String channelId = "channel456";
        List<Long> ids = List.of(1L, 2L, 3L);

        // First submission exists
        QotdSubmission submission1 = createSubmissionWithId(1L, guildId, "user1", "testuser", "Question 1");
        when(submissionRepo.findById(1L)).thenReturn(Optional.of(submission1));
        when(submissionRepo.save(any(QotdSubmission.class))).thenReturn(submission1);

        // Second submission doesn't exist
        when(submissionRepo.findById(2L)).thenReturn(Optional.empty());

        // Third submission exists
        QotdSubmission submission3 = createSubmissionWithId(3L, guildId, "user3", "testuser", "Question 3");
        when(submissionRepo.findById(3L)).thenReturn(Optional.of(submission3));

        when(questionRepo.save(any(QotdQuestion.class))).thenReturn(new QotdQuestion());

        QotdDtos.BulkActionResult result = service.approveBulk(guildId, channelId, ids, "admin123", "AdminUser");

        assertEquals(2, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("ID 2"));
    }

    @Test
    @DisplayName("rejectBulk should handle mixed outcomes")
    void testRejectBulk_Mixed() {
        String guildId = "guild123";
        List<Long> ids = List.of(10L, 11L, 12L);

        // 10: success
        QotdSubmission s10 = createSubmissionWithId(10L, guildId, "u10", "user10", "Q10");
        when(submissionRepo.findById(10L)).thenReturn(Optional.of(s10));
        when(submissionRepo.save(any(QotdSubmission.class))).thenReturn(s10);

        // 11: not found
        when(submissionRepo.findById(11L)).thenReturn(Optional.empty());

        // 12: already processed
        QotdSubmission s12 = createSubmissionWithId(12L, guildId, "u12", "user12", "Q12");
        s12.setStatus(QotdSubmission.Status.APPROVED);
        when(submissionRepo.findById(12L)).thenReturn(Optional.of(s12));

        QotdDtos.BulkActionResult res = service.rejectBulk(guildId, ids, "admin123", "AdminUser");
        assertEquals(1, res.successCount());
        assertEquals(2, res.failureCount());
        assertEquals(2, res.errors().size());
    }

    @Test
    @DisplayName("listPending should return pending submissions for guild")
    void testListPending() {
        String guildId = "guild123";
        QotdSubmission sub1 = createSubmissionWithId(1L, guildId, "user1", "user1name", "Q1");
        QotdSubmission sub2 = createSubmissionWithId(2L, guildId, "user2", "user2name", "Q2");

        when(submissionRepo.findByGuildIdAndStatusOrderByCreatedAtAsc(guildId, QotdSubmission.Status.PENDING))
            .thenReturn(List.of(sub1, sub2));

        List<QotdDtos.QotdSubmissionDto> result = service.listPending(guildId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Q1", result.get(0).text());
        assertEquals("Q2", result.get(1).text());
    }

    @Test
    @DisplayName("submit should have separate rate limits per guild for same user")
    void testSubmit_SeparateRateLimitsPerGuild() {
        String userId = "userX";
        when(submissionRepo.save(any(QotdSubmission.class))).thenAnswer(invocation -> {
            QotdSubmission saved = invocation.getArgument(0);
            return createSubmissionWithId(1L, saved.getGuildId(), saved.getUserId(),
                    saved.getUsername(), saved.getText());
        });

        // Consume 3 in g1
        assertDoesNotThrow(() -> service.submit("g1", userId, "name", "Q1"));
        assertDoesNotThrow(() -> service.submit("g1", userId, "name", "Q2"));
        assertDoesNotThrow(() -> service.submit("g1", userId, "name", "Q3"));
        assertThrows(IllegalStateException.class, () -> service.submit("g1", userId, "name", "Q4"));

        // g2 should be independent
        assertDoesNotThrow(() -> service.submit("g2", userId, "name", "Q1"));
        assertDoesNotThrow(() -> service.submit("g2", userId, "name", "Q2"));
        assertDoesNotThrow(() -> service.submit("g2", userId, "name", "Q3"));
    }

    // Helper method to create a submission with ID using reflection
    private QotdSubmission createSubmissionWithId(Long id, String guildId, String userId, String username, String text) {
        QotdSubmission submission = new QotdSubmission(guildId, userId, username, text);
        try {
            java.lang.reflect.Field idField = QotdSubmission.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(submission, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID via reflection", e);
        }
        return submission;
    }
}
