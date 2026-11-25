package nu.miguel.assetChannel.pack;

import nu.miguel.assetChannel.asset.*;
import nu.miguel.assetChannel.util.PathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class PackDeveloper {
    static final int GENERATED_ICON_FIRST = 0xE000;
    static final int GENERATED_ICON_LAST = 0xF8FF;

    public static List<Asset> createPack() {
        List<Asset> assets = new ArrayList<>();

        try {
            List<Path> packs = PathResolver.contentPacks();
            java.util.Map<String, Path> owners = new java.util.HashMap<>();
            for (Path pack : packs) {
                List<Asset> content = new ArrayList<>();
                scanIcons(content, pack.resolve("sources/icons"));
                scanSounds(content, pack.resolve("sources/sounds"));
                for (AssetFile file : scanSoundFiles(pack.resolve("sources/scene-audio")))
                    content.add(new TimelineAudio(file.id(), file.path()));
                scanFonts(content, pack.resolve("sources/fonts"));
                for (AssetFile file :
                        scanFiles(pack.resolve("definitions/effects"), Set.of("yml", "yaml")))
                    content.add(EffectCurve.load(file.id(), file.path()));
                scanMusic(content, pack.resolve("sources/music"));
                scanHuds(content, pack.resolve("definitions/hud"));
                for (AssetFile file : scanFiles(pack.resolve("sources/models"), Set.of("bbmodel")))
                    content.add(RigCompiler.compile(file.id(), file.path()));
                for (AssetFile file :
                        scanFiles(pack.resolve("definitions/blocks"), Set.of("yml", "yaml")))
                    content.add(BlockDefinition.load(file.id(), file.path()));
                for (Asset asset : content) {
                    String key = asset.getClass().getName() + ":" + asset.getId();
                    Path previous = owners.putIfAbsent(key, pack);
                    if (previous != null)
                        throw new IllegalArgumentException(
                                "Duplicate asset id '"
                                        + asset.getId()
                                        + "' in "
                                        + previous
                                        + " and "
                                        + pack);
                    assets.add(asset);
                }
            }
            var extras =
                    PackExtras.scanPacks(
                            packs,
                            resources ->
                                    AutomaticBlockContent.prepare(
                                            ClientEffects.prepare(
                                                    ScreenFonts.prepare(
                                                            CaptionFonts.prepare(
                                                                    RigCompiler.merge(
                                                                            resources, assets),
                                                                    assets),
                                                            assets),
                                                    assets),
                                            assets,
                                            PathResolver.getDataPath().toPath()));
            assets.add(extras.resources());
            assets.add(extras);

        } catch (IOException e) {
            throw new RuntimeException("Failed to scan asset folders", e);
        }

        validateAssets(assets);

        if (!PackBuilder.build(assets))
            throw new IllegalStateException(
                    "Resource-pack build failed; previous pack was retained");
        return List.copyOf(assets);
    }

    private static void scanIcons(List<Asset> assets, Path root) throws IOException {
        for (AssetFile file : scanFiles(root, Set.of("png"))) {
            Path configPath = getConfigPath(file.path());

            Icon icon = new Icon(file.id(), file.path(), configPath);
            assets.add(icon);
        }
    }

    private static void scanSounds(List<Asset> assets, Path root) throws IOException {
        for (AssetFile file : scanSoundFiles(root)) {

            Sound sound = new Sound(file.id(), file.path());
            assets.add(sound);
        }
    }

    static List<AssetFile> scanSoundFiles(Path root) throws IOException {
        return scanFiles(root, Set.of("ogg", "mp3"));
    }

    private static void scanFonts(List<Asset> assets, Path root) throws IOException {
        for (AssetFile file : scanFiles(root, Set.of("ttf", "otf"))) {

            Font font = new Font(file.id(), file.path());
            assets.add(font);
        }
    }

    static void validateAssets(List<Asset> assets) {
        NativeBlockStates.validate(assets);
        Set<String> itemModels =
                assets.stream()
                        .filter(ImportedResources.class::isInstance)
                        .map(ImportedResources.class::cast)
                        .flatMap(imported -> imported.models().stream())
                        .map(nu.miguel.assetChannel.api.ModelAsset::id)
                        .collect(java.util.stream.Collectors.toSet());
        for (Asset asset : assets)
            if (asset instanceof BlockDefinition block) {
                if (!itemModels.contains(block.descriptor().model()))
                    throw new IllegalArgumentException(
                            "Block "
                                    + block.getId()
                                    + " references missing item model "
                                    + block.descriptor().model());
                if (block.descriptor().particleModel() != null
                        && !itemModels.contains(block.descriptor().particleModel()))
                    throw new IllegalArgumentException(
                            "Block "
                                    + block.getId()
                                    + " references missing particle item model "
                                    + block.descriptor().particleModel());
            }
        assignIconCodePoints(assets);
        Set<String> typedIds = new HashSet<>();
        Set<Integer> iconCodePoints = new HashSet<>();
        for (Asset asset : assets) {
            String id = asset.getId();
            if (!id.matches("[a-z0-9._-]+"))
                throw new IllegalArgumentException("Invalid asset id: " + id);
            String key = asset.getClass().getName() + ":" + id;
            if (!typedIds.add(key)) throw new IllegalArgumentException("Duplicate asset id: " + id);
            if (asset instanceof Icon icon && !iconCodePoints.add(icon.getCodePoint())) {
                throw new IllegalArgumentException(
                        "Duplicate icon Unicode: U+" + String.format("%04X", icon.getCodePoint()));
            }
        }
        Set<String> icons =
                assets.stream()
                        .filter(Icon.class::isInstance)
                        .map(Asset::getId)
                        .collect(java.util.stream.Collectors.toSet());
        Set<String> fonts =
                assets.stream()
                        .filter(Font.class::isInstance)
                        .map(Asset::getId)
                        .collect(java.util.stream.Collectors.toSet());
        for (Asset asset : assets)
            if (asset instanceof ImportedResources imported)
                imported.fonts().stream()
                        .filter(font -> font.key().namespace().equals("assetchannel"))
                        .forEach(font -> fonts.add(font.key().value().replace('/', '.')));
        for (Asset asset : assets)
            if (asset instanceof Hud hud) {
                if (hud.background() != null && !icons.contains(hud.background()))
                    throw new IllegalArgumentException(
                            "HUD "
                                    + hud.getId()
                                    + " references unknown background icon '"
                                    + hud.background()
                                    + "'");
                for (String text : List.of(hud.content(), hud.subtitle(), hud.fallbackContent()))
                    validateHudTags(hud, text, icons, fonts);
                for (Hud.Variable variable : hud.variables().values())
                    if (variable.defaultValue() != null)
                        validateHudTags(hud, variable.defaultValue(), icons, fonts);
            }
    }

    /** Assigns repeatable private-use characters to icons whose sidecar omits unicode. */
    static void assignIconCodePoints(List<Asset> assets) {
        List<Icon> icons =
                assets.stream().filter(Icon.class::isInstance).map(Icon.class::cast).toList();
        Set<Integer> used = new HashSet<>();
        for (Icon icon : icons)
            if (icon.hasConfiguredCodePoint() && !used.add(icon.getCodePoint()))
                throw new IllegalArgumentException(
                        "Duplicate icon Unicode: U+" + String.format("%04X", icon.getCodePoint()));

        int capacity = GENERATED_ICON_LAST - GENERATED_ICON_FIRST + 1;
        for (Icon icon :
                icons.stream()
                        .filter(value -> !value.hasConfiguredCodePoint())
                        .sorted(java.util.Comparator.comparing(Icon::getId))
                        .toList()) {
            int offset = Math.floorMod(icon.getId().hashCode(), capacity);
            int selected = -1;
            for (int probe = 0; probe < capacity; probe++) {
                int candidate = GENERATED_ICON_FIRST + (offset + probe) % capacity;
                if (used.add(candidate)) {
                    selected = candidate;
                    break;
                }
            }
            if (selected < 0)
                throw new IllegalArgumentException(
                        "No generated icon Unicode values remain in U+E000-U+F8FF");
            icon.assignGeneratedCodePoint(selected);
        }
    }

    private static void validateHudTags(
            Hud hud, String text, Set<String> icons, Set<String> fonts) {
        var spacingMatcher = java.util.regex.Pattern.compile("<space:([^>]+)>").matcher(text);
        while (spacingMatcher.find()) {
            try {
                SpacingFont.component(Integer.parseInt(spacingMatcher.group(1)));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(
                        "HUD " + hud.getId() + " has invalid spacing: " + spacingMatcher.group(),
                        error);
            }
        }
        var iconsMatcher = java.util.regex.Pattern.compile("<icon:([a-z0-9._-]+)>").matcher(text);
        while (iconsMatcher.find())
            if (!icons.contains(iconsMatcher.group(1)))
                throw new IllegalArgumentException(
                        "HUD "
                                + hud.getId()
                                + " references unknown icon '"
                                + iconsMatcher.group(1)
                                + "'");
        var fontsMatcher =
                java.util.regex.Pattern.compile("<font:assetchannel:([a-z0-9._/-]+)>")
                        .matcher(text);
        while (fontsMatcher.find()) {
            String id = fontsMatcher.group(1).replace('/', '.');
            if (!id.equals("icons") && !id.equals("spacing") && !fonts.contains(id))
                throw new IllegalArgumentException(
                        "HUD " + hud.getId() + " references unknown font '" + id + "'");
        }
        try {
            var empty =
                    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                            "var",
                            (args, context) -> {
                                args.popOr("HUD variable name is required");
                                return net.kyori.adventure.text.minimessage.tag.Tag.inserting(
                                        net.kyori.adventure.text.Component.empty());
                            });
            var emptyIcon =
                    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                            "icon",
                            (args, context) -> {
                                args.popOr("Icon id is required");
                                return net.kyori.adventure.text.minimessage.tag.Tag.inserting(
                                        net.kyori.adventure.text.Component.empty());
                            });
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(text, empty, emptyIcon, SpacingFont.resolver());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(
                    "HUD " + hud.getId() + " contains invalid MiniMessage: " + error.getMessage(),
                    error);
        }
    }

    private static void scanHuds(List<Asset> assets, Path root) throws IOException {
        for (AssetFile file : scanFiles(root, Set.of("yml", "yaml")))
            assets.add(Hud.load(file.id(), file.path()));
    }

    private static void scanMusic(List<Asset> assets, Path root) throws IOException {
        if (!Files.exists(root)) return;

        try (Stream<Path> folders = Files.list(root)) {
            for (Path folder : folders.sorted().toList()) {
                if (Files.isSymbolicLink(folder))
                    throw new IllegalArgumentException(
                            "Source symlinks are unsupported: " + folder);
                if (!Files.isDirectory(folder)) continue;
                String id =
                        root.relativize(folder)
                                .toString()
                                .replace('\\', '/')
                                .toLowerCase(Locale.ROOT)
                                .replace('/', '.');
                assets.add(Music.load(id, folder));
            }
        }
    }

    private static List<AssetFile> scanFiles(Path root, Set<String> extensions) throws IOException {

        if (!Files.exists(root)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(root)) {
            return files.sorted()
                    .peek(path -> {
                        if (Files.isSymbolicLink(path))
                            throw new IllegalArgumentException(
                                    "Source symlinks are unsupported: " + path);
                    })
                    .filter(Files::isRegularFile)
                    .filter(path -> hasExtension(path, extensions))
                    .map(path -> new AssetFile(createId(root, path), path))
                    .toList();
        }
    }

    private static boolean hasExtension(Path path, Set<String> extensions) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex == -1) {
            return false;
        }

        String extension = fileName.substring(dotIndex + 1);

        return extensions.contains(extension);
    }

    private static String createId(Path root, Path file) {
        Path relative = root.relativize(file);

        String path = relative.toString().replace('\\', '/').toLowerCase(Locale.ROOT);

        int dotIndex = path.lastIndexOf('.');

        if (dotIndex != -1) {
            path = path.substring(0, dotIndex);
        }

        return path.replace('/', '.');
    }

    private static Path getConfigPath(Path assetPath) {
        String fileName = assetPath.getFileName().toString();

        int dotIndex = fileName.lastIndexOf('.');

        String name = dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);

        return assetPath.resolveSibling(name + ".yml");
    }
}
