package com.discordbot;

import com.discordbot.web.controller.QotdStreamController;
import com.discordbot.web.dto.qotd.QotdDtos;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.QotdStreamService;
import com.discordbot.web.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QotdStreamControllerTest {

    private QotdStreamController controller;
    private QotdStreamService streamService;
    private AdminService adminService;
    private RateLimitService rateLimitService;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        streamService = mock(QotdStreamService.class);
        adminService = mock(AdminService.class);
        rateLimitService = mock(RateLimitService.class);
        controller = new QotdStreamController(streamService, adminService, rateLimitService);
        mockAuth = mock(Authentication.class);

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);
    }

    // ========== RATE LIMITING TESTS ==========

    @Test
    @DisplayName("uploadCsv should return 429 when rate limited")
    void testUploadCsv_RateLimited() throws IOException {
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(false);
        MultipartFile file = createMockMultipartFile("file", "questions.csv", "text/csv", "Q1\nQ2\n".getBytes());

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(429, response.getStatusCode().value());
        verify(streamService, never()).uploadCsv(anyString(), anyLong(), any());
    }

    // ========== PERMISSION TESTS ==========

    @Test
    @DisplayName("uploadCsv should return 403 when user cannot manage guild")
    void testUploadCsv_PermissionDenied() throws IOException {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);
        MultipartFile file = createMockMultipartFile("file", "questions.csv", "text/csv", "Q1\nQ2\n".getBytes());

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(streamService, never()).uploadCsv(anyString(), anyLong(), any());
    }

    // ========== EXTENSION VALIDATION TESTS ==========

    @Test
    @DisplayName("uploadCsv should accept .csv even with text/plain content-type")
    void testUploadCsv_AcceptsTextPlain() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "questions.csv", "text/plain", "Q1\nQ2\n".getBytes());
        QotdDtos.UploadCsvResult result = new QotdDtos.UploadCsvResult(2, 0, Collections.emptyList());
        when(streamService.uploadCsv("guild123", 42L, file)).thenReturn(result);

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().successCount());
        verify(streamService, times(1)).uploadCsv("guild123", 42L, file);
    }

    @Test
    @DisplayName("uploadCsv should accept .csv with null content-type")
    void testUploadCsv_AcceptsNullContentType() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "questions.csv", null, "Q1\nQ2\n".getBytes());
        QotdDtos.UploadCsvResult result = new QotdDtos.UploadCsvResult(2, 0, Collections.emptyList());
        when(streamService.uploadCsv("guild123", 42L, file)).thenReturn(result);

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        verify(streamService, times(1)).uploadCsv("guild123", 42L, file);
    }

    @Test
    @DisplayName("uploadCsv should reject non-.csv filenames")
    void testUploadCsv_RejectsNonCsvExtension() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "questions.txt", "text/csv", "Q1\nQ2\n".getBytes());

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(400, response.getStatusCode().value());
        verify(streamService, never()).uploadCsv(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("uploadCsv should reject double-extension files for security (prevents 'malicious.exe.csv' attacks)")
    void testUploadCsv_RejectsDoubleExtension() throws IOException {
        // Security test: Ensure filenames with multiple extensions are rejected
        // This prevents potential attacks where a file is crafted to bypass validation
        MultipartFile file = createMockMultipartFile("file", "malicious.exe.csv", "text/csv", "Q1\nQ2\n".getBytes());

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(400, response.getStatusCode().value());
        verify(streamService, never()).uploadCsv(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("uploadCsv should accept uppercase .CSV extension")
    void testUploadCsv_AcceptsUppercaseExtension() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "questions.CSV", "text/csv", "Q1\nQ2\n".getBytes());
        QotdDtos.UploadCsvResult result = new QotdDtos.UploadCsvResult(2, 0, Collections.emptyList());
        when(streamService.uploadCsv("guild123", 42L, file)).thenReturn(result);

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        verify(streamService, times(1)).uploadCsv("guild123", 42L, file);
    }

    @Test
    @DisplayName("uploadCsv should accept mixed-case .Csv extension")
    void testUploadCsv_AcceptsMixedCaseExtension() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "questions.Csv", "text/csv", "Q1\nQ2\n".getBytes());
        QotdDtos.UploadCsvResult result = new QotdDtos.UploadCsvResult(2, 0, Collections.emptyList());
        when(streamService.uploadCsv("guild123", 42L, file)).thenReturn(result);

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        verify(streamService, times(1)).uploadCsv("guild123", 42L, file);
    }

    @Test
    @DisplayName("uploadCsv should reject null filename")
    void testUploadCsv_RejectsNullFilename() throws IOException {
        MultipartFile file = createMockMultipartFile("file", null, "text/csv", "Q1\nQ2\n".getBytes());

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(400, response.getStatusCode().value());
        verify(streamService, never()).uploadCsv(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("uploadCsv should reject empty files")
    void testUploadCsv_RejectsEmptyFile() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "questions.csv", "text/csv", new byte[0]);

        var response = controller.uploadCsv("guild123", 42L, file, mockAuth);

        assertEquals(400, response.getStatusCode().value());
        verify(streamService, never()).uploadCsv(anyString(), anyLong(), any());
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
