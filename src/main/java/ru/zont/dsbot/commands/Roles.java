package ru.zont.dsbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.cli.Options;
import ru.zont.dsbot.players.PlayerCharacter;
import ru.zont.dsbot.players.PlayerProfile;
import ru.zont.dsbot.players.PlayersDB;
import ru.zont.dsbot.players.RgRoles;
import ru.zont.dsbot.util.RgPermissions;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.commands.CommandAdapter;
import ru.zont.dsbot.core.commands.Input;
import ru.zont.dsbot.core.commands.Routing;
import ru.zont.dsbot.core.commands.exceptions.InvalidSyntaxException;
import ru.zont.dsbot.core.util.MessageBatch;
import ru.zont.dsbot.core.util.MessageSplitter;
import ru.zont.dsbot.core.util.ResponseTarget;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.zont.dsbot.util.StringsRG.STR;

public class Roles extends RGCommandAdapter {

    public static final String USER_MENTION_REGEX = "<@!?(\\d+)>";

    public Roles(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    private void printProfiles(PlayerProfile ply,
                               @Nullable List<PlayerCharacter> chars,
                               ResponseTarget replyTo,
                               String title, int color) {
        ArrayList<String> strings = new ArrayList<>(chars != null ? chars.size() + 1 : 1);
        strings.add(STR.get("roles.list.player",
                ply.getDsId(), ply.getName(), ply.getSteamId(),
                PlayersDB.setToString(ply.getRoles())));

        if (chars != null)
            for (PlayerCharacter c : chars)
                strings.add(STR.get("roles.list.char",
                        c.getName(), c.getSide(),
                        PlayersDB.setToString(c.getRoles())));

        MessageBatch.sendNow(replyTo.respondEmbeds(MessageSplitter.embeds(
                String.join("\n\n", strings),
                new EmbedBuilder()
                        .setTitle(title)
                        .setColor(color))));
    }

    @Override
    public void onCall(ResponseTarget replyTo, Input input, MessageReceivedEvent event, Object... params) {
        final List<String> args = input.getCommandLine().getArgList();
        if (args.size() < 1)
            throw InvalidSyntaxException.insufficientArgs(STR.get("err.no_action_arg"), this);

        switch (args.get(0)) {
            case "add" -> add(input, replyTo);
            case "rm" -> rm(input, replyTo);
            case "list" -> list(input, replyTo);
            case "bind" -> bind(input, replyTo);
            case "known" -> known(input, replyTo);
            default -> throw InvalidSyntaxException.argument(1, STR.get("roles.err.action", args.get(0)), this);
        }
    }

    private void known(Input input, ResponseTarget replyTo) {
        final List<String> argList = input.getCommandLine().getArgList();
        if (argList.size() < 3)
            throw InvalidSyntaxException.insufficientArgs(STR.get("roles.err.known.args"), this);

        final String idStr = argList.get(1);
        if (!idStr.matches("-?\\s?\\d+"))
            throw InvalidSyntaxException.argument(2, STR.get("roles.err.known.id"), this);

        final int id = Integer.parseInt(idStr);
        String name = String.join(" ", argList.subList(2, argList.size()));
        RgRoles.addRole(id, name);
        replyTo.setOK();
    }

    private void add(Input input, ResponseTarget replyTo) {
        final List<String> argList = input.getCommandLine().getArgList();
        final LinkedList<Integer> roles = parseIds(input);
        final int idx = roles.size() + 1;
        final String disId = getDisId(argList, idx);
        final String steamId = getSteamId(argList, idx + 1);
        final PlayersDB db = PlayersDB.getInstance(getBot());

        final PlayerProfile player;
        if (steamId != null)
            player = db.addRoles(steamId, disId, roles);
        else player = db.addRoles(disId, roles);

        final LinkedList<PlayerCharacter> chars;
        if (!input.getCommandLine().hasOption("A")) {
            chars = db.addRolesToCharacters(player.getSteamId(), roles, input.getCommandLine().hasOption("a"));
        } else chars = null;

        printProfiles(player, chars, replyTo, STR.get("roles.list.updated"), 0x11d011);
    }

    private void rm(Input input, ResponseTarget replyTo) {
        final List<String> argList = input.getCommandLine().getArgList();
        final LinkedList<Integer> roles = parseIds(input);
        final int idx = roles.size() + 1;
        final String disId = getDisId(argList, idx);
        final PlayersDB db = PlayersDB.getInstance(getBot());

        final PlayerProfile player = db.rmRoles(disId, roles);

        final LinkedList<PlayerCharacter> chars = db
                .rmRolesFromCharactersByDisId(player.getDsId(), roles);

        printProfiles(player, chars, replyTo, STR.get("roles.list.updated"), 0x11d011);
    }

    private void list(Input input, ResponseTarget replyTo) {
        final List<String> argList = input.getCommandLine().getArgList();

        if (argList.size() < 2) {
            listRoles(replyTo);
            return;
        }

        try {
            final String disId = getDisId(argList, 1);
            listByDisId(disId, replyTo);
        } catch (InvalidSyntaxException ignored1) {
            try {
                final String steamId = getSteamId(argList, 1);
                listBySteamId(steamId, replyTo);
            } catch (InvalidSyntaxException ignored2) {
                if (argList.get(1).matches("[\\-+]?\\d+"))
                    listByRole(Integer.parseInt(argList.get(1)), replyTo);
                else
                    throw new InvalidSyntaxException(STR.get("roles.err.list"), this);
            }
        }
    }

    private void listByRole(int role, ResponseTarget replyTo) {
        final PlayersDB db = PlayersDB.getInstance(getBot());
        final LinkedList<PlayerProfile> players = db.getPlayersWithRole(role);

        final ArrayList<String> strings = new ArrayList<>();
        for (PlayerProfile player : players) {
            LinkedList<PlayerCharacter> chars = db.getPlayerCharacters(player.getSteamId());
            StringBuilder sb = new StringBuilder("<@%s> (%s) %s".formatted(
                    player.getDsId(), player.getSteamId(), PlayersDB.setToString(player.getRoles())));
            for (PlayerCharacter c : chars)
                sb.append("\n:arrow_forward: %s (%s) %s".formatted(
                        c.getName(), c.getSide(), PlayersDB.setToString(c.getRoles())));
            strings.add(sb.toString());
        }

        MessageBatch.sendNow(replyTo.respondEmbeds(MessageSplitter.embeds(
                String.join("\n", strings),
                new EmbedBuilder()
                        .setTitle(STR.get("roles.list.with_role"))
                        .setColor(0x1111d0)
                        .build())));
    }

    private void listBySteamId(String steamId, ResponseTarget replyTo) {
        final PlayersDB db = PlayersDB.getInstance(getBot());
        final PlayerProfile player = db.getPlayer(steamId);
        final LinkedList<PlayerCharacter> chars = db.getPlayerCharacters(player.getSteamId());
        printProfiles(player, chars, replyTo, STR.get("roles.list.title"), 0x1111d0);
    }

    private void listByDisId(String disId, ResponseTarget replyTo) {
        final PlayersDB db = PlayersDB.getInstance(getBot());
        final PlayerProfile player = db.getPlayerByDisId(disId);
        final LinkedList<PlayerCharacter> chars = db.getPlayerCharactersByDisId(disId);
        printProfiles(player, chars, replyTo, STR.get("roles.list.title"), 0x1111d0);
    }

    private void listRoles(ResponseTarget replyTo) {
        final HashMap<Integer, String> roles = new HashMap<>();
        final ArrayList<Integer> ids = new ArrayList<>();
        RgRoles.getKnownRoles().forEach((name, id) -> {
            if (!roles.containsKey(id)) {
                roles.put(id, name);
                ids.add(id);
            }
        });

        ids.sort(Integer::compareTo);
        final String content = String.join("\n",
                ids.stream()
                        .map(id -> "`%d`: %s".formatted(id, roles.get(id)))
                        .toList());

        MessageBatch.sendNow(replyTo.respondEmbeds(MessageSplitter.embeds(
                content,
                new EmbedBuilder()
                        .setTitle(STR.get("roles.list.known"))
                        .setColor(0x1111d0)
                        .build()
        )));
    }

    private void bind(Input input, ResponseTarget replyTo) {
        final List<String> argList = input.getCommandLine().getArgList();
        final String disId = getDisId(argList, 1);
        final String steamId = getSteamId(argList, 2);
        final PlayersDB db = PlayersDB.getInstance(getBot());

        db.bindPlayer(steamId, disId);
        replyTo.setOK();
    }

    private String getDisId(List<String> argList, int idx) {
        if (argList.size() < idx + 1)
            throw InvalidSyntaxException.insufficientArgs("", this);
        final String mention = argList.get(idx);

        final Matcher matcher = Pattern.compile(USER_MENTION_REGEX).matcher(mention);
        if (!matcher.matches())
            throw InvalidSyntaxException.argument(idx + 1, STR.get("roles.err.mention"), this);

        return matcher.group(1);
    }

    private String getSteamId(List<String> argList, int idx) {
        if (argList.size() < idx + 1)
            return null;
        final String steamId = argList.get(idx);

        if (!steamId.matches("7656\\d+"))
            throw InvalidSyntaxException.argument(idx + 1, STR.get("roles.err.steamid"), this);

        return steamId;
    }

    private LinkedList<Integer> parseIds(Input input) {
        final LinkedList<Integer> list = new LinkedList<>();
        final List<String> argList = input.getCommandLine().getArgList();
        for (String s : argList.subList(1, argList.size())) {
            if (s.matches("[\\-+]?\\d+"))
                list.add(Integer.parseInt(s));
            else break;
        }

        if (list.size() == 0)
            throw new InvalidSyntaxException(STR.get("roles.err.id"));

        return list;
    }

    @Override
    public Options getOptions() {
        return new Options()
                .addOption("a", "all", false, STR.get("roles.opt.all"))
                .addOption("A", "allow-only", false, STR.get("roles.opt.allow_only"));
    }

    @Override
    public String getName() {
        return "roles";
    }

    @Override
    public List<String> getAliases() {
        return List.of("r");
    }

    @Override
    public String getShortDesc() {
        return STR.get("roles.desc.short");
    }

    @Override
    public String getDescription() {
        return STR.get("roles.desc");
    }

    @Override
    public Routing getRouting() {
        return new Routing()
                .addRoute("add")
                .addRoute("rm")
                .addRoute("list")
                .addRoute("bind")
                .addRoute("known");
    }

    @Override
    public String getArgsSyntax() {
        return "<RoleID> [RoleID [RoleID ...]] <@user> [steamid]";
    }

    @Override
    public boolean allowForeignGuilds() {
        return false;
    }

    @Override
    public boolean checkPermission(RgPermissions util) {
        return util.permSetCanManagePlayers();
    }
}
