package ru.zont.dsbot.players;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.zont.dsbot.Main;
import ru.zont.dsbot.core.ZDSBot;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.security.auth.login.LoginException;
import java.sql.SQLException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlayersDBTest {

    private static ZDSBot bot;

    @BeforeAll
    static void setup() throws SQLException, LoginException, InterruptedException {
        bot = Main.mainBot("Nzg1MTc1OTUyODI0OTkxNzc1.GjF90S.OUSoggvx_DzsQwmiKXXKVaq2cxdt6fJQyFq9ZY");
        bot.setDbConnection("jdbc:mariadb://185.189.255.57:3306/arma3rg?user=rgbot&password=43092");
        bot.getJda().awaitReady();
    }

    @AfterAll
    static void shutdown() {
        bot.getJda().shutdown();
    }

    @Test
    void getPlayer() {
        PlayersDB pl = new PlayersDB(bot);
        PlayerProfile player = pl.getPlayer("76561198267425745");
        assertEquals("611906057501147158", player.dsId);
        assertTrue(player.roles.contains(1));
        assertFalse(player.roles.contains(-228));
    }

    @Test
    void setToString() throws ScriptException {
        String actual = PlayersDB.setToString(Set.of(-1, 1, 10, 22, 102));
        String expr = actual
                .replaceAll("\\[ ", "")
                .replaceAll(" ]", "")
                .replaceAll(", ", "+");
        assertTrue(actual.startsWith("[ "));
        assertTrue(actual.endsWith(" ]"));
        assertEquals("-", expr.replaceAll("\\+", "").replaceAll("\\d", ""));

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
        if (engine == null) {
            System.err.println("JS engine not found!");
            return;
        }

        assertEquals(10+22+102, engine.eval(expr));
    }
}