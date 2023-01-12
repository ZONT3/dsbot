package ru.zont.dsbot.players;

import java.sql.Timestamp;
import java.util.HashSet;

public class PlayerCharacter {
    final long id;
    final String name;
    final String steamId;
    final HashSet<Integer> roles;
    final Timestamp lastupd;
    final String side;

    public PlayerCharacter(long id, String name, String steamId, HashSet<Integer> roles, Timestamp lastupd, String side) {
        this.id = id;
        this.name = name;
        this.steamId = steamId;
        this.roles = roles;
        this.lastupd = lastupd;
        this.side = side;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSteamId() {
        return steamId;
    }

    public HashSet<Integer> getRoles() {
        return roles;
    }

    public Timestamp getLastupd() {
        return lastupd;
    }

    public String getSide() {
        return side;
    }
}
