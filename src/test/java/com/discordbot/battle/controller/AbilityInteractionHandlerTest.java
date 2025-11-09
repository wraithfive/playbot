package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.Ability;
import com.discordbot.battle.entity.CharacterAbility;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.AbilityRepository;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.AbilityService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AbilityInteractionHandlerTest {

    private AbilityInteractionHandler handler;
    private BattleProperties battleProperties;
    private PlayerCharacterRepository characterRepository;
    private CharacterAbilityRepository characterAbilityRepository;
    private AbilityRepository abilityRepository;
    private AbilityService abilityService;

    private PlayerCharacter character;
    private Ability ability1;
    private Ability ability2;

    @BeforeEach
    void setup() {
        battleProperties = new BattleProperties();
        battleProperties.setEnabled(true);

        characterRepository = mock(PlayerCharacterRepository.class);
        characterAbilityRepository = mock(CharacterAbilityRepository.class);
        abilityRepository = mock(AbilityRepository.class);
        abilityService = mock(AbilityService.class);

        handler = new AbilityInteractionHandler(
            battleProperties,
            characterRepository,
            characterAbilityRepository,
            abilityRepository,
            abilityService
        );

        character = new PlayerCharacter("u1", "g1", "Warrior", "Human", 15, 14, 13, 12, 10, 8);
        ability1 = new Ability("power-strike", "Power Strike", "SKILL", "Warrior", 1, "", "DAMAGE+3", "Deal extra damage");
        ability2 = new Ability("battle-focus", "Battle Focus", "TALENT", null, 1, "", "CRIT_CHANCE+5", "Increases crit chance");
    }

    @Test
    void handleTypeFilter_updatesEmbed() {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        MessageEditCallbackAction updateAction = mock(MessageEditCallbackAction.class);

        when(event.getComponentId()).thenReturn("abilities:u1:type");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u1");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("g1");
        when(event.getValues()).thenReturn(List.of("SKILL"));

        when(characterRepository.findByUserIdAndGuildId("u1", "g1")).thenReturn(Optional.of(character));
        when(characterAbilityRepository.findByCharacter(character)).thenReturn(List.of());
        when(abilityService.listAvailableForCharacter(character)).thenReturn(List.of(ability1, ability2));

        when(event.deferEdit()).thenReturn(updateAction);
        when(updateAction.setEmbeds(any(MessageEmbed.class))).thenReturn(updateAction);
        when(updateAction.setComponents(any(ActionRow.class), any(ActionRow.class))).thenReturn(updateAction);
        when(updateAction.setComponents(any(ActionRow.class))).thenReturn(updateAction);
        doNothing().when(updateAction).queue();

        handler.onStringSelectInteraction(event);

        verify(event).deferEdit();
        verify(updateAction).setEmbeds(any(MessageEmbed.class));
        verify(updateAction).queue();
    }

    @Test
    void handleAbilityPreview_showsDetailedEmbed() {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        MessageEditCallbackAction updateAction = mock(MessageEditCallbackAction.class);

        when(event.getComponentId()).thenReturn("abilities:u1:avail");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u1");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("g1");
        when(event.getValues()).thenReturn(List.of("power-strike"));

        when(characterRepository.findByUserIdAndGuildId("u1", "g1")).thenReturn(Optional.of(character));
        when(abilityRepository.findByKey("power-strike")).thenReturn(Optional.of(ability1));
        when(characterAbilityRepository.findByCharacter(character)).thenReturn(List.of());

        when(event.deferEdit()).thenReturn(updateAction);
        when(updateAction.setEmbeds(any(MessageEmbed.class))).thenReturn(updateAction);
        when(updateAction.setComponents(any(ActionRow.class))).thenReturn(updateAction);
        doNothing().when(updateAction).queue();

        handler.onStringSelectInteraction(event);

        verify(event).deferEdit();
        verify(updateAction).setEmbeds(any(MessageEmbed.class));
        verify(updateAction).setComponents(any(ActionRow.class));
        verify(updateAction).queue();
    }

    @Test
    void handleBackButton_returnsToList() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        MessageEditCallbackAction updateAction = mock(MessageEditCallbackAction.class);

        when(event.getComponentId()).thenReturn("abilities:u1:back");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u1");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("g1");

        when(characterRepository.findByUserIdAndGuildId("u1", "g1")).thenReturn(Optional.of(character));
        when(characterAbilityRepository.findByCharacter(character)).thenReturn(List.of());
        when(abilityService.listAvailableForCharacter(character)).thenReturn(List.of(ability1));

        when(event.deferEdit()).thenReturn(updateAction);
        when(updateAction.setEmbeds(any(MessageEmbed.class))).thenReturn(updateAction);
        when(updateAction.setComponents(any(ActionRow.class), any(ActionRow.class))).thenReturn(updateAction);
        when(updateAction.setComponents(any(ActionRow.class))).thenReturn(updateAction);
        doNothing().when(updateAction).queue();

        handler.onButtonInteraction(event);

        verify(event).deferEdit();
        verify(updateAction).setEmbeds(any(MessageEmbed.class));
        verify(updateAction).queue();
    }

    @Test
    void handleLearnButton_learnsAbilityAndRefreshes() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        MessageEditCallbackAction updateAction = mock(MessageEditCallbackAction.class);
        CharacterAbility link = new CharacterAbility(character, ability1);

        when(event.getComponentId()).thenReturn("abilities:u1:learn:power-strike");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u1");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("g1");

        when(characterRepository.findByUserIdAndGuildId("u1", "g1")).thenReturn(Optional.of(character));
        when(abilityService.learnAbility("g1", "u1", "power-strike")).thenReturn(link);
        when(characterAbilityRepository.findByCharacter(character)).thenReturn(List.of(link));
        when(abilityService.listAvailableForCharacter(character)).thenReturn(List.of(ability2));

        when(event.deferEdit()).thenReturn(updateAction);
        when(updateAction.setEmbeds(any(MessageEmbed.class))).thenReturn(updateAction);
        when(updateAction.setComponents(any(ActionRow.class), any(ActionRow.class))).thenReturn(updateAction);
        when(updateAction.setComponents(any(ActionRow.class))).thenReturn(updateAction);
        doNothing().when(updateAction).queue();

        handler.onButtonInteraction(event);

        verify(abilityService).learnAbility("g1", "u1", "power-strike");
        verify(event).deferEdit();
        verify(updateAction).setEmbeds(any(MessageEmbed.class));
        verify(updateAction).queue();
    }

    @Test
    void handleLearnButton_showsErrorOnFailure() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        var replyAction = mock(net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction.class);

        when(event.getComponentId()).thenReturn("abilities:u1:learn:power-strike");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u1");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("g1");

        when(characterRepository.findByUserIdAndGuildId("u1", "g1")).thenReturn(Optional.of(character));
        when(abilityService.learnAbility("g1", "u1", "power-strike"))
            .thenThrow(new IllegalStateException("Already learned"));

        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onButtonInteraction(event);

        verify(event).reply(contains("Already learned"));
    }

    @Test
    void rejectsInteractionFromWrongUser() {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        User user = mock(User.class);
        var replyAction = mock(net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction.class);

        when(event.getComponentId()).thenReturn("abilities:u1:type");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u2"); // Different user

        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        handler.onStringSelectInteraction(event);

        verify(event).reply(contains("isn't for you"));
    }

    @Test
    void handleAbilityPreview_learnedAbility_showsGreenEmbed() {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        MessageEditCallbackAction updateAction = mock(MessageEditCallbackAction.class);

        when(event.getComponentId()).thenReturn("abilities:u1:avail");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u1");
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("g1");
        when(event.getValues()).thenReturn(List.of("power-strike"));

        // Character has already learned Power Strike
        // Use reflection to set the ability ID since there's no setter
        ability1 = new Ability("power-strike", "Power Strike", "SKILL", "Warrior", 1, "", "DAMAGE+3", "Deal extra damage");
        try {
            var idField = Ability.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(ability1, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CharacterAbility link = new CharacterAbility(character, ability1);
        when(characterRepository.findByUserIdAndGuildId("u1", "g1")).thenReturn(Optional.of(character));
        when(abilityRepository.findByKey("power-strike")).thenReturn(Optional.of(ability1));
        when(characterAbilityRepository.findByCharacter(character)).thenReturn(List.of(link));

        when(event.deferEdit()).thenReturn(updateAction);
        when(updateAction.setEmbeds(any(MessageEmbed.class))).thenReturn(updateAction);
        when(updateAction.setComponents(any(ActionRow.class))).thenReturn(updateAction);
        doNothing().when(updateAction).queue();

        handler.onStringSelectInteraction(event);

        verify(event).deferEdit();
        // Capture embed to verify color and disabled learn button
        var embedCaptor = org.mockito.ArgumentCaptor.forClass(MessageEmbed.class);
        verify(updateAction).setEmbeds(embedCaptor.capture());
        MessageEmbed embed = embedCaptor.getValue();
        assertEquals(java.awt.Color.GREEN.getRGB(), embed.getColorRaw(), "Learned ability preview should have green color");
        
        // Capture ActionRow to verify learn button is disabled
        var rowCaptor = org.mockito.ArgumentCaptor.forClass(ActionRow.class);
        verify(updateAction).setComponents(rowCaptor.capture());
        var row = rowCaptor.getValue();
        var buttons = row.getComponents().stream()
            .filter(c -> c instanceof net.dv8tion.jda.api.components.buttons.Button)
            .map(c -> (net.dv8tion.jda.api.components.buttons.Button) c)
            .toList();
        // Find learn button by checking if it has "Learn" label
        var learnButton = buttons.stream()
            .filter(b -> "Learn".equals(b.getLabel()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected learn button in ActionRow"));
        assertTrue(learnButton.isDisabled(), "Learn button should be disabled for already learned ability");
    }
}
