package ru.zont.dsbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.Main;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.config.ZDSBConfig;
import ru.zont.dsbot.core.util.MessageSplitter;
import ru.zont.dsbot.core.util.ResponseTarget;
import ru.zont.dsbot.util.RgPermissions;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ru.zont.dsbot.util.StringsRG.STR;

public class Config extends RGSlashCommandAdapter {

    public Config(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        ConfigRG cfg = ConfigRG.castConfig(getConfig());
        ResponseTarget responseTarget = new ResponseTarget(event);

        final Map<String, String> inline = new LinkedHashMap<>(){{
            put("Новые видео", channel(cfg.mediaVideoChannel, "/media videos-set-channel"));
            put("Роль для уведомления", role(cfg.mediaPushingRole, "/media role"));
            put("Мониторинг DCS", channel(cfg.dcsMonitoringChannel, "/dcs set-channel"));
            put("Лог вышедших пользователей", channel(cfg.greetingsLeaveChannel, "/bureau leave-channel"));
            put("Авто-выдача роли всем новым пользователям", role(cfg.greetingsAutoRole, "/bureau join-role"));
            put("Публичное приветствие пользователей", channel(cfg.greetingsChannel, "/bureau greetings-channel"));
            put("Бюро пропусков (выдача других ролей)", channel(cfg.greetingsBureauChannel, "/bureau channel"));
            put("Роли для бюро пропусков", "*См. `/bureau roles-list`*");
            put("Мониторинг TS3", channel(cfg.tsMonitoringChannel, "/ts set-channel"));
            put("Список серверов TS3", "*См. `/ts list`*");
        }};

        final Map<String, String> multiline = new LinkedHashMap<>(){{
            put("Публичное сообщение приветствия", stringMulti(cfg.greetingsMessagePublic, "/bureau greetings-message"));
            put("Приветствие в ЛС пользователя", stringMulti(cfg.greetingsMessagePrivate, "/bureau greetings-private-message"));
            put("Сообщение в бюро пропусков", stringMulti(cfg.greetingsMessageBureau, "/bureau message"));
        }};

        final List<String> content = List.of(
                String.join("\n", inline.entrySet().stream()
                        .map(e -> "**%s**: %s".formatted(e.getKey(), e.getValue()))
                        .toList()),
                String.join("\n\n", multiline.entrySet().stream()
                        .map(e -> "**%s**:\n%s".formatted(e.getKey(), e.getValue()))
                        .toList())
        );

        responseTarget.respondEmbedsNow(new MessageSplitter(String.join("\n\n", content)).splitEmbeds(
                new EmbedBuilder()
                        .setTitle(STR.get("config.title"))
                        .setColor(0x8AB327)
                        .build(),
                MessageSplitter.SplitPolicy.NEWLINE));
    }

    private static String channel(ZDSBConfig.Entry entry, String command) {
        final String value = entry.getString();
        return "%s `%s`".formatted(value != null ? "<#%s>".formatted(value) : STR.get("config.off"), command);
    }

    private static String role(ZDSBConfig.Entry entry, String command) {
        final String value = entry.getString();
        return "%s `%s`".formatted(value != null ? "<@&%s>".formatted(value) : STR.get("config.off"), command);
    }

    private static String stringMulti(ZDSBConfig.Entry entry, String command) {
        return String.join("\n",
                "`%s`".formatted(command),
                entry.getString(STR.get("config.off")));
    }

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public String getShortDesc() {
        return STR.get("config.desc");
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc());
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public boolean checkPermission(RgPermissions util) {
        return true;
    }
}
