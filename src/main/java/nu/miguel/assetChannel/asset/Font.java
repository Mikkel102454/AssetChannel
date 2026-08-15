package nu.miguel.assetChannel.asset;

import nu.miguel.assetChannel.texturepack.TexturePack;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import java.util.List;

public class Font implements Asset{
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

            JsonObject provider = new JsonObject();
            provider.addProperty("type", "ttf");
            // Minecraft resolves TTF files relative to assets/<namespace>/font/.
            provider.addProperty("file", providerFile());
            JsonArray shift = new JsonArray();
            shift.add(0); shift.add(0);
            provider.add("shift", shift);
            provider.addProperty("size", 11);
            provider.addProperty("oversample", 1.0);
            JsonArray providers = new JsonArray();
            providers.add(provider);
            JsonObject root = new JsonObject();
            root.add("providers", providers);
            Path definition = definitionPath(texturePack.getAssetPath());
            Files.createDirectories(definition.getParent());
            Files.writeString(definition,
                    new GsonBuilder().setPrettyPrinting().create().toJson(root));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    public String getKey() { return "assetchannel:" + id; }

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
