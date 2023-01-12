package ru.zont.dsbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.Input;
import ru.zont.dsbot.core.commands.PermissionsUtil;
import ru.zont.dsbot.core.commands.SlashCommandAdapter;
import ru.zont.dsbot.core.util.ResponseTarget;
import ru.zont.dsbot.core.commands.exceptions.InsufficientPermissionsException;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.commands.exceptions.OnlySlashUsageException;

import java.time.OffsetDateTime;
import java.util.List;

import static ru.zont.dsbot.util.StringsRG.STR;

public class Clear extends SlashCommandAdapter {
    public Clear(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public void onCall(ResponseTarget replyTo, Input input, MessageReceivedEvent event, Object... params) {
        throw new OnlySlashUsageException();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        OptionMapping amountOpt = event.getOption("amount");

        if (amountOpt == null && event.getOptions().stream().map(OptionMapping::getName).noneMatch(s -> s.startsWith("past")))
            throw new InvalidSyntaxException(STR.get("clear.err.required_opt"));

        MessageChannel target = event.getOption("target", event.getChannel(), OptionMapping::getAsTextChannel);
        checkChannel(target, event.getMember());

        if (amountOpt != null) {
            clear(amountOpt.getAsInt(), target);
        } else {
            final float past =
                    event.getOption("past-days", 0., OptionMapping::getAsDouble).floatValue() * 24f * 60f +
                    event.getOption("past-hours", 0., OptionMapping::getAsDouble).floatValue() * 60f +
                    event.getOption("past-minutes", 0., OptionMapping::getAsDouble).floatValue();
            clear(past, event.getTimeCreated(), target);
        }
        if (target == event.getChannel())
            event.getHook().deleteOriginal().queue();
        else new ResponseTarget(event).setOK();
    }

    private void checkChannel(MessageChannel target, Member member) {
        if (target == null) throw new InvalidSyntaxException(STR.get("clear.err.target"));
        if (target.getType() == ChannelType.PRIVATE)
            return;
        if (member == null)
            throw new NullPointerException("Member WTF");
        if (target instanceof TextChannel c)
            if (c.canTalk(member) && member.hasPermission(c, Permission.MESSAGE_MANAGE))
                return;
        throw new InsufficientPermissionsException(STR.get("clear.err.permission"));
    }

    private void clear(int amount, MessageChannel channel) {
        channel.getIterableHistory()
                .takeAsync(amount)
                .thenAccept(list -> channel.purgeMessages(list.stream()
                        .filter(m -> !m.getFlags().contains(Message.MessageFlag.LOADING))
                        .toList()));
    }

    private void clear(double past, OffsetDateTime timeCreated, MessageChannel channel) {
        long now = timeCreated.toEpochSecond();
        long pastSeconds = (long) (past * 60);
        List<Message> list = channel.getIterableHistory()
                .takeAsync(100)
                .thenApply(l -> l.stream()
                        .filter(m -> now - m.getTimeCreated().toEpochSecond() <= pastSeconds)
                        .filter(m -> !m.getFlags().contains(Message.MessageFlag.LOADING))
                        .toList())
                .join();

        channel.purgeMessages(list);
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc()).addOptions(
                new OptionData(OptionType.INTEGER, "amount", STR.get("clear.opt.amount"))
                        .setRequiredRange(2, 100),
                new OptionData(OptionType.NUMBER, "past-minutes", STR.get("clear.opt.past"))
                        .setRequiredRange(0.5, 60.),
                new OptionData(OptionType.NUMBER, "past-hours", STR.get("clear.opt.past.hours"))
                        .setRequiredRange(0.5, 24. * 30.),
                new OptionData(OptionType.NUMBER, "past-days", STR.get("clear.opt.past.days"))
                        .setRequiredRange(0.5, 60.),
                new OptionData(OptionType.CHANNEL, "target", STR.get("clear.opt.target"))
                        .setChannelTypes(ChannelType.TEXT)
        );
    }

    @Override
    public boolean checkPermission(PermissionsUtil util) {
        return util.permSetMessageManage();
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public String getShortDesc() {
        return STR.get("clear.desc.short");
    }
}
