package ru.zont.dsbot.media;

import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.TwitchHelixBuilder;
import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ru.zont.dsbot.ConfigRG;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.zont.dsbot.util.StringsRG.STR;

public class TwitchData extends MediaData<TwitchData.StreamData> {
    public static final int COLOR = 0x6441A4;
    public static final Pattern ID_PATTERN = Pattern.compile("https?://(?:\\w+\\.)?twitch\\.tv/(\\w+)(?:\\?.*)?(?:/.*)?");
    public static final String LOGO = "https://assets.help.twitch.tv/Glitch_Purple_RGB.png";
    private static final String CHANNEL_FORMAT = "https://www.twitch.tv/%s";

    private final TwitchHelix helix;

    public static TwitchData newInstance(ConfigRG.BotConfig cfg) {
        return new TwitchData(cfg.ttvKey.getString(), cfg.ttvSecret.getString());
    }

    public TwitchData(String key, String secret) {
        super(10000);
        if (key == null || secret == null)
            throw new IllegalArgumentException("Twitch key and/or secret not stated");

        helix = TwitchHelixBuilder.builder()
                .withClientId(key)
                .withClientSecret(secret)
                .build();
    }


    @Override
    public String getName(String link) {
        return Objects.requireNonNull(getUser(getId(link))).getDisplayName();
    }

    @Override
    public int getUpdatePeriodMinutes() {
        return 2;
    }

    @Override
    public boolean linksHere(String link) {
        try {
            return getId(link) != null;
        } catch (Exception ignored) { }
        return false;
    }

    private User getUser(String id) {
        List<User> users = helix.getUsers(null, null, Collections.singletonList(id)).execute().getUsers();
        if (users.size() != 1) return null;
        return users.get(0);
    }

    private String getId(String link) {
        Matcher matcher = ID_PATTERN.matcher(link);
        if (!matcher.find()) throw new IllegalArgumentException("Not a Twitch channel link");
        return matcher.group(1);
    }

    public List<Stream> getStreams(String id) {
        return helix.getStreams(
                null, null, null,
                null, null, null, null,
                Collections.singletonList(id)
        ).execute().getStreams();
    }

    @Override
    protected List<StreamData> getFirstPosts(String link) {
        String id = getId(link);
        User user = getUser(id);
        return getStreams(id).stream()
                .map(s -> new StreamData(s, user))
                .toList();
    }

    @Override
    protected List<StreamData> getNextPosts(String link, long lastPosted) {
        String id = getId(link);
        User user = getUser(id);
        return getStreams(id).stream()
                .filter(s -> s.getStartedAtInstant().toEpochMilli() > lastPosted)
                .map(s -> new StreamData(s, user))
                .toList();
    }

    @Override
    protected MessageEmbed buildEmbed(StreamData post) {
        String userName = post.stream.getUserName();
        return new EmbedBuilder()
                .setColor(COLOR)
                .setTitle(STR.get("media.new.stream"), CHANNEL_FORMAT.formatted(userName))
                .setAuthor(userName,
                        CHANNEL_FORMAT.formatted(userName),
                        post.user.getProfileImageUrl())
                .setThumbnail(LOGO)
                .setImage(post.stream.getThumbnailUrl(240, 135))
                .setDescription("**%s**".formatted(post.stream.getTitle()))
                .build();
    }

    @Override
    protected String getId(StreamData post) {
        return post.stream.getId();
    }

    @Override
    protected long getPostTimestamp(StreamData post) {
        return post.stream.getStartedAtInstant().toEpochMilli();
    }

    public record StreamData(Stream stream, User user) { }
}
