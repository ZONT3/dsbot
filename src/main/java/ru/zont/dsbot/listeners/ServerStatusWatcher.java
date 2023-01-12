package ru.zont.dsbot.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.NotNull;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.listeners.DisplayedWatcherAdapter;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static ru.zont.dsbot.util.StringsRG.STR;

public class ServerStatusWatcher extends DisplayedWatcherAdapter {
    public ServerStatusWatcher(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public @NotNull List<MessageEmbed> getMessages() {
        return List.of(getGmsMessage(), getStatisticsMessage(), getStatusMessage());
    }

    private MessageEmbed getGmsMessage() {
        return new EmbedBuilder()
                .setTitle(STR.get("status.gms.title"))
                .setDescription("их нет)")
                .setColor(0x9900FF)
                .build();
    }

    private MessageEmbed getStatisticsMessage() {
        return new EmbedBuilder()
                .setTitle(STR.get("status.statistics.title"))
                .setDescription("CurrentTimeMillis: %s".formatted(System.currentTimeMillis()))
                .setColor(0x0078D7)
                .build();
    }

    private MessageEmbed getStatusMessage() {
        return new EmbedBuilder()
                .setTitle(STR.get("status.title"))
                .setDescription("Calendar: %s".formatted(Calendar.getInstance().toString()))
                .setColor(0xF4900C)
                .build();
    }

    @Override
    public String getChannelId() {
        return ConfigRG.castConfig(getConfig()).serverStatusChannel.getString();
    }

    @Override
    public List<String> getAllowedGuilds() {
        return Collections.singletonList(ConfigRG.castBotConfig(getBotConfig()).mainGuild.getString());
    }
}
