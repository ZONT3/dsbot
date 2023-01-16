package ru.zont.dsbot.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zont.dsbot.ConfigRG;
import ru.zont.dsbot.util.StringsRG;
import ru.zont.dsbot.core.GuildContext;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.config.ZDSBConfig;
import ru.zont.dsbot.core.listeners.GuildListenerAdapter;
import ru.zont.dsbot.core.util.DescribedException;
import ru.zont.dsbot.core.util.ResponseTarget;

import java.util.*;

public class GreetingsListener extends GuildListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(GreetingsListener.class);
    public static final int COLOR = 0x23A510;

    private boolean bureauInitialized = false;
    private String bureauMessage;
    private String bureauMessageTitle;
    private MessageChannel bureauChannel;
    private Message greetingsMessageObject;
    private HashMap<String, String> reactionsToRoles;
    private List<String> reactions;

    public GreetingsListener(ZDSBot bot, GuildContext context) {
        super(bot, context);
    }

    @Override
    public boolean init(Guild guild) {
        super.init(guild);
        bureauInitialized = false;

        GuildContext context = getContext();
        final ConfigRG cfg = ConfigRG.castConfig(getConfig());
        assert context != null;

        if (!ZDSBConfig.checkConfigEntries(null, cfg.greetingsBureauChannel, cfg.greetingsMessageBureau)) {
            log.warn(context.formatLog("Has unfilled bureau config entries, disabling"));
            return true;
        }

        bureauMessage = cfg.greetingsMessageBureau.getString();
        bureauMessageTitle = cfg.greetingsMessageBureauTitle.getString();

        final String bureauChannelId = cfg.greetingsBureauChannel.getString();
        bureauChannel = context.findChannel(bureauChannelId);

        if (bureauChannel == null)
            log.warn(context.formatLog("Cannot find bureau channel id{}"), bureauChannelId);

        updateReactions(cfg);
        initBureauChannel();

        return true;
    }

    @Override
    public void onEvent(Guild guild, GenericEvent event) {
        assert getContext() != null;

        try {
            if (event instanceof GuildMemberJoinEvent e)
                onMemberJoin(e);
            else if (event instanceof GuildMemberRemoveEvent e)
                onMemberLeave(e);
            else if (event instanceof GenericMessageReactionEvent e)
                onReaction(e);
        } catch (HierarchyException e) {
            getContext().getErrorReporter().reportError(null, e);
        } catch (Exception e) {
            bureauInitialized = false;
            GuildContext context = getContext();
            assert context != null;
            log.error("Stopping [Greetings] for guild {} due to error below", context.getGuildNameNormalized());
            getContext().getErrorReporter().reportError(null, e);
        }
    }

    private void onMemberLeave(GuildMemberRemoveEvent event) {
        GuildContext context = getContext();
        ConfigRG cfg = ConfigRG.castConfig(getConfig());
        assert context != null;

        MessageChannel channel = context.findChannel(cfg.greetingsLeaveChannel.getString());
        if (channel == null) return;

        channel.sendMessageEmbeds(new EmbedBuilder()
                .setDescription(StringsRG.STR.get("greetings.left",
                        event.getUser().getAsMention(),
                        event.getUser().getAsTag()))
                .setColor(ResponseTarget.WARNING_COLOR)
                .build()).queue();
    }

    private void onMemberJoin(GuildMemberJoinEvent event) {
        final Member member = event.getMember();
        if (member.getUser().isBot()) return;

        GuildContext context = getContext();
        assert context != null;
        ConfigRG cfg = ConfigRG.castConfig(getConfig());

        String autoRoleId = cfg.greetingsAutoRole.getString();
        if (autoRoleId != null) {
            Guild guild = context.getGuild();
            Role autoRole = guild.getRoleById(autoRoleId);
            if (autoRole != null)
                guild.addRoleToMember(member, autoRole).queue();
        }

        if (!bureauInitialized) return;
        final String memberMention = member.getAsMention();
        final String channelMention = bureauChannel.getAsMention();

        MessageChannel publicChannel = context.findChannel(cfg.greetingsChannel.getString());
        String greetingsMessagePublic = cfg.greetingsMessagePublic.getString();
        String greetingsMessagePrivate = cfg.greetingsMessagePrivate.getString();

        if (publicChannel != null && greetingsMessagePublic != null)
            publicChannel.sendMessage(greetingsMessagePublic.formatted(memberMention, channelMention)).queue();
        if (greetingsMessagePrivate != null)
            member.getUser().openPrivateChannel().complete()
                .sendMessage(greetingsMessagePrivate.formatted(memberMention, channelMention))
                .queue();
    }

    private void onReaction(GenericMessageReactionEvent event) {
        if (!bureauInitialized) return;
        final String emoji = getValidReaction(event);
        if (emoji == null)
            return;

        final Member member = event.retrieveMember().complete();
        if (member.getUser().isBot())
            return;

        final String roleId = reactionsToRoles.get(emoji);
        final GuildContext context = getContext();
        assert context != null;
        final Guild guild = context.getGuild();
        final Role role = guild.getRoleById(roleId);
        if (role == null)
            throw new IllegalStateException("Unknown role " + roleId);

        if (event instanceof MessageReactionAddEvent)
            guild.addRoleToMember(member, role).queue();
        else if (event instanceof MessageReactionRemoveEvent)
            guild.removeRoleFromMember(member, role).queue();
    }

    private String getValidReaction(GenericMessageReactionEvent event) {
        if (!event.isFromGuild() || greetingsMessageObject == null)
            return null;
        if (!event.getMessageId().equals(greetingsMessageObject.getId()))
            return null;
        if (!event.getReactionEmote().isEmoji())
            return null;
        final MessageReaction.ReactionEmote emoji = event.getReactionEmote();

        Optional<String> reaction = reactions.stream()
                .filter(s -> s.equalsIgnoreCase(emoji.getAsCodepoints()) || s.equals(emoji.getEmoji()))
                .findAny();
        return reaction.orElse(null);
    }

    public void updateReactions(ConfigRG cfg) {
        final List<String> roles = cfg.greetingsRoles.toList();
        reactions = cfg.greetingsReactions.toList()
                .stream()
                .map(String::toUpperCase)
                .toList();
        if (roles.size() != reactions.size())
            throw new DescribedException("Roles and reactions doesn't match sizes");

        reactionsToRoles = new HashMap<>();
        for (int i = 0; i < reactions.size(); i++)
            reactionsToRoles.put(reactions.get(i), roles.get(i));
    }

    public void initBureauChannel() {
        if (bureauChannel == null) return;
        bureauInitialized = true;

        final List<Message> messages = bureauChannel.getHistory().retrievePast(100).complete();

        final String bureauTitle = bureauMessageTitle != null
                ? bureauMessageTitle
                : StringsRG.STR.get("greetings.bureau.title");
        final MessageEmbed finalEmbed = new EmbedBuilder()
                .setTitle(bureauTitle)
                .setColor(COLOR)
                .setDescription(bureauMessage)
                .build();

        for (Message m : messages) {
            List<MessageEmbed> embeds = m.getEmbeds();
            if (embeds.size() > 0) {
                MessageEmbed embed = embeds.get(0);
                if (embed.getTitle() != null && embed.getTitle().equals(bureauTitle)) {
                    greetingsMessageObject = m;
                    addReactions(greetingsMessageObject);
                    if (!Objects.equals(finalEmbed.getDescription(), embed.getDescription()))
                        m.editMessageEmbeds(finalEmbed).queue();
                    return;
                }
            }
        }

        greetingsMessageObject = bureauChannel.sendMessageEmbeds(finalEmbed).complete();
        addReactions(greetingsMessageObject);
    }

    private void addReactions(Message message) {
        for (String reaction : reactions)
            message.addReaction(reaction).complete();
    }

    @Override
    public Set<Class<? extends GenericEvent>> getTypes() {
        return Set.of(GuildMemberJoinEvent.class, MessageReactionAddEvent.class, MessageReactionRemoveEvent.class,
                GuildMemberRemoveEvent.class);
    }

    @Override
    public boolean allowGlobal() {
        return false;
    }
}
