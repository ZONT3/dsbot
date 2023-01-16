package ru.zont.dsbot.commands;

import com.google.gson.JsonArray;
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
import ru.zont.dsbot.media.TrovoData;
import ru.zont.dsbot.media.TwitchData;
import ru.zont.dsbot.media.YoutubeData;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.PermissionsUtil;
import ru.zont.dsbot.core.commands.SlashCommandAdapter;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.util.DescribedException;
import ru.zont.dsbot.core.util.LiteJSON;
import ru.zont.dsbot.core.util.MessageSplitter;
import ru.zont.dsbot.core.util.ResponseTarget;
import ru.zont.dsbot.util.CommandSupport;
import ru.zont.dsbot.util.StringsRG;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class Media extends SlashCommandAdapter {
    public static final String SOURCE_YOUTUBE = "YouTube";
    public static final String SOURCE_TWITCH = "Twitch";
    public static final String SOURCE_VK = "VK";
    public static final String SOURCE_TROVO = "trovo";

    public static final HashMap<String, String> SOURCE_IMAGES = new HashMap<>(){{
        put(SOURCE_YOUTUBE, YoutubeData.LOGO);
        put(SOURCE_TWITCH, TwitchData.LOGO);
        put(SOURCE_TROVO, TrovoData.LOGO);
    }};

    public static final HashMap<String, Integer> SOURCE_COLORS = new HashMap<>(){{
        put(SOURCE_YOUTUBE, YoutubeData.COLOR);
        put(SOURCE_TWITCH, TwitchData.COLOR);
        put(SOURCE_TROVO, TrovoData.COLOR);
    }};

    private final LiteJSON sources;
    private final YoutubeData yt;
    private final TwitchData ttv;
    private final TrovoData trovo;

    public Media(ZDSBot bot, GuildContext context) {
        super(bot, context);
        if (context != null) {
            sources = context.getLJInstance("media");
        } else {
            sources = null;
        }

        if (context != null) {
            ConfigRG.BotConfig cfg = getBotConfig();
            yt = GuildContext.getInstanceGlobal(YoutubeData.class, () -> YoutubeData.newInstance(cfg));
            ttv = GuildContext.getInstanceGlobal(TwitchData.class, () -> TwitchData.newInstance(cfg));
            trovo = GuildContext.getInstanceGlobal(TrovoData.class, () -> TrovoData.newInstance(cfg));
        } else {
            yt = null;
            ttv = null;
            trovo = null;
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

        final String sourceType = getSourceType(link);
        final String sourceName;
        try {
            sourceName = getSourceName(link, sourceType);
        } catch (IllegalArgumentException e) {
            throw new DescribedException(StringsRG.STR.get("media.err.link"));
        }

        if (sourceName == null)
            throw new DescribedException(StringsRG.STR.get("media.err.unknown_source"));

        sources.opList((Consumer<JsonArray>) l -> l.add(link));
        responseTarget.respondEmbedLater(new EmbedBuilder()
                        .setTitle(StringsRG.STR.get("media.add.success"))
                        .setDescription(sourceName)
                        .setThumbnail(SOURCE_IMAGES.getOrDefault(sourceType, null))
                        .setColor(SOURCE_COLORS.get(sourceType))
                        .setFooter(sourceType)
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

    private String getSourceType(String link) {
        checkUrl(link);
        link = link.toLowerCase();
        if (yt.linksHere(link))
            return SOURCE_YOUTUBE;
        if (ttv.linksHere(link))
            return SOURCE_TWITCH;
        if (trovo.linksHere(link))
            return SOURCE_TROVO;
        if (link.contains("vk.com") || link.contains("t.me"))
            throw new DescribedException(StringsRG.STR.get("media.err.unsupported_source"));
        throw new DescribedException(StringsRG.STR.get("media.err.link"));
    }

    @Nullable
    private String getSourceName(@Nonnull String link, String sourceType) {
        Objects.requireNonNull(link);
        if (sourceType == null)
            sourceType = getSourceType(link);
        switch (sourceType) {
            case SOURCE_YOUTUBE -> {
                return yt.getName(link);
            }
            case SOURCE_TWITCH -> {
                return ttv.getName(link);
            }
            case SOURCE_TROVO -> {
                return trovo.getName(link);
            }
        }
        throw new IllegalStateException();
    }

    @Nullable
    private String getSourceTypeAndName(String link) {
        String sourceType;
        String sourceName;
        try {
            sourceType = getSourceType(link);
            sourceName = getSourceName(link, sourceType);
        } catch (Exception e) {
            sourceType = null;
            sourceName = null;
        }
        if (sourceType != null)
            return "[%s] %s".formatted(sourceType, sourceName);
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
    public boolean checkPermission(PermissionsUtil util) {
        return util.permSetAdmin();
    }
}
