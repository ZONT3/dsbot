package ru.zont.dsbot.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.NotNull;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.util.DCSData;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.listeners.DisplayedWatcherAdapter;
import ru.zont.dsbot.core.util.LiteJSON;

import java.util.Collections;
import java.util.List;

import static ru.zont.dsbot.util.StringsRG.STR;

public class DCSWatcher extends DisplayedWatcherAdapter {
    private final LiteJSON servers;
    private final DCSData dcsData;

    public DCSWatcher(ZDSBot bot, GuildContext context) {
        super(bot, context);
        if (context != null) {
            servers = DCSData.getServersInstance(context);
            dcsData = DCSData.getInstance(context);
        } else {
            servers = null;
            dcsData = null;
        }
    }

    @Override
    public @NotNull List<MessageEmbed> getMessages() {
        final List<DCSData.ServerData> serverData = servers.getList().stream()
                .map(targetIp -> {
                    DCSData.ServerData data = dcsData.findData(targetIp);
                    if (data == null) {
                        data = new DCSData.ServerData();
                        data.ip = targetIp;
                    }
                    return data;
                })
                .toList();
        if (serverData.size() == 0)
            return Collections.emptyList();

        return buildMessages(serverData);
    }

    private List<MessageEmbed> buildMessages(List<DCSData.ServerData> serverData) {
        return serverData.stream().map(data -> {
            if (data.name != null) {
                return new EmbedBuilder()
                        .setTitle("**%s**".formatted(data.getName()))
                        .setDescription("```\n%s\n```\n%s".formatted(data.ip, data.getDescription()))
                        .addField(STR.get("dcs.server.players"), data.getPlayers(), true)
                        .addField(STR.get("dcs.server.mission"), data.mission, true)
                        .addField(STR.get("dcs.server.mission_time"), data.getMissionTime(), true)
                        .setColor(0x1474A6)
                        .build();
            } else {
                return new EmbedBuilder()
                        .setTitle(STR.get("dcs.err.no_serv.title"))
                        .setDescription(STR.get("dcs.err.no_serv", DCSData.countCached(), data.ip))
                        .build();
            }
        }).toList();
    }

    @Override
    public boolean doClearChannel() {
        return false;
    }

    @Override
    public String getChannelId() {
        return ConfigRG.castConfig(getConfig()).dcsMonitoringChannel.getString();
    }
}
