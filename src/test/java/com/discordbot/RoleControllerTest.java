package com.discordbot;

import com.discordbot.web.controller.RoleController;
import com.discordbot.web.dto.*;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.RateLimitService;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RoleController - focusing on security, rate limiting, and CSV handling
 */
@SuppressWarnings("null")
class RoleControllerTest {

    private RoleController roleController;
    private AdminService adminService;
    private RateLimitService rateLimitService;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        rateLimitService = mock(RateLimitService.class);
        roleController = new RoleController(adminService, rateLimitService);
        mockAuth = createMockAuth("user123");
    }

    // ========== CREATE ROLE TESTS ==========

    @Test
    @DisplayName("createRole should return 401 when authentication is null")
    void testCreateRole_NoAuth() {
        CreateRoleRequest request = new CreateRoleRequest("Test Role", "COMMON", "#FF5733", null, null);

        ResponseEntity<GachaRoleInfo> response = roleController.createRole("guild123", request, null);

        assertEquals(401, response.getStatusCode().value());
        verify(adminService, never()).createGatchaRole(anyString(), any());
    }

    @Test
    @DisplayName("createRole should return 403 when user cannot manage guild")
    void testCreateRole_NoPermission() {
        CreateRoleRequest request = new CreateRoleRequest("Test Role", "COMMON", "#FF5733", null, null);
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<GachaRoleInfo> response = roleController.createRole("guild123", request, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(adminService, never()).createGatchaRole(anyString(), any());
    }

    @Test
    @DisplayName("createRole should create role successfully")
    void testCreateRole_Success() {
        CreateRoleRequest request = new CreateRoleRequest("Test Role", "COMMON", "#FF5733", null, null);
        GachaRoleInfo expectedRole = new GachaRoleInfo("role123", "gacha:Test Role (COMMON)", "Test Role", "COMMON", "#FF5733", 1);

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(adminService.createGatchaRole("guild123", request)).thenReturn(expectedRole);

        ResponseEntity<GachaRoleInfo> response = roleController.createRole("guild123", request, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedRole, response.getBody());
        verify(adminService, times(1)).createGatchaRole("guild123", request);
    }

    @Test
    @DisplayName("createRole should return 400 on IllegalArgumentException")
    void testCreateRole_InvalidRequest() {
        CreateRoleRequest request = new CreateRoleRequest("", "INVALID_RARITY", "not-a-color", null, null);
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(adminService.createGatchaRole("guild123", request)).thenThrow(new IllegalArgumentException("Invalid rarity"));

        ResponseEntity<GachaRoleInfo> response = roleController.createRole("guild123", request, mockAuth);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    @DisplayName("createRole should return 500 on unexpected exception")
    void testCreateRole_ServerError() {
        CreateRoleRequest request = new CreateRoleRequest("Test Role", "COMMON", "#FF5733", null, null);
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(adminService.createGatchaRole("guild123", request)).thenThrow(new RuntimeException("Unexpected error"));

        ResponseEntity<GachaRoleInfo> response = roleController.createRole("guild123", request, mockAuth);

        assertEquals(500, response.getStatusCode().value());
    }

    // ========== GET ROLES TESTS ==========

    @Test
    @DisplayName("getGatchaRoles should return 401 when authentication is null")
    void testGetRoles_NoAuth() {
        ResponseEntity<List<GachaRoleInfo>> response = roleController.getGatchaRoles("guild123", null);

        assertEquals(401, response.getStatusCode().value());
        verify(adminService, never()).getGatchaRoles(anyString());
    }

    @Test
    @DisplayName("getGatchaRoles should return 403 when user cannot manage guild")
    void testGetRoles_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<List<GachaRoleInfo>> response = roleController.getGatchaRoles("guild123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(adminService, never()).getGatchaRoles(anyString());
    }

    @Test
    @DisplayName("getGatchaRoles should return roles successfully")
    void testGetRoles_Success() {
        List<GachaRoleInfo> roles = Arrays.asList(
            new GachaRoleInfo("role1", "gacha:Common Role (COMMON)", "Common Role", "COMMON", "#FF5733", 1),
            new GachaRoleInfo("role2", "gacha:Rare Role (RARE)", "Rare Role", "RARE", "#33FF57", 2)
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(adminService.getGatchaRoles("guild123")).thenReturn(roles);

        ResponseEntity<List<GachaRoleInfo>> response = roleController.getGatchaRoles("guild123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals(roles, response.getBody());
    }

    // ========== HIERARCHY CHECK TESTS ==========

    @Test
    @DisplayName("checkRoleHierarchy should return 401 when authentication is null")
    void testCheckHierarchy_NoAuth() {
        ResponseEntity<RoleHierarchyStatus> response = roleController.checkRoleHierarchy("guild123", null);

        assertEquals(401, response.getStatusCode().value());
        verify(adminService, never()).checkRoleHierarchy(anyString());
    }

    @Test
    @DisplayName("checkRoleHierarchy should return 403 when user cannot manage guild")
    void testCheckHierarchy_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<RoleHierarchyStatus> response = roleController.checkRoleHierarchy("guild123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(adminService, never()).checkRoleHierarchy(anyString());
    }

    @Test
    @DisplayName("checkRoleHierarchy should return status successfully")
    void testCheckHierarchy_Success() {
        RoleHierarchyStatus status = new RoleHierarchyStatus(true, "PlayBot", 10, 5, Collections.emptyList());

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(adminService.checkRoleHierarchy("guild123")).thenReturn(status);

        ResponseEntity<RoleHierarchyStatus> response = roleController.checkRoleHierarchy("guild123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(status, response.getBody());
    }

    // ========== INIT DEFAULT ROLES TESTS ==========

    @Test
    @DisplayName("initializeDefaultRoles should return 401 when authentication is null")
    void testInitDefaults_NoAuth() {
        ResponseEntity<BulkRoleCreationResult> response = roleController.initializeDefaultRoles("guild123", null);

        assertEquals(401, response.getStatusCode().value());
        verify(adminService, never()).initializeDefaultRoles(anyString());
    }

    @Test
    @DisplayName("initializeDefaultRoles should return 403 when user cannot manage guild")
    void testInitDefaults_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<BulkRoleCreationResult> response = roleController.initializeDefaultRoles("guild123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(adminService, never()).initializeDefaultRoles(anyString());
    }

    @Test
    @DisplayName("initializeDefaultRoles should return 429 when rate limited")
    void testInitDefaults_RateLimited() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(false);

        ResponseEntity<BulkRoleCreationResult> response = roleController.initializeDefaultRoles("guild123", mockAuth);

        assertEquals(429, response.getStatusCode().value());
        verify(adminService, never()).initializeDefaultRoles(anyString());
    }

    @Test
    @DisplayName("initializeDefaultRoles should create roles successfully")
    void testInitDefaults_Success() {
        BulkRoleCreationResult result = new BulkRoleCreationResult(
            5, 0, 0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);
        when(adminService.initializeDefaultRoles("guild123")).thenReturn(result);

        ResponseEntity<BulkRoleCreationResult> response = roleController.initializeDefaultRoles("guild123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(5, response.getBody().successCount());
        verify(adminService, times(1)).initializeDefaultRoles("guild123");
    }

    // ========== CSV UPLOAD TESTS ==========

    @Test
    @DisplayName("uploadCsv should return 401 when authentication is null")
    void testUploadCsv_NoAuth() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "roles.csv", "text/csv", "name,rarity,color\n".getBytes());

        ResponseEntity<BulkRoleCreationResult> response = roleController.uploadCsv("guild123", file, null);

        assertEquals(401, response.getStatusCode().value());
        verify(adminService, never()).createBulkGatchaRoles(anyString(), anyList());
    }

    @Test
    @DisplayName("uploadCsv should return 403 when user cannot manage guild")
    void testUploadCsv_NoPermission() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "roles.csv", "text/csv", "name,rarity,color\n".getBytes());
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<BulkRoleCreationResult> response = roleController.uploadCsv("guild123", file, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(adminService, never()).createBulkGatchaRoles(anyString(), anyList());
    }

    @Test
    @DisplayName("uploadCsv should return 429 when rate limited")
    void testUploadCsv_RateLimited() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "roles.csv", "text/csv", "name,rarity,color\n".getBytes());
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(false);

        ResponseEntity<BulkRoleCreationResult> response = roleController.uploadCsv("guild123", file, mockAuth);

        assertEquals(429, response.getStatusCode().value());
        verify(adminService, never()).createBulkGatchaRoles(anyString(), anyList());
    }

    @Test
    @DisplayName("uploadCsv should return 400 when file is empty")
    void testUploadCsv_EmptyFile() throws IOException {
        MultipartFile file = createMockMultipartFile("file", "roles.csv", "text/csv", new byte[0]);
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);

        ResponseEntity<BulkRoleCreationResult> response = roleController.uploadCsv("guild123", file, mockAuth);

        assertEquals(400, response.getStatusCode().value());
        verify(adminService, never()).createBulkGatchaRoles(anyString(), anyList());
    }

    @Test
    @DisplayName("uploadCsv should return 413 when file exceeds 1MB")
    void testUploadCsv_FileTooLarge() throws IOException {
        // Create a file larger than 1MB
        byte[] largeContent = new byte[1024 * 1024 + 1]; // 1MB + 1 byte
        MultipartFile file = createMockMultipartFile("file", "roles.csv", "text/csv", largeContent);

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);

        ResponseEntity<BulkRoleCreationResult> response = roleController.uploadCsv("guild123", file, mockAuth);

        assertEquals(413, response.getStatusCode().value());
        verify(adminService, never()).createBulkGatchaRoles(anyString(), anyList());
    }

    @Test
    @DisplayName("uploadCsv should parse CSV and create roles successfully")
    void testUploadCsv_Success() throws IOException {
        String csvContent = "name,rarity,color\n" +
                            "Common Role,COMMON,#FF5733\n" +
                            "Rare Role,RARE,#33FF57\n" +
                            "Epic Role,EPIC,#3357FF\n";
        MultipartFile file = createMockMultipartFile("file", "roles.csv", "text/csv", csvContent.getBytes());

        BulkRoleCreationResult result = new BulkRoleCreationResult(
            3, 0, 0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);
        when(adminService.createBulkGatchaRoles(eq("guild123"), anyList())).thenReturn(result);

        ResponseEntity<BulkRoleCreationResult> response = roleController.uploadCsv("guild123", file, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().successCount());
        verify(adminService, times(1)).createBulkGatchaRoles(eq("guild123"), anyList());
    }

    @Test
    @DisplayName("uploadCsv should skip empty lines in CSV")
    void testUploadCsv_SkipEmptyLines() throws IOException {
        String csvContent = "name,rarity,color\n" +
                            "Common Role,COMMON,#FF5733\n" +
                            "\n" +
                            "Rare Role,RARE,#33FF57\n" +
                            "   \n" +
                            "Epic Role,EPIC,#3357FF\n";
        MultipartFile file = createMockMultipartFile("file", "roles.csv", "text/csv", csvContent.getBytes());

        BulkRoleCreationResult result = new BulkRoleCreationResult(
            3, 0, 0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);
        when(adminService.createBulkGatchaRoles(eq("guild123"), anyList())).thenReturn(result);

        ResponseEntity<BulkRoleCreationResult> response = roleController.uploadCsv("guild123", file, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().successCount());
    }

    @Test
    @DisplayName("uploadCsv should handle malformed CSV gracefully")
    void testUploadCsv_MalformedCsv() throws IOException {
        String csvContent = "name,rarity,color\n" +
                            "Only Two Fields,COMMON\n" +
                            "Valid Role,RARE,#33FF57\n";
        MultipartFile file = createMockMultipartFile("file", "roles.csv", "text/csv", csvContent.getBytes());

        BulkRoleCreationResult result = new BulkRoleCreationResult(
            1, 0, 0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);
        when(adminService.createBulkGatchaRoles(eq("guild123"), anyList())).thenReturn(result);

        ResponseEntity<BulkRoleCreationResult> response = roleController.uploadCsv("guild123", file, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        // Should only parse the valid line
        assertEquals(1, response.getBody().successCount());
    }

    // ========== DOWNLOAD EXAMPLE CSV TESTS ==========

    @Test
    @DisplayName("downloadExampleCsv should return 401 when authentication is null")
    void testDownloadExample_NoAuth() {
        ResponseEntity<Resource> response = roleController.downloadExampleCsv("guild123", null);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    @DisplayName("downloadExampleCsv should return 403 when user cannot manage guild")
    void testDownloadExample_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<Resource> response = roleController.downloadExampleCsv("guild123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    @DisplayName("downloadExampleCsv should return CSV file successfully")
    void testDownloadExample_Success() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);

        ResponseEntity<Resource> response = roleController.downloadExampleCsv("guild123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().toString().contains("example-roles.csv"));
    }

    // ========== DELETE ROLE TESTS ==========

    @Test
    @DisplayName("deleteRole should return 401 when authentication is null")
    void testDeleteRole_NoAuth() {
        ResponseEntity<RoleDeletionResult> response = roleController.deleteRole("guild123", "role123", null);

        assertEquals(401, response.getStatusCode().value());
        verify(adminService, never()).deleteGatchaRole(anyString(), anyString());
    }

    @Test
    @DisplayName("deleteRole should return 403 when user cannot manage guild")
    void testDeleteRole_NoPermission() {
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<RoleDeletionResult> response = roleController.deleteRole("guild123", "role123", mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(adminService, never()).deleteGatchaRole(anyString(), anyString());
    }

    @Test
    @DisplayName("deleteRole should delete role successfully")
    void testDeleteRole_Success() {
        RoleDeletionResult result = new RoleDeletionResult("role123", "Test Role", true, null);

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(adminService.deleteGatchaRole("guild123", "role123")).thenReturn(result);

        ResponseEntity<RoleDeletionResult> response = roleController.deleteRole("guild123", "role123", mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        verify(adminService, times(1)).deleteGatchaRole("guild123", "role123");
    }

    @Test
    @DisplayName("deleteRole should return 400 when deletion fails")
    void testDeleteRole_Failure() {
        RoleDeletionResult result = new RoleDeletionResult("role123", "Test Role", false, "Role not found");

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(adminService.deleteGatchaRole("guild123", "role123")).thenReturn(result);

        ResponseEntity<RoleDeletionResult> response = roleController.deleteRole("guild123", "role123", mockAuth);

        assertEquals(400, response.getStatusCode().value());
        assertFalse(response.getBody().success());
        assertEquals("Role not found", response.getBody().error());
    }

    // ========== BULK DELETE TESTS ==========

    @Test
    @DisplayName("bulkDeleteRoles should return 401 when authentication is null")
    void testBulkDelete_NoAuth() {
        BulkRoleDeletionRequest request = new BulkRoleDeletionRequest(Arrays.asList("role1", "role2"));

        ResponseEntity<BulkRoleDeletionResult> response = roleController.bulkDeleteRoles("guild123", request, null);

        assertEquals(401, response.getStatusCode().value());
        verify(adminService, never()).deleteBulkGatchaRoles(anyString(), anyList());
    }

    @Test
    @DisplayName("bulkDeleteRoles should return 403 when user cannot manage guild")
    void testBulkDelete_NoPermission() {
        BulkRoleDeletionRequest request = new BulkRoleDeletionRequest(Arrays.asList("role1", "role2"));
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(false);

        ResponseEntity<BulkRoleDeletionResult> response = roleController.bulkDeleteRoles("guild123", request, mockAuth);

        assertEquals(403, response.getStatusCode().value());
        verify(adminService, never()).deleteBulkGatchaRoles(anyString(), anyList());
    }

    @Test
    @DisplayName("bulkDeleteRoles should return 429 when rate limited")
    void testBulkDelete_RateLimited() {
        BulkRoleDeletionRequest request = new BulkRoleDeletionRequest(Arrays.asList("role1", "role2"));
        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(false);

        ResponseEntity<BulkRoleDeletionResult> response = roleController.bulkDeleteRoles("guild123", request, mockAuth);

        assertEquals(429, response.getStatusCode().value());
        verify(adminService, never()).deleteBulkGatchaRoles(anyString(), anyList());
    }

    @Test
    @DisplayName("bulkDeleteRoles should delete roles successfully")
    void testBulkDelete_Success() {
        BulkRoleDeletionRequest request = new BulkRoleDeletionRequest(Arrays.asList("role1", "role2", "role3"));
        BulkRoleDeletionResult result = new BulkRoleDeletionResult(
            3,
            0,
            Collections.emptyList(),
            Collections.emptyList()
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);
        when(adminService.deleteBulkGatchaRoles("guild123", Arrays.asList("role1", "role2", "role3"))).thenReturn(result);

        ResponseEntity<BulkRoleDeletionResult> response = roleController.bulkDeleteRoles("guild123", request, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().successCount());
        verify(adminService, times(1)).deleteBulkGatchaRoles("guild123", Arrays.asList("role1", "role2", "role3"));
    }

    @Test
    @DisplayName("bulkDeleteRoles should handle partial failures")
    void testBulkDelete_PartialFailure() {
        BulkRoleDeletionRequest request = new BulkRoleDeletionRequest(Arrays.asList("role1", "role2", "role3"));
        BulkRoleDeletionResult result = new BulkRoleDeletionResult(
            2,
            1,
            Collections.emptyList(),
            Arrays.asList("Failed to delete role3: Role not found")
        );

        when(adminService.canManageGuild(mockAuth, "guild123")).thenReturn(true);
        when(rateLimitService.allowBulkOperation(mockAuth)).thenReturn(true);
        when(adminService.deleteBulkGatchaRoles("guild123", Arrays.asList("role1", "role2", "role3"))).thenReturn(result);

        ResponseEntity<BulkRoleDeletionResult> response = roleController.bulkDeleteRoles("guild123", request, mockAuth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().successCount());
        assertEquals(1, response.getBody().failureCount());
        assertFalse(response.getBody().errors().isEmpty());
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
