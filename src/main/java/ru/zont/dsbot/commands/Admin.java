package ru.zont.dsbot.commands;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.util.ResponseTarget;
import ru.zont.dsbot.util.RgPermissions;

import java.util.Objects;

import static ru.zont.dsbot.util.StringsRG.STR;

public class Admin extends RGSlashCommandAdapter {
    public Admin(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public String getShortDesc() {
        return STR.get("admin.desc.short");
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case "set" -> {
                final Role role = event.getOption("role", OptionMapping::getAsRole);
                if (role != null)
                    ConfigRG.castConfig(getConfig()).botAdminRole.setValue(role.getId());
            }
            case "disable" -> ConfigRG.castConfig(getConfig()).botAdminRole.clearValue();
            default -> throw new IllegalStateException();
        }
        new ResponseTarget(event).setOK();
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), getShortDesc()).addSubcommands(
                new SubcommandData("set", STR.get("admin.set"))
                        .addOption(OptionType.ROLE, "role", STR.get("common.opt.role"), true),
                new SubcommandData("disable", STR.get("admin.disable"))
        );
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public boolean checkPermission(RgPermissions util) {
        return util.permSetOnlyTrueAdmin();
    }
}
