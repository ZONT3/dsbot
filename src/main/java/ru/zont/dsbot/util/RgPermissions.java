package ru.zont.dsbot.util;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.CommandAdapter;
import ru.zont.dsbot.core.commands.PermissionsUtil;

public class RgPermissions extends PermissionsUtil {

    public static RgPermissions newInstance(CommandAdapter adapter, MessageReceivedEvent event) {
        return new RgPermissions(adapter.getBot(), adapter.getContext(), event);
    }

    public static RgPermissions newInstance(CommandAdapter adapter, SlashCommandInteractionEvent event) {
        return new RgPermissions(adapter.getBot(), adapter.getContext(), event);
    }

    public RgPermissions(ZDSBot bot, GuildContext context, MessageReceivedEvent event) {
        super(bot, context, event);
    }

    public RgPermissions(ZDSBot bot, GuildContext context, SlashCommandInteractionEvent event) {
        super(bot, context, event);
    }

    public boolean permSetCanManagePlayers() {
        return checkGuildAdmin() || checkAnyRoleFrom(getContext().getConfig().rolesCanManagePlayers.toList());
    }
}
