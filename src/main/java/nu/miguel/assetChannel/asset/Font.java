package nu.miguel.assetChannel.asset;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nu.miguel.assetChannel.texturepack.TexturePack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class Font implements Asset {
    String id;
    Path path;

    public Font(String id, Path path) {
        this.id = id;
        this.path = path;
    }

    public static boolean build(TexturePack texturePack, List<Font> fonts) {
        for (Font font : fonts) if (!font.build(texturePack)) return false;
        return true;
    }

    @Override
    public boolean build(TexturePack texturePack) {
        try {
            String resourcePath = id.replace('.', '/');
            String extension = extension(path);
            Path fontRoot = texturePack.getAssetPath().resolve("font");
            Path destination = fontRoot.resolve(resourcePath + "." + extension);
            Files.createDirectories(destination.getParent());
            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);

            JsonObject provider = provider();
            JsonArray providers = new JsonArray();
            providers.add(provider);
            JsonObject root = new JsonObject();
            root.add("providers", providers);
            Path definition = definitionPath(texturePack.getAssetPath());
            Files.createDirectories(definition.getParent());
            Files.writeString(
                    definition, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            // Preserve existing dotted API keys while retaining canonical nested resource paths.
            if (id.contains(".")) {
                JsonObject reference = new JsonObject();
                reference.addProperty("type", "reference");
                reference.addProperty("id", "assetchannel:" + resourcePath);
                JsonArray aliases = new JsonArray();
                aliases.add(reference);
                JsonObject alias = new JsonObject();
                alias.add("providers", aliases);
                Files.writeString(
                        fontRoot.resolve(id + ".json"), new GsonBuilder().create().toJson(alias));
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    public String getKey() {
        return "assetchannel:" + id;
    }

    public Path sourcePath() {
        return path;
    }

    public JsonObject provider() {
        String name = path.getFileName().toString();
        Path sidecar = path.resolveSibling(name.substring(0, name.lastIndexOf('.')) + ".yml");
        var config =
                Files.exists(sidecar)
                        ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                                sidecar.toFile())
                        : new org.bukkit.configuration.file.YamlConfiguration();
        double size = config.getDouble("size", 11), oversample = config.getDouble("oversample", 1);
        java.util.List<?> offset = config.getList("shift", java.util.List.of(0, 0));
        if (!Double.isFinite(size)
                || size <= 0
                || !Double.isFinite(oversample)
                || oversample <= 0
                || offset.size() != 2
                || !(offset.get(0) instanceof Number x)
                || !(offset.get(1) instanceof Number y)
                || !Double.isFinite(x.doubleValue())
                || !Double.isFinite(y.doubleValue()))
            throw new IllegalArgumentException("Invalid font settings: " + sidecar);
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "ttf");
        provider.addProperty("file", providerFile());
        JsonArray shift = new JsonArray();
        shift.add(x);
        shift.add(y);
        provider.add("shift", shift);
        provider.addProperty("size", size);
        provider.addProperty("oversample", oversample);
        return provider;
    }

    Path definitionPath(Path assetPath) {
        return assetPath.resolve("font").resolve(id.replace('.', '/') + ".json");
    }

    String providerFile() {
        return "assetchannel:" + id.replace('.', '/') + "." + extension(path);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }
}
