package ru.zont.dsbot.commands;

import com.google.gson.JsonElement;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.util.DescribedException;
import ru.zont.dsbot.core.util.LiteJSON;
import ru.zont.dsbot.core.util.MessageSplitter;
import ru.zont.dsbot.core.util.ResponseTarget;
import ru.zont.dsbot.media.MediaData;
import ru.zont.dsbot.media.TrovoData;
import ru.zont.dsbot.media.TwitchData;
import ru.zont.dsbot.media.YoutubeData;
import ru.zont.dsbot.util.CommandSupport;
import ru.zont.dsbot.util.RgPermissions;
import ru.zont.dsbot.util.StringsRG;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Media extends RGSlashCommandAdapter {
    private final LiteJSON sources;
    private final List<MediaData> mediaDataList;

    public Media(ZDSBot bot, GuildContext context) {
        super(bot, context);
        if (context != null) {
            sources = context.getLJInstance("media");
        } else {
            sources = null;
        }

        if (context != null) {
            ConfigRG.BotConfig cfg = getBotConfig();
            MediaData yt = GuildContext.getInstanceGlobal(YoutubeData.class, () -> YoutubeData.newInstance(cfg));
            MediaData ttv = GuildContext.getInstanceGlobal(TwitchData.class, () -> TwitchData.newInstance(cfg));
            MediaData trovo = GuildContext.getInstanceGlobal(TrovoData.class, () -> TrovoData.newInstance(cfg));
            mediaDataList = Stream.of(yt, ttv, trovo)
                    .filter(Objects::nonNull)
                    .toList();
        } else {
            mediaDataList = null;
        }
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        ConfigRG cfg = ConfigRG.castConfig(getConfig());
        ResponseTarget responseTarget = new ResponseTarget(event);
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case "add" -> add(event, responseTarget);
            case "rm" -> rm(event, responseTarget);
            case "list" -> list(responseTarget);

            case "videos-set-channel" -> {
                CommandSupport.setChannel(cfg.mediaVideoChannel, event);
                responseTarget.setOK();
            }
            case "videos-disable" -> {
                cfg.mediaVideoChannel.clearValue();
                responseTarget.setOK();
            }

            case "posts-set-channel" -> {
                CommandSupport.setChannel(cfg.mediaPostsChannel, event);
                responseTarget.setOK();
            }
            case "posts-disable" -> {
                cfg.mediaPostsChannel.clearValue();
                responseTarget.setOK();
            }
            case "role" -> {
                Role role = Objects.requireNonNull(event.getOption("role", OptionMapping::getAsRole));
                cfg.mediaPushingRole.setValue(role.getId());
            }
            case "role-disable" -> {
                cfg.mediaPushingRole.clearValue();
                responseTarget.setOK();
            }
        }
    }

    private void add(SlashCommandInteractionEvent event, ResponseTarget responseTarget) {
        final String link = event.getOption("link", OptionMapping::getAsString);
        if (link == null) throw new IllegalStateException();

        final MediaData source = getSource(link);
        final String sourceName;
        try {
            sourceName = getSourceName(link, source);
        } catch (IllegalArgumentException e) {
            throw new DescribedException(StringsRG.STR.get("media.err.link"));
        }

        if (sourceName == null)
            throw new DescribedException(StringsRG.STR.get("media.err.unknown_source"));

        sources.addIfNotContains(link);
        responseTarget.respondEmbedLater(new EmbedBuilder()
                        .setTitle(StringsRG.STR.get("media.add.success"))
                        .setDescription(sourceName)
                        .setThumbnail(source.getLogo())
                        .setColor(source.getColor())
                        .setFooter(source.getName())
                .build());
    }

    private void rm(SlashCommandInteractionEvent event, ResponseTarget responseTarget) {
        String link = event.getOption("link", OptionMapping::getAsString);
        if (link == null) throw new IllegalStateException();

        boolean success = sources.opList(list -> {
            List<JsonElement> found = StreamSupport.stream(list.spliterator(), false)
                    .filter(s -> link.equals(s.getAsString()))
                    .toList();
            found.forEach(list::remove);
            return found.size() > 0;
        });

        if (success)
            responseTarget.setOK();
        else responseTarget.respondEmbedNow(new EmbedBuilder()
                        .setDescription(StringsRG.STR.get("common.err.rm"))
                        .setColor(ResponseTarget.WARNING_COLOR)
                .build());
    }

    private void list(ResponseTarget responseTarget) {
        List<String> lines = sources.getList().parallelStream()
                .map(s -> {
                    String sourceName = getSourceTypeAndName(s);
                    if (sourceName != null) return sourceName;
                    else return "~~%s~~".formatted(s);
                })
                .toList();
        responseTarget.respondEmbedsNow(MessageSplitter.embeds(String.join("\n", lines),
                new EmbedBuilder()
                        .setTitle(StringsRG.STR.get("media.list"))
                        .setColor(0x3030b0)
                        .build()));
    }

    private void checkUrl(String url) {
        if (!url.matches("https?://.+?\\..+/.+"))
            throw new InvalidSyntaxException("Malformed URL");
    }

    private MediaData getSource(String link) {
        checkUrl(link);

        final Optional<MediaData> any = mediaDataList.stream()
                .filter(d -> d.linksHere(link))
                .findAny();

        if (any.isPresent())
            return any.get();

        if (link.contains("vk.com") || link.contains("t.me"))
            throw new DescribedException(StringsRG.STR.get("media.err.unsupported_source"));

        throw new DescribedException(StringsRG.STR.get("media.err.link"));
    }

    @Nullable
    private String getSourceName(@Nonnull String link, MediaData source) {
        Objects.requireNonNull(link);
        if (source == null)
            source = getSource(link);
        return source.getChannelTitle(link);
    }

    @Nullable
    private String getSourceTypeAndName(String link) {
        MediaData source;
        String sourceName;
        try {
            source = getSource(link);
            sourceName = getSourceName(link, source);
        } catch (Exception e) {
            source = null;
            sourceName = null;
        }
        if (source != null)
            return "[%s] %s".formatted(source.getName(), sourceName);
        return null;
    }

    @Override
    public void onSlashCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (sources == null) {
            event.replyChoices(Collections.emptyList()).complete();
            return;
        }

        event.replyChoices(sources.getList().parallelStream()
                .map(s -> {
                    String sourceName = getSourceTypeAndName(s);
                    return new Command.Choice(sourceName != null ? sourceName : s, s);
                })
                .toList()).complete();
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc()).addSubcommands(
                new SubcommandData("add", StringsRG.STR.get("media.add"))
                        .addOption(OptionType.STRING, "link", StringsRG.STR.get("media.opt.link"), true),
                new SubcommandData("rm", StringsRG.STR.get("media.rm"))
                        .addOption(OptionType.STRING, "link", StringsRG.STR.get("media.opt.link"), true, true),
                new SubcommandData("list", StringsRG.STR.get("media.list")),

                new SubcommandData("videos-set-channel", StringsRG.STR.get("media.videos.set_channel")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", StringsRG.STR.get("monitoring.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData("videos-disable", StringsRG.STR.get("media.videos.disable")),

                new SubcommandData("posts-set-channel", StringsRG.STR.get("media.posts.set_channel")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", StringsRG.STR.get("monitoring.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData("posts-disable", StringsRG.STR.get("media.posts.disable")),

                new SubcommandData("role", StringsRG.STR.get("media.push_role"))
                        .addOption(OptionType.ROLE, "role", StringsRG.STR.get("common.opt.role"), true),
                new SubcommandData("role-disable", StringsRG.STR.get("media.push_role.disable"))
        );
    }

    @Override
    public String getName() {
        return "media";
    }

    @Override
    public String getShortDesc() {
        return StringsRG.STR.get("media.des.short");
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public boolean checkPermission(RgPermissions util) {
        return util.permSetAdmin();
    }
}
