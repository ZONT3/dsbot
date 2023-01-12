package ru.zont.dsbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Role;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.listeners.GreetingsListener;
import ru.zont.dsbot.util.CommandSupport;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.Input;
import ru.zont.dsbot.core.commands.PermissionsUtil;
import ru.zont.dsbot.core.commands.SlashCommandAdapter;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.commands.exceptions.OnlySlashUsageException;
import ru.zont.dsbot.core.config.ZDSBConfig;
import ru.zont.dsbot.core.util.MessageSplitter;
import ru.zont.dsbot.core.util.ResponseTarget;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static ru.zont.dsbot.util.StringsRG.STR;

public class Greetings extends SlashCommandAdapter {
    public Greetings(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public void onCall(ResponseTarget replyTo, Input input, MessageReceivedEvent event, Object... params) {
        throw new OnlySlashUsageException();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        ConfigRG cfg = ConfigRG.castConfig(getConfig());
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case "leave-channel" -> CommandSupport.setChannel(cfg.greetingsLeaveChannel, event);
            case "leave-channel-disable" -> cfg.greetingsLeaveChannel.clearValue();

            case "join-role" -> {
                Role role = Objects.requireNonNull(event.getOption("role", OptionMapping::getAsRole));
                CommandSupport.checkRole(event, role);
                cfg.greetingsAutoRole.setValue(role.getId());
            }
            case "join-role-disable" -> cfg.greetingsAutoRole.clearValue();

            case "greetings-channel" -> CommandSupport.setChannel(cfg.greetingsChannel, event);
            case "greetings-message" -> CommandSupport.setMessage(cfg.greetingsMessagePublic, event);
            case "greetings-disable" -> cfg.greetingsChannel.clearValue();

            case "greetings-private-message" -> CommandSupport.setMessage(cfg.greetingsMessagePrivate, event);
            case "greetings-private-disable" -> cfg.greetingsMessagePrivate.clearValue();

            case "roles-add" -> addBureauRole(event, cfg);
            case "roles-rm" -> rmBureauRole(event, cfg);
            case "roles-list" -> {
                listBureauRoles(cfg, new ResponseTarget(event));
                return;
            }

            case "disable" -> {
                cfg.greetingsBureauChannel.clearValue();
                initGreetingsListener();
            }
            case "channel" -> {
                CommandSupport.setChannel(cfg.greetingsBureauChannel, event);
                initGreetingsListener();
            }
            case "message" -> {
                CommandSupport.setMessage(cfg.greetingsMessageBureau, event);
                String title = event.getOption("title", OptionMapping::getAsString);
                if (title != null) cfg.greetingsMessageBureauTitle.setValue(title);
                initGreetingsListener();
            }
        }
        new ResponseTarget(event).setOK();
    }

    private void addBureauRole(SlashCommandInteractionEvent event, ConfigRG cfg) {
        String emoji = event.getOption("reaction", OptionMapping::getAsString);
        checkEmoji(Objects.requireNonNull(emoji).trim());
        Role role = Objects.requireNonNull(event.getOption("role", OptionMapping::getAsRole));
        CommandSupport.checkRole(event, role);

        cfg.greetingsReactions.opList(l -> l.add(Emoji.fromUnicode(emoji).getName()));
        cfg.greetingsRoles.opList(l -> l.add(role.getId()));

        initGreetingsListener();
    }

    private void rmBureauRole(SlashCommandInteractionEvent event, ConfigRG cfg) {
        checkReactionsConfig(cfg.greetingsReactions.toList(), cfg.greetingsRoles.toList());

        String emoji = event.getOption("reaction", OptionMapping::getAsString);
        checkEmoji(Objects.requireNonNull(emoji).trim());

        String emojiName = Emoji.fromUnicode(emoji).getName();
        int i = cfg.greetingsReactions.toList().indexOf(emojiName);

        cfg.greetingsReactions.opList(l -> l.remove(emojiName));
        cfg.greetingsRoles.opList(l -> l.remove(i));

        initGreetingsListener();
    }

    private void listBureauRoles(ConfigRG cfg, ResponseTarget responseTarget) {
        List<String> reactions = cfg.greetingsReactions.toList();
        List<String> roles = cfg.greetingsRoles.toList();
        checkReactionsConfig(reactions, roles);

        String content = String.join("\n", IntStream.range(0, reactions.size())
                .mapToObj(i -> "%s <=> <@&%s>".formatted(reactions.get(i), roles.get(i)))
                .toList());
        responseTarget.respondEmbedsNow(MessageSplitter.embeds(content,
                new EmbedBuilder()
                        .setTitle(STR.get("greetings.list.title"))
                        .setColor(GreetingsListener.COLOR)));
    }

    private void initGreetingsListener() {
        GreetingsListener greetingsListener = getGreetingsListener();
        if (greetingsListener != null)
            greetingsListener.init(Objects.requireNonNull(getContext()).getGuild());
    }

    @Nullable
    private GreetingsListener getGreetingsListener() {
        return Objects.requireNonNull(getContext()).getListenerInstance(GreetingsListener.class);
    }

    private void checkReactionsConfig(List<String> reactions, List<String> roles) {
        if (reactions.size() != roles.size())
            throw new IllegalStateException("Invalid config: lists has different sizes. Please fix the roles and reactions config.");
    }

    private void checkEmoji(String emoji) {
        if (emoji.startsWith(":") || (!emoji.toLowerCase().startsWith("u") && emoji.length() > 2))
            throw new InvalidSyntaxException(STR.get("greetings.err.emoji"));
    }

    @Override
    public void onSlashCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
        ConfigRG cfg = ConfigRG.castConfig(getConfig());
        final List<Command.Choice> choices;
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case "greetings-message" -> choices = getSingleChoice(cfg.greetingsMessagePublic);
            case "greetings-private-message" -> choices = getSingleChoice(cfg.greetingsMessagePrivate);
            case "roles-rm" -> choices = cfg.greetingsReactions.toList().stream()
                    .map(s -> Emoji.fromUnicode(s).getName())
                    .map(s -> new Command.Choice(s, s))
                    .toList();
            case "message" -> {
                if ("message".equals(event.getFocusedOption().getName()))
                    choices = getSingleChoice(cfg.greetingsMessageBureau);
                else choices = getSingleChoice(cfg.greetingsMessageBureauTitle);
            }
            default -> choices = Collections.emptyList();
        }
        event.replyChoices(choices).complete();
    }

    @NotNull
    private List<Command.Choice> getSingleChoice(ZDSBConfig.Entry configEntry) {
        final List<Command.Choice> choices;
        String message = configEntry.getString();
        if (message != null)
            choices = Collections.singletonList(new Command.Choice(message, message));
        else choices = Collections.emptyList();
        return choices;
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc()).addSubcommands(
                new SubcommandData("leave-channel", STR.get("greetings.leave_channel")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", STR.get("greetings.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData("leave-channel-disable", STR.get("greetings.leave_channel.disable")),

                new SubcommandData("join-role", STR.get("greetings.join_role"))
                        .addOption(OptionType.ROLE, "role", STR.get("common.opt.role"), true),
                new SubcommandData("join-role-disable", STR.get("greetings.join_role.disable")),

                new SubcommandData("greetings-channel", STR.get("greetings.public")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", STR.get("greetings.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData("greetings-message", STR.get("greetings.public.message"))
                        .addOption(OptionType.STRING, "message", STR.get("greetings.opt.message"), true, true),
                new SubcommandData("greetings-disable", STR.get("greetings.public.disable")),

                new SubcommandData("greetings-private-message", STR.get("greetings.private.message"))
                        .addOption(OptionType.STRING, "message", STR.get("greetings.opt.message"), true, true),
                new SubcommandData("greetings-private-disable", STR.get("greetings.private.disable")),

                new SubcommandData("roles-add", STR.get("greetings.roles.add"))
                        .addOption(OptionType.STRING, "reaction", STR.get("greetings.opt.reaction"), true)
                        .addOption(OptionType.ROLE, "role", STR.get("common.opt.role"), true),
                new SubcommandData("roles-rm", STR.get("greetings.roles.rm"))
                        .addOption(OptionType.STRING, "reaction", STR.get("greetings.opt.reaction"), true, true),
                new SubcommandData("roles-list", STR.get("greetings.roles.list")),

                new SubcommandData("channel", STR.get("greetings.bureau")).addOptions(
                        new OptionData(OptionType.CHANNEL, "channel", STR.get("greetings.opt.channel"), true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData("disable", STR.get("greetings.bureau.disable")),
                new SubcommandData("message", STR.get("greetings.bureau.message"))
                        .addOption(OptionType.STRING, "message", STR.get("greetings.opt.message"), true, true)
                        .addOption(OptionType.STRING, "title", STR.get("greetings.opt.title"), false, true)

        );
    }

    @Override
    public String getName() {
        return "bureau";
    }

    @Override
    public String getShortDesc() {
        return STR.get("greetings.desc.short");
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
