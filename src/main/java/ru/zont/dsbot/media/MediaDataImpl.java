package ru.zont.dsbot.media;

import net.dv8tion.jda.api.entities.MessageEmbed;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.util.LiteJSON;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public abstract class MediaDataImpl<T> implements MediaData {
    private final int maxPostsMemory;

    private final Deque<String> lastPosts;
    private final LiteJSON postsData;

    private LocalDateTime nextPoint;

    public MediaDataImpl(int maxPostsMemory) {
        this.maxPostsMemory = maxPostsMemory;
        this.lastPosts = new ArrayDeque<>(maxPostsMemory);
        this.postsData = GuildContext.getInstanceGlobal("media-posts", () -> new LiteJSON("media-posts"));
    }

    @Override
    public abstract String getChannelTitle(String link);

    protected int getUpdatePeriodMinutes() {
        return 15;
    }

    @Override
    public boolean shouldUpdate() {
        return nextPoint == null || LocalDateTime.now().isAfter(nextPoint);
    }

    @Override
    public void scheduleNextUpdate() {
        nextPoint = LocalDateTime.now().plusMinutes(getUpdatePeriodMinutes());
    }

    @Override
    public final List<MessageEmbed> getNewPosts(String link) {
        List<T> posts;
        if (!postsData.get().has(link)) posts = getFirstPosts(link);
        else posts = getNextPosts(link, postsData.get().get(link).getAsLong());

        if (posts.isEmpty()) return Collections.emptyList();
        storePosted(posts, link);
        return posts.stream()
                .map(this::buildEmbed)
                .toList();
    }

    protected final void storePosted(List<T> posts, String link) {
        posts.forEach(v -> lastPosts.addLast(getId(v)));
        while (lastPosts.size() > maxPostsMemory)
            lastPosts.removeFirst();

        postsData.op(o -> {
            o.addProperty(link, posts.stream()
                    .map(this::getPostTimestamp)
                    .reduce(0L, Math::max));
        });
    }

    protected final boolean notPostedRecently(T post) {
        return !lastPosts.contains(getId(post));
    }

    @Override
    public abstract boolean linksHere(String link);

    protected abstract List<T> getFirstPosts(String link);

    protected abstract List<T> getNextPosts(String link, long lastPosted);

    protected abstract MessageEmbed buildEmbed(T post);

    protected abstract String getId(T post);

    protected abstract long getPostTimestamp(T post);

    @Override
    public abstract String getLogo();

    @Override
    public abstract int getColor();
}
