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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeData extends MediaData<YoutubeData.VideoData> {
    private static final Logger log = LoggerFactory.getLogger(YoutubeData.class);

    public static final Pattern LINK_PATTERN = Pattern.compile("https?://(?:\\w+\\.)?youtube\\.com/channel/([\\w-]+)(?:\\?.*)?(?:/.*)?");
    public static final String LOGO = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/YouTube_full-color_icon_%282017%29.svg/1024px-YouTube_full-color_icon_%282017%29.svg.png";
    public static final String VIDEO_FORMAT = "https://youtube.com/watch?v=%s";
    public static final String CHANNEL_FORMAT = "https://www.youtube.com/channel/%s";
    public static final int COLOR = 0xFF0202;

    private static final HashMap<String, String> link2id = new HashMap<>();

    private final YouTube api;
    private final String key;


    public static YoutubeData newInstance(ConfigRG.BotConfig cfg) {
        return new YoutubeData(cfg.googleKey.getString());
    }

    public YoutubeData(String key) {
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

    @Override
    public boolean linksHere(String link) {
        try {
            getId(link);
            return true;
        } catch (Exception ignored) { }
        return false;
    }

    private static String getId(String link) {
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
    public String getName(String link) {
        Channel channel = getChannel(getId(link));
        return channel != null ? channel.getSnippet().getTitle() : null;
    }

    @Override
    @Nonnull
    protected List<VideoData> getFirstPosts(String link) {
        Channel channel = getChannel(getId(link));
        if (channel == null) throw new IllegalStateException("Failed to fetch a channel");
        return getVideos(channel, 1).stream()
                .map(v -> new VideoData(v, channel))
                .toList();
    }

    @Override
    @Nonnull
    protected List<VideoData> getNextPosts(String link, long lastPost) {
        Channel channel = getChannel(getId(link));
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

    private Channel getChannel(String id) {
        try {
            List<Channel> found = api.channels().list("snippet,contentDetails")
                    .setKey(key)
                    .setId(id)
                    .execute().getItems();
            if (found.size() > 1)
                throw new IllegalStateException("More than one result");
            if (found.size() == 0)
                return null;
            return found.get(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record VideoData(Video video, Channel channel) { }
}
