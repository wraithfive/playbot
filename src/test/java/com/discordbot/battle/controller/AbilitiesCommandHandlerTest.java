package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.Ability;
import com.discordbot.battle.entity.CharacterAbility;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.AbilityService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AbilitiesCommandHandlerTest {

    private AbilitiesCommandHandler handler;
    private PlayerCharacterRepository pcRepo;
    private CharacterAbilityRepository charAbilityRepo;
    private AbilityService abilityService;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;
    private BattleProperties battleProperties;

    private PlayerCharacter character;

    @BeforeEach
    void setup() {
        battleProperties = new BattleProperties();
        battleProperties.setEnabled(true);
        // Set required class configs
        battleProperties.getClassConfig().getWarrior().setBaseHp(10);
        battleProperties.getClassConfig().getRogue().setBaseHp(8);
        battleProperties.getClassConfig().getMage().setBaseHp(6);
        battleProperties.getClassConfig().getCleric().setBaseHp(8);

        pcRepo = mock(PlayerCharacterRepository.class);
        charAbilityRepo = mock(CharacterAbilityRepository.class);
        abilityService = mock(AbilityService.class);
        handler = new AbilitiesCommandHandler(battleProperties, pcRepo, charAbilityRepo, abilityService);

        event = mock(SlashCommandInteractionEvent.class);
        replyAction = mock(ReplyCallbackAction.class);

        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        when(event.getUser()).thenReturn(user);
        when(event.getGuild()).thenReturn(guild);
        when(user.getId()).thenReturn("u1");
        when(guild.getId()).thenReturn("g1");
    when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
    when(replyAction.addComponents(any(ActionRow.class))).thenReturn(replyAction);
    when(replyAction.addComponents(any(ActionRow.class), any(ActionRow.class))).thenReturn(replyAction); // varargs (2 rows)
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        character = new PlayerCharacter("u1", "g1", "Warrior", "Human", 15, 14, 13, 12, 10, 8);
        when(pcRepo.findByUserIdAndGuildId("u1", "g1")).thenReturn(Optional.of(character));
    }

    @Test
    void canHandle_returnsTrueWhenEnabled() {
        assertTrue(handler.canHandle("abilities"));
    }

    @Test
    void handle_listsLearnedAndAvailable() {
        Ability a1 = new Ability("power-strike", "Power Strike", "SKILL", "Warrior", 1, "", "DAMAGE+3", "Deal extra damage");
        Ability a2 = new Ability("battle-focus", "Battle Focus", "TALENT", null, 1, "", "CRIT_CHANCE+5", "Increases crit chance");
        CharacterAbility ca = new CharacterAbility(character, a1);

        when(charAbilityRepo.findByCharacter(character)).thenReturn(List.of(ca));
        when(abilityService.listAvailableForCharacter(character)).thenReturn(List.of(a2));

        handler.handle(event);

        verify(event).replyEmbeds(any(MessageEmbed.class));
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    void handle_noCharacter_showsError() {
        when(pcRepo.findByUserIdAndGuildId("u1", "g1")).thenReturn(Optional.empty());
        var replyStrAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyStrAction);
        when(replyStrAction.setEphemeral(anyBoolean())).thenReturn(replyStrAction);

        handler.handle(event);

        verify(event).reply(contains("create a character"));
    }

    @Test
    void handle_typeFilterApplied() {
        Ability a1 = new Ability("power-strike", "Power Strike", "SKILL", "Warrior", 1, "", "DAMAGE+3", "Deal extra damage");
        Ability a2 = new Ability("battle-focus", "Battle Focus", "TALENT", null, 1, "", "CRIT_CHANCE+5", "Increases crit chance");
        CharacterAbility ca = new CharacterAbility(character, a1);
        when(charAbilityRepo.findByCharacter(character)).thenReturn(List.of(ca));
        when(abilityService.listAvailableForCharacter(character)).thenReturn(List.of(a2));

        OptionMapping typeOption = mock(OptionMapping.class);
        when(typeOption.getAsString()).thenReturn("SKILL");
        when(event.getOption("type")).thenReturn(typeOption);

        handler.handle(event);

        verify(event).replyEmbeds(any(MessageEmbed.class));
    }

    @Test
    void handle_typeAndClassFiltering_combined() {
        // Learned abilities: one SKILL restricted to Warrior, one TALENT unrestricted
        Ability learnedSkillWarrior = new Ability("whirlwind", "Whirlwind", "SKILL", "Warrior", 1, "", "DAMAGE+4", "Spin attack");
        Ability learnedTalentGlobal = new Ability("battle-focus", "Battle Focus", "TALENT", null, 1, "", "CRIT_CHANCE+5", "Increases crit chance");
        CharacterAbility link1 = new CharacterAbility(character, learnedSkillWarrior);
        CharacterAbility link2 = new CharacterAbility(character, learnedTalentGlobal);
        when(charAbilityRepo.findByCharacter(character)).thenReturn(List.of(link1, link2));

        // Available abilities: mix of matching/non-matching class & type
        Ability availSkillWarrior = new Ability("riposte", "Riposte", "SKILL", "Warrior", 1, "", "DAMAGE+2", "Counter attack");
    Ability availTalentGlobal = new Ability("grit", "Grit", "TALENT", null, 1, "", "HP+2", "Slightly tougher");
    // AbilityService is responsible for class filtering; handler further filters by type only
    when(abilityService.listAvailableForCharacter(character)).thenReturn(List.of(availSkillWarrior, availTalentGlobal));

        // Apply SKILL type filter
        OptionMapping typeOption = mock(OptionMapping.class);
        when(typeOption.getAsString()).thenReturn("SKILL");
        when(event.getOption("type")).thenReturn(typeOption);

        handler.handle(event);

        // Capture embed and components to assert filtering logic
        var embedCaptor = org.mockito.ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        MessageEmbed embed = embedCaptor.getValue();

        // Learned field should include only SKILL learned ability(s) (Whirlwind) not talent
        var learnedFieldOpt = embed.getFields().stream().filter(f -> f.getName().startsWith("Learned")).findFirst();
        assertTrue(learnedFieldOpt.isPresent(), "Expected Learned field to be present");
        String learnedValue = learnedFieldOpt.get().getValue();
        assertTrue(learnedValue.contains("Whirlwind"));
        assertFalse(learnedValue.contains("Battle Focus"));

        // Capture action rows and inspect available menu options
        var rowCaptor = org.mockito.ArgumentCaptor.forClass(ActionRow.class);
        verify(replyAction).addComponents(rowCaptor.capture(), rowCaptor.capture());
        var rows = rowCaptor.getAllValues();
        assertEquals(2, rows.size(), "Expected two action rows (type filter + available menu)");
        var secondRow = rows.get(1);
        var components = secondRow.getComponents();
        assertFalse(components.isEmpty(), "Second row should contain the available select menu");
        var menu = components.get(0);
        assertTrue(menu instanceof net.dv8tion.jda.api.components.selections.StringSelectMenu, "Expected StringSelectMenu component");
        var select = (net.dv8tion.jda.api.components.selections.StringSelectMenu) menu;
        var optionLabels = select.getOptions().stream().map(o -> o.getLabel()).toList();
        assertTrue(optionLabels.contains("Riposte"), "Available should include Riposte (matching class+type)");
        assertFalse(optionLabels.contains("Arcane Bolt"), "Available should exclude Arcane Bolt (wrong class)");
        assertFalse(optionLabels.contains("Grit"), "Available should exclude Grit (different type)");
    }

    @Test
    void handle_learnedAbilitiesNotInAvailable() {
        // Learned: Power Strike
        Ability powerStrike = new Ability("power-strike", "Power Strike", "SKILL", "Warrior", 1, "", "DAMAGE+3", "Deal extra damage");
        CharacterAbility ca = new CharacterAbility(character, powerStrike);
        when(charAbilityRepo.findByCharacter(character)).thenReturn(List.of(ca));

        // Available: AbilityService already filters out learned abilities, so mock returns only unlearned
        Ability battleFocus = new Ability("battle-focus", "Battle Focus", "TALENT", null, 1, "", "CRIT_CHANCE+5", "Increases crit chance");
        when(abilityService.listAvailableForCharacter(character)).thenReturn(List.of(battleFocus));

        handler.handle(event);

        // Capture embed to assert learned is present in learned section, not available
        var embedCaptor = org.mockito.ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        MessageEmbed embed = embedCaptor.getValue();

        var learnedFieldOpt = embed.getFields().stream().filter(f -> f.getName().startsWith("Learned")).findFirst();
        assertTrue(learnedFieldOpt.isPresent());
        String learnedValue = learnedFieldOpt.get().getValue();
        assertTrue(learnedValue.contains("Power Strike"), "Learned should include Power Strike");

        // Capture action rows and inspect available menu options
        var rowCaptor = org.mockito.ArgumentCaptor.forClass(ActionRow.class);
        verify(replyAction).addComponents(rowCaptor.capture(), rowCaptor.capture());
        var rows = rowCaptor.getAllValues();
        assertEquals(2, rows.size());
        var secondRow = rows.get(1);
        var menu = (net.dv8tion.jda.api.components.selections.StringSelectMenu) secondRow.getComponents().get(0);
        var optionLabels = menu.getOptions().stream().map(o -> o.getLabel()).toList();
        assertFalse(optionLabels.contains("Power Strike"), "Available menu should not include already learned Power Strike");
        assertTrue(optionLabels.contains("Battle Focus"), "Available menu should include Battle Focus");
    }
}