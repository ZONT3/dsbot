package ru.zont.dsbot.listeners;

import com.google.gson.JsonElement;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.NotNull;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.util.TSData;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.listeners.DisplayedWatcherAdapter;
import ru.zont.dsbot.core.util.LiteJSON;
import ru.zont.dsbot.core.util.MessageSplitter;
import ru.zont.dsbot.util.StringsRG;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TSWatcher extends DisplayedWatcherAdapter {
    private static final String TS3_ICON = "https://icons.iconarchive.com/icons/papirus-team/papirus-apps/256/teamspeak-3-icon.png";
    private final LiteJSON servers;
    private final ArrayList<TSData> tsDataList;

    public TSWatcher(ZDSBot bot, GuildContext context) {
        super(bot, context);
        servers = context != null ? context.getLJInstance("ts-servers") : null;
        tsDataList = new ArrayList<>();
    }

    private void updateServerList() {
        List<TSData> newList = servers.getList(JsonElement::getAsJsonObject).stream()
                .map(TSData::new)
                .filter(d -> tsDataList.stream()
                        .noneMatch(d2 -> d.getConfigHash().equals(d2.getConfigHash())))
                .toList();
        newList.forEach(TSData::connect);

        List<TSData> toRemove = tsDataList.stream()
                .filter(d -> newList.stream().noneMatch(d2 -> d.getConfigHash().equals(d2.getConfigHash())))
                .toList();
        toRemove.forEach(TSData::shutdown);

        tsDataList.removeAll(toRemove);
        tsDataList.addAll(newList);
    }

    @NotNull
    @Override
    public List<MessageEmbed> getMessages() {
        updateServerList();
        return tsDataList.stream()
                .mapMulti((TSData d, Consumer<MessageEmbed> c) -> buildMessage(d).forEach(c))
                .toList();
    }

    private List<MessageEmbed> buildMessage(TSData data) {
        try {
            Map<String, List<String>> clients = data.fetchClients();
            List<String> lines = clients.keySet().stream()
                    .flatMap(k -> Stream.of(
                                    Stream.of("**%s**".formatted(k)), clients.get(k).stream(), Stream.of(""))
                            .flatMap(s -> s))
                    .toList();
            String title = data.getTitle();
            return MessageSplitter.embeds(
                    String.join("\n", lines),
                    new EmbedBuilder()
                            .setTitle(title != null ? title : data.getHost())
                            .setFooter(title != null ? data.getHost() : null, title != null ? TS3_ICON : null)
                            .setColor(TSData.COLOR));
        } catch (Exception e) {
            getErrorReporter().reportError(null, e);
            return Collections.singletonList(new EmbedBuilder()
                    .setTitle(data.getHost())
                    .setDescription(StringsRG.STR.get("ts3.err.data"))
                    .build());
        }
    }

    @Override
    public String getChannelId() {
        return ConfigRG.castConfig(getConfig()).tsMonitoringChannel.getString();
    }

    @Override
    public long getPeriod() {
        return 60;
    }
}
