package ru.zont.dsbot.listeners;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.media.*;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.listeners.WatcherAdapter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Stream;

public class MediaWatcher extends WatcherAdapter {
    private static final Logger log = LoggerFactory.getLogger(MediaWatcher.class);
    @Deprecated
    private static final List<LocalTime> updatePoints = List.of(
            LocalTime.of(15, 5),
            LocalTime.of(18, 5),
            LocalTime.of(22, 5));

    private final List<MediaData> mediaDataList;

    private final HashSet<String> sourcesList = new HashSet<>();

    public MediaWatcher(ZDSBot bot, GuildContext context) {
        super(bot, context);

        if (context == null) {
            ConfigRG.BotConfig cfg = getBotConfig();
            MediaData yt = GuildContext.getInstanceGlobal(YoutubeData.class, () -> YoutubeData.newInstance(cfg));
            MediaData ttv = GuildContext.getInstanceGlobal(TwitchData.class, () -> TwitchData.newInstance(cfg));
            MediaData trovo = GuildContext.getInstanceGlobal(TrovoData.class, () -> TrovoData.newInstance(cfg));
            mediaDataList = Stream.of(yt, ttv, trovo)
                    .filter(Objects::nonNull).toList();
            log.info("Initialized media: {}", mediaDataList.stream().map(MediaData::getName).toList());
        } else {
            mediaDataList = null;
        }
    }

    @Override
    public long getPeriod() {
        return 60;
    }

    @Override
    public void update() {
        sourcesList.clear();
        onEachGuild(context -> {
            ConfigRG cfg = context.getConfig();
            if (!isPostsEnabled(context, cfg)) return;
            sourcesList.addAll(getMediaSources(context));
        });
        if (sourcesList.size() == 0) return;

        HashMap<String, List<MessageEmbed>> newPosts = new HashMap<>();

        for (MediaData m : mediaDataList) {
            List<String> thisSources = sourcesList.stream().filter(m::linksHere).toList();
            if (thisSources.size() > 0 && m.shouldUpdate()) {
                thisSources.forEach(s -> newPosts.put(s, m.getNewPosts(s)));
                m.scheduleNextUpdate();
            }
        }

        onEachGuild(context -> {
            ConfigRG cfg = context.getConfig();
            MessageChannel mediaChannel = getMediaChannel(context, cfg);
            if (mediaChannel == null) return;
            List<String> mediaSources = getMediaSources(context);

            List<MessageEmbed> posts = newPosts.keySet().stream()
                    .filter(mediaSources::contains)
                    .flatMap(link -> newPosts.get(link).stream())
                    .toList();
            post(cfg, mediaChannel, posts);
        });
    }

    private void post(ConfigRG cfg, MessageChannel channel, List<MessageEmbed> posts) {
        String roleId = cfg.mediaPushingRole.getString();
        for (int i = 0; i < posts.size(); i += 10) {
            List<MessageEmbed> embeds = posts.subList(i, Math.min(i + 10, posts.size()));
            if (roleId != null) {
                channel.sendMessage(new MessageBuilder()
                        .append("<@&%s>".formatted(roleId))
                        .setEmbeds(embeds)
                        .build()).queue();
            } else channel.sendMessageEmbeds(embeds).queue();
        }
    }

    private List<String> getMediaSources(GuildContext context) {
        return context.getLJInstance("media").getList();
    }

    private MessageChannel getMediaChannel(GuildContext context, ConfigRG cfg) {
        return context.findChannel(cfg.mediaVideoChannel.getString());
    }

    private boolean isPostsEnabled(GuildContext context, ConfigRG cfg) {
        return getMediaChannel(context, cfg) != null;
    }

    @SuppressWarnings("unused")
    @Deprecated
    private LocalDateTime scheduleNextSlowYtUpdate() {
        LocalTime now = LocalTime.now();
        Optional<LocalTime> nextPointOptional = updatePoints.stream()
                .filter(now::isBefore)
                .findFirst();
        boolean nextDay = nextPointOptional.isEmpty();
        LocalTime nextPoint = nextPointOptional.orElse(updatePoints.get(0));
        return LocalDateTime.now()
                .withHour(nextPoint.getHour())
                .withMinute(nextPoint.getMinute())
                .plusDays(nextDay ? 1 : 0);
    }

    @Override
    public boolean allowGlobal() {
        return true;
    }

    @Override
    public List<String> getAllowedGuilds() {
        return null;
    }
}
