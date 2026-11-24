package nu.miguel.assetChannel.util;

import nu.miguel.assetChannel.Main;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PathResolver {
    static boolean hasBeenEnsured;
    static File dataPath;

    private PathResolver() {}

    public static void ensurePaths() {
        initialize(Main.plugin.getDataFolder().toPath());
    }

    public static void initialize(Path root) {
        try {
            Files.createDirectories(root);
            LegacyLayoutMigration.migrate(root.toAbsolutePath().normalize());
            for (String folder :
                    List.of(
                            "assets",
                            "definitions/blocks",
                            "definitions/hud",
                            "sources/icons",
                            "sources/fonts",
                            "sources/sounds",
                            "sources/music"))
                Files.createDirectories(root.resolve("packs/main").resolve(folder));
            Files.createDirectories(root.resolve("generated/cache"));
            dataPath = root.toFile();
            hasBeenEnsured = true;
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Cannot prepare AssetChannel folders: " + error.getMessage(), error);
        }
    }

    public static File getDataPath() {
        if (!hasBeenEnsured)
            throw new IllegalStateException("AssetChannel folders have not been initialized");
        return dataPath;
    }

    public static Path getPacksPath() {
        return getDataPath().toPath().resolve("packs");
    }

    public static Path getGeneratedPath() {
        return getDataPath().toPath().resolve("generated");
    }

    public static Path getPackZipPath() {
        return getGeneratedPath().resolve("pack.zip");
    }

    public static List<Path> contentPacks() throws IOException {
        if (!Files.exists(getPacksPath())) return List.of();
        try (var paths = Files.list(getPacksPath())) {
            List<Path> result = new ArrayList<>();
            for (Path path :
                    paths.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                if (Files.isSymbolicLink(path))
                    throw new IllegalArgumentException(
                            "Content pack symlinks are unsupported: " + path);
                if (!Files.isDirectory(path)) continue;
                if (!path.getFileName().toString().matches("[a-z0-9][a-z0-9_-]*"))
                    throw new IllegalArgumentException("Invalid content pack folder: " + path);
                result.add(path);
            }
            result.sort(
                    Comparator.comparing((Path p) -> !p.getFileName().toString().equals("main"))
                            .thenComparing(p -> p.getFileName().toString()));
            return List.copyOf(result);
        }
    }

    // These helpers retain their signatures and point to the default content pack.
    public static File getIconPath() {
        return main("sources/icons");
    }

    public static File getFontPath() {
        return main("sources/fonts");
    }

    public static File getSoundPath() {
        return main("sources/sounds");
    }

    public static File getMusicPath() {
        return main("sources/music");
    }

    public static File getHudPath() {
        return main("definitions/hud");
    }

    private static File main(String relative) {
        return getPacksPath().resolve("main").resolve(relative).toFile();
    }
}
