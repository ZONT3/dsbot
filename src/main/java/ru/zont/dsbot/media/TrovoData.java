package ru.zont.dsbot.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.io.IOUtils;
import ru.zont.dsbot.ConfigRG;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.zont.dsbot.util.StringsRG.STR;

public class TrovoData extends MediaDataImpl<TrovoData.TrovoStream> {

    public static final Pattern USERNAME_PATTERN = Pattern.compile("https?://(?:\\w+\\.)?trovo\\.live/s/(\\w+)(?:\\?.*)?(?:/.*)?");
    private static final String CHANNEL_FORMAT = "https://trovo.live/s/%s";
    public static final String LOGO = "https://upload.wikimedia.org/wikipedia/commons/b/b9/Trovo_Logo.png";
    public static final int COLOR = 0x1AAB77;
    private final String clientId;

    private TrovoData(String clientId) {
        super(10000);
        this.clientId = clientId;
    }

    public static TrovoData newInstance(ConfigRG.BotConfig cfg) {
        final String userID = cfg.trovoUserID.getString();
        if (userID == null) return null;
        return new TrovoData(userID);
    }

    @Override
    public String getChannelTitle(String link) {
        return getChannel(getUserName(link)).nickName;
    }

    @Override
    public boolean linksHere(String link) {
        try {
            return getUserName(link) != null;
        } catch (Exception ignored) {
        }
        return false;
    }

    private String getUserName(String link) {
        final Matcher m = USERNAME_PATTERN.matcher(link);
        if (!m.matches()) throw new IllegalArgumentException("Invalid link");
        return m.group(1);
    }

    private TrovoStream getStream(String link) throws IOException {
        final Channel channel = getChannel(getUserName(link));

        JsonObject obj = callAPI("https://open-api.trovo.live/openplatform/channels/id", "{\"channel_id\":\"%s\"}".formatted(channel.channelId));
        if (!obj.has("channel_url")) throw new IllegalArgumentException("Invalid respond from REST API");
        if (!obj.get("is_live").getAsBoolean()) return null;

        return new TrovoStream(channel,
                obj.get("live_title").getAsString(),
                obj.get("started_at").getAsLong() * 1000,
                obj.get("category_name").getAsString(),
                obj.get("thumbnail").getAsString(),
                obj.get("profile_pic").getAsString()
        );
    }

    @Override
    public int getUpdatePeriodMinutes() {
        return 2;
    }

    private Channel getChannel(String userName) {
        try {
            final JsonObject obj = callAPI("https://open-api.trovo.live/openplatform/getusers", "{\"user\":[\"%s\"]}".formatted(userName));

            if (!obj.has("users")) throw new IllegalStateException("Invalid respond from REST API");
            final JsonArray users = obj.get("users").getAsJsonArray();
            if (users.size() == 0) throw new IllegalStateException("User not found");
            final JsonObject user = users.get(0).getAsJsonObject();
            if (!user.has("channel_id")) throw new IllegalArgumentException("Invalid user entity");

            final String channelId = user.get("channel_id").getAsString();
            final String nickname = user.has("nickname") ? user.get("nickname").getAsString() : userName;
            return new Channel(channelId, userName, nickname);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject callAPI(String urlString, String request) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection connection = ((HttpURLConnection) url.openConnection());
        connection.setRequestMethod("POST");

        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Client-ID", clientId);

        connection.setDoOutput(true);
        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
        writer.write(request);
        writer.flush();
        writer.close();
        connection.getOutputStream().close();

        InputStream responseStream = connection.getResponseCode() / 100 == 2
                ? connection.getInputStream()
                : connection.getErrorStream();

        final JsonElement element = JsonParser.parseString(IOUtils.toString(responseStream, StandardCharsets.UTF_8));
        return element.getAsJsonObject();
    }

    @Override
    protected List<TrovoStream> getFirstPosts(String link) {
        TrovoStream stream;
        try {
            stream = getStream(link);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (stream == null) return Collections.emptyList();
        return List.of(stream);
    }

    @Override
    protected List<TrovoStream> getNextPosts(String link, long lastPosted) {
        TrovoStream stream;
        try {
            stream = getStream(link);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (stream == null || stream.timestamp <= lastPosted)
            return Collections.emptyList();
        return List.of(stream);
    }

    @Override
    protected MessageEmbed buildEmbed(TrovoStream post) {
        return new EmbedBuilder()
                .setColor(COLOR)
                .setTitle(STR.get("media.new.stream"), CHANNEL_FORMAT.formatted(post.channel.userName))
                .setAuthor(post.channel.userName,
                        CHANNEL_FORMAT.formatted(post.channel.userName),
                        post.userThumbnail)
                .setThumbnail(LOGO)
                .setImage(post.thumbnail)
                .setDescription("**%s**".formatted(post.title))
                .build();
    }

    @Override
    protected String getId(TrovoStream post) {
        return "%s:%d".formatted(post.title, post.timestamp);
    }

    @Override
    protected long getPostTimestamp(TrovoStream post) {
        return post.timestamp;
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
        return "Trovo";
    }

    public record TrovoStream(
            Channel channel,
            String title,
            long timestamp,
            String category,
            String thumbnail,
            String userThumbnail
    ) {
    }

    private record Channel(String channelId, String userName, String nickName) {
    }
}
