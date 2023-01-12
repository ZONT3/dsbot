package ru.zont.dsbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.apache.commons.cli.Options;
import ru.zont.dsbot.util.StringsRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.Input;
import ru.zont.dsbot.core.commands.PermissionsUtil;
import ru.zont.dsbot.core.commands.SlashCommandAdapter;
import ru.zont.dsbot.core.util.ResponseTarget;

public class Test extends SlashCommandAdapter {
    public Test(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public void onCall(ResponseTarget replyTo, Input input, MessageReceivedEvent event, Object... params) {
        final String content = input.getContent();
        final String contentUnrecognized = input.getContentUnrecognized();
        final String contentFull = input.getContentFull();

        replyTo.respondEmbed(new EmbedBuilder()
                        .setTitle(StringsRG.STR.get("rg.test"))
                        .setDescription("""
                                Content: `%s`
                                Unrecognized: `%s`
                                Full: `%s`
                                """.formatted(content, contentUnrecognized, contentFull))
                .build()).queue();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        OptionMapping anInt = event.getOption("int");
        OptionMapping aNumber = event.getOption("number");
        OptionMapping aString = event.getOption("content");
        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setTitle(StringsRG.STR.get("rg.test"))
                .setDescription("""
                                Int: `%d`
                                Number: `%f`
                                Content: `%s`
                                Options: `%s`
                                """.formatted(
                                        anInt != null ? anInt.getAsInt() : 0,
                                        aNumber != null ? aNumber.getAsDouble() : 0.,
                                        aString != null ? aString.getAsString() : "null",
                                        event.getOptions().toString()))
                .build()).queue();
    }

    @Override
    public SlashCommandData getSlashCommand() {
        return Commands.slash(getName(), "Test command")
                .addOption(OptionType.STRING, "content", "Test string", true)
                .addOption(OptionType.INTEGER, "int", "Test int")
                .addOption(OptionType.NUMBER, "number", "Test number");
    }

    @Override
    public Options getOptions() {
        return new Options()
                .addOption("a", "aboba", false, "eblo")
                .addOption("s", "suka", true, "eblo");
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String getShortDesc() {
        return "test command";
    }

    @Override
    public boolean checkPermission(PermissionsUtil util) {
        return util.permSetAdmin();
    }

    @Override
    public boolean isGlobal() {
        return false;
    }
}
