package ru.zont.dsbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.Input;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.commands.exceptions.OnlySlashUsageException;
import ru.zont.dsbot.core.util.DescribedException;
import ru.zont.dsbot.core.util.ResponseTarget;
import ru.zont.dsbot.util.RgPermissions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static ru.zont.dsbot.util.StringsRG.STR;

public class Say extends RGSlashCommandAdapter {
    public static final String TIMESTAMP_FORMAT = "dd.MM.yyyy HH:mm:ss";
    public static final List<String> TARGET_OPTS = List.of("target", "target-user", "target-id");
    public static final List<String> NOT_EMBED_OPTS = Stream
            .concat(TARGET_OPTS.stream(), Stream.of("message", "reply-to"))
            .toList();
    public static final int CHOICES_LIMIT = OptionData.MAX_CHOICES;

    public Say(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public void onCall(ResponseTarget replyTo, Input input, MessageReceivedEvent event, Object... params) {
        throw new OnlySlashUsageException();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        final String messageStr = event.getOption("message", OptionMapping::getAsString);
        final List<OptionMapping> options = event.getOptions();
        assert messageStr != null;

        if (options.stream().map(OptionMapping::getName).noneMatch(TARGET_OPTS::contains))
            throw InvalidSyntaxException.insufficientArgs(STR.get("say.err.target"), this);

        final OptionMapping idOpt = event.getOption("target-id");
        final MessageChannel channel;
        final JDA jda = getBot().getJda();
        if (idOpt != null) {
            final String id = idOpt.getAsString();
            final GuildChannel guildChannel = jda.getGuildChannelById(id);
            if (guildChannel instanceof MessageChannel c) {
                channel = c;
            } else {
                final User user = jda.getUserById(id);
                if (user != null)
                    channel = user.openPrivateChannel().complete();
                else throw new InvalidSyntaxException(STR.get("say.err.target_id"));
            }
        } else {
            final User user = event.getOption("target-user", OptionMapping::getAsUser);
            if (user == null)
                channel = event.getOption("target", OptionMapping::getAsMessageChannel);
            else channel = user.openPrivateChannel().complete();
        }

        if (channel == null)
            throw new RuntimeException("Failed to get channel after all tries");

        final List<String> optional = options
                .stream()
                .map(OptionMapping::getName)
                .filter(Predicate.not(NOT_EMBED_OPTS::contains))
                .toList();

        Message message;
        Message replyTo = null;

        final String messageId = event.getOption("reply-to", OptionMapping::getAsString);
        if (messageId != null) {
            try {
                replyTo = channel.retrieveMessageById(messageId.replaceAll("\\d+-", ""))
                        .complete();
            } catch (Exception ignored) { }
        }

        if (optional.size() < 1) {
            message = new MessageBuilder().append(messageStr).build();
        } else {
            try {
                final EmbedBuilder builder = new EmbedBuilder().setDescription(messageStr);
                for (String name : optional) {
                    String option = event.getOption(name, OptionMapping::getAsString);
                    assert option != null;
                    switch (name) {
                        case "color" -> {
                            String substring = option.substring(option.length() - 6);
                            if (!substring.matches("[\\da-fA-F]{6}"))
                                throw new InvalidSyntaxException("Invalid color");
                            builder.setColor(Integer.parseInt(substring, 16));
                        }
                        case "title" -> builder.setTitle(option);
                        case "title-link" ->
                                builder.setTitle(event.getOption("title", OptionMapping::getAsString), option);
                        case "image" -> builder.setImage(option);
                        case "small-img" -> builder.setThumbnail(option);
                        case "author-img", "author-link", "author-name" -> builder.setAuthor(
                                event.getOption("author-name", OptionMapping::getAsString),
                                event.getOption("author-link", OptionMapping::getAsString),
                                event.getOption("author-img", OptionMapping::getAsString));
                        case "footer" -> builder.setFooter(option);
                        case "timestamp" -> builder.setTimestamp(new SimpleDateFormat(TIMESTAMP_FORMAT)
                                .parse(option).toInstant().atZone(ZoneId.of("GMT+3")));
                    }
                }

                message = new MessageBuilder().setEmbeds(builder.build()).build();
            } catch (IllegalArgumentException e) {
                throw new DescribedException(
                        STR.get("say.err.build.title"),
                        STR.get("say.err.build",
                                MessageEmbed.EMBED_MAX_LENGTH_BOT,
                                MessageEmbed.TITLE_MAX_LENGTH,
                                MessageEmbed.DESCRIPTION_MAX_LENGTH,
                                e.getMessage()));
            } catch (ParseException e) {
                throw new DescribedException(STR.get("say.err.date"), e.getMessage());
            }
        }

        if (replyTo != null)
            replyTo.reply(message).queue();
        else
            channel.sendMessage(message).queue();

        new ResponseTarget(event).setOK();
    }

    @Override
    public void onSlashCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
        final String query = event.getFocusedOption().getValue().toLowerCase();
        if (query.length() < 2) {
            event.replyChoices(Collections.emptyList()).complete();
            return;
        }

        final List<GuildChannel> channels = getBot().getJda().getGuilds().parallelStream()
                .flatMap(guild -> guild.getChannels().stream()
                        .filter(ch -> List.of(
                                        ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_NEWS_THREAD,
                                        ChannelType.GUILD_PRIVATE_THREAD, ChannelType.GUILD_PUBLIC_THREAD)
                                .contains(ch.getType()))
                        .filter(ch -> ch.getName().toLowerCase().contains(query))
                        .limit(CHOICES_LIMIT))
                .limit(CHOICES_LIMIT)
                .toList();

        final List<Command.Choice> choices;
        final Stream<Command.Choice> channelStream = channels
                .stream()
                .map(ch -> new Command.Choice("%2$s (%1$s)".formatted(ch.getGuild().getName(), ch.getName()), ch.getId()));

        if (channels.size() < CHOICES_LIMIT) {
            Stream<Command.Choice> userStream = getBot().getJda().getUsers().parallelStream()
                    .filter(u -> u.getName().toLowerCase().contains(query))
                    .map(u -> new Command.Choice(u.getAsTag(), u.getId()));
            choices = Stream.concat(channelStream, userStream).limit(CHOICES_LIMIT).toList();
        } else choices = channelStream.toList();

        event.replyChoices(choices).complete();
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc()).addOptions(
                new OptionData(OptionType.STRING, "message", STR.get("say.opt.message"), true),
                new OptionData(OptionType.CHANNEL, "target", STR.get("say.opt.target"))
                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_NEWS_THREAD,
                                ChannelType.GUILD_PRIVATE_THREAD, ChannelType.GUILD_PUBLIC_THREAD),
                new OptionData(OptionType.USER, "target-user", STR.get("say.opt.target_user")),
                new OptionData(OptionType.STRING, "target-id", STR.get("say.opt.target_id"), false, true),
                new OptionData(OptionType.STRING, "color", STR.get("say.opt.color")),
                new OptionData(OptionType.STRING, "reply-to", STR.get("say.opt.reply_to")),
                new OptionData(OptionType.STRING, "title", STR.get("say.opt.title")),
                new OptionData(OptionType.STRING, "title-link", STR.get("say.opt.title_link")),
                new OptionData(OptionType.STRING, "image", STR.get("say.opt.image")),
                new OptionData(OptionType.STRING, "small-img", STR.get("say.opt.small_img")),
                new OptionData(OptionType.STRING, "author-name", STR.get("say.opt.author_name")),
                new OptionData(OptionType.STRING, "author-link", STR.get("say.opt.author_link")),
                new OptionData(OptionType.STRING, "author-img", STR.get("say.opt.author_img")),
                new OptionData(OptionType.STRING, "footer", STR.get("say.opt.footer")),
                new OptionData(OptionType.STRING, "timestamp", STR.get("say.opt.timestamp", TIMESTAMP_FORMAT))
        );
    }

    @Override
    public boolean checkPermission(RgPermissions util) {
        return util.permSetAdminFromMain();
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public String getName() {
        return "say";
    }

    @Override
    public String getShortDesc() {
        return "Secret command for elite";
    }
}
