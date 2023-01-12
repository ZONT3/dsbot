package ru.zont.dsbot.http;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.util.DescribedException;
import ru.zont.dsbot.core.util.MessageBatch;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class GoogleFormHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(GoogleFormHandler.class);

    private final GuildContext context;
    private HttpServer server;

    public GoogleFormHandler(ZDSBot bot) throws IOException {
        ConfigRG.BotConfig botCfg = ConfigRG.castBotConfig(bot.getConfig());
        int webPort = botCfg.formsWebPort.getInt();
        if (webPort < 80 || webPort > 65535) {
            log.warn("Web port is invalid or not exists ({})", webPort);
            this.context = null;
            return;
        }

        context = bot.getMainGuildContext();
        if (this.context == null) {
            log.error("Cannot find guild context");
            return;
        }

        final String channelId = botCfg.formsChannel.getValue();
        MessageChannel channel = this.context.findChannel(channelId);
        if (channel == null) {
            log.error("Cannot find default forms channel, id{}", channelId);
            return;
        }

        setupWebServer(webPort);
    }

    private void setupWebServer(int port) throws IOException {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/postForm", this);
        server.setExecutor(threadPoolExecutor);
        server.start();
        log.info("Web server started");
    }

    public void shutdown() {
        if (server == null) return;
        server.stop(5000);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"post".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtils.response(exchange, "Only POST method is acceptable", 400);
                return;
            }

            final List<String> contentType = exchange.getRequestHeaders().get("Content-type");
            if (contentType == null || contentType.size() < 1 || !contentType.contains("application/json")) {
                HttpUtils.response(exchange, "Content-type should be JSON", 400);
                return;
            }

            final String title = getTitle(exchange);
            if (title == null) {
                context.getErrorReporter().reportError(null,
                        new DescribedException("GoogleForm: Title not specified"));
                HttpUtils.response(exchange, "Specify the title header", 400);
                return;
            }

            try {
                final String content = IOUtils.toString(exchange.getRequestBody(), StandardCharsets.UTF_8);
                newForm(new Gson().fromJson(content, JsonObject.class), title, getChannel(exchange));
            } catch (JsonSyntaxException e) {
                context.getErrorReporter().reportError(null, e);
                HttpUtils.response(exchange, "Not a valid JSON", 400);
                return;
            }
        } catch (Throwable e) {
            context.getErrorReporter().reportError(null, e);
            HttpUtils.response(exchange, "Internal server error: " + e.getMessage(), 500);
            return;
        }

        HttpUtils.response(exchange, "OK", 200);
    }

    private String getTitle(HttpExchange e) {
        List<String> title = e.getRequestHeaders().get("title");
        if (title == null || title.size() < 1)
            return null;
        return title.get(0);
    }

    private MessageChannel getChannel(HttpExchange e) {
        List<String> channel = e.getRequestHeaders().get("channel");
        if (channel == null || channel.size() < 1)
            return null;
        return context.getBot().findChannelById(channel.get(0));
    }

    public void newForm(JsonObject form, String title, @Nullable MessageChannel channel) {
        ConfigRG.BotConfig cfg = ConfigRG.castBotConfig(context.getBot().getConfig());
        if (channel == null)
            channel = context.findChannel(cfg.formsChannel.getValue());
        if (channel == null)
            throw new IllegalStateException("Cannot find forms channel anymore");

        EmbedBuilder formEmbed = new EmbedBuilder()
                .setTitle(title)
                .setColor(0xf01010);

        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, JsonElement> entry : form.entrySet())
            content.append(parseField(entry.getKey(), entry.getValue().getAsJsonObject().get("data")));

        MessageBatch.sendNowWithMessageSplitter(channel, content, formEmbed);
    }

    private static String parseField(String key, JsonElement data) {
        String res;
        if (data.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            boolean f = true;
            for (JsonElement d : data.getAsJsonArray()) {
                if (!f) builder.append(", ");
                else f = false;
                if (d.isJsonArray())
                    builder.append(d.getAsJsonArray().toString());
                else if (d.isJsonPrimitive())
                    builder.append(d.getAsString());
            }
            res = builder.toString();
        } else res = data.getAsString();
        return String.format("**%s**\n%s\n", key, res);
    }
}
