package ru.zont.dsbot.util;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.CommandAdapter;
import ru.zont.dsbot.core.commands.PermissionsUtil;

import java.util.Objects;

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

    @Override
    protected ConfigRG getConfig() {
        return ConfigRG.castConfig(super.getConfig());
    }

    public boolean permSetCanManagePlayers() {
        return checkGuildAdmin() || checkAnyRoleFrom(getContext().getConfig().rolesCanManagePlayers.toList());
    }

    public boolean permSetOnlyTrueAdmin() {
        return super.permSetAdmin();
    }

    @Override
    public boolean permSetAdmin() {
        if (super.permSetAdmin()) return true;

        final String roleId = getConfig().botAdminRole.getString();
        if (roleId == null) return false;

        checkMember();
        return getMember().getRoles().stream()
                .map(Role::getId)
                .anyMatch(roleId::equals);
    }

    @Override
    public boolean permSetAdminFromMain() {
        GuildContext c = Objects.requireNonNull(getBot().getMainGuildContext());
        Member m = c.getGuild().getMember(getAuthor());
        if (m == null) return false;
        if (m.isOwner() || m.hasPermission(Permission.ADMINISTRATOR)) return true;

        final String roleId = getConfig().botAdminRole.getString();
        if (roleId == null) return false;

        return m.getRoles().stream()
                .map(Role::getId)
                .anyMatch(roleId::equals);
    }
}
