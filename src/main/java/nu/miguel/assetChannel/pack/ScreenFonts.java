package nu.miguel.assetChannel.pack;

import com.google.gson.*;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.CaptionLayout;
import nu.miguel.assetChannel.api.ScreenFontAsset;
import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.texturepack.TexturePack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.awt.font.FontRenderContext;
import java.io.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Compiles positioned text variants from imported font media. */
public record ScreenFonts(Map<String, ScreenFontAsset> fonts, ImportedResources resources)
        implements Asset {
    public ScreenFonts {
        fonts = Map.copyOf(fonts);
    }

    public String getId() {
        return "screen-fonts";
    }

    public boolean build(TexturePack pack) {
        return true;
    }

    static ImportedResources prepare(ImportedResources imported, List<Asset> assets) {
        try {
            var result =
                    compile(
                            imported,
                            assets.stream()
                                    .filter(nu.miguel.assetChannel.asset.Font.class::isInstance)
                                    .map(nu.miguel.assetChannel.asset.Font.class::cast)
                                    .toList());
            assets.add(result);
            return result.resources();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public static ScreenFonts compile(
            ImportedResources imported, List<nu.miguel.assetChannel.asset.Font> sources)
            throws IOException {
        var files = new TreeMap<>(imported.sourceFiles());
        var descriptors = new TreeMap<String, ScreenFontAsset>();
        for (var source : sources) {
            byte[] bytes = Files.readAllBytes(source.sourcePath());
            java.awt.Font face;
            try (var in = new ByteArrayInputStream(bytes)) {
                face = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
            } catch (java.awt.FontFormatException invalid) {
                continue;
            }
            String name = source.sourcePath().getFileName().toString();
            Path sidecar =
                    source.sourcePath()
                            .resolveSibling(name.substring(0, name.lastIndexOf('.')) + ".yml");
            var config = new YamlConfiguration();
            if (Files.exists(sidecar))
                try {
                    config.load(sidecar.toFile());
                } catch (org.bukkit.configuration.InvalidConfigurationException error) {
                    throw new IOException("Invalid screen font sidecar: " + sidecar, error);
                }
            if (!config.getBoolean("screen.enabled", true)) continue;
            int min = config.getInt("screen.min-y", -256),
                    max = config.getInt("screen.max-y", 64),
                    step = config.getInt("screen.step", 2);
            double size =
                    config.getDouble("screen.size", source.provider().get("size").getAsDouble());
            if (min < -1024
                    || max > 1024
                    || min > max
                    || step < 1
                    || step > 16
                    || (max - min) / step > 512
                    || !Double.isFinite(size)
                    || size < 4
                    || size > 64)
                throw new IllegalArgumentException("Invalid screen font range/size: " + sidecar);
            face = face.deriveFont((float) size);
            var context = new FontRenderContext(null, true, true);
            var advances = new TreeMap<Integer, Float>();
            String characters =
                    config.getString(
                            "screen.characters",
                            config.getString("caption.characters", CaptionLayout.characters()));
            for (int cp : (characters + "? ").codePoints().distinct().toArray())
                if (!Character.isISOControl(cp) && face.canDisplay(cp))
                    advances.put(
                            cp,
                            face.createGlyphVector(context, Character.toString(cp))
                                    .getGlyphMetrics(0)
                                    .getAdvance());
            String prefix = "assetchannel:generated/screen/" + source.getId().replace('.', '/'),
                    media = prefix + "/source.ttf";
            put(files, path(media), bytes);
            var rows = new TreeMap<Integer, Key>();
            for (int y = min; y <= max; y += step) {
                String id = prefix + "/y_" + (y < 0 ? "n" + (-y) : "p" + y);
                var provider = new JsonObject();
                provider.addProperty("type", "ttf");
                provider.addProperty("file", media);
                provider.addProperty("size", size);
                provider.addProperty("oversample", 2);
                var shift = new JsonArray();
                shift.add(0);
                shift.add(y);
                provider.add("shift", shift);
                var providers = new JsonArray();
                providers.add(provider);
                var json = new JsonObject();
                json.add("providers", providers);
                put(files, path(id + ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
                rows.put(y, Key.key(id));
            }
            descriptors.put(
                    source.getId(),
                    new ScreenFontAsset(
                            source.getId(), rows, advances, (int) Math.ceil(size * 1.25)));
        }
        var advances = new JsonObject();
        for (int n = -63; n <= 63; n++)
            if (n != 0) advances.addProperty(Character.toString(0xF100 + n + 64), n / 64f);
        var provider = new JsonObject();
        provider.addProperty("type", "space");
        provider.add("advances", advances);
        var providers = new JsonArray();
        providers.add(provider);
        var json = new JsonObject();
        json.add("providers", providers);
        put(
                files,
                "assetchannel/font/screen_spacing.json",
                json.toString().getBytes(StandardCharsets.UTF_8));
        return new ScreenFonts(descriptors, ImportedResources.prepared(files));
    }

    private static String path(String id) {
        return id.replace(":", "/font/");
    }

    private static void put(Map<String, byte[]> files, String path, byte[] bytes) {
        if (files.putIfAbsent(path, bytes) != null)
            throw new IllegalArgumentException("Generated screen font collision: " + path);
    }
}
