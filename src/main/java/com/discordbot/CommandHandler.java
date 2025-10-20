package com.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;

public class CommandHandler extends ListenerAdapter {

    private static final String PREFIX = "!";

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("Bot is ready!");
        System.out.println("Logged in as: " + event.getJDA().getSelfUser().getName());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw();

        // Check if message starts with prefix
        if (!message.startsWith(PREFIX)) {
            return;
        }

        // Parse command and arguments
        String[] parts = message.substring(PREFIX.length()).split("\\s+");
        String command = parts[0].toLowerCase();

        // Handle commands
        switch (command) {
            case "help":
                handleHelp(event);
                break;

            case "ping":
                handlePing(event);
                break;

            case "hello":
                handleHello(event);
                break;

            case "serverinfo":
                handleServerInfo(event);
                break;

            case "userinfo":
                handleUserInfo(event);
                break;

            default:
                event.getChannel().sendMessage("Unknown command. Type `!help` for a list of commands.").queue();
        }
    }

    private void handleHelp(MessageReceivedEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Bot Commands");
        embed.setColor(Color.BLUE);
        embed.setDescription("Here are all available commands:");
        embed.addField("!help", "Shows this help message", false);
        embed.addField("!ping", "Check if the bot is responsive", false);
        embed.addField("!hello", "Get a friendly greeting", false);
        embed.addField("!serverinfo", "Get information about the server", false);
        embed.addField("!userinfo", "Get information about yourself", false);
        embed.setTimestamp(Instant.now());
        embed.setFooter("Bot made with JDA");

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void handlePing(MessageReceivedEvent event) {
        long gatewayPing = event.getJDA().getGatewayPing();
        event.getChannel().sendMessage("Pong! Gateway ping: " + gatewayPing + "ms").queue();
    }

    private void handleHello(MessageReceivedEvent event) {
        String username = event.getAuthor().getName();
        event.getChannel().sendMessage("Hello, " + username + "! How can I help you today?").queue();
    }

    private void handleServerInfo(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("This command can only be used in a server!").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Server Information");
        embed.setColor(Color.GREEN);
        embed.addField("Server Name", event.getGuild().getName(), true);
        embed.addField("Members", String.valueOf(event.getGuild().getMemberCount()), true);
        embed.addField("Owner", event.getGuild().getOwner().getUser().getName(), true);
        embed.addField("Created", "<t:" + event.getGuild().getTimeCreated().toEpochSecond() + ":F>", false);
        embed.setThumbnail(event.getGuild().getIconUrl());
        embed.setTimestamp(Instant.now());

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleUserInfo(MessageReceivedEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("User Information");
        embed.setColor(Color.CYAN);
        embed.addField("Username", event.getAuthor().getName(), true);
        embed.addField("ID", event.getAuthor().getId(), true);
        embed.addField("Account Created", "<t:" + event.getAuthor().getTimeCreated().toEpochSecond() + ":F>", false);
        embed.setThumbnail(event.getAuthor().getAvatarUrl());
        embed.setTimestamp(Instant.now());

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
}
