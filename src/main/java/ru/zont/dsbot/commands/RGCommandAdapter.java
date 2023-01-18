package ru.zont.dsbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.CommandAdapter;
import ru.zont.dsbot.core.commands.PermissionsUtil;
import ru.zont.dsbot.util.RgPermissions;

public abstract class RGCommandAdapter extends CommandAdapter {
    public RGCommandAdapter(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public boolean checkPermission(MessageReceivedEvent event) {
        return checkPermission(RgPermissions.newInstance(this, event));
    }

    @Override
    public final boolean checkPermission(PermissionsUtil util) {
        throw new IllegalStateException();
    }

    public abstract boolean checkPermission(RgPermissions util);
}
