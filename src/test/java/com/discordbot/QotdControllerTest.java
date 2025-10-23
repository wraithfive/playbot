package com.discordbot;

import com.discordbot.web.controller.QotdController;
import com.discordbot.web.dto.qotd.QotdDtos;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.QotdService;
import com.discordbot.web.service.QotdSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for QotdController - focusing on security and bulk operations
 */
class QotdControllerTest {

    private QotdController qotdController;
    private QotdService qotdService;
    private QotdSubmissionService submissionService;
    private AdminService adminService;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        qotdService = mock(QotdService.class);
        submissionService = mock(QotdSubmissionService.class);
        adminService = mock(AdminService.class);
        qotdController = new QotdController(qotdService, submissionService, adminService);
        mockAuth = createMockAuth("user123");
    }

    // ========== CHANNEL LISTING TESTS ==========

    @Test
    @DisplayName("listChannels should return 403 when user cannot manage guild")
    void testListChannels_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<List<QotdDtos.TextChannelInfo>> response = qotdController.listChannels("guild123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).listTextChannels(anyString());
    }

    @Test
    @DisplayName("listChannels should return channels successfully")
    void testListChannels_Success() {
        List<QotdDtos.TextChannelInfo> channels = Arrays.asList(
            new QotdDtos.TextChannelInfo("channel1", "general"),
            new QotdDtos.TextChannelInfo("channel2", "questions")
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(qotdService.listTextChannels("guild123")).thenReturn(channels);

        ResponseEntity<List<QotdDtos.TextChannelInfo>> response = qotdController.listChannels("guild123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        verify(qotdService, times(1)).listTextChannels("guild123");
    }

    // ========== CONFIG LISTING TESTS ==========

    @Test
    @DisplayName("listConfigs should return 403 when user cannot manage guild")
    void testListConfigs_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<List<QotdDtos.QotdConfigDto>> response = qotdController.listConfigs("guild123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).listGuildConfigs(anyString());
    }

    @Test
    @DisplayName("listConfigs should return configs successfully")
    void testListConfigs_Success() {
        List<QotdDtos.QotdConfigDto> configs = Arrays.asList(
            new QotdDtos.QotdConfigDto("channel1", true, "UTC", "0 9 * * MON,WED,FRI", false, null, 0, Collections.emptyList()),
            new QotdDtos.QotdConfigDto("channel2", false, "America/New_York", null, true, null, 0, Collections.emptyList())
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(qotdService.listGuildConfigs("guild123")).thenReturn(configs);

        ResponseEntity<List<QotdDtos.QotdConfigDto>> response = qotdController.listConfigs("guild123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        verify(qotdService, times(1)).listGuildConfigs("guild123");
    }

    // ========== GET CONFIG TESTS ==========

    @Test
    @DisplayName("getConfig should return 403 when user cannot manage guild")
    void testGetConfig_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<QotdDtos.QotdConfigDto> response = qotdController.getConfig("guild123", "channel1", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).getConfig(anyString(), anyString());
    }

    @Test
    @DisplayName("getConfig should validate channel belongs to guild")
    void testGetConfig_ValidatesChannel() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        QotdDtos.QotdConfigDto config = new QotdDtos.QotdConfigDto("channel1", true, "UTC", "0 9 * * *", false, null, 0, Collections.emptyList());
        when(qotdService.getConfig("guild123", "channel1")).thenReturn(config);

        ResponseEntity<QotdDtos.QotdConfigDto> response = qotdController.getConfig("guild123", "channel1", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
        verify(qotdService, times(1)).getConfig("guild123", "channel1");
    }

    // ========== UPDATE CONFIG TESTS ==========

    @Test
    @DisplayName("updateConfig should return 403 when user cannot manage guild")
    void testUpdateConfig_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);
        QotdDtos.UpdateConfigRequest request = new QotdDtos.UpdateConfigRequest(
            true, "UTC", null, Arrays.asList("MON", "WED", "FRI"), "09:00", false
        );

        ResponseEntity<QotdDtos.QotdConfigDto> response = qotdController.updateConfig("guild123", "channel1", request, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).updateConfig(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("updateConfig should update config successfully")
    void testUpdateConfig_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        QotdDtos.UpdateConfigRequest request = new QotdDtos.UpdateConfigRequest(
            true, "UTC", null, Arrays.asList("MON", "WED", "FRI"), "09:00", false
        );
        QotdDtos.QotdConfigDto updatedConfig = new QotdDtos.QotdConfigDto("channel1", true, "UTC", "0 9 * * MON,WED,FRI", false, null, 0, Collections.emptyList());
        when(qotdService.updateConfig("guild123", "channel1", request)).thenReturn(updatedConfig);

        ResponseEntity<QotdDtos.QotdConfigDto> response = qotdController.updateConfig("guild123", "channel1", request, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
        verify(qotdService, times(1)).updateConfig("guild123", "channel1", request);
    }

    // ========== QUESTION MANAGEMENT TESTS ==========

    @Test
    @DisplayName("getQuestions should return 403 when user cannot manage guild")
    void testGetQuestions_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<List<QotdDtos.QotdQuestionDto>> response = qotdController.getQuestions("guild123", "channel1", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).listQuestions(anyString(), anyString());
    }

    @Test
    @DisplayName("getQuestions should return questions successfully")
    void testGetQuestions_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        List<QotdDtos.QotdQuestionDto> questions = Arrays.asList(
            new QotdDtos.QotdQuestionDto(1L, "What is your favorite color?", Instant.now()),
            new QotdDtos.QotdQuestionDto(2L, "What is your favorite food?", Instant.now())
        );
        when(qotdService.listQuestions("guild123", "channel1")).thenReturn(questions);

        ResponseEntity<List<QotdDtos.QotdQuestionDto>> response = qotdController.getQuestions("guild123", "channel1", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
    }

    @Test
    @DisplayName("addQuestion should return 403 when user cannot manage guild")
    void testAddQuestion_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);
        QotdDtos.UpsertQuestionRequest request = new QotdDtos.UpsertQuestionRequest("What is your favorite color?");

        ResponseEntity<QotdDtos.QotdQuestionDto> response = qotdController.addQuestion("guild123", "channel1", request, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).addQuestion(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("addQuestion should add question successfully")
    void testAddQuestion_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        QotdDtos.UpsertQuestionRequest request = new QotdDtos.UpsertQuestionRequest("What is your favorite color?");
        QotdDtos.QotdQuestionDto question = new QotdDtos.QotdQuestionDto(1L, "What is your favorite color?", Instant.now());
        when(qotdService.addQuestion("guild123", "channel1", "What is your favorite color?")).thenReturn(question);

        ResponseEntity<QotdDtos.QotdQuestionDto> response = qotdController.addQuestion("guild123", "channel1", request, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("What is your favorite color?", response.getBody().text());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
    }

    @Test
    @DisplayName("deleteQuestion should return 403 when user cannot manage guild")
    void testDeleteQuestion_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<?> response = qotdController.deleteQuestion("guild123", "channel1", 1L, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).deleteQuestion(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("deleteQuestion should delete question successfully")
    void testDeleteQuestion_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        doNothing().when(qotdService).deleteQuestion("guild123", "channel1", 1L);

        ResponseEntity<?> response = qotdController.deleteQuestion("guild123", "channel1", 1L, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
        verify(qotdService, times(1)).deleteQuestion("guild123", "channel1", 1L);
    }

    // ========== CSV UPLOAD TESTS ==========

    @Test
    @DisplayName("uploadCsv should return 403 when user cannot manage guild")
    void testUploadCsv_NoPermission() throws IOException {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);
        MultipartFile file = createMockMultipartFile("file", "questions.csv", "text/csv", "Question 1\n".getBytes());

        ResponseEntity<QotdDtos.UploadCsvResult> response = qotdController.uploadCsv("guild123", "channel1", file, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).uploadCsv(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("uploadCsv should return 400 when file is empty")
    void testUploadCsv_EmptyFile() throws IOException {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        MultipartFile file = createMockMultipartFile("file", "questions.csv", "text/csv", new byte[0]);

        ResponseEntity<QotdDtos.UploadCsvResult> response = qotdController.uploadCsv("guild123", "channel1", file, mockAuth);

        assertEquals(400, response.getStatusCode().value());
        verify(qotdService, never()).uploadCsv(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("uploadCsv should upload CSV successfully")
    void testUploadCsv_Success() throws IOException {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        String csvContent = "Question 1\nQuestion 2\nQuestion 3\n";
        MultipartFile file = createMockMultipartFile("file", "questions.csv", "text/csv", csvContent.getBytes());
        QotdDtos.UploadCsvResult result = new QotdDtos.UploadCsvResult(3, 0, Collections.emptyList());
        when(qotdService.uploadCsv("guild123", "channel1", csvContent)).thenReturn(result);

        ResponseEntity<QotdDtos.UploadCsvResult> response = qotdController.uploadCsv("guild123", "channel1", file, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().successCount());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
    }

    // ========== POST NOW TESTS ==========

    @Test
    @DisplayName("postNow should return 403 when user cannot manage guild")
    void testPostNow_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<?> response = qotdController.postNow("guild123", "channel1", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(qotdService, never()).postNextQuestion(anyString(), anyString());
    }

    @Test
    @DisplayName("postNow should post question successfully")
    void testPostNow_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(qotdService.postNextQuestion("guild123", "channel1")).thenReturn(true);

        ResponseEntity<?> response = qotdController.postNow("guild123", "channel1", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
        verify(qotdService, times(1)).postNextQuestion("guild123", "channel1");
    }

    @Test
    @DisplayName("postNow should return 400 when posting fails")
    void testPostNow_Failure() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(qotdService.postNextQuestion("guild123", "channel1")).thenReturn(false);

        ResponseEntity<?> response = qotdController.postNow("guild123", "channel1", mockAuth);

        assertEquals(400, response.getStatusCode().value());
    }

    // ========== SUBMISSIONS TESTS ==========

    @Test
    @DisplayName("listPending should return 403 when user cannot manage guild")
    void testListPending_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<List<QotdDtos.QotdSubmissionDto>> response = qotdController.listPending("guild123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(submissionService, never()).listPending(anyString());
    }

    @Test
    @DisplayName("listPending should return pending submissions successfully")
    void testListPending_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        List<QotdDtos.QotdSubmissionDto> submissions = Arrays.asList(
            new QotdDtos.QotdSubmissionDto(1L, "What is your favorite game?", "user1", "User One", QotdDtos.SubmissionStatus.PENDING, Instant.now()),
            new QotdDtos.QotdSubmissionDto(2L, "What is your favorite movie?", "user2", "User Two", QotdDtos.SubmissionStatus.PENDING, Instant.now())
        );
        when(submissionService.listPending("guild123")).thenReturn(submissions);

        ResponseEntity<List<QotdDtos.QotdSubmissionDto>> response = qotdController.listPending("guild123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        verify(submissionService, times(1)).listPending("guild123");
    }

    @Test
    @DisplayName("approve should return 403 when user cannot manage guild")
    void testApprove_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<QotdDtos.QotdSubmissionDto> response = qotdController.approve("guild123", "channel1", 1L, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(submissionService, never()).approve(anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("approve should approve submission successfully")
    void testApprove_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(mockAuth.getName()).thenReturn("user123");
        QotdDtos.QotdSubmissionDto approved = new QotdDtos.QotdSubmissionDto(
            1L, "What is your favorite game?", "user1", "User One",
            QotdDtos.SubmissionStatus.APPROVED, Instant.now()
        );
        when(submissionService.approve("guild123", "channel1", 1L, "user123", "user123")).thenReturn(approved);

        ResponseEntity<QotdDtos.QotdSubmissionDto> response = qotdController.approve("guild123", "channel1", 1L, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(QotdDtos.SubmissionStatus.APPROVED, response.getBody().status());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
    }

    @Test
    @DisplayName("reject should return 403 when user cannot manage guild")
    void testReject_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<QotdDtos.QotdSubmissionDto> response = qotdController.reject("guild123", 1L, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(submissionService, never()).reject(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("reject should reject submission successfully")
    void testReject_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(mockAuth.getName()).thenReturn("user123");
        QotdDtos.QotdSubmissionDto rejected = new QotdDtos.QotdSubmissionDto(
            1L, "What is your favorite game?", "user1", "User One",
            QotdDtos.SubmissionStatus.REJECTED, Instant.now()
        );
        when(submissionService.reject("guild123", 1L, "user123", "user123")).thenReturn(rejected);

        ResponseEntity<QotdDtos.QotdSubmissionDto> response = qotdController.reject("guild123", 1L, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(QotdDtos.SubmissionStatus.REJECTED, response.getBody().status());
    }

    // ========== BULK OPERATIONS TESTS ==========

    @Test
    @DisplayName("bulkApprove should return 403 when user cannot manage guild")
    void testBulkApprove_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);
        QotdDtos.BulkIdsRequest request = new QotdDtos.BulkIdsRequest(Arrays.asList(1L, 2L, 3L));

        ResponseEntity<QotdDtos.BulkActionResult> response = qotdController.bulkApprove("guild123", "channel1", request, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(submissionService, never()).approveBulk(anyString(), anyString(), anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("bulkApprove should approve multiple submissions successfully")
    void testBulkApprove_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(mockAuth.getName()).thenReturn("user123");
        QotdDtos.BulkIdsRequest request = new QotdDtos.BulkIdsRequest(Arrays.asList(1L, 2L, 3L));
        QotdDtos.BulkActionResult result = new QotdDtos.BulkActionResult(3, 0, Collections.emptyList());
        when(submissionService.approveBulk("guild123", "channel1", Arrays.asList(1L, 2L, 3L), "user123", "user123")).thenReturn(result);

        ResponseEntity<QotdDtos.BulkActionResult> response = qotdController.bulkApprove("guild123", "channel1", request, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().successCount());
        verify(qotdService, times(1)).validateChannelBelongsToGuild("guild123", "channel1");
    }

    @Test
    @DisplayName("bulkReject should return 403 when user cannot manage guild")
    void testBulkReject_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);
        QotdDtos.BulkIdsRequest request = new QotdDtos.BulkIdsRequest(Arrays.asList(1L, 2L, 3L));

        ResponseEntity<QotdDtos.BulkActionResult> response = qotdController.bulkReject("guild123", request, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(submissionService, never()).rejectBulk(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("bulkReject should reject multiple submissions successfully")
    void testBulkReject_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(mockAuth.getName()).thenReturn("user123");
        QotdDtos.BulkIdsRequest request = new QotdDtos.BulkIdsRequest(Arrays.asList(1L, 2L, 3L));
        QotdDtos.BulkActionResult result = new QotdDtos.BulkActionResult(3, 0, Collections.emptyList());
        when(submissionService.rejectBulk("guild123", Arrays.asList(1L, 2L, 3L), "user123", "user123")).thenReturn(result);

        ResponseEntity<QotdDtos.BulkActionResult> response = qotdController.bulkReject("guild123", request, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().successCount());
    }

    @Test
    @DisplayName("bulkApprove should handle partial failures")
    void testBulkApprove_PartialFailure() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(mockAuth.getName()).thenReturn("user123");
        QotdDtos.BulkIdsRequest request = new QotdDtos.BulkIdsRequest(Arrays.asList(1L, 2L, 3L));
        QotdDtos.BulkActionResult result = new QotdDtos.BulkActionResult(
            2, 1, Arrays.asList("Submission 3 not found")
        );
        when(submissionService.approveBulk("guild123", "channel1", Arrays.asList(1L, 2L, 3L), "user123", "user123")).thenReturn(result);

        ResponseEntity<QotdDtos.BulkActionResult> response = qotdController.bulkApprove("guild123", "channel1", request, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().successCount());
        assertEquals(1, response.getBody().failureCount());
        assertFalse(response.getBody().errors().isEmpty());
    }

    // ========== DOWNLOAD EXAMPLE CSV TESTS ==========

    @Test
    @DisplayName("downloadExampleQotdCsv should return 403 when user cannot manage guild")
    void testDownloadExample_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<Resource> response = qotdController.downloadExampleQotdCsv("guild123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    @DisplayName("downloadExampleQotdCsv should return CSV file successfully")
    void testDownloadExample_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);

        ResponseEntity<Resource> response = qotdController.downloadExampleQotdCsv("guild123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().toString().contains("example-qotd.csv"));
    }

    // ========== HELPER METHODS ==========

    private Authentication createMockAuth(String userId) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", userId);
        attributes.put("username", "testuser");

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.emptyList(),
            attributes,
            "id"
        );

        when(auth.getPrincipal()).thenReturn(oauth2User);
        when(auth.getName()).thenReturn(userId);

        return auth;
    }

    private MultipartFile createMockMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getName()).thenReturn(name);
        when(file.getOriginalFilename()).thenReturn(originalFilename);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn((long) content.length);
        when(file.isEmpty()).thenReturn(content.length == 0);
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
            when(file.getBytes()).thenReturn(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }
}
