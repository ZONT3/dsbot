package ru.zont.dsbot.media;

import net.dv8tion.jda.api.entities.MessageEmbed;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.util.LiteJSON;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public abstract class MediaData<T> {
    private final int maxPostsMemory;

    private final Deque<String> lastPosts;
    private final LiteJSON postsData;

    private LocalDateTime nextPoint;

    public MediaData(int maxPostsMemory) {
        this.maxPostsMemory = maxPostsMemory;
        this.lastPosts = new ArrayDeque<>(maxPostsMemory);
        this.postsData = GuildContext.getInstanceGlobal("media-posts", () -> new LiteJSON("media-posts"));
    }

    public abstract String getName(String link);

    public int getUpdatePeriodMinutes() {
        return 15;
    }

    public boolean shouldUpdate() {
        return nextPoint == null || LocalDateTime.now().isAfter(nextPoint);
    }

    public void scheduleNextUpdate() {
        nextPoint = LocalDateTime.now().plusMinutes(getUpdatePeriodMinutes());
    }

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

    public abstract boolean linksHere(String link);

    protected abstract List<T> getFirstPosts(String link);

    protected abstract List<T> getNextPosts(String link, long lastPosted);

    protected abstract MessageEmbed buildEmbed(T post);

    protected abstract String getId(T post);

    protected abstract long getPostTimestamp(T post);
}
