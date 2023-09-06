package ru.zont.dsbot.listeners;

import com.google.gson.JsonObject;
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

import java.util.*;
import java.util.stream.Stream;

public class MediaWatcher extends WatcherAdapter {
    private static final Logger log = LoggerFactory.getLogger(MediaWatcher.class);

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
            try {
                List<String> thisSources = sourcesList.stream().filter(m::linksHere).toList();
                if (thisSources.size() > 0 && m.shouldUpdate()) {
                    m.doUpdate(thisSources);
                    thisSources.forEach(s -> newPosts.put(s, m.getNewPosts(s)));
                    m.scheduleNextUpdate();
                }
            } catch (Exception e) {
                log.error("MediaData {} has thrown an error during update", m.getName(), e);
            }
        }

        onEachGuild(context -> {
            ConfigRG cfg = context.getConfig();
            MessageChannel mediaChannel = getMediaChannel(context, cfg);
            if (mediaChannel == null) return;
            List<String> mediaSources = getMediaSources(context);
            final JsonObject notifications = getMediaNotifications(context);

            for (String link: newPosts.keySet()) {
                if (!mediaSources.contains(link))
                    continue;
                post(cfg, mediaChannel, newPosts.get(link),
                        notifications.has(link) ? notifications.get(link).getAsString() : "");
            }
        });
    }

    private void post(ConfigRG cfg, MessageChannel channel, List<MessageEmbed> posts, String messageText) {
        String roleId = cfg.mediaPushingRole.getString();
        if (roleId != null)
            messageText = String.join(messageText.isEmpty() ? "" : ", ", "<@&%s>".formatted(roleId), messageText);

        for (int i = 0; i < posts.size(); i += 10) {
            List<MessageEmbed> embeds = posts.subList(i, Math.min(i + 10, posts.size()));
            final MessageBuilder messageBuilder = new MessageBuilder()
                    .setEmbeds(embeds);
            if (!messageText.isEmpty())
                messageBuilder.append(messageText);
            channel.sendMessage(messageBuilder.build()).queue();
        }
    }

    private List<String> getMediaSources(GuildContext context) {
        return context.getLJInstance("media").getList();
    }

    private JsonObject getMediaNotifications(GuildContext context) {
        return context.getLJInstance("media-notifications").get();
    }

    private MessageChannel getMediaChannel(GuildContext context, ConfigRG cfg) {
        return context.findChannel(cfg.mediaVideoChannel.getString());
    }

    private boolean isPostsEnabled(GuildContext context, ConfigRG cfg) {
        return getMediaChannel(context, cfg) != null;
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
