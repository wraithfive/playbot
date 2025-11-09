package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.Ability;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.AbilityRepository;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.AbilityService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.buttons.Button;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Handles interactive ability selection and learning via select menus and buttons.
 */
@Component
public class AbilityInteractionHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AbilityInteractionHandler.class);

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository characterRepository;
    private final CharacterAbilityRepository characterAbilityRepository;
    private final AbilityRepository abilityRepository;
    private final AbilityService abilityService;

    public AbilityInteractionHandler(BattleProperties battleProperties,
                                     PlayerCharacterRepository characterRepository,
                                     CharacterAbilityRepository characterAbilityRepository,
                                     AbilityRepository abilityRepository,
                                     AbilityService abilityService) {
        this.battleProperties = battleProperties;
        this.characterRepository = characterRepository;
        this.characterAbilityRepository = characterAbilityRepository;
        this.abilityRepository = abilityRepository;
        this.abilityService = abilityService;
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!battleProperties.isEnabled()) return;
        String id = event.getComponentId();
        if (!id.startsWith("abilities:")) return; // Not ours
        String[] parts = id.split(":");
        if (parts.length < 3) return;
        String userId = parts[1];
        String action = parts[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("❌ This ability panel isn't for you.").setEphemeral(true).queue();
            return;
        }
        if (event.getGuild() == null) {
            event.reply("❌ Must be used in a guild.").setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        PlayerCharacter pc = characterRepository.findByUserIdAndGuildId(userId, guildId).orElse(null);
        if (pc == null) {
            event.reply("❌ You need a character first.").setEphemeral(true).queue();
            return;
        }

        switch (action) {
            case "type" -> handleTypeFilter(event, pc); // value: TALENT/SKILL/SPELL/ALL
            case "avail" -> handleAbilityPreview(event, pc); // value: ability key
            default -> logger.debug("Unknown ability select action: {}", action);
        }
    }

    private void handleTypeFilter(StringSelectInteractionEvent event, PlayerCharacter pc) {
        String selected = (event.getValues().isEmpty() || event.getValues().get(0) == null || event.getValues().get(0).isBlank())
            ? "ALL"
            : event.getValues().get(0);
        String typeFilter = selected.toUpperCase(Locale.ROOT);
        List<Ability> available = abilityService.listAvailableForCharacter(pc).stream()
            .filter(a -> typeFilter.equals("ALL") || typeFilter.equals(a.getType()))
            .sorted((a,b)->a.getName().compareToIgnoreCase(b.getName()))
            .collect(Collectors.toList());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📜 Abilities");
        embed.setColor(Color.MAGENTA);
        embed.setDescription(String.format("Class: **%s** | Filter: **%s**", pc.getCharacterClass(), typeFilter));
        addLearnedAbilitiesField(embed, pc);
        embed.addField("Available (" + available.size() + ")", available.isEmpty()?"—":"Select below to preview", false);

        String userId = event.getUser().getId();
        StringSelectMenu typeMenu = StringSelectMenu.create("abilities:" + userId + ":type")
            .setPlaceholder("Filter by type…")
            .addOption("All", "ALL", "Show all types")
            .addOption("Talent", "TALENT")
            .addOption("Skill", "SKILL")
            .addOption("Spell", "SPELL")
            .build();
        StringSelectMenu availMenu = buildAvailableMenu(userId, available);

        var action = event.deferEdit();
        action.setEmbeds(embed.build());
        if (availMenu == null) {
            action.setComponents(ActionRow.of(typeMenu));
        } else {
            action.setComponents(ActionRow.of(typeMenu), ActionRow.of(availMenu));
        }
        action.queue();
    }

    private void handleAbilityPreview(StringSelectInteractionEvent event, PlayerCharacter pc) {
        String key = event.getValues().isEmpty() ? null : event.getValues().get(0);
        if (key == null) {
            event.reply("❌ Invalid selection.").setEphemeral(true).queue();
            return;
        }
        Ability ability = abilityRepository.findByKey(key).orElse(null);
        if (ability == null) {
            event.reply("❌ Ability not found.").setEphemeral(true).queue();
            return;
        }
        boolean learned = characterAbilityRepository.findByCharacter(pc).stream().anyMatch(ca->ca.getAbility().getId().equals(ability.getId()));
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔍 " + ability.getName());
        embed.setColor(learned ? Color.GREEN : Color.ORANGE);
        embed.setDescription(ability.getDescription());
        embed.addField("Key", ability.getKey(), true);
        embed.addField("Type", ability.getType(), true);
        if (ability.getClassRestriction()!=null) embed.addField("Class Restriction", ability.getClassRestriction(), true);
        embed.addField("Req Level", String.valueOf(ability.getRequiredLevel()), true);
        embed.addField("Prerequisites", ability.getPrerequisites().isBlank()?"—":ability.getPrerequisites(), false);
        embed.addField("Effect", ability.getEffect(), false);
        embed.setFooter(learned?"Already learned":"Click Learn to acquire this ability");

        String userId = event.getUser().getId();
        Button learnBtn = Button.primary("abilities:" + userId + ":learn:" + ability.getKey(), "Learn")
            .withDisabled(learned);
        Button backBtn = Button.secondary("abilities:" + userId + ":back", "← Back to List");
        
        event.deferEdit()
            .setEmbeds(embed.build())
            .setComponents(ActionRow.of(backBtn, learnBtn))
            .queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!battleProperties.isEnabled()) return;
        String id = event.getComponentId();
        if (!id.startsWith("abilities:")) return;
        String[] parts = id.split(":");
        if (parts.length < 3) return;
        String userId = parts[1];
        String action = parts[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("❌ Not your ability panel.").setEphemeral(true).queue();
            return;
        }
        if (event.getGuild()==null) {
            event.reply("❌ Must be in a guild.").setEphemeral(true).queue();
            return;
        }
        
        String guildId = event.getGuild().getId();
        PlayerCharacter pc = characterRepository.findByUserIdAndGuildId(userId, guildId).orElse(null);
        if (pc == null) {
            event.reply("❌ You need a character first.").setEphemeral(true).queue();
            return;
        }
        
        if ("back".equals(action)) {
            refreshAbilityList(event, pc, "ALL");
        } else if ("learn".equals(action) && parts.length >= 4) {
            String key = parts[3];
            handleLearnAbility(event, pc, key);
        }
    }

    private void handleLearnAbility(ButtonInteractionEvent event, PlayerCharacter pc, String key) {
        try {
            abilityService.learnAbility(event.getGuild().getId(), event.getUser().getId(), key);
            // Refresh the list showing the updated state
            refreshAbilityList(event, pc, "ALL");
        } catch (IllegalStateException ise) {
            event.reply("❌ " + ise.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Ability learn failed", e);
            event.reply("❌ " + "Failed to learn ability.").setEphemeral(true).queue();
        }
    }
    
    private void refreshAbilityList(ButtonInteractionEvent event, PlayerCharacter pc, String typeFilter) {
        List<Ability> available = abilityService.listAvailableForCharacter(pc).stream()
            .filter(a -> typeFilter.equals("ALL") || typeFilter.equals(a.getType()))
            .sorted((a,b)->a.getName().compareToIgnoreCase(b.getName()))
            .collect(Collectors.toList());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📜 Abilities");
        embed.setColor(Color.MAGENTA);
        embed.setDescription(String.format("Class: **%s**", pc.getCharacterClass()));
        addLearnedAbilitiesField(embed, pc);
        embed.addField("Available (" + available.size() + ")", available.isEmpty()?"—":"Select below to preview", false);

        String userId = event.getUser().getId();
        StringSelectMenu typeMenu = StringSelectMenu.create("abilities:" + userId + ":type")
            .setPlaceholder("Filter by type…")
            .addOption("All", "ALL", "Show all types")
            .addOption("Talent", "TALENT")
            .addOption("Skill", "SKILL")
            .addOption("Spell", "SPELL")
            .build();
        StringSelectMenu availMenu = buildAvailableMenu(userId, available);

        var action = event.deferEdit();
        action.setEmbeds(embed.build());
        if (availMenu == null) {
            action.setComponents(ActionRow.of(typeMenu));
        } else {
            action.setComponents(ActionRow.of(typeMenu), ActionRow.of(availMenu));
        }
        action.queue();
    }

    /**
     * Helper method to add the learned abilities field to an embed.
     * Extracts duplicate logic for retrieving and formatting learned abilities.
     */
    private void addLearnedAbilitiesField(EmbedBuilder embed, PlayerCharacter pc) {
        List<Ability> learned = getLearnedAbilities(pc);
        String learnedDisplay = learned.isEmpty()
            ? "—"
            : learned.stream()
                .limit(10)
                .map(Ability::getName)
                .collect(Collectors.joining(", "));
        embed.addField("Learned (" + learned.size() + ")", learnedDisplay, false);
    }

    /**
     * Returns the list of abilities the character has already learned.
     */
    private List<Ability> getLearnedAbilities(PlayerCharacter pc) {
        return characterAbilityRepository.findByCharacter(pc).stream()
            .map(com.discordbot.battle.entity.CharacterAbility::getAbility)
            .toList();
    }

    private StringSelectMenu buildAvailableMenu(String userId, List<Ability> available) {
        if (available.isEmpty()) return null;
        StringSelectMenu.Builder b = StringSelectMenu.create("abilities:" + userId + ":avail")
            .setPlaceholder("Select an ability to preview…");
        available.stream().limit(25).forEach(a -> {
            String desc = (a.getClassRestriction() != null ? a.getClassRestriction() + " · " : "") + a.getType();
            b.addOption(a.getName(), a.getKey(), desc);
        });
        return b.build();
    }
}
