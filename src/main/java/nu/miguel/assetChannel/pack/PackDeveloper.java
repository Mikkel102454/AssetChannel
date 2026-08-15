package nu.miguel.assetChannel.pack;

import nu.miguel.assetChannel.asset.*;
import nu.miguel.assetChannel.util.PathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;

public class PackDeveloper {

    public static List<Asset> createPack() {
        List<Asset> assets = new ArrayList<>();

        try {
            scanIcons(assets);
            scanSounds(assets);
            scanFonts(assets);
            scanMusic(assets);

        } catch (IOException e) {
            throw new RuntimeException("Failed to scan asset folders", e);
        }

        validateAssets(assets);

        if (!PackBuilder.build(assets)) throw new IllegalStateException("Resource-pack build failed; previous pack was retained");
        return List.copyOf(assets);
    }

    private static void scanIcons(List<Asset> assets) throws IOException {
        Path root = PathResolver.getIconPath().toPath();

        for (AssetFile file : scanFiles(root, Set.of("png"))) {
            Path configPath = getConfigPath(file.path());

            Icon icon = new Icon(file.id(), file.path(), configPath);
            assets.add(icon);
        }
    }

    private static void scanSounds(List<Asset> assets) throws IOException {
        Path root = PathResolver.getSoundPath().toPath();

        for (AssetFile file : scanFiles(root, Set.of("ogg"))) {

            Sound sound = new Sound(file.id(), file.path());
            assets.add(sound);
        }
    }

    private static void scanFonts(List<Asset> assets) throws IOException {
        Path root = PathResolver.getFontPath().toPath();

        for (AssetFile file : scanFiles(root, Set.of("ttf", "otf"))) {

            Font font = new Font(file.id(), file.path());
            assets.add(font);
        }
    }

    private static void validateAssets(List<Asset> assets) {
        Set<String> typedIds = new HashSet<>();
        Set<Integer> iconCodePoints = new HashSet<>();
        for (Asset asset : assets) {
            String id = asset.getId();
            if (!id.matches("[a-z0-9._-]+")) throw new IllegalArgumentException("Invalid asset id: " + id);
            String key = asset.getClass().getName() + ":" + id;
            if (!typedIds.add(key)) throw new IllegalArgumentException("Duplicate asset id: " + id);
            if (asset instanceof Icon icon && !iconCodePoints.add(icon.getCodePoint())) {
                throw new IllegalArgumentException("Duplicate icon Unicode: U+"
                        + String.format("%04X", icon.getCodePoint()));
            }
        }
    }

    private static void scanMusic(List<Asset> assets) throws IOException {
        Path root = PathResolver.getMusicPath().toPath();
        if (!Files.exists(root)) return;

        try (Stream<Path> folders = Files.list(root)) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) {
                String id = root.relativize(folder).toString().replace('\\', '/').toLowerCase(Locale.ROOT).replace('/', '.');
                assets.add(Music.load(id, folder));
            }
        }
    }

    private static List<AssetFile> scanFiles(Path root, Set<String> extensions) throws IOException {

        if (!Files.exists(root)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> hasExtension(path, extensions))
                    .map(path -> new AssetFile(
                            createId(root, path),
                            path
                    ))
                    .toList();
        }
    }

    private static boolean hasExtension(Path path, Set<String> extensions) {
        String fileName = path
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex == -1) {
            return false;
        }

        String extension = fileName.substring(dotIndex + 1);

        return extensions.contains(extension);
    }

    private static String createId(Path root, Path file) {
        Path relative = root.relativize(file);

        String path = relative
                .toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);

        int dotIndex = path.lastIndexOf('.');

        if (dotIndex != -1) {
            path = path.substring(0, dotIndex);
        }

        return path.replace('/', '.');
    }

    private static Path getConfigPath(Path assetPath) {
        String fileName = assetPath.getFileName().toString();

        int dotIndex = fileName.lastIndexOf('.');

        String name = dotIndex == -1
                ? fileName
                : fileName.substring(0, dotIndex);

        return assetPath.resolveSibling(name + ".yml");
    }
}
