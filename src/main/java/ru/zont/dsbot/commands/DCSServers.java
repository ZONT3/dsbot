package ru.zont.dsbot.commands;

import com.google.gson.JsonElement;
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
import ru.zont.dsbot.util.DCSData;
import ru.zont.dsbot.util.StringsRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.Input;
import ru.zont.dsbot.core.commands.PermissionsUtil;
import ru.zont.dsbot.core.commands.SlashCommandAdapter;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.util.DescribedException;
import ru.zont.dsbot.core.util.LiteJSON;
import ru.zont.dsbot.core.util.MessageSplitter;
import ru.zont.dsbot.core.util.ResponseTarget;

import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static ru.zont.dsbot.util.StringsRG.STR;

public class DCSServers extends SlashCommandAdapter {
    private final LiteJSON servers;
    private final DCSData dcsData;

    public DCSServers(ZDSBot bot, GuildContext context) {
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
    public void onCall(ResponseTarget replyTo, Input input, MessageReceivedEvent event, Object... params) {
        checkData();

        final List<String> args = input.getCommandLine().getArgList();
        if (args.size() < 1)
            throw InvalidSyntaxException.insufficientArgs(STR.get("err.no_action_arg"), this);

        switch (args.get(0)) {
            case "add" -> add(getIp(input), replyTo);
            case "rm" -> rm(getIp(input), replyTo);
            case "list" -> list(replyTo);
            default -> throw InvalidSyntaxException.argument(1, STR.get("roles.err.action", args.get(0)), this);
        }
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        checkData();

        ResponseTarget replyTo = new ResponseTarget(event);
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case "list" -> list(replyTo);
            case "add" -> add(DCSData.normIp(event.getOption("ip", OptionMapping::getAsString)), replyTo);
            case "rm" -> rm(DCSData.normIp(event.getOption("ip", OptionMapping::getAsString)), replyTo);
            case "set-channel" -> setChannel(event.getOption("channel", OptionMapping::getAsMessageChannel), replyTo);
            case "disable" -> {
                ConfigRG.castConfig(getConfig()).dcsMonitoringChannel.clearValue();
                replyTo.setOK();
            }
            default -> throw new IllegalArgumentException();
        }
    }

    @Override
    public void onSlashCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if ("rm".equals(event.getSubcommandName())) {
            event.replyChoices(servers.getList().parallelStream().map(ip -> {
                DCSData.ServerData data = dcsData.findData(ip);
                return new Command.Choice(data != null ? data.name : ip, ip);
            }).toList()).complete();
        } else if ("add".equals(event.getSubcommandName())) {
            event.replyChoices(dcsData.findServers(event.getFocusedOption().getValue())
                    .stream()
                    .map(data -> new Command.Choice(data.name, data.ip))
                    .toList()).complete();
        }
    }

    private void checkData() {
        if (servers == null)
            throw new DescribedException(STR.get("dcs.err.no_channel.title"), STR.get("dcs.err.no_channel"));
        if (dcsData == null)
            throw new IllegalStateException("Data not initialized (%s)".formatted(getContext() != null
                    ? getContext().getGuildNameNormalized() : "GLOBAL"));
    }

    private void list(ResponseTarget replyTo) {
        MessageEmbed base = new EmbedBuilder()
                .setTitle(STR.get("monitoring.list.title"))
                .setColor(0x1747A5)
                .build();
        replyTo.respondEmbedsNow(MessageSplitter.embeds(String.join("\n",
                servers.getList().stream().map(ip -> {
                    DCSData.ServerData data = dcsData.findData(ip);
                    return "**%s**\n`%s`".formatted(data != null ? data.name : "unknown", ip);
                }).toList()), base));
    }

    private void add(String ip, ResponseTarget replyTo) {
        servers.opList(list -> {
            list.add(ip);
        });

        DCSData.ServerData data = dcsData.findData(ip);
        if (data == null) {
            replyTo.respondEmbedLater(new EmbedBuilder()
                            .setTitle(STR.get("dcs.err.no_serv.title"))
                            .setDescription(STR.get("dcs.err.no_serv"))
                            .setFooter(STR.get("dcs.server.added.however"))
                            .setColor(ResponseTarget.WARNING_COLOR)
                    .build());
        } else {
            replyTo.respondEmbedLater(new EmbedBuilder()
                            .setTitle(STR.get("dcs.server.added"))
                            .setDescription("**%s**\n%s".formatted(data.name, data.getDescription()))
                            .addField(STR.get("dcs.server.players"), data.getPlayers(), true)
                            .addField(STR.get("dcs.server.mission"), data.mission, true)
                            .addField(STR.get("dcs.server.mission_time"), data.getMissionTime(), true)
                            .setColor(ResponseTarget.OK_COLOR)
                    .build());
        }
    }

    private void rm(String ip, ResponseTarget replyTo) {
        boolean success = servers.opList(list -> {
            List<JsonElement> found = StreamSupport.stream(list.spliterator(), false)
                    .filter(e -> ip.equals(e.getAsString()))
                    .toList();
            int size = found.size();
            found.forEach(list::remove);
            return size > 0;
        });

        if (success)
            replyTo.setOK();
        else {
            replyTo.respondEmbedLater(new EmbedBuilder()
                            .setDescription(STR.get("dcs.err.no_serv.title"))
                            .setColor(ResponseTarget.WARNING_COLOR)
                    .build());
        }
    }

    private void setChannel(MessageChannel channel, ResponseTarget replyTo) {
        if (channel == null) throw new InvalidSyntaxException("Unknown channel");
        ConfigRG.castConfig(getConfig()).dcsMonitoringChannel.setValue(channel.getId());
        replyTo.setOK();
    }

    private String getIp(Input input) {
        final List<String> args = input.getCommandLine().getArgList();
        if (args.size() < 2)
            throw InvalidSyntaxException.insufficientArgs(null, this);
        return DCSData.normIp(args.get(1));
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc()).addSubcommands(
                new SubcommandData("list", STR.get("monitoring.list.title")),
                new SubcommandData("add", STR.get("dcs.add.title"))
                        .addOption(OptionType.STRING, "ip", STR.get("dcs.opt.ip"), true, true),
                new SubcommandData("rm", STR.get("dcs.rm.title"))
                        .addOption(OptionType.STRING, "ip", STR.get("dcs.opt.ip"), true, true),
                new SubcommandData("set-channel", STR.get("monitoring.set_channel")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", STR.get("monitoring.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData("disable", STR.get("dcs.disable"))
        );
    }

    @Override
    public String getName() {
        return "dcs";
    }

    @Override
    public String getShortDesc() {
        return StringsRG.STR.get("dcs.desc.short");
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public boolean checkPermission(PermissionsUtil util) {
        return util.permSetAdmin();
    }
}
