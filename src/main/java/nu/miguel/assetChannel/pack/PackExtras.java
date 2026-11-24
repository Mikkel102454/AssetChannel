package nu.miguel.assetChannel.pack;

import com.google.gson.*;

import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.texturepack.TexturePack;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Native metadata, icons and overlays combined with the effective resource catalog. */
public final class PackExtras implements Asset {
    private final Map<String, byte[]> files;
    private final JsonObject metadata;
    private final ImportedResources resources;

    private PackExtras(
            Map<String, byte[]> files, JsonObject metadata, ImportedResources resources) {
        this.files = files;
        this.metadata = metadata;
        this.resources = resources;
    }

    public ImportedResources resources() {
        return resources;
    }

    public static PackExtras scan(Path root, Path extraAssets) throws IOException {
        var files = readFiles(root, false);
        var metadata = readMetadata(files);
        return new PackExtras(
                files, metadata, ImportedResources.scanLayers(layers(root, extraAssets, metadata)));
    }

    public static PackExtras scanPacks(List<Path> packs) throws IOException {
        return scanPacks(packs, java.util.function.UnaryOperator.identity());
    }

    static PackExtras scanPacks(
            List<Path> packs, java.util.function.UnaryOperator<ImportedResources> prepare)
            throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        Map<String, Path> owners = new HashMap<>();
        JsonObject metadata = new JsonObject();
        List<List<Path>> resourceLayers = new ArrayList<>();
        for (Path root : packs) {
            var sourceFiles = readFiles(root, true);
            JsonObject sourceMetadata = readMetadata(sourceFiles);
            resourceLayers.add(layers(root, null, sourceMetadata));
            Map<String, String> overlayNames = new HashMap<>();
            if (sourceMetadata.has("overlays")) {
                JsonArray combined =
                        metadata.has("overlays")
                                ? metadata.getAsJsonObject("overlays").getAsJsonArray("entries")
                                : new JsonArray();
                for (JsonElement raw :
                        sourceMetadata.getAsJsonObject("overlays").getAsJsonArray("entries")) {
                    JsonObject entry = raw.getAsJsonObject().deepCopy();
                    String old = entry.get("directory").getAsString();
                    if (Set.of("assets", "definitions", "sources").contains(old))
                        throw new IllegalArgumentException(
                                "Reserved overlay directory '" + old + "' in " + root);
                    String renamed = root.getFileName() + "__" + old;
                    overlayNames.put(old, renamed);
                    entry.addProperty("directory", renamed);
                    combined.add(entry);
                }
                JsonObject overlays = new JsonObject();
                overlays.add("entries", combined);
                metadata.add("overlays", overlays);
            }
            for (var section : sourceMetadata.entrySet()) {
                if (section.getKey().equals("overlays")) continue;
                if (section.getKey().equals("pack")) {
                    if (!metadata.has("pack")
                            && section.getValue().getAsJsonObject().has("description"))
                        metadata.add("pack", section.getValue().deepCopy());
                } else
                    mergeMetadata(metadata, section.getKey(), section.getValue(), root.toString());
            }
            for (var entry : sourceFiles.entrySet()) {
                String path = entry.getKey();
                if (path.equals("pack.mcmeta") || path.startsWith("assets/")) continue;
                if (path.equals("pack.png") && files.containsKey(path)) continue;
                int slash = path.indexOf('/');
                if (slash > 0 && overlayNames.containsKey(path.substring(0, slash)))
                    path = overlayNames.get(path.substring(0, slash)) + path.substring(slash);
                Path previous = owners.putIfAbsent(path, root);
                if (previous != null)
                    throw new IllegalArgumentException(
                            "Duplicate pack file '" + path + "' in " + previous + " and " + root);
                files.put(path, entry.getValue());
            }
        }
        return new PackExtras(
                files, metadata, ImportedResources.scanPacks(resourceLayers, prepare));
    }

    private static Map<String, byte[]> readFiles(Path root, boolean contentPack)
            throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (Files.exists(root))
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted().toList()) {
                    if (Files.isSymbolicLink(path))
                        throw new IllegalArgumentException("Pack symlinks unsupported: " + path);
                    if (!Files.isRegularFile(path)) continue;
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    if (contentPack
                            && (relative.startsWith("definitions/")
                                    || relative.startsWith("sources/"))) continue;
                    if (!relative.matches("[a-z0-9/._-]+")
                            || Arrays.asList(relative.split("/")).contains(".."))
                        throw new IllegalArgumentException("Invalid pack path: " + relative);
                    files.put(relative, Files.readAllBytes(path));
                }
            }
        return files;
    }

    private static JsonObject readMetadata(Map<String, byte[]> files) {
        return files.containsKey("pack.mcmeta")
                ? JsonParser.parseString(
                                new String(
                                        files.get("pack.mcmeta"),
                                        java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject()
                : new JsonObject();
    }

    private static List<Path> layers(Path root, Path extraAssets, JsonObject metadata) {
        List<Path> layers = new ArrayList<>();
        layers.add(root.resolve("assets"));
        if (extraAssets != null) layers.add(extraAssets);
        if (metadata.has("overlays"))
            for (JsonElement raw : metadata.getAsJsonObject("overlays").getAsJsonArray("entries")) {
                JsonObject entry = raw.getAsJsonObject();
                String directory = entry.get("directory").getAsString();
                if (!directory.matches("[a-z0-9_-]+"))
                    throw new IllegalArgumentException("Invalid overlay directory: " + directory);
                JsonElement min = entry.get("min_format"), max = entry.get("max_format");
                if (entry.has("formats")) {
                    JsonElement format = entry.get("formats");
                    if (format.isJsonObject()) {
                        min = format.getAsJsonObject().get("min_inclusive");
                        max = format.getAsJsonObject().get("max_inclusive");
                    } else if (format.isJsonArray()) {
                        min = format.getAsJsonArray().get(0);
                        max = format.getAsJsonArray().get(1);
                    } else {
                        min = format;
                        max = format;
                    }
                }
                if (compare(min, false) <= 0 && compare(max, true) >= 0)
                    layers.add(root.resolve(directory).resolve("assets"));
            }
        return layers;
    }

    private static void mergeMetadata(
            JsonObject target, String key, JsonElement value, String source) {
        if (!target.has(key)) {
            target.add(key, value.deepCopy());
            return;
        }
        JsonElement existing = target.get(key);
        if (existing.isJsonObject() && value.isJsonObject()) {
            for (var child : value.getAsJsonObject().entrySet())
                mergeMetadata(existing.getAsJsonObject(), child.getKey(), child.getValue(), source);
        } else if (!existing.equals(value))
            throw new IllegalArgumentException(
                    "Conflicting pack metadata '" + key + "' in " + source);
    }

    private static int compare(JsonElement version, boolean upper) {
        if (version == null) return upper ? 1 : -1;
        int major =
                version.isJsonArray()
                        ? version.getAsJsonArray().get(0).getAsInt()
                        : version.getAsInt();
        int minor =
                version.isJsonArray()
                        ? version.getAsJsonArray().get(1).getAsInt()
                        : (upper ? Integer.MAX_VALUE : 0);
        return major == 97 ? Integer.compare(minor, 1) : Integer.compare(major, 97);
    }

    @Override
    public String getId() {
        return "native-pack-extras";
    }

    @Override
    public boolean build(TexturePack pack) {
        try {
            for (var entry : files.entrySet()) {
                if (entry.getKey().equals("pack.mcmeta") || entry.getKey().startsWith("assets/"))
                    continue;
                Path target = pack.getRootPath().resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue(), StandardOpenOption.CREATE_NEW);
            }
            Path metaPath = pack.getRootPath().resolve("pack.mcmeta");
            JsonObject result =
                    JsonParser.parseString(Files.readString(metaPath)).getAsJsonObject();
            for (var entry : metadata.entrySet())
                if (!entry.getKey().equals("pack")) result.add(entry.getKey(), entry.getValue());
            if (metadata.has("pack") && metadata.getAsJsonObject("pack").has("description"))
                result.getAsJsonObject("pack")
                        .add("description", metadata.getAsJsonObject("pack").get("description"));
            Files.writeString(
                    metaPath, new GsonBuilder().setPrettyPrinting().create().toJson(result));
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Could not stage pack extras", error);
        }
    }
}
