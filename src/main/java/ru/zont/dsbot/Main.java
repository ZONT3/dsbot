package ru.zont.dsbot;

import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zont.dsbot.commands.*;
import ru.zont.dsbot.http.GoogleFormHandler;
import ru.zont.dsbot.listeners.DCSWatcher;
import ru.zont.dsbot.listeners.GreetingsListener;
import ru.zont.dsbot.listeners.MediaWatcher;
import ru.zont.dsbot.listeners.TSWatcher;
import ru.zont.dsbot.core.ZDSBot;
import ru.zont.dsbot.core.util.ZDSBotBuilder;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.sql.SQLException;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Object lock = new Object();

    public static void main(String[] args) throws LoginException, InterruptedException, SQLException, IOException {
        checkArgs(args);
        final ZDSBot bot = mainBot(args[0]);

        if (args.length > 1 && args[1].startsWith("jdbc"))
            bot.setDbConnection(args[1]);

        bot.getJda().awaitReady();

        GoogleFormHandler googleFormHandler = new GoogleFormHandler(bot);

        try {
            while (!Thread.interrupted()) {
                synchronized (lock) {
                    lock.wait();
                }
            }
        } catch (InterruptedException ignored) { }
        log.info("Main thread interrupted");

        googleFormHandler.shutdown();
        bot.getJda().shutdown();
    }

    private static void checkArgs(String[] args) {
        if (args.length < 1)
            throw new IllegalStateException("Too few arguments. Should supply bot token, jdbc");
    }

    public static ZDSBot mainBot(String token) throws LoginException, InterruptedException {
        return ZDSBotBuilder.createLight(token)
                .config(ConfigRG.class, ConfigRG.BotConfig.class)
                .allCoreCommands()
                .addCommandAdapters(Roles.class, Say.class, Clear.class, DCSServers.class, TSServers.class,
                        Greetings.class, Media.class, Admin.class, Config.class)
                .addGuildListeners(GreetingsListener.class, DCSWatcher.class, TSWatcher.class, MediaWatcher.class)
                .addDefaultIntents()
                .onJdaBuilder(b -> b.enableIntents(GatewayIntent.GUILD_MESSAGE_REACTIONS))
                .setCacheAll()
                .loadVersionName("version")
                .build();
    }
}
