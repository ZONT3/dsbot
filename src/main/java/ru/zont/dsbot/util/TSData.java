package ru.zont.dsbot.util;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import com.github.theholywaffle.teamspeak3.api.reconnect.ConnectionHandler;
import com.github.theholywaffle.teamspeak3.api.reconnect.ReconnectStrategy;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TSData implements ConnectionHandler {
    public static final int COLOR = 0x00c8ff;
    private static final Logger log = LoggerFactory.getLogger(TSData.class);

    private static final String DS_BOT_NAME = "DS Bot II";
    private final int id;
    private final String pass;
    private final String login;
    private final String host;
    private String title;

    private TS3Query query;
    private TS3Api api;

    public TSData(@NotNull JsonObject configObject) {
        host = configObject.get("host").getAsString();
        login = configObject.get("login").getAsString();
        pass = configObject.get("pass").getAsString();

        JsonElement idElement = configObject.get("virtualServerId");
        id = idElement != null && idElement.isJsonPrimitive() ? idElement.getAsInt() : 1;

        JsonElement titleElement = configObject.get("title");
        title = titleElement != null ? titleElement.getAsString() : null;
    }

    public void connect() {
        TS3Config config = new TS3Config()
                .setHost(host)
                .setReconnectStrategy(ReconnectStrategy.exponentialBackoff())
                .setConnectionHandler(this);
        query = new TS3Query(config);
        query.connect();
        api = query.getApi();
        api.login(login, pass);
        api.selectVirtualServerById(id, DS_BOT_NAME);

        if (title == null)
            title = fetchServerName();
    }

    public void shutdown() {
        api.logout();
        query.exit();
    }

    public Map<String, List<String>> fetchClients() {
        if (!query.isConnected())
            throw new IllegalStateException("Query disconnected");

        return api.getClients().stream()
                .filter(c -> !c.isServerQueryClient())
                .collect(Collectors.groupingBy(
                        c -> api.getChannelInfo(c.getChannelId()).getName(),
                        Collectors.mapping(Client::getNickname, Collectors.toList())));
    }

    public String fetchServerName() {
        return api.getServerInfo().getName();
    }

    @Override
    public void onConnect(TS3Api api) {
        log.info("{}@{} connected", login, host);
    }

    @Override
    public void onDisconnect(TS3Query ts3Query) {
        log.info("{}@{} disconnected", login, host);
    }

    public String getConfigHash() {
        return String.valueOf(String.join(":", host, login, pass, String.valueOf(id)).hashCode());
    }

    public String getHost() {
        return host;
    }

    public String getTitle() {
        return title;
    }
}
