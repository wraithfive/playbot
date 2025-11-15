package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.Ability;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.AbilityService;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.Comparator;
import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Handles the /abilities command to view learned and available abilities.
 */
@Component
public class AbilitiesCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbilitiesCommandHandler.class);

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository characterRepository;
    private final CharacterAbilityRepository characterAbilityRepository;
    private final AbilityService abilityService;

    public AbilitiesCommandHandler(BattleProperties battleProperties,
                                   PlayerCharacterRepository characterRepository,
                                   CharacterAbilityRepository characterAbilityRepository,
                                   AbilityService abilityService) {
        this.battleProperties = battleProperties;
        this.characterRepository = characterRepository;
        this.characterAbilityRepository = characterAbilityRepository;
        this.abilityService = abilityService;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "abilities".equals(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            var guild = event.getGuild();
            if (guild == null) {
                event.reply("❌ This command must be used in a server (guild).")
                    .setEphemeral(true).queue();
                return;
            }
            String guildId = guild.getId();
            String userId = event.getUser().getId();

            var characterOpt = characterRepository.findByUserIdAndGuildId(userId, guildId);
            if (characterOpt.isEmpty()) {
                event.reply("❌ You need to create a character first with /create-character.")
                    .setEphemeral(true)
                    .queue();
                return;
            }
            PlayerCharacter pc = characterOpt.get();

            // Defensive option parsing: trim and normalize, avoid literal "null" strings
            final String filter = Optional.ofNullable(event.getOption("filter"))
                .map(opt -> opt.getAsString())
                .map(v -> v == null ? "" : v.trim().toLowerCase(Locale.ROOT))
                .orElse("");
            final String typeFilter = Optional.ofNullable(event.getOption("type"))
                .map(opt -> opt.getAsString())
                .map(v -> v == null ? "" : v.trim().toUpperCase(Locale.ROOT))
                .orElse("");

            // Learned - eagerly fetch abilities to avoid LazyInitializationException
            var learnedLinks = characterAbilityRepository.findByCharacter(pc);
            List<Ability> learned = learnedLinks.stream()
                .map(l -> l.getAbility())
                .sorted(Comparator.comparing(Ability::getType).thenComparing(Ability::getName))
                .collect(Collectors.toList());

            // Available
            List<Ability> available = abilityService.listAvailableForCharacter(pc).stream()
                .sorted(Comparator.comparing(Ability::getType).thenComparing(Ability::getName))
                .collect(Collectors.toList());

            if (!typeFilter.isEmpty()) {
                learned = learned.stream().filter(a -> typeFilter.equalsIgnoreCase(a.getType())).toList();
                available = available.stream().filter(a -> typeFilter.equalsIgnoreCase(a.getType())).toList();
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("📜 Abilities");
            embed.setColor(Color.MAGENTA);
            embed.setDescription(String.format("Class: **%s**", pc.getCharacterClass()));

            // Summaries in fields
            if (!"available".equals(filter)) {
                embed.addField("Learned (" + learned.size() + ")", summarizeNames(learned), false);
            }
            if (!"learned".equals(filter)) {
                embed.addField("Available (" + available.size() + ")", available.isEmpty() ? "—" : "Use the selector below to preview and learn.", false);
            }

            // Build interactive components
            // Use a distinct variable name to avoid accidental shadowing
            String requesterUserId = event.getUser().getId();
            StringSelectMenu typeMenu = StringSelectMenu.create("abilities:" + requesterUserId + ":type")
                .setPlaceholder("Filter by type…")
                .addOption("All", "ALL", "Show all types")
                .addOption("Talent", "TALENT")
                .addOption("Skill", "SKILL")
                .addOption("Spell", "SPELL")
                .build();

            StringSelectMenu availMenu = buildAvailableMenu(requesterUserId, available);

            var reply = event.replyEmbeds(embed.build())
                .setEphemeral(true);
            if (availMenu == null) {
                reply.addComponents(ActionRow.of(typeMenu));
            } else {
                reply.addComponents(ActionRow.of(typeMenu), ActionRow.of(availMenu));
            }
            reply.queue();
        } catch (Exception e) {
            logger.error("/abilities failed", e);
            event.reply("❌ Failed to list abilities.").setEphemeral(true).queue();
        }
    }

    private String summarizeNames(List<Ability> abilities) {
        if (abilities.isEmpty()) return "—";
        return abilities.stream().limit(10).map(Ability::getName).collect(Collectors.joining(", "))
            + (abilities.size() > 10 ? String.format(" …and %d more", abilities.size() - 10) : "");
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
