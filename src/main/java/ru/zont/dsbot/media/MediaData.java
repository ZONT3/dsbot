package ru.zont.dsbot.media;

import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

public interface MediaData {
    String getChannelTitle(String link);

    boolean shouldUpdate();

    void scheduleNextUpdate();

    List<MessageEmbed> getNewPosts(String link);

    boolean linksHere(String link);

    String getLogo();

    int getColor();

    String getName();
}
