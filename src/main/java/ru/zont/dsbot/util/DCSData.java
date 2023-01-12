package ru.zont.dsbot.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.util.LiteJSON;
import ru.zont.dsbot.core.util.Strings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static ru.zont.dsbot.util.StringsRG.STR;

public class DCSData {
    public static final Pattern IP_PATTERN = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?::(\\d{2,5}))?$");
    private static final Logger log = LoggerFactory.getLogger(DCSData.class);
    public static final int DEFAULT_PORT = 10308;

    private static final Object lock = new Object();
    public static final int CACHE_LIFETIME = 30_000;
    private static long nextCache = 0L;
    private static long lastFetch = 0L;
    private static JsonArray jsonCache = null;
    private static final HashMap<String, ServerData> dataCache = new HashMap<>();

    private final GuildContext context;

    public DCSData(GuildContext context) {
        this.context = context;
    }

    public static DCSData getInstance(GuildContext context) {
        return context.getInstance(DCSData.class, () -> new DCSData(context));
    }

    @NotNull
    public static LiteJSON getServersInstance(GuildContext context) {
        return context.getLJInstance("dcs");
    }

    public static String normIp(String ip) {
        if (ip == null)
            throw new IllegalArgumentException();

        final Matcher m = IP_PATTERN.matcher(ip.trim());
        if (m.find()) {
            final int port;
            final String rawPort = m.group(5);
            if (rawPort != null && !rawPort.isEmpty())
                port = Integer.parseInt(rawPort);
            else port = DEFAULT_PORT;
            return "%d.%d.%d.%d:%d".formatted(
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), port);
        }
        throw new InvalidSyntaxException(STR.get("dcs.err.ip"));
    }

    public List<ServerData> findServers(String nameQuery) {
        if (nameQuery.length() < 2)
            return Collections.emptyList();

        ServerData data = fetchCachedData(nameQuery);
        if (data != null)
            return List.of(data);

        if (IP_PATTERN.matcher(nameQuery).matches()) {
            data = findData(nameQuery);
            if (data != null)
                return List.of(data);
            else return Collections.emptyList();
        }

        return StreamSupport.stream(jsonCache.spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .filter(obj -> isValidServer(obj) && obj.get("NAME").getAsString()
                        .toLowerCase().contains(nameQuery.toLowerCase()))
                .limit(25)
                .map(obj -> parseData(obj, parseIp(obj)))
                .toList();
    }

    public ServerData findData(String targetIp) {
        try {
            ServerData data = fetchCachedData(targetIp);
            if (data != null) return data;

            Optional<JsonObject> optional = StreamSupport.stream(jsonCache.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(obj -> isValidServer(obj) && parseIp(obj).equals(targetIp))
                    .findAny();

            if (optional.isPresent()) {
                JsonObject object = optional.get();
                String ip = parseIp(object);
                data = parseData(object, ip);
                dataCache.put(ip, data);
                return data;
            }
        } catch (Exception e) {
            log.error("Cannot parse data", e);
        }
        return null;
    }

    private boolean isValidServer(JsonObject obj) {
        return obj.has("IP_ADDRESS") && obj.has("NAME");
    }

    private String parseIp(JsonObject obj) {
        final String ipAddress = obj.getAsJsonPrimitive("IP_ADDRESS").getAsString();
        final String port = obj.has("PORT") ? obj.get("PORT").getAsString() : String.valueOf(DEFAULT_PORT);
        return normIp("%s:%s".formatted(ipAddress, port));
    }

    @NotNull
    private ServerData parseData(JsonObject dataObj, String ip) {
        ServerData data;
        data = new ServerData();
        data.name = dataObj.getAsJsonPrimitive("NAME").getAsString();
        data.ip = ip;
        data.mission = dataObj.getAsJsonPrimitive("MISSION_NAME").getAsString();
        data.players = dataObj.getAsJsonPrimitive("PLAYERS").getAsInt();
        data.playersMax = dataObj.getAsJsonPrimitive("PLAYERS_MAX").getAsInt();
        data.description = dataObj.getAsJsonPrimitive("DESCRIPTION").getAsString();
        data.missionTime = dataObj.getAsJsonPrimitive("MISSION_TIME_FORMATTED").getAsString();
        data.fetchTime = lastFetch;

        if ("No".equals(data.description.trim()) || data.description.isBlank())
            data.description = STR.get("dcs.description.no");
        return data;
    }

    @Nullable
    private ServerData fetchCachedData(String targetIp) {
        ServerData data;
        synchronized (lock) {
            if (jsonCache == null || nextCache <= System.currentTimeMillis()) {
                fetchData();
                data = null;
            } else if (targetIp != null) {
                data = dataCache.getOrDefault(targetIp, null);
            } else {
                data = null;
            }
        }
        return data;
    }

    private void fetchData() {
        final ConfigRG.BotConfig cfg = ConfigRG.castBotConfig(context.getBot().getConfig());
        if (!ConfigRG.checkConfigEntries(null, cfg.dcsLogin, cfg.dcsPass))
            throw new IllegalStateException("Login/Password not set");

        final String resp;
        try {
            lastFetch = System.currentTimeMillis();
            resp = post(new LinkedList<>() {{
                add(new BasicNameValuePair("AUTH_FORM", "Y"));
                add(new BasicNameValuePair("TYPE", "AUTH"));
                add(new BasicNameValuePair("backurl", "/en/personal/server/?ajax=y"));
                add(new BasicNameValuePair("USER_LOGIN", cfg.dcsLogin.getString()));
                add(new BasicNameValuePair("USER_PASSWORD", cfg.dcsPass.getString()));
                add(new BasicNameValuePair("USER_REMEMBER", "Y"));
            }});
        } catch (IOException e) {
            log.error("Cannot fetch DCS Servers.", e);
            return;
        }

        if (resp != null && !resp.isBlank()) {
            jsonCache = JsonParser.parseString(resp).getAsJsonObject().get("SERVERS").getAsJsonArray();
            nextCache = System.currentTimeMillis() + CACHE_LIFETIME;
        } else {
            log.error("No response from dcs website");
        }
    }

    public static int countCached() {
        if (jsonCache != null)
            return jsonCache.size();
        return 0;
    }

    private static String post(List<NameValuePair> payload) throws IOException {
        final CloseableHttpClient client = HttpClients.createDefault();

        final HttpPost post = new HttpPost("https://www.digitalcombatsimulator.com/en/personal/server/?login=yes&ajax=y");
        post.setEntity(new UrlEncodedFormEntity(payload));
        post.setHeader("Accept", "application/x-www-form-urlencoded");
        post.setHeader("Content-type", "application/x-www-form-urlencoded");

        final CloseableHttpResponse resp = client.execute(post);
        final String res = IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8);
        resp.close();

        return res;
    }

    public static class ServerData {
        public static final String BLANK_TIME = "--:--:--";
        public static final int DESC_MAX_LENGTH = 715;
        public static final int NAME_MAX_LENGTH = 64;

        public String name;
        public String ip;
        public String mission;
        public int players;
        public int playersMax;
        public String description;
        public String missionTime;
        private long fetchTime;

        public String getMissionTime() {
            String[] split = missionTime.trim().split(":");

            if (!Arrays.stream(split).allMatch(s -> s.matches("\\d{1,2}")))
                return BLANK_TIME;

            int h, m, s;
            if (split.length == 2) {
                h = 0;
                m = Integer.parseInt(split[0]);
                s = Integer.parseInt(split[1]);
            } else if (split.length == 3) {
                h = Integer.parseInt(split[0]);
                m = Integer.parseInt(split[1]);
                s = Integer.parseInt(split[2]);
            } else {
                return BLANK_TIME;
            }

            if (Stream.of(h, m, s).allMatch(i -> i == 0) || Stream.of(h, m, s).anyMatch(i -> i < 0))
                return BLANK_TIME;

            long delta = System.currentTimeMillis() - fetchTime;
            h += delta / 1000 / 60 / 60;
            m += delta / 1000 / 60 % 60;
            s += delta / 1000 % 60;
            return "%02d:%02d:%02d".formatted(h, m, s);
        }

        public String getName() {
            return Strings.trimSnippet(name, NAME_MAX_LENGTH);
        }

        public String getDescription() {
            return Strings.trimSnippet(description
                    .replaceAll("<br />", "\n")
                    .replaceAll("<.+?>", ""), DESC_MAX_LENGTH);
        }

        public String getPlayers() {
            return "%d / %d".formatted(players, playersMax);
        }
    }
}
