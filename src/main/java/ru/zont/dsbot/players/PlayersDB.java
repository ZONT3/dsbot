package ru.zont.dsbot.players;

import org.jetbrains.annotations.NotNull;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.util.DescribedException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static ru.zont.dsbot.util.StringsRG.STR;

public class PlayersDB {
    private static final WeakHashMap<ZDSBot, PlayersDB> instances = new WeakHashMap<>();

    public static PlayersDB getInstance(ZDSBot bot) {
        if (instances.containsKey(bot))
            return instances.get(bot);
        else {
            PlayersDB ref = new PlayersDB(bot);
            instances.put(bot, ref);
            return ref;
        }
    }

    private final ZDSBot bot;

    public PlayersDB(ZDSBot bot) {
        this.bot = bot;
    }

    public PlayerProfile getPlayer(String steamId) {
        return bot.getDbConnectionHandler().withPrepStatement("""
                SELECT * FROM profiles
                WHERE p_uid = ?
                """, (st) -> {
            st.setString(1, steamId);
            ResultSet r = st.executeQuery();
            if (!r.next())
                throw new DescribedException("No such player SteamID in the database");
            return getPlayerProfile(r);
        });
    }

    public PlayerProfile getPlayerByDisId(String disId) {
        return bot.getDbConnectionHandler().withPrepStatement("""
                SELECT * FROM profiles
                WHERE p_id_dis = ?
                """, st -> {
            st.setString(1, disId);
            ResultSet r = st.executeQuery();
            if (!r.next())
                throw new PlayerNotBoundException();
            return getPlayerProfile(r);
        });
    }

    public void bindPlayer(String steamId, String disId) {
        bot.getDbConnectionHandler().withPrepStatement("""
                INSERT INTO profiles (p_id_dis, p_uid, p_roles)
                VALUES (?, ?, '[ ]')
                ON DUPLICATE KEY UPDATE
                p_id_dis = ?
                """, st -> {
            st.setString(1, disId);
            st.setString(2, steamId);
            st.setString(3, disId);
            st.executeUpdate();
        });
    }

    public void updateRoles(String steamId, String disId, Set<Integer> newRoles) {
        bot.getDbConnectionHandler().withPrepStatement("""
                INSERT INTO profiles (p_id_dis, p_uid, p_roles)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                p_id_dis = ?, p_roles = ?
                """, st -> {
            String roles = setToString(newRoles);
            st.setString(1, disId);
            st.setString(2, steamId);
            st.setString(3, roles);
            st.setString(4, disId);
            st.setString(5, roles);
            st.executeUpdate();
        });
    }

    public PlayerProfile addRoles(String disId, Collection<Integer> newRoles) {
        PlayerProfile ply = getPlayerByDisId(disId);
        addRoles(ply, disId, newRoles);
        return ply;
    }

    public PlayerProfile addRoles(String steamId, String disId, Collection<Integer> newRoles) {
        PlayerProfile player = getPlayer(steamId);
        addRoles(player, disId, newRoles);
        return player;
    }

    public void addRoles(PlayerProfile player, String disId, Collection<Integer> newRoles) {
        player.roles.addAll(newRoles);
        updateRoles(player.steamId, disId, player.roles);
    }

    public PlayerProfile rmRoles(String disId, Collection<Integer> toRm) {
        PlayerProfile player = getPlayerByDisId(disId);
        rmRoles(player, toRm);
        return player;
    }
    
    public void rmRoles(PlayerProfile player, Collection<Integer> toRm) {
        toRm.forEach(player.roles::remove);
        updateRoles(player.steamId, player.dsId, player.roles);
    }

    public LinkedList<PlayerProfile> getPlayersWithRole(int role) {
        return bot.getDbConnectionHandler().withPrepStatement("""
            SELECT p_id_dis, p_roles, p_name, p_uid, p_id FROM profiles
            """, st -> {
            ResultSet r = st.executeQuery();
            LinkedList<PlayerProfile> res = new LinkedList<>();
            while (r.next()) {
                PlayerProfile player = getPlayerProfile(r);
                HashSet<Integer> roles = player.getRoles();
                if (roles.contains(role))
                    res.add(player);
            }
            return res;
        });
    }

    public LinkedList<PlayerCharacter> getPlayerCharacters(String steamId) {
        return bot.getDbConnectionHandler().withPrepStatement("""
                SELECT c_id, c_name, c_uid, c_roles, c_lastupd, c_side
                FROM characters
                WHERE c_uid = ?
                ORDER BY c_lastupd DESC
                """, st -> {
            st.setString(1, steamId);
            ResultSet r = st.executeQuery();
            LinkedList<PlayerCharacter> res = new LinkedList<>();
            while (r.next())
                res.add(getPlayerCharacter(r));
            return res;
        });
    }

    public LinkedList<PlayerCharacter> getPlayerCharactersByDisId(String disId) {
        return bot.getDbConnectionHandler().withPrepStatement("""
                SELECT c_id, c_name, c_uid, c_roles, c_lastupd, c_side
                FROM characters, profiles
                WHERE c_uid = p_uid AND p_id_dis = ?
                ORDER BY c_lastupd DESC
                """, st -> {
            st.setString(1, disId);
            ResultSet r = st.executeQuery();
            LinkedList<PlayerCharacter> res = new LinkedList<>();
            while (r.next())
                res.add(getPlayerCharacter(r));
            return res;
        });
    }

    public void updateRolesForCharacter(PlayerCharacter chr, HashSet<Integer> roles) {
        bot.getDbConnectionHandler().withPrepStatement("""
                UPDATE characters
                SET c_roles = ?
                WHERE c_id = ?
                LIMIT 1
                """, st -> {
            st.setString(1, setToString(roles));
            st.setLong(2, chr.id);
            st.executeBatch();
        });
    }

    public LinkedList<PlayerCharacter> addRolesToCharacters(String steamId, Collection<Integer> newRoles, boolean all) {
        LinkedList<PlayerCharacter> chars = getPlayerCharacters(steamId);
        addRolesToCharacters(chars, newRoles, all);
        return chars;
    }

    public void addRolesToCharacters(List<PlayerCharacter> chars, Collection<Integer> newRoles, boolean all) {
        for (PlayerCharacter c : chars) {
            c.roles.addAll(newRoles);
            updateRolesForCharacter(c, c.roles);
            if (!all) break;
        }
    }

    public LinkedList<PlayerCharacter> rmRolesFromCharactersByDisId(String disId, Collection<Integer> toRm) {
        LinkedList<PlayerCharacter> chars = getPlayerCharactersByDisId(disId);
        rmRolesFromCharacters(chars, toRm);
        return chars;
    }

    public void rmRolesFromCharacters(List<PlayerCharacter> chars, Collection<Integer> toRm) {
        for (PlayerCharacter c : chars) {
            toRm.forEach(c.roles::remove);
            updateRolesForCharacter(c, c.roles);
        }
    }

    @NotNull
    private PlayerProfile getPlayerProfile(ResultSet r) throws SQLException {
        return new PlayerProfile(
                r.getLong("p_id"),
                r.getString("p_name"),
                r.getString("p_id_dis"),
                r.getString("p_uid"),
                parseSet(r.getString("p_roles"))
        );
    }

    @NotNull
    private PlayerCharacter getPlayerCharacter(ResultSet r) throws SQLException {
        return new PlayerCharacter(
                r.getLong("c_id"),
                r.getString("c_name"),
                r.getString("c_uid"),
                parseSet(r.getString("c_roles")),
                r.getTimestamp("c_lastupd"),
                r.getString("c_side")
        );
    }

    public static String setToString(Set<Integer> set) {
        return String.join(" ",
                "[",
                String.join(", ", set.stream().map(String::valueOf).toList()),
                "]");
    }

    public static HashSet<Integer> parseSet(String str) {
        return new HashSet<>(
                Arrays.stream(str.replaceAll("[\\[\\]\s]", "").split(","))
                        .filter(s -> s.matches("\\d+"))
                        .map(Integer::parseInt)
                        .toList()
        );
    }

    public static class PlayerNotBoundException extends DescribedException {
        public PlayerNotBoundException() {
            super(STR.get("ply.err.not_bound"), STR.get("ply.err.not_bound.desc"));
        }
    }
}
