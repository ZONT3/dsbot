package ru.zont.dsbot.util;

import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ru.zont.dsbot.core.config.ZDSBConfig;
import ru.zont.dsbot.core.util.DescribedException;

import java.util.Objects;

import static ru.zont.dsbot.util.StringsRG.STR;

public class CommandSupport {
    public static void setMessage(ZDSBConfig.Entry entry, SlashCommandInteractionEvent event) {
        entry.setValue(Objects.requireNonNull(event.getOption("message", OptionMapping::getAsString)));
    }

    public static void setChannel(ZDSBConfig.Entry entry, SlashCommandInteractionEvent event) {
        GuildMessageChannel channel = event.getOption("channel", OptionMapping::getAsMessageChannel);
        if (channel == null || !channel.canTalk())
            throw new DescribedException(StringsRG.STR.get("err.cannot_talk.title"), StringsRG.STR.get("err.cannot_talk"));
        entry.setValue(channel.getId());
    }


    public static void checkRole(SlashCommandInteractionEvent event, Role role) {
        if (role == null) throw new IllegalStateException();
        if (!Objects.requireNonNull(event.getGuild()).getSelfMember().canInteract(role))
            throw new DescribedException(STR.get("greetings.err.cannot_interact"));
    }
}
