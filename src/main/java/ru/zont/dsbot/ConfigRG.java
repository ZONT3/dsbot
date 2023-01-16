package ru.zont.dsbot;

import ru.zont.dsbot.core.config.ZDSBBasicConfig;
import ru.zont.dsbot.core.config.ZDSBBotConfig;
import ru.zont.dsbot.core.config.ZDSBConfig;

import java.io.File;

public class ConfigRG extends ZDSBBasicConfig {
    public ConfigRG(String configName, File dir, ZDSBConfig inherit) {
        super(configName, dir, inherit);
        super.prefix = new Entry("rg.");
    }

    public Entry serverStatusChannel = new Entry();

    public Entry greetingsChannel = new Entry();
    public Entry greetingsBureauChannel = new Entry();
    public Entry greetingsMessageBureau = new Entry();
    public Entry greetingsMessagePublic = new Entry();
    public Entry greetingsMessagePrivate = new Entry();
    public Entry greetingsReactions = new Entry();
    public Entry greetingsRoles = new Entry();
    public Entry greetingsAutoRole = new Entry();
    public Entry greetingsMessageBureauTitle = new Entry();
    public Entry greetingsLeaveChannel = new Entry();

    public Entry dcsMonitoringChannel = new Entry();

    public Entry tsMonitoringChannel = new Entry();

    public Entry mediaVideoChannel = new Entry();
    public Entry mediaPostsChannel = new Entry();
    public Entry mediaPushingRole = new Entry();

    public static ConfigRG castConfig(ZDSBBasicConfig config) {
        return (ConfigRG) config;
    }

    public static BotConfig castBotConfig(ZDSBBotConfig config) {
        return (BotConfig) config;
    }

    public static class BotConfig extends ZDSBBotConfig {
        public BotConfig(String name, File dir, ZDSBConfig inherit) {
            super(name, dir, inherit);
            super.botName = new Entry("RightGames Bot");
        }

        public Entry formsWebPort = new Entry("false");
        public Entry formsChannel = new Entry();

        public Entry dcsLogin = new Entry();
        public Entry dcsPass = new Entry();

        public Entry ttvKey = new Entry();
        public Entry ttvSecret = new Entry();
        public Entry trovoUserID = new Entry();
        public Entry googleKey = new Entry();
    }
}
