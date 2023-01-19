package ru.zont.dsbot.media;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.Video;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.core.util.Strings;
import ru.zont.dsbot.util.StringsRG;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeData extends MediaDataImpl<YoutubeData.VideoData> {
    private static final Logger log = LoggerFactory.getLogger(YoutubeData.class);

    private static final List<LocalTime> updatePoints = List.of(
            LocalTime.of(15, 5),
            LocalTime.of(18, 5),
            LocalTime.of(22, 5));

    public static final Pattern LINK_PATTERN = Pattern.compile("https?://(?:\\w+\\.)?youtube\\.com/channel/([\\w-]+)(?:\\?.*)?(?:/.*)?");
    public static final String LOGO = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/YouTube_full-color_icon_%282017%29.svg/1024px-YouTube_full-color_icon_%282017%29.svg.png";
    public static final String VIDEO_FORMAT = "https://youtube.com/watch?v=%s";
    public static final String CHANNEL_FORMAT = "https://www.youtube.com/channel/%s";
    public static final int COLOR = 0xFF0202;

    private static final HashMap<String, String> link2id = new HashMap<>();
    private static final HashMap<String, Channel> id2channel = new HashMap<>();
    private static LocalDateTime nextCacheUpdate = null;

    private final YouTube api;
    private final String key;


    public static YoutubeData newInstance(ConfigRG.BotConfig cfg) {
        final String key = cfg.googleKey.getString();
        if (key == null) return null;
        return new YoutubeData(key);
    }

    private YoutubeData(String key) {
        super(10000);
        if (key == null) throw new IllegalArgumentException("Google key not stated");
        this.key = key;

        try {
            api = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), null)
                    .setApplicationName("ds-bot")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void scheduleNextCacheUpdate() {
        LocalTime now = LocalTime.now();
        Optional<LocalTime> nextPointOptional = updatePoints.stream()
                .filter(now::isBefore)
                .findFirst();
        boolean nextDay = nextPointOptional.isEmpty();
        LocalTime nextPoint = nextPointOptional.orElse(updatePoints.get(0));
        nextCacheUpdate = LocalDateTime.now()
                .withHour(nextPoint.getHour())
                .withMinute(nextPoint.getMinute())
                .plusDays(nextDay ? 1 : 0);
    }

    @Override
    public boolean linksHere(String link) {
        try {
            getChannelId(link);
            return true;
        } catch (Exception ignored) { }
        return false;
    }

    private static String getChannelId(String link) {
        if (!link.contains("youtu"))
            throw new IllegalArgumentException("Not a YouTube channel link");

        String cached = link2id.getOrDefault(link, null);
        if (cached != null) {
            if (cached.isBlank())
                throw new IllegalArgumentException("Not a YouTube channel link");
            return cached;
        }

        Matcher matcher = LINK_PATTERN.matcher(link);
        if (!matcher.find()) {
            String nLink = findCanonicalLink(link);
            matcher = null;
            if (nLink != null) {
                matcher = LINK_PATTERN.matcher(nLink);
                if (!matcher.find()) matcher = null;
            }
            if (matcher == null) {
                link2id.put(link, "");
                throw new IllegalArgumentException("Not a YouTube channel link");
            }
        }

        String id = matcher.group(1);
        link2id.put(link, id);
        return id;
    }

    private static String findCanonicalLink(String link) {
        try {
            Document document = Jsoup.connect(link).get();
            Elements e = document.body().getElementsByAttributeValue("rel", "canonical");
            if (e.size() > 0 && e.hasAttr("href"))
                return e.attr("href");
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String getChannelTitle(String link) {
        Channel channel = getChannel(getChannelId(link), false);
        return channel != null ? channel.getSnippet().getTitle() : null;
    }

    @Override
    @Nonnull
    protected List<VideoData> getFirstPosts(String link) {
        Channel channel = getChannel(getChannelId(link), true);
        if (channel == null) throw new IllegalStateException("Failed to fetch a channel");
        return getVideos(channel, 1).stream()
                .map(v -> new VideoData(v, channel))
                .toList();
    }

    @Override
    @Nonnull
    protected List<VideoData> getNextPosts(String link, long lastPost) {
        Channel channel = getChannel(getChannelId(link), true);
        if (channel == null) throw new IllegalStateException("Failed to fetch a channel");
        return getVideos(channel, 5).stream()
                .map(v -> new VideoData(v, channel))
                .filter(this::notPostedRecently)
                .filter(v -> getPostTimestamp(v) > lastPost)
                .toList();
    }

    @Override
    protected MessageEmbed buildEmbed(VideoData data) {
        return new EmbedBuilder()
                .setColor(COLOR)
                .setTitle(StringsRG.STR.get("media.new.video"), VIDEO_FORMAT.formatted(data.video.getId()))
                .setAuthor(data.channel.getSnippet().getTitle(),
                        CHANNEL_FORMAT.formatted(data.channel.getId()),
                        data.channel.getSnippet().getThumbnails().getDefault().getUrl())
                .setThumbnail(LOGO)
                .setImage(data.video.getSnippet().getThumbnails().getMedium().getUrl())
                .setDescription("**%s**\n\n%s".formatted(data.video.getSnippet().getTitle(),
                        Strings.trimSnippet(data.video.getSnippet().getDescription(), 200)))
                .build();
    }

    @Override
    protected String getId(VideoData v) {
        return v.video.getId();
    }

    @Override
    protected long getPostTimestamp(VideoData v) {
        return v.video.getSnippet().getPublishedAt().getValue();
    }

    private List<Video> getVideos(Channel channel, long maxResults) {
        try {
            String uploads = channel.getContentDetails()
                    .getRelatedPlaylists()
                    .getUploads();
            if (uploads == null) {
                log.error("'Uploaded' playlist does not exists for channel {}", channel.getSnippet().getTitle());
                return Collections.emptyList();
            }
            List<String> ids = api.playlistItems().list("contentDetails")
                    .setKey(key)
                    .setPlaylistId(uploads)
                    .setMaxResults(maxResults)
                    .execute().getItems().stream().map(i -> i.getContentDetails().getVideoId()).toList();
            return api.videos().list("snippet")
                    .setKey(key)
                    .setId(String.join(",", ids))
                    .execute().getItems();
        } catch (GoogleJsonResponseException e) {
            log.error("Cannot get videos for %s".formatted(channel.getSnippet().getTitle()), e);
            return Collections.emptyList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void doUpdate(List<String> links) {
        try {
            final List<String> toUpdate = links.stream()
                    .map(link -> {
                        try {
                            return getChannelId(link);
                        } catch (Exception ignored) {
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .map(id -> {
                        final Channel cached = getCached(id);
                        if (cached != null) return null;
                        return id;
                    })
                    .filter(Objects::nonNull)
                    .toList();

            if (toUpdate.size() > 0) {
                final List<Channel> channels = getChannels(toUpdate);
                channels.forEach(c -> id2channel.put(c.getId(), c));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private Channel getChannel(String id, boolean privateCall) {
        final Channel cached = getCached(id);
        if (cached != null) return cached;

        final String reason;
        if (privateCall) reason = ". Check quota.";
        else reason = " from public call.";
        log.warn("Updating single channel {}{}", id, reason);
        try {
            List<Channel> found = getChannels(List.of(id));
            if (found.size() > 1)
                throw new IllegalStateException("More than one result");
            if (found.size() == 0)
                return null;

            final Channel channel = found.get(0);
            id2channel.put(id, channel);
            return channel;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Channel getCached(String id) {
        final boolean cacheAlive = nextCacheUpdate != null && LocalDateTime.now().isBefore(nextCacheUpdate);
        if (cacheAlive && id2channel.containsKey(id))
            return id2channel.get(id);
        else if (!cacheAlive) {
            id2channel.clear();
            scheduleNextCacheUpdate();
        }
        return null;
    }

    private List<Channel> getChannels(List<String> ids) throws IOException {
        return api.channels().list("snippet,contentDetails")
                .setKey(key)
                .setId(String.join(",", ids))
                .execute().getItems();
    }

    @Override
    public String getLogo() {
        return LOGO;
    }

    @Override
    public int getColor() {
        return COLOR;
    }

    @Override
    public String getName() {
        return "YouTube";
    }

    public record VideoData(Video video, Channel channel) { }
}
