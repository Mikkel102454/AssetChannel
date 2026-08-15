package nu.miguel.assetChannel.util;

import nu.miguel.assetChannel.Main;

import java.util.logging.Level;

public class Logger {
    public static void log(Level level, String message) {
        Main.plugin.getLogger().log(level, "[AssetChannel] " + message);
    }
}
