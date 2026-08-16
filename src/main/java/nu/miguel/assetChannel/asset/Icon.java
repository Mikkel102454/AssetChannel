package nu.miguel.assetChannel.asset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nu.miguel.assetChannel.texturepack.TexturePack;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class Icon implements Asset {
    public static final String FONT_KEY = "assetchannel:icons";

    String id;
    Path path;
    Path config;

    private Integer codePoint;

    private int height;
    private int ascent;
    private int width;

    public Icon(String id, Path path, Path config) {
        this.id = id;
        this.path = path;
        this.config = config;

        loadConfig();
    }

    private void loadConfig() {
        if (config == null || !Files.isRegularFile(config)) {
            throw new IllegalArgumentException("Missing config file for icon '" + id + "': " + config);
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config.toFile());
            if (!yaml.contains("height")) {
                throw new IllegalArgumentException("Missing 'height' in config for icon '" + id + "'");
            }

            if (!yaml.contains("ascent")) {
                throw new IllegalArgumentException("Missing 'ascent' in config for icon '" + id + "'");
            }

            this.codePoint = yaml.contains("unicode") ? parseUnicode(yaml.getString("unicode", "")) : null;
            this.height = yaml.getInt("height");
            this.ascent = yaml.getInt("ascent");

            validateConfig();

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read config for icon '" + id + "'", e);
        }
    }

    private int parseUnicode(String value) {
        String cleaned = value.trim().toUpperCase();

        if (cleaned.startsWith("U+")) {
            cleaned = cleaned.substring(2);
        }

        if (cleaned.startsWith("\\U")) {
            cleaned = cleaned.substring(2);
        }

        try {
            int codePoint = Integer.parseInt(cleaned, 16);

            if (!Character.isValidCodePoint(codePoint)
                    || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                throw new IllegalArgumentException("Invalid Unicode code point for icon '" + id + "': " + value);
            }

            return codePoint;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid unicode value for icon '" + id + "': " + value, e);
        }
    }

    private void validateConfig() {
        if (height <= 0) {
            throw new IllegalArgumentException("Height must be greater than 0 for icon '" + id + "'");
        }

        if (ascent > height) {
            throw new IllegalArgumentException("Ascent cannot be greater than height for icon '" + id + "'");
        }

        validateImageDimensions();
    }

    private void validateImageDimensions() {
        try {
            BufferedImage image = ImageIO.read(path.toFile());

            if (image == null) {
                throw new IllegalArgumentException("Failed to read PNG for icon '" + id + "'");
            }

            int width = image.getWidth();
            int imageHeight = image.getHeight();
            this.width = Math.max(1, (int) Math.round((double) width * height / imageHeight));

            if (width > 256) {
                throw new IllegalArgumentException("Image width must not be greater than 256px for icon '" + id + "'. Found: " + width + "px");
            }

            if (imageHeight > 256) {
                throw new IllegalArgumentException("Image height must not be greater than 256px for icon '" + id + "'. Found: " + imageHeight + "px");
            }

        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image for icon '" + id + "'", e);
        }
    }

    public static boolean build(TexturePack texturePack, List<Icon> icons) {
        try {
            Path fontPath = texturePack.getAssetPath().resolve("font");

            Files.createDirectories(fontPath);

            JsonArray providers = new JsonArray();

            for (Icon icon : icons) {
                JsonObject provider = new JsonObject();

                provider.addProperty("type", "bitmap");

                provider.addProperty(
                        "file",
                        "assetchannel:font/"
                                + icon.id.replace('.', '/')
                                + ".png"
                );

                provider.addProperty("ascent", icon.ascent);
                provider.addProperty("height", icon.height);

                JsonArray chars = new JsonArray();
                chars.add(Character.toString(icon.codePoint));

                provider.add("chars", chars);

                providers.add(provider);
            }

            JsonObject root = new JsonObject();
            root.add("providers", providers);

            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

            Files.writeString(
                    fontPath.resolve("icons.json"),
                    gson.toJson(root)
            );

            for (Icon icon : icons) {
                if (!icon.build(texturePack)) {
                    return false;
                }
            }

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean build(TexturePack texturePack) {
        try {
            Path iconRoot = texturePack.getAssetPath().resolve("textures").resolve("font");

            Path relativeIconPath = Path.of(id.replace('.', '/') + ".png");
            Path destination = iconRoot.resolve(relativeIconPath);

            Files.createDirectories(destination.getParent());

            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    public int getCodePoint() {
        if (codePoint == null) throw new IllegalStateException("Icon '" + id + "' has not been assigned Unicode yet");
        return codePoint;
    }

    public boolean hasConfiguredCodePoint() { return codePoint != null; }

    public void assignGeneratedCodePoint(int value) {
        if (codePoint != null) throw new IllegalStateException("Icon '" + id + "' already has Unicode");
        if (!Character.isValidCodePoint(value)
                || value >= Character.MIN_SURROGATE && value <= Character.MAX_SURROGATE)
            throw new IllegalArgumentException("Invalid generated Unicode for icon '" + id + "'");
        codePoint = value;
    }

    public int getHeight() {
        return height;
    }

    public int getAscent() {
        return ascent;
    }

    public int getWidth() { return width; }
}
