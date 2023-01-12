package ru.zont.dsbot.players;

import java.util.HashSet;

public class PlayerProfile {
    final long id;
    final String name;
    final String dsId;
    final String steamId;
    final HashSet<Integer> roles;

    public PlayerProfile(long id, String name, String dsId, String steamId, HashSet<Integer> roles) {
        this.id = id;
        this.name = name;
        this.dsId = dsId;
        this.steamId = steamId;
        this.roles = roles;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDsId() {
        return dsId;
    }

    public String getSteamId() {
        return steamId;
    }

    public HashSet<Integer> getRoles() {
        return roles;
    }
}
