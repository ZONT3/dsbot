package ru.zont.dsbot.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Mentions;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static ru.zont.dsbot.util.StringsRG.STR;

public class Media extends RGSlashCommandAdapter {
    private final LiteJSON sources;
    private final LiteJSON notifications;
    private final List<MediaData> mediaDataList;

    public Media(ZDSBot bot, GuildContext context) {
        super(bot, context);
        if (context != null) {
            sources = context.getLJInstance("media");
            notifications = context.getLJInstance("media-notifications");
        } else {
            sources = null;
            notifications = null;
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

            case "notification-add" -> {
                final String link = event.getOption("link", OptionMapping::getAsString);
                final Mentions mentions = event.getOption("mentions", OptionMapping::getMentions);
                addNotification(link, mentions == null ? Collections.singletonList(event.getUser()) : Stream.concat(
                                mentions.getRoles().stream(),
                                mentions.getUsers().stream())
                        .toList());
                responseTarget.setOK();
            }
            case "notification-rm" -> {
                final String link = event.getOption("link", OptionMapping::getAsString);
                final Mentions mentions = event.getOption("mentions", OptionMapping::getMentions);
                rmNotification(link, mentions == null ? null : Stream.concat(
                                mentions.getRoles().stream(),
                                mentions.getUsers().stream())
                        .toList());
                responseTarget.setOK();
            }

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
            throw new DescribedException(STR.get("media.err.link"));
        }

        if (sourceName == null)
            throw new DescribedException(STR.get("media.err.unknown_source"));

        sources.addIfNotContains(link);
        responseTarget.respondEmbedLater(new EmbedBuilder()
                .setTitle(STR.get("media.add.success"))
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
                .setDescription(STR.get("common.err.rm"))
                .setColor(ResponseTarget.WARNING_COLOR)
                .build());
    }

    private void list(ResponseTarget responseTarget) {
        List<String> lines = sources.getList().parallelStream()
                .map(s -> {
                    String sourceName = getSourceTypeAndName(s);
                    if (sourceName != null) return "- %s %s".formatted(sourceName, s);
                    else return "~~%s~~".formatted(s);
                })
                .toList();
        responseTarget.respondEmbedsNow(MessageSplitter.embeds(String.join("\n", lines),
                new EmbedBuilder()
                        .setTitle(STR.get("media.list"))
                        .setColor(0x3030b0)
                        .build()));
    }

    private void addNotification(String link, List<IMentionable> mentions) {
        final JsonObject currentObj = notifications.get();
        final String currentStr = currentObj.has(link) ? currentObj.get(link).getAsString() : "";
        final Set<String> current = new HashSet<>(Set.of(currentStr.split(",\\s*")));
        current.addAll(mentions.stream().map(IMentionable::getAsMention).toList());
        notifications.op((Consumer<JsonObject>) o -> o.addProperty(link, String.join(", ", current)));
    }

    private void rmNotification(String link, @Nullable List<IMentionable> mentions) {
        final JsonObject currentObj = notifications.get();
        final String currentStr = currentObj.has(link) ? currentObj.get(link).getAsString() : "";
        final Set<String> current = new HashSet<>(Set.of(currentStr.split(",\\s*")));

        final String newStr;
        if (mentions != null) {
            mentions.stream().map(IMentionable::getAsMention).toList().forEach(current::remove);
            newStr = String.join(", ", current);
        } else newStr = "";

        if (newStr.isEmpty() && currentObj.has(link))
            notifications.op((Consumer<JsonObject>) o -> o.remove(link));
        else if (!newStr.isEmpty())
            notifications.op((Consumer<JsonObject>) o -> o.addProperty(link, newStr));
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
            throw new DescribedException(STR.get("media.err.unsupported_source"));

        throw new DescribedException(STR.get("media.err.link"));
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
        if (event.getSubcommandName() != null && event.getSubcommandName().startsWith("notification")) {
            if ("link".equals(event.getFocusedOption().getName()))
                autoCompleteSources(event);
            else {
                if (notifications == null)
                    event.replyChoices(Collections.emptyList()).complete();
                else
                    event.replyChoices(notifications.get().entrySet().stream()
                                    .filter(e -> e.getKey().equals(event.getOption("link", OptionMapping::getAsString)))
                                    .flatMap(e -> Arrays.stream(e.getValue().getAsString().split(",\\s*")))
                                    .map(str -> new Command.Choice(str, str))
                                    .limit(OptionData.MAX_CHOICES)
                                    .toList())
                            .complete();
            }
        } else autoCompleteSources(event);
    }

    private void autoCompleteSources(CommandAutoCompleteInteractionEvent event) {
        if (sources == null) {
            event.replyChoices(Collections.emptyList()).complete();
            return;
        }

        event.replyChoices(sources.getList().parallelStream()
                .map(s -> {
                    String sourceName = getSourceTypeAndName(s);
                    return new Command.Choice(sourceName != null ? sourceName : s, s);
                })
                .limit(OptionData.MAX_CHOICES)
                .toList()).complete();
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc()).addSubcommands(
                new SubcommandData("add", STR.get("media.add"))
                        .addOption(OptionType.STRING, "link", STR.get("media.opt.link"), true),
                new SubcommandData("rm", STR.get("media.rm"))
                        .addOption(OptionType.STRING, "link", STR.get("media.opt.link"), true, true),
                new SubcommandData("list", STR.get("media.list")),

                new SubcommandData("videos-set-channel", STR.get("media.videos.set_channel")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", STR.get("monitoring.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData("videos-disable", STR.get("media.videos.disable")),

                new SubcommandData("posts-set-channel", STR.get("media.posts.set_channel")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", STR.get("monitoring.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData("posts-disable", STR.get("media.posts.disable")),

                new SubcommandData("role", STR.get("media.push_role"))
                        .addOption(OptionType.ROLE, "role", STR.get("common.opt.role"), true),
                new SubcommandData("role-disable", STR.get("media.push_role.disable")),

                new SubcommandData("notification-add", STR.get("media.notification.add"))
                        .addOption(OptionType.STRING, "link", STR.get("media.opt.link"), true, true)
                        .addOption(OptionType.STRING, "mentions", STR.get("media.opt.notification.add.mentions")),
                new SubcommandData("notification-rm", STR.get("media.notification.rm"))
                        .addOption(OptionType.STRING, "link", STR.get("media.opt.link"), true, true)
                        .addOption(OptionType.STRING, "mentions", STR.get("media.opt.notification.rm.mentions"), false, true)
        );
    }

    @Override
    public String getName() {
        return "media";
    }

    @Override
    public String getShortDesc() {
        return STR.get("media.des.short");
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
