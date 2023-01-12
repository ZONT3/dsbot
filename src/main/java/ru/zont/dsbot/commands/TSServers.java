package ru.zont.dsbot.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.util.TSData;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.Input;
import ru.zont.dsbot.core.commands.PermissionsUtil;
import ru.zont.dsbot.core.commands.SlashCommandAdapter;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.commands.exceptions.OnlySlashUsageException;
import ru.zont.dsbot.core.util.LiteJSON;
import ru.zont.dsbot.core.util.MessageSplitter;
import ru.zont.dsbot.core.util.ResponseTarget;

import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static ru.zont.dsbot.util.StringsRG.STR;

public class TSServers extends SlashCommandAdapter {
    private final LiteJSON servers;

    public TSServers(ZDSBot bot, GuildContext context) {
        super(bot, context);
        servers = context != null ? context.getLJInstance("ts-servers") : null;
    }

    @Override
    public void onCall(ResponseTarget replyTo, Input input, MessageReceivedEvent event, Object... params) {
        throw new OnlySlashUsageException();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        final ResponseTarget responseTarget = new ResponseTarget(event);
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case "list" -> list(responseTarget);
            case "add" -> add(responseTarget,
                    event.getOption("host", OptionMapping::getAsString),
                    event.getOption("login", OptionMapping::getAsString),
                    event.getOption("password", OptionMapping::getAsString),
                    event.getOption("title", OptionMapping::getAsString),
                    event.getOption("virtual-server-id", -1, OptionMapping::getAsInt));
            case "rm" -> rm(responseTarget, event.getOption("host", OptionMapping::getAsString));
            case "set-channel" -> setChannel(responseTarget,
                    event.getOption("channel", OptionMapping::getAsMessageChannel));
        }
    }

    private void list(ResponseTarget responseTarget) {
        MessageEmbed base = new EmbedBuilder()
                .setTitle(STR.get("monitoring.list.title"))
                .setColor(TSData.COLOR)
                .build();
        responseTarget.respondEmbedsNow(MessageSplitter.embeds(String.join("\n",
                servers.getList(JsonElement::getAsJsonObject).stream()
                        .map(o -> "`%s`".formatted(o.get("host").getAsString()))
                        .toList()), base));
    }

    private void add(ResponseTarget responseTarget,
                     String host,
                     String login,
                     String password,
                     String title,
                     int vsId) {
        JsonObject serverJson = new JsonObject();
        serverJson.addProperty("host", host);
        serverJson.addProperty("login", login);
        serverJson.addProperty("pass", password);
        if (title != null)
            serverJson.addProperty("title", title);
        if (vsId > 0)
            serverJson.addProperty("virtualServerId", vsId);

        String serverName;
        Exception exception = null;
        try {
            serverName = checkServer(serverJson);
        } catch (Exception e) {
            exception = e;
            serverName = null;
        }

        servers.opList(list -> {
            StreamSupport.stream(list.spliterator(), false)
                    .filter(e -> e.getAsJsonObject().get("host").getAsString().equals(host))
                    .toList().forEach(list::remove);
            list.add(serverJson);
        });

        if (serverName == null) {
            responseTarget.respondEmbedLater(new EmbedBuilder()
                    .setTitle(STR.get("ts3.err.no_info.title"))
                    .setDescription(STR.get("ts3.err.no_info", exception != null ? exception.getMessage() : "null"))
                    .setColor(ResponseTarget.WARNING_COLOR)
                    .build());
        } else {
            responseTarget.respondEmbedLater(new EmbedBuilder()
                    .setTitle(STR.get("ts3.server.added"))
                    .setDescription(serverName)
                    .setColor(ResponseTarget.OK_COLOR)
                    .build());
        }
    }

    private void rm(ResponseTarget responseTarget, String host) {
        boolean success = servers.opList(list -> {
            List<JsonElement> found = StreamSupport.stream(list.spliterator(), false)
                    .filter(e -> e.getAsJsonObject().get("host").getAsString().equals(host))
                    .toList();
            int size = found.size();
            found.forEach(list::remove);
            return size > 0;
        });

        if (success)
            responseTarget.setOK();
        else {
            responseTarget.respondEmbedLater(new EmbedBuilder()
                    .setDescription(STR.get("ts3.err.no_server"))
                    .setColor(ResponseTarget.WARNING_COLOR)
                    .build());
        }
    }

    private void setChannel(ResponseTarget responseTarget, MessageChannel channel) {
        if (channel == null) throw new InvalidSyntaxException("Unknown channel");
        ConfigRG.castConfig(getConfig()).tsMonitoringChannel.setValue(channel.getId());
        responseTarget.setOK();
    }

    private String checkServer(JsonObject config) {
        TSData tsData = new TSData(config);
        tsData.connect();
        String name = tsData.fetchServerName();
        tsData.shutdown();
        return name;
    }

    @Override
    public void onSlashCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
        event.replyChoices(servers.getList(JsonElement::getAsJsonObject).stream()
                .map(d -> {
                    final String host = d.get("host").getAsString();
                    return new Command.Choice(host, host);
                }).toList()).complete();
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc()).addSubcommands(
                new SubcommandData("list", STR.get("ts3.list.title")),
                new SubcommandData("add", STR.get("ts3.add.title"))
                        .addOption(OptionType.STRING, "host", STR.get("ts3.opt.host"), true)
                        .addOption(OptionType.STRING, "login", STR.get("ts3.opt.login"), true)
                        .addOption(OptionType.STRING, "password", STR.get("ts3.opt.password"), true)
                        .addOption(OptionType.STRING, "title", STR.get("ts3.opt.title"))
                        .addOptions(
                                new OptionData(OptionType.INTEGER, "virtual-server-id",
                                        STR.get("ts3.opt.virtual_server_id")).setMinValue(1)),
                new SubcommandData("rm", STR.get("ts3.rm.title"))
                        .addOption(OptionType.STRING, "host", STR.get("ts3.opt.host"), true, true),
                new SubcommandData("set-channel", STR.get("monitoring.set_channel")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", STR.get("monitoring.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS))
        );
    }

    @Override
    public String getName() {
        return "ts";
    }

    @Override
    public String getShortDesc() {
        return STR.get("ts3.desc.short");
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    public boolean checkPermission(PermissionsUtil util) {
        return util.permSetAdmin();
    }
}
