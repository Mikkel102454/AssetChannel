package nu.miguel.assetChannel.pack;

import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.asset.Icon;
import nu.miguel.assetChannel.texturepack.TexturePack;
import nu.miguel.assetChannel.util.Logger;

import java.util.List;
import java.util.logging.Level;

import nu.miguel.assetChannel.asset.Font;
import nu.miguel.assetChannel.asset.Sound;
import nu.miguel.assetChannel.asset.Music;

public class PackBuilder {

    public static boolean build(List<Asset> assets) {
        TexturePack texturePack = new TexturePack();
        boolean published = false;
        try {
            if (!buildIcons(texturePack, assets)) return false;
            if (!buildFonts(texturePack, assets)) return false;
            if (!buildSounds(texturePack, assets)) return false;
            if (!buildMusic(texturePack, assets)) return false;
            if (!SpacingFont.build(texturePack)) return false;
            for (Asset asset : assets) if (asset instanceof ImportedResources && !asset.build(texturePack)) return false;
            for (Asset asset : assets) if (asset instanceof PackExtras && !asset.build(texturePack)) return false;
            texturePack.publish();
            published = true;
            return true;
        } finally {
            if (!published) texturePack.discard();
        }
    }

    private static boolean buildIcons(TexturePack texturePack, List<Asset> assets) {
        List<Icon> icons = assets.stream()
                .filter(Icon.class::isInstance)
                .map(Icon.class::cast)
                .toList();

        if (icons.isEmpty()) {
            return true;
        }

        if (!Icon.build(texturePack, icons)) {
            Logger.log(Level.WARNING, "Failed to build icons.");
            return false;
        }
        return true;
    }

    private static boolean buildFonts(TexturePack texturePack, List<Asset> assets) {
        List<Font> fonts = assets.stream()
                .filter(Font.class::isInstance)
                .map(Font.class::cast)
                .toList();

        if (fonts.isEmpty()) {
            return true;
        }

        if (!Font.build(texturePack, fonts)) {
            Logger.log(Level.WARNING, "Failed to build fonts.");
            return false;
        }
        return true;
    }

    private static boolean buildSounds(TexturePack texturePack, List<Asset> assets) {
        List<Sound> sounds = assets.stream()
                .filter(Sound.class::isInstance)
                .map(Sound.class::cast)
                .toList();

        if (sounds.isEmpty()) {
            return true;
        }

        if (!Sound.build(texturePack, sounds)) {
            Logger.log(Level.WARNING, "Failed to build sounds.");
            return false;
        }
        return true;
    }

    private static boolean buildMusic(TexturePack texturePack, List<Asset> assets) {
        List<Music> music = assets.stream().filter(Music.class::isInstance).map(Music.class::cast).toList();
        if (music.isEmpty()) return true;
        if (!Music.build(texturePack, music)) {
            Logger.log(Level.WARNING, "Failed to build music.");
            return false;
        }
        return true;
    }
}
