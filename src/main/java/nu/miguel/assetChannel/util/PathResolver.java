package nu.miguel.assetChannel.util;

import nu.miguel.assetChannel.Main;

import java.io.File;
import java.util.logging.Level;

public class PathResolver {
    static boolean hasBeenEnsured;
    static File dataPath;
    static File iconPath;
    static File fontPath;
    static File soundPath;
    static File musicPath;

    public static void ensurePaths(){
        dataPath = ensureDataPath();
        iconPath = ensureIconPath(dataPath);
        fontPath = ensureFontPath(dataPath);
        soundPath = ensureSoundPath(dataPath);
        musicPath = ensureMusicPath(dataPath);

        hasBeenEnsured = true;
    }

    private static File ensureDataPath(){
        File dataFolder = Main.plugin.getDataFolder();
        if (!dataFolder.exists()) {
            if(!dataFolder.mkdirs()){
                Logger.log(Level.SEVERE, "Failed to create data folder");
            }
        }
        return dataFolder;
    }

    public static File getDataPath(){
        if(!hasBeenEnsured){
            Logger.log(Level.WARNING, "Tried to get data folder path without ensuring it exists");
            return null;
        }
        return dataPath;
    }

    private static File ensureIconPath(File dataFolder){
        File iconPath = new File(dataFolder, "icons");
        if (!iconPath.exists()) {
            if(!iconPath.mkdirs()){
                Logger.log(Level.SEVERE, "Failed to create icon data folder");
            }
        }
        return iconPath;
    }

    public static File getIconPath(){
        if(!hasBeenEnsured){
            Logger.log(Level.WARNING, "Tried to get icon data folder path without ensuring it exists");
            return null;
        }
        return iconPath;
    }

    private static File ensureFontPath(File dataFolder){
        File fontPath = new File(dataFolder, "font");
        if (!fontPath.exists()) {
            if(!fontPath.mkdirs()){
                Logger.log(Level.SEVERE, "Failed to create font data folder");
            }
        }
        return fontPath;
    }

    public static File getFontPath(){
        if(!hasBeenEnsured){
            Logger.log(Level.WARNING, "Tried to get font data folder path without ensuring it exists");
            return null;
        }
        return fontPath;
    }

    private static File ensureSoundPath(File dataFolder){
        File soundPath = new File(dataFolder, "sound");
        if (!soundPath.exists()) {
            if(!soundPath.mkdirs()){
                Logger.log(Level.SEVERE, "Failed to create sound data folder");
            }
        }
        return soundPath;
    }

    public static File getSoundPath(){
        if(!hasBeenEnsured){
            Logger.log(Level.WARNING, "Tried to get sound data folder path without ensuring it exists");
            return null;
        }
        return soundPath;
    }

    private static File ensureMusicPath(File dataFolder){
        File path = new File(dataFolder, "music");
        if (!path.exists() && !path.mkdirs()) {
            Logger.log(Level.SEVERE, "Failed to create music data folder");
        }
        return path;
    }

    public static File getMusicPath(){
        if(!hasBeenEnsured){
            Logger.log(Level.WARNING, "Tried to get music data folder path without ensuring it exists");
            return null;
        }
        return musicPath;
    }
}
