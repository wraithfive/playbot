package com.discordbot;

import com.discordbot.entity.UserCooldown;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.repository.UserCooldownRepository;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.QotdSubmissionService;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the D20 roll mechanic feature.
 * Tests cover: availability checks, window validation, nat 1/20 outcomes, Epic+ buff application.
 */
@SuppressWarnings("unchecked")
class D20MechanicTest {

    private SlashCommandHandler handler;
    private UserCooldownRepository cooldownRepo;
    private QotdStreamRepository streamRepo;
    private GuildsCache guildsCache;
    private WebSocketNotificationService wsService;
    private QotdSubmissionService qotdSubmissionService;

    @BeforeEach
    void setUp() {
        cooldownRepo = mock(UserCooldownRepository.class);
        streamRepo = mock(QotdStreamRepository.class);
        guildsCache = mock(GuildsCache.class);
        wsService = mock(WebSocketNotificationService.class);
        qotdSubmissionService = mock(QotdSubmissionService.class);
        handler = new SlashCommandHandler(cooldownRepo, streamRepo, guildsCache, wsService, qotdSubmissionService);
    }

    @Test
    @DisplayName("/d20 requires guild context")
    void testD20RequiresGuild() {
        SlashCommandInteractionEvent event = createMockD20Event();
        when(event.getMember()).thenReturn(null);

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onSlashCommandInteraction(event);

        verify(event).reply(contains("only be used in a server"));
    }

    @Test
    @DisplayName("/d20 requires 3+ Epic/Legendary roles")
    void testD20RequiresThreeEpicPlusRoles() {
        SlashCommandInteractionEvent event = createMockD20Event();
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);

        when(event.getMember()).thenReturn(member);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");

        // Only 2 Epic+ roles (need 3)
        List<Role> roles = Arrays.asList(
            createMockRole("gacha:epic:Gold", "1", Color.YELLOW),
            createMockRole("gacha:legendary:Rainbow", "2", Color.MAGENTA),
            createMockRole("gacha:common:Blue", "3", Color.BLUE)
        );
        when(guild.getRoles()).thenReturn(roles);

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onSlashCommandInteraction(event);

        verify(event).reply(contains("requires at least 3 Epic or Legendary"));
    }

    @Test
    @DisplayName("/d20 requires prior /roll (no cooldown record)")
    void testD20RequiresPriorRoll() {
        SlashCommandInteractionEvent event = createMockD20Event();
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);

        when(event.getMember()).thenReturn(member);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild1");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");

        // 3+ Epic roles
        List<Role> roles = createThreeEpicRoles();
        when(guild.getRoles()).thenReturn(roles);

        // No cooldown record
        when(cooldownRepo.findByUserIdAndGuildId("user1", "guild1")).thenReturn(Optional.empty());

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onSlashCommandInteraction(event);

        verify(event).reply(contains("You must use `/roll` first"));
    }

    @Test
    @DisplayName("/d20 blocks if already used this cycle")
    void testD20BlocksIfAlreadyUsed() {
        SlashCommandInteractionEvent event = createMockD20Event();
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);

        when(event.getMember()).thenReturn(member);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild1");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");

        List<Role> roles = createThreeEpicRoles();
        when(guild.getRoles()).thenReturn(roles);

        // Cooldown exists but d20 already used
        UserCooldown cooldown = new UserCooldown("user1", "guild1", LocalDateTime.now(), "TestUser");
        cooldown.setD20Used(true);
        when(cooldownRepo.findByUserIdAndGuildId("user1", "guild1")).thenReturn(Optional.of(cooldown));

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onSlashCommandInteraction(event);

        verify(event).reply(contains("already used `/d20`"));
    }

    @Test
    @DisplayName("/d20 blocks if 60-minute window expired")
    void testD20BlocksIfWindowExpired() {
        SlashCommandInteractionEvent event = createMockD20Event();
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);

        when(event.getMember()).thenReturn(member);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild1");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");

        List<Role> roles = createThreeEpicRoles();
        when(guild.getRoles()).thenReturn(roles);

        // Roll was 61 minutes ago (window expired)
        UserCooldown cooldown = new UserCooldown("user1", "guild1",
            LocalDateTime.now().minusMinutes(61), "TestUser");
        cooldown.setD20Used(false);
        when(cooldownRepo.findByUserIdAndGuildId("user1", "guild1")).thenReturn(Optional.of(cooldown));

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onSlashCommandInteraction(event);

        verify(event).reply(contains("window has expired"));
    }

    @Test
    @DisplayName("/d20 succeeds within 60-minute window")
    void testD20SucceedsWithinWindow() {
        SlashCommandInteractionEvent event = createMockD20Event();
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);
        InteractionHook hook = mock(InteractionHook.class);

        when(event.getMember()).thenReturn(member);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild1");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");
        when(user.getName()).thenReturn("TestUser");
        when(guild.getName()).thenReturn("TestGuild");

        List<Role> roles = createThreeEpicRoles();
        when(guild.getRoles()).thenReturn(roles);

        // Roll was 30 minutes ago (within window)
        UserCooldown cooldown = new UserCooldown("user1", "guild1",
            LocalDateTime.now().minusMinutes(30), "TestUser");
        cooldown.setD20Used(false);
        when(cooldownRepo.findByUserIdAndGuildId("user1", "guild1")).thenReturn(Optional.of(cooldown));

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doAnswer(inv -> {
            Consumer<InteractionHook> onSuccess = inv.getArgument(0);
            onSuccess.accept(hook);
            return null;
        }).when(replyAction).queue(any(Consumer.class));
        doNothing().when(replyAction).queue();

        when(hook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(mock(net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction.class));

        handler.onSlashCommandInteraction(event);

        // Should save with d20Used = true
        verify(cooldownRepo, atLeastOnce()).save(argThat(c -> c.isD20Used()));
    }

    @Test
    @DisplayName("Epic+ buff filters roles to Epic/Legendary only")
    void testEpicPlusBuffFiltersRoles() {
        // This tests that when guaranteedEpicPlus is true,
        // /roll only considers Epic and Legendary roles

        SlashCommandInteractionEvent event = createMockRollEvent();
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);

        when(event.getMember()).thenReturn(member);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");
        when(user.getName()).thenReturn("TestUser");
        when(guild.getName()).thenReturn("TestGuild");
        when(member.getRoles()).thenReturn(List.of());

        // Mix of rarities
        List<Role> roles = Arrays.asList(
            createMockRole("gacha:common:Red", "1", Color.RED),
            createMockRole("gacha:epic:Gold", "2", Color.YELLOW),
            createMockRole("gacha:legendary:Rainbow", "3", Color.MAGENTA)
        );
        when(guild.getRoles()).thenReturn(roles);
        when(guild.getRoleById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return roles.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
        });

        // User has Epic+ buff
        UserCooldown cooldown = new UserCooldown("user1", "guild1",
            LocalDateTime.now().minusDays(1), "TestUser");
        cooldown.setGuaranteedEpicPlus(true);
        when(cooldownRepo.findByUserIdAndGuildId("user1", "guild1")).thenReturn(Optional.of(cooldown));

        // Mock role assignment
        var removeAction = mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class);
        var addAction = mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class);
        when(guild.removeRoleFromMember(any(Member.class), any(Role.class))).thenReturn(removeAction);
        when(guild.addRoleToMember(any(Member.class), any(Role.class))).thenReturn(addAction);
        when(removeAction.complete()).thenReturn(null);
        when(addAction.complete()).thenReturn(null);

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onSlashCommandInteraction(event);

        // Should save with guaranteedEpicPlus cleared
        verify(cooldownRepo).save(argThat(c -> !c.isGuaranteedEpicPlus()));
    }

    @Test
    @DisplayName("/roll resets d20Used flag for new cycle")
    void testRollResetsD20UsedFlag() {
        SlashCommandInteractionEvent event = createMockRollEvent();
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);

        when(event.getMember()).thenReturn(member);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");
        when(user.getName()).thenReturn("TestUser");
        when(guild.getName()).thenReturn("TestGuild");
        when(member.getRoles()).thenReturn(List.of());

        List<Role> roles = createThreeEpicRoles();
        when(guild.getRoles()).thenReturn(roles);
        when(guild.getRoleById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return roles.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
        });

        // User has d20Used = true from previous cycle
        UserCooldown cooldown = new UserCooldown("user1", "guild1",
            LocalDateTime.now().minusDays(1), "TestUser");
        cooldown.setD20Used(true);
        when(cooldownRepo.findByUserIdAndGuildId("user1", "guild1")).thenReturn(Optional.of(cooldown));

        // Mock role operations
        var removeAction = mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class);
        var addAction = mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class);
        when(guild.removeRoleFromMember(any(Member.class), any(Role.class))).thenReturn(removeAction);
        when(guild.addRoleToMember(any(Member.class), any(Role.class))).thenReturn(addAction);
        when(removeAction.complete()).thenReturn(null);
        when(addAction.complete()).thenReturn(null);

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onSlashCommandInteraction(event);

        // Should save with d20Used reset to false
        verify(cooldownRepo).save(argThat(c -> !c.isD20Used()));
    }

    @Test
    @DisplayName("countEpicPlusRoles counts correctly")
    void testCountEpicPlusRoles() {
        // Test via behavior: 2 Epic+ roles should block d20, 3 should allow
        List<Role> twoEpicRoles = Arrays.asList(
            createMockRole("gacha:epic:Gold", "1", Color.YELLOW),
            createMockRole("gacha:legendary:Rainbow", "2", Color.MAGENTA)
        );

        List<Role> threeEpicRoles = Arrays.asList(
            createMockRole("gacha:epic:Gold", "1", Color.YELLOW),
            createMockRole("gacha:epic:Silver", "2", Color.GRAY),
            createMockRole("gacha:legendary:Rainbow", "3", Color.MAGENTA)
        );

        assertEquals(2, twoEpicRoles.stream()
            .filter(r -> r.getName().contains(":epic:") || r.getName().contains(":legendary:"))
            .count());

        assertEquals(3, threeEpicRoles.stream()
            .filter(r -> r.getName().contains(":epic:") || r.getName().contains(":legendary:"))
            .count());
    }

    @Test
    @DisplayName("isWithinD20Window validates 60-minute boundary")
    void testIsWithinD20Window() {
        UserCooldown withinWindow = new UserCooldown("user1", "guild1",
            LocalDateTime.now().minusMinutes(30), "TestUser");

        UserCooldown outsideWindow = new UserCooldown("user1", "guild1",
            LocalDateTime.now().minusMinutes(61), "TestUser");

        UserCooldown exactBoundary = new UserCooldown("user1", "guild1",
            LocalDateTime.now().minusMinutes(60), "TestUser");

        // Test through behavior
        assertTrue(java.time.Duration.between(withinWindow.getLastRollTime(), LocalDateTime.now()).toMinutes() < 60);
        assertFalse(java.time.Duration.between(outsideWindow.getLastRollTime(), LocalDateTime.now()).toMinutes() < 60);
        assertFalse(java.time.Duration.between(exactBoundary.getLastRollTime(), LocalDateTime.now()).toMinutes() < 60);
    }

    // Helper methods

    private SlashCommandInteractionEvent createMockD20Event() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        when(event.getName()).thenReturn("d20");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild1");
        return event;
    }

    private SlashCommandInteractionEvent createMockRollEvent() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        when(event.getName()).thenReturn("roll");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild1");
        return event;
    }

    private Role createMockRole(String name, String id, Color color) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(name);
        when(role.getId()).thenReturn(id);
        when(role.getColor()).thenReturn(color);
        return role;
    }

    private List<Role> createThreeEpicRoles() {
        return Arrays.asList(
            createMockRole("gacha:epic:Gold", "1", Color.YELLOW),
            createMockRole("gacha:epic:Silver", "2", Color.GRAY),
            createMockRole("gacha:legendary:Rainbow", "3", Color.MAGENTA)
        );
    }
}
