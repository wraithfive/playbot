package com.discordbot.web.service;

import com.discordbot.web.dto.BulkRoleCreationResult;
import com.discordbot.web.dto.BulkRoleDeletionResult;
import com.discordbot.web.dto.CreateRoleRequest;
import com.discordbot.web.dto.GachaRoleInfo;
import com.discordbot.web.dto.GuildInfo;
import com.discordbot.web.dto.RoleDeletionResult;
import com.discordbot.web.dto.RoleHierarchyStatus;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
 

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
    private static final String GATCHA_PREFIX = "gacha:";
    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";

    private final JDA jda;
    private final RestTemplate restTemplate;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GuildsCache guildsCache;
    private final WebSocketNotificationService webSocketNotificationService;

    public AdminService(JDA jda, OAuth2AuthorizedClientService authorizedClientService, 
                       GuildsCache guildsCache, WebSocketNotificationService webSocketNotificationService) {
        this.jda = jda;
        this.restTemplate = new RestTemplate();
        this.authorizedClientService = authorizedClientService;
        this.guildsCache = guildsCache;
        this.webSocketNotificationService = webSocketNotificationService;
    }

    /**
     * Generate a bot invite URL for a specific guild with required permissions
     */
    public String generateBotInviteUrl(String guildId) {
        String clientId = jda.getSelfUser().getId();

        // Required permissions for the bot:
        // - MANAGE_ROLES: Create and assign gacha roles
        // - VIEW_CHANNEL: See channels to respond to commands
        // - SEND_MESSAGES: Send roll results and help messages
        long permissions = Permission.MANAGE_ROLES.getRawValue() |
                          Permission.VIEW_CHANNEL.getRawValue() |
                          Permission.MESSAGE_SEND.getRawValue();

        String inviteUrl = String.format(
            "https://discord.com/api/oauth2/authorize?client_id=%s&permissions=%d&guild_id=%s&scope=bot%%20applications.commands",
            clientId,
            permissions,
            guildId
        );

        logger.info("Generated bot invite URL for guild: {}", guildId);
        return inviteUrl;
    }

    /**
     * Remove bot from a specific guild
     */
    public void leaveBotFromGuild(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.warn("Attempted to leave guild {} but bot is not present", guildId);
            throw new IllegalArgumentException("Bot is not in the specified guild");
        }

        String guildName = guild.getName();
        guild.leave().queue(
            success -> logger.info("Bot successfully left guild: {} ({})", guildName, guildId),
            error -> logger.error("Failed to leave guild: {} ({}): {}", guildName, guildId, error.getMessage())
        );
    }

    /**
     * Get all guilds where the authenticated user is an admin AND the bot is present
     */
    public List<GuildInfo> getManageableGuilds(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User)) {
            logger.warn("Unauthorized access attempt to getManageableGuilds");
            return new ArrayList<>();
        }

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String userId = oauth2User.getAttribute("id");
        String accessToken = getAccessToken(authentication);

        if (accessToken == null) {
            logger.error("No access token found for user: {}", userId);
            return new ArrayList<>();
        }

        // Fetch user's guilds from Discord API
        List<Map<String, Object>> userGuilds = getUserGuildsFromDiscord(accessToken);
        List<GuildInfo> manageableGuilds = new ArrayList<>();

        for (Map<String, Object> userGuild : userGuilds) {
            String guildId = (String) userGuild.get("id");
            String guildName = (String) userGuild.get("name");
            String icon = (String) userGuild.get("icon");
            Long permissions = Long.parseLong(userGuild.get("permissions").toString());

            // Check if user has admin permissions (using bitwise AND)
            boolean isAdmin = (permissions & Permission.ADMINISTRATOR.getRawValue()) != 0 ||
                             (permissions & Permission.MANAGE_SERVER.getRawValue()) != 0;

            if (!isAdmin) {
                continue;
            }

            // Check if bot is in this guild
            Guild guild = jda.getGuildById(guildId);
            boolean botPresent = guild != null;

            logger.info("Checking guild - ID: {}, Name: {}, Bot present: {}", guildId, guildName, botPresent);
            if (!botPresent) {
                logger.info("Bot NOT found in guild {}. Available guild IDs in JDA: {}",
                    guildId, jda.getGuilds().stream().map(Guild::getId).toList());
            }

            String iconUrl = icon != null ?
                "https://cdn.discordapp.com/icons/" + guildId + "/" + icon + ".png" : null;

            manageableGuilds.add(new GuildInfo(guildId, guildName, iconUrl, true, botPresent));
        }

        logger.info("User {} can manage {} guilds", userId, manageableGuilds.size());
        return manageableGuilds;
    }

    /**
     * Check if user has admin permissions in a specific guild (regardless of bot presence)
     */
    public boolean isUserAdminInGuild(Authentication authentication, String guildId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User)) {
            return false;
        }

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String userId = oauth2User.getAttribute("id");
        String accessToken = getAccessToken(authentication);

        if (accessToken == null) {
            return false;
        }

        List<Map<String, Object>> userGuilds = getUserGuildsFromDiscord(accessToken);

        for (Map<String, Object> userGuild : userGuilds) {
            if (guildId.equals(userGuild.get("id"))) {
                Long permissions = Long.parseLong(userGuild.get("permissions").toString());
                boolean isAdmin = (permissions & Permission.ADMINISTRATOR.getRawValue()) != 0 ||
                                 (permissions & Permission.MANAGE_SERVER.getRawValue()) != 0;
                return isAdmin;
            }
        }

        logger.warn("User {} attempted to access guild {} without permissions", userId, guildId);
        return false;
    }

    /**
     * Check if user has admin permissions in a specific guild AND bot is present
     */
    public boolean canManageGuild(Authentication authentication, String guildId) {
        if (!isUserAdminInGuild(authentication, guildId)) {
            return false;
        }

        // Also verify bot is in the guild
        Guild guild = jda.getGuildById(guildId);
        return guild != null;
    }

    /**
     * Get all gatcha roles for a guild
     */
    public List<GachaRoleInfo> getGatchaRoles(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.warn("Guild not found: {}", guildId);
            return new ArrayList<>();
        }

        List<GachaRoleInfo> gatchaRoles = new ArrayList<>();

        for (Role role : guild.getRoles()) {
            if (role.getName().toLowerCase().startsWith(GATCHA_PREFIX)) {
                gatchaRoles.add(mapRoleToDto(role));
            }
        }

        logger.info("Found {} gatcha roles in guild: {}", gatchaRoles.size(), guild.getName());
        return gatchaRoles;
    }

    /**
     * Check if the bot's role is positioned correctly in the hierarchy
     * (must be above all gacha roles to manage them)
     */
    public RoleHierarchyStatus checkRoleHierarchy(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.warn("Guild not found: {}", guildId);
            return new RoleHierarchyStatus(false, "Unknown", 0, 0, List.of("Guild not found"));
        }

        // Get the bot's role (the highest role assigned to the bot member)
        net.dv8tion.jda.api.entities.Member botMember = guild.getSelfMember();
        List<Role> botRoles = botMember.getRoles();
        
        if (botRoles.isEmpty()) {
            return new RoleHierarchyStatus(false, "No role assigned", 0, 0, 
                List.of("Bot has no roles assigned"));
        }

        // Get the highest role (lowest position number = highest in hierarchy)
        Role botHighestRole = botRoles.get(0);
        int botPosition = botHighestRole.getPosition();

        // Find all gacha roles and check if any are above the bot's role
        List<String> conflictingRoles = new ArrayList<>();
        int highestGachaPosition = -1;

        for (Role role : guild.getRoles()) {
            if (role.getName().toLowerCase().startsWith(GATCHA_PREFIX)) {
                if (highestGachaPosition == -1 || role.getPosition() > highestGachaPosition) {
                    highestGachaPosition = role.getPosition();
                }
                
                // If gacha role position > bot position, it's above the bot (conflicting)
                if (role.getPosition() > botPosition) {
                    conflictingRoles.add(role.getName());
                }
            }
        }

        boolean isValid = conflictingRoles.isEmpty();
        
        if (!isValid) {
            logger.warn("Role hierarchy issue in guild {}: Bot role '{}' (pos {}) is below {} gacha role(s)", 
                guild.getName(), botHighestRole.getName(), botPosition, conflictingRoles.size());
        }

        return new RoleHierarchyStatus(
            isValid,
            botHighestRole.getName(),
            botPosition,
            highestGachaPosition,
            conflictingRoles
        );
    }

    /**
     * Create a single gatcha role in a guild
     */
    public GachaRoleInfo createGatchaRole(String guildId, CreateRoleRequest request) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Guild not found: " + guildId);
        }

        // Build full role name: gatcha:rarity:displayName
        String fullName = GATCHA_PREFIX + request.rarity() + ":" + request.name();

        // Parse hex color (e.g., "#FF5733" -> Color)
        java.awt.Color color = parseHexColor(request.colorHex());

        try {
            Role createdRole = guild.createRole()
                .setName(fullName)
                .setColor(color)
                .setMentionable(false)
                .setHoisted(false)
                .complete();

            logger.info("Created role {} in guild {}", fullName, guild.getName());
            // Notify connected clients so UIs refresh role list in real-time
            try {
                webSocketNotificationService.notifyRolesChanged(guildId, "created");
            } catch (Exception wsEx) {
                logger.warn("Failed to send websocket notification for created role {}: {}", fullName, wsEx.getMessage());
            }

            return mapRoleToDto(createdRole);
        } catch (Exception e) {
            logger.error("Failed to create role {} in guild {}: {}", fullName, guildId, e.getMessage());
            throw new RuntimeException("Failed to create role: " + e.getMessage(), e);
        }
    }

    /**
     * Create multiple gatcha roles from a list of requests
     * Skips roles that already exist (duplicate detection by full name)
     */
    public BulkRoleCreationResult createBulkGatchaRoles(String guildId, List<CreateRoleRequest> requests) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Guild not found: " + guildId);
        }

        List<GachaRoleInfo> createdRoles = new ArrayList<>();
        List<String> skippedRoles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (CreateRoleRequest request : requests) {
            try {
                // Build full role name to check for duplicates
                String fullName = GATCHA_PREFIX + request.rarity() + ":" + request.name();
                
                // Check if role already exists
                Role existingRole = guild.getRolesByName(fullName, true).stream().findFirst().orElse(null);
                
                if (existingRole != null) {
                    skippedRoles.add(request.name());
                    logger.debug("Skipped duplicate role '{}' in guild {}", fullName, guild.getName());
                    continue;
                }
                
                // Create new role
                GachaRoleInfo created = createGatchaRole(guildId, request);
                createdRoles.add(created);
            } catch (Exception e) {
                String errorMsg = String.format("Failed to create '%s': %s", request.name(), e.getMessage());
                errors.add(errorMsg);
                logger.warn(errorMsg);
            }
        }

        logger.info("Bulk role creation in guild {}: {} created, {} skipped, {} failed",
            guildId, createdRoles.size(), skippedRoles.size(), errors.size());

        BulkRoleCreationResult result = new BulkRoleCreationResult(
            createdRoles.size(),
            skippedRoles.size(),
            errors.size(),
            createdRoles,
            skippedRoles,
            errors
        );
        guildsCache.evictAll();
        
        // Notify connected clients to refresh role list
        if (createdRoles.size() > 0) {
            webSocketNotificationService.notifyRolesChanged(guildId, "created");
        }
        
        return result;
    }

    /**
     * Initialize default gatcha roles in a guild
     */
    public BulkRoleCreationResult initializeDefaultRoles(String guildId) {
        List<CreateRoleRequest> defaultRoles = List.of(
            // Legendary (2 roles)
            new CreateRoleRequest("Sunset Glow", "legendary", "#FF6B35"),
            new CreateRoleRequest("Midnight Purple", "legendary", "#7209B7"),

            // Epic (2 roles)
            new CreateRoleRequest("Ocean Dream", "epic", "#3A86FF"),
            new CreateRoleRequest("Forest Emerald", "epic", "#06A77D"),

            // Rare (2 roles)
            new CreateRoleRequest("Cherry Blossom", "rare", "#FF006E"),
            new CreateRoleRequest("Sky Blue", "rare", "#00B4D8"),

            // Uncommon (2 roles)
            new CreateRoleRequest("Lavender Mist", "uncommon", "#B5A2C8"),
            new CreateRoleRequest("Peach Sorbet", "uncommon", "#FFB5A7"),

            // Common (2 roles)
            new CreateRoleRequest("Mint Green", "common", "#98D8C8"),
            new CreateRoleRequest("Soft Pink", "common", "#FFB3D9")
        );

        logger.info("Initializing {} default roles in guild {}", defaultRoles.size(), guildId);
        BulkRoleCreationResult result = createBulkGatchaRoles(guildId, defaultRoles);
        guildsCache.evictAll();
        return result;
    }

    /**
     * Delete all roles matching a specific prefix (for cleaning up old/misnamed roles)
     */
    public int deleteRolesByPrefix(String guildId, String prefix) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Guild not found: " + guildId);
        }

        int deletedCount = 0;
        List<Role> rolesToDelete = new ArrayList<>();

        // Find all roles with the specified prefix
        for (Role role : guild.getRoles()) {
            if (role.getName().toLowerCase().startsWith(prefix.toLowerCase())) {
                rolesToDelete.add(role);
            }
        }

        // Delete each role
        for (Role role : rolesToDelete) {
            try {
                role.delete().complete();
                logger.info("Deleted role {} from guild {}", role.getName(), guild.getName());
                deletedCount++;
            } catch (Exception e) {
                logger.error("Failed to delete role {} from guild {}: {}",
                    role.getName(), guildId, e.getMessage());
            }
        }

        logger.info("Deleted {} roles with prefix '{}' from guild {}",
            deletedCount, prefix, guild.getName());
        return deletedCount;
    }

    /**
     * Clean up old "gacha:" roles (without 't') that don't match current naming convention
     */
    public int cleanupOldGachaRoles(String guildId) {
        return deleteRolesByPrefix(guildId, "gacha:");
    }

    /**
     * Evict the guilds cache for the current user's access token
     */
    public void evictGuildsCache(Authentication authentication) {
        String accessToken = getAccessToken(authentication);
        guildsCache.evictForToken(accessToken);
    }

    /**
     * Delete a single gacha role by ID
     */
    public RoleDeletionResult deleteGatchaRole(String guildId, String roleId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.error("Guild not found: {}", guildId);
            return new RoleDeletionResult(roleId, null, false, "Guild not found");
        }

        Role role = guild.getRoleById(roleId);
        if (role == null) {
            logger.warn("Role not found: {} in guild: {}", roleId, guildId);
            return new RoleDeletionResult(roleId, null, false, "Role not found");
        }

        // Only allow deletion of gacha roles
        if (!role.getName().startsWith("gacha:")) {
            logger.warn("Attempted to delete non-gacha role: {} in guild: {}", role.getName(), guildId);
            return new RoleDeletionResult(roleId, role.getName(), false, "Can only delete gacha roles");
        }

        String roleName = role.getName();
        try {
            // Use .complete() to wait for the deletion and catch errors synchronously
            role.delete().complete();
            logger.info("Deleted role: {} (ID: {}) from guild: {}", roleName, roleId, guild.getName());
            return new RoleDeletionResult(roleId, roleName, true, null);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            // Provide more specific error message for permission issues
            if (errorMsg != null && (errorMsg.contains("Missing Permissions") || errorMsg.contains("50013"))) {
                errorMsg = "Bot lacks permission (role hierarchy issue - bot's role must be above this role)";
            }
            logger.error("Failed to delete role: {} (ID: {}) from guild: {}: {}", roleName, roleId, guild.getName(), errorMsg, e);
            return new RoleDeletionResult(roleId, roleName, false, errorMsg);
        }
    }

    /**
     * Delete multiple gacha roles by their IDs
     */
    public BulkRoleDeletionResult deleteBulkGatchaRoles(String guildId, List<String> roleIds) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.error("Guild not found: {}", guildId);
            return new BulkRoleDeletionResult(0, roleIds.size(), List.of(), 
                List.of("Guild not found"));
        }

        List<RoleDeletionResult> deletedRoles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (String roleId : roleIds) {
            RoleDeletionResult result = deleteGatchaRole(guildId, roleId);
            
            if (result.success()) {
                successCount++;
                deletedRoles.add(result);
            } else {
                failureCount++;
                errors.add(String.format("Role %s: %s", roleId, result.error()));
            }
        }

        logger.info("Bulk delete complete for guild {}: {} succeeded, {} failed", 
            guild.getName(), successCount, failureCount);

        // Notify connected clients to refresh role list
        if (successCount > 0) {
            webSocketNotificationService.notifyRolesChanged(guildId, "deleted");
        }

        return new BulkRoleDeletionResult(successCount, failureCount, deletedRoles, errors);
    }

    // Helper methods

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getUserGuildsFromDiscord(String accessToken) {
        // Use Caffeine's get() with loader function to handle caching automatically
        return guildsCache.get(accessToken, key -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + key);

            try {
                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    DISCORD_API_BASE + "/users/@me/guilds",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    (Class<List<Map<String, Object>>>) (Class<?>) List.class
                );

                List<Map<String, Object>> guilds = response.getBody();
                if (guilds == null) {
                    logger.warn("Discord API returned null guilds list");
                    guilds = new ArrayList<>();
                }
                logger.info("Successfully fetched {} guilds from Discord API", guilds.size());
                return guilds;
            } catch (Exception e) {
                logger.error("Failed to fetch user guilds from Discord API - this may indicate an invalid/expired token or API issue", e);
                // Don't cache failures - let it retry on next request
                throw new RuntimeException("Discord API call failed", e);
            }
        });
    }

    private String getAccessToken(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User)) {
            return null;
        }

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String userId = oauth2User.getAttribute("id");

        if (userId == null) {
            logger.error("User ID not found in OAuth2User attributes");
            return null;
        }

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
            "discord",
            userId
        );

        if (client == null || client.getAccessToken() == null) {
            logger.error("OAuth2 authorized client not found for user: {}", userId);
            return null;
        }

        return client.getAccessToken().getTokenValue();
    }

    private GachaRoleInfo mapRoleToDto(Role role) {
        String fullName = role.getName();
        String afterPrefix = fullName.substring(GATCHA_PREFIX.length());
        String[] parts = afterPrefix.split(":");

        String rarity = null;
        String displayName = afterPrefix;

        if (parts.length >= 2) {
            rarity = parts[0];
            displayName = afterPrefix.substring(parts[0].length() + 1);
        }

        String colorHex = role.getColor() != null ?
            String.format("#%06X", role.getColor().getRGB() & 0xFFFFFF) : null;

        return new GachaRoleInfo(
            role.getId(),
            fullName,  // fullName instead of getName()
            displayName,
            rarity,
            colorHex,
            role.getPosition()
        );
    }

    /**
     * Parse hex color string to java.awt.Color
     */
    private java.awt.Color parseHexColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return java.awt.Color.WHITE;
        }

        // Remove # if present
        String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;

        try {
            int rgb = Integer.parseInt(hex, 16);
            return new java.awt.Color(rgb);
        } catch (NumberFormatException e) {
            logger.warn("Invalid hex color '{}', using white", hexColor);
            return java.awt.Color.WHITE;
        }
    }
}
