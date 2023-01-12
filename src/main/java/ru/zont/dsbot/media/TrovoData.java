package ru.zont.dsbot.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrovoData extends MediaData<TrovoData.TrovoStream> {

    public static final Pattern USERNAME_PATTERN = Pattern.compile("https?://(?:\\w+\\.)?trovo\\.live/(\\w+)(?:\\?.*)?(?:/.*)?");
    private static final String CHANNEL_URL_FORMAT = "https://trovo.live/s/%s";
    private final String clientId;

    public TrovoData(String clientId) {
        super(10000);
        this.clientId = clientId;
    }

    @Override
    public String getName(String link) {
        return getChannel(getUserName(link)).nickName;
    }

    @Override
    public boolean linksHere(String link) {
        try {
            return getUserName(link) != null;
        } catch (Exception ignored) { }
        return false;
    }

    private String getUserName(String link) {
        final Matcher m = USERNAME_PATTERN.matcher(link);
        if (!m.matches()) throw new IllegalArgumentException("Invalid link");
        return m.group(1);
    }

    private TwitchData.StreamData getStream(String link) {
        final Channel channel = getChannel(getUserName(link));
        // TODO
        // URL: https://open-api.trovo.live/openplatform/channels/id
        // DATA: "{\"channel_id\":}".formatted(channel.channelId)
        return null;
    }

    private Channel getChannel(String userName) {
        try {
            final URL url = new URL(CHANNEL_URL_FORMAT.formatted(userName));
            final HttpURLConnection connection = ((HttpURLConnection) url.openConnection());
            connection.setRequestMethod("POST");

            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Client-ID", clientId);

            connection.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write("{\"user\":[%s]}".formatted(userName));
            writer.flush();
            writer.close();
            connection.getOutputStream().close();

            InputStream responseStream = connection.getResponseCode() / 100 == 2
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            final JsonElement element = JsonParser.parseString(IOUtils.toString(responseStream, StandardCharsets.UTF_8));
            final JsonObject obj = element.getAsJsonObject();

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

    @Override
    protected List<TrovoStream> getFirstPosts(String link) {
        return null;
    }

    @Override
    protected List<TrovoStream> getNextPosts(String link, long lastPosted) {
        return null;
    }

    @Override
    protected MessageEmbed buildEmbed(TrovoStream post) {
        return null;
    }

    @Override
    protected String getId(TrovoStream post) {
        return null;
    }

    @Override
    protected long getPostTimestamp(TrovoStream post) {
        return 0;
    }

    /**
     * @param title live_title
     * @param timestamp started_at
     * @param category category_name
     * @param thumbnail thumbnail
     * @param userThumbnail profile_pic
     */
    public record TrovoStream(
            Channel channel,
            String title,
            long timestamp,
            String category,
            String thumbnail,
            String userThumbnail
    ) { }

    private record Channel(String channelId, String userName, String nickName) {
    }
}
