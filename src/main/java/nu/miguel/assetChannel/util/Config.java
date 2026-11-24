package nu.miguel.assetChannel.util;

import nu.miguel.assetChannel.Main;

import java.util.logging.Level;

public class Config {
    public static Object get(String key, Object fallback) {
        Object value = Main.config.get(key);

        if (value == null) {
            if (fallback == null) {
                Logger.log(Level.SEVERE, "No value with key '" + key + "'");
                throw new IllegalStateException("No value with key '" + key + "'");
            }
            return fallback;
        }

        return value;
    }
}
