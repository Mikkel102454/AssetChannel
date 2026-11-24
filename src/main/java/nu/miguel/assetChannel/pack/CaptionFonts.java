package nu.miguel.assetChannel.pack;

import com.google.gson.*;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.texturepack.TexturePack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.awt.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Derives caption variants exclusively from imported font files and optional imported box textures.
 */
public final class CaptionFonts implements Asset {
    private final Map<String, CaptionFontAsset> fonts;
    private final ImportedResources resources;

    private CaptionFonts(Map<String, CaptionFontAsset> fonts, ImportedResources resources) {
        this.fonts = Map.copyOf(fonts);
        this.resources = resources;
    }

    public Map<String, CaptionFontAsset> fonts() {
        return fonts;
    }

    public ImportedResources resources() {
        return resources;
    }

    public String getId() {
        return "caption-fonts";
    }

    public boolean build(TexturePack pack) {
        return true;
    }

    static ImportedResources prepare(ImportedResources imported, List<Asset> assets) {
        try {
            var compiled =
                    compile(
                            imported,
                            assets.stream()
                                    .filter(nu.miguel.assetChannel.asset.Font.class::isInstance)
                                    .map(nu.miguel.assetChannel.asset.Font.class::cast)
                                    .toList());
            assets.add(compiled);
            return compiled.resources();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public static CaptionFonts compile(
            ImportedResources imported, List<nu.miguel.assetChannel.asset.Font> sources)
            throws IOException {
        Map<String, byte[]> files = new TreeMap<>(imported.sourceFiles());
        Map<String, CaptionFontAsset> fonts = new TreeMap<>();
        for (var source : sources) {
            java.awt.Font face;
            try (var input = new ByteArrayInputStream(Files.readAllBytes(source.sourcePath()))) {
                face = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, input);
            } catch (FontFormatException invalid) {
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
                    throw new IOException("Invalid font sidecar " + sidecar, error);
                }
            if (!config.getBoolean("caption.enabled", true)) continue;
            double size =
                    config.getDouble("caption.size", source.provider().get("size").getAsDouble());
            int outline = config.getInt("caption.outline-width", 1),
                    shadow = config.getInt("caption.shadow-offset", 1);
            if (!Double.isFinite(size)
                    || size < 4
                    || size > 64
                    || outline < 0
                    || outline > 4
                    || shadow < 0
                    || shadow > 4)
                throw new IllegalArgumentException("Invalid caption settings: " + sidecar);
            face = face.deriveFont((float) (size * 2));
            FontRenderContext context = new FontRenderContext(null, true, true);
            LineMetrics line = face.getLineMetrics("Ag", context);
            int pad = outline * 2 + 1,
                    baseline = (int) Math.ceil(line.getAscent()) + pad,
                    cellHeight = (int) Math.ceil(line.getHeight()) + pad * 2 + shadow * 2;
            if (cellHeight % 2 != 0) cellHeight++;
            int renderHeight = cellHeight / 2;
            int lowerAscent = renderHeight - 54, upperAscent = lowerAscent + renderHeight - 1;
            // Native bitmap providers require ascent <= height. Transparent bottom
            // padding preserves the imported face's scale and baseline for large fonts.
            cellHeight = Math.max(cellHeight, upperAscent * 2);
            String characters = config.getString("caption.characters", CaptionLayout.characters());
            var points = new LinkedHashSet<Integer>();
            characters.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(points::add);
            points.add((int) '?');
            points.add((int) ' ');
            Map<Integer, GlyphVector> glyphs = new LinkedHashMap<>();
            Map<Integer, Integer> advances = new LinkedHashMap<>(),
                    renderedAdvances = new LinkedHashMap<>();
            int cellWidth = 2;
            for (int cp : points) {
                if (!face.canDisplay(cp) && cp != '?') continue;
                var glyph = face.createGlyphVector(context, Character.toString(cp));
                var bounds = glyph.getPixelBounds(context, 0, 0);
                int required =
                        bounds.isEmpty()
                                ? (int) Math.ceil(glyph.getGlyphMetrics(0).getAdvance())
                                : bounds.width + pad * 2 + shadow * 2;
                int advance = Math.max(2, (required + 1) / 2 + 1);
                renderedAdvances.put(cp, advance);
                advances.put(
                        cp, Math.max(1, Math.round(glyph.getGlyphMetrics(0).getAdvance() / 2)));
                glyphs.put(cp, glyph);
                cellWidth = Math.max(cellWidth, (advance - 1) * 2);
            }
            if (cellWidth > 256 || cellHeight > 256)
                throw new IllegalArgumentException(
                        "Caption glyph exceeds native font tile limits: " + source.getId());
            int[] codepoints = glyphs.keySet().stream().mapToInt(Integer::intValue).toArray();
            int columns = 16, rows = (codepoints.length + columns - 1) / columns;
            String prefix = "assetchannel:generated/captions/" + source.getId().replace('.', '/');
            var variants =
                    new EnumMap<CaptionFontAsset.Decoration, CaptionFontAsset.Lines>(
                            CaptionFontAsset.Decoration.class);
            JsonArray grid = new JsonArray();
            for (int row = 0; row < rows; row++) {
                StringBuilder chars = new StringBuilder();
                for (int col = 0; col < columns; col++) {
                    int index = row * columns + col;
                    chars.appendCodePoint(index < codepoints.length ? codepoints[index] : 0);
                }
                grid.add(chars.toString());
            }
            for (var decoration : CaptionFontAsset.Decoration.values()) {
                String style = decoration.name().toLowerCase(Locale.ROOT);
                boolean
                        border =
                                decoration == CaptionFontAsset.Decoration.OUTLINE
                                        || decoration == CaptionFontAsset.Decoration.OUTLINE_SHADOW,
                        drop =
                                decoration == CaptionFontAsset.Decoration.SHADOW
                                        || decoration == CaptionFontAsset.Decoration.OUTLINE_SHADOW;
                BufferedImage image =
                        new BufferedImage(
                                columns * cellWidth,
                                rows * cellHeight,
                                BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 0; i < codepoints.length; i++) {
                    int cp = codepoints[i],
                            x = i % columns * cellWidth,
                            y = i / columns * cellHeight;
                    var glyph = glyphs.get(cp);
                    var bounds = glyph.getPixelBounds(context, 0, 0);
                    Shape shape = glyph.getOutline(x + pad - bounds.x, y + baseline);
                    g.setClip(x, y, cellWidth, cellHeight);
                    if (drop && shadow > 0) {
                        g.translate(shadow * 2, shadow * 2);
                        g.setColor(color(config.getString("caption.shadow-color", "#00000099")));
                        g.fill(shape);
                        g.translate(-shadow * 2, -shadow * 2);
                    }
                    if (border && outline > 0) {
                        g.setStroke(
                                new BasicStroke(
                                        outline * 4f,
                                        BasicStroke.CAP_ROUND,
                                        BasicStroke.JOIN_ROUND));
                        g.setColor(color(config.getString("caption.outline-color", "#000000")));
                        g.draw(shape);
                    }
                    g.setColor(Color.WHITE);
                    g.fill(shape);
                    image.setRGB(x + (renderedAdvances.get(cp) - 1) * 2 - 1, y, 0x01000000);
                }
                g.dispose();
                String texture = prefix + "/" + style;
                png(files, path(texture, "textures", ".png"), image);
                for (String row : new String[] {"upper", "lower"}) {
                    JsonObject provider = new JsonObject();
                    provider.addProperty("type", "bitmap");
                    provider.addProperty("file", texture + ".png");
                    provider.addProperty("height", cellHeight / 2);
                    provider.addProperty("ascent", row.equals("upper") ? upperAscent : lowerAscent);
                    provider.add("chars", grid);
                    font(files, prefix + "/" + style + "/" + row, List.of(provider));
                }
                variants.put(
                        decoration,
                        new CaptionFontAsset.Lines(
                                Key.key(prefix + "/" + style + "/upper"),
                                Key.key(prefix + "/" + style + "/lower")));
            }
            CaptionFontAsset.Box box = null;
            String boxTexture = config.getString("caption.box.texture");
            if (boxTexture != null)
                box = box(files, imported, config, prefix, renderHeight, boxTexture);
            fonts.put(
                    source.getId(),
                    new CaptionFontAsset(
                            source.getId(), variants, advances, box, renderedAdvances));
        }
        return new CaptionFonts(fonts, ImportedResources.prepared(files));
    }

    private static CaptionFontAsset.Box box(
            Map<String, byte[]> files,
            ImportedResources imported,
            YamlConfiguration config,
            String prefix,
            int lineHeight,
            String texture)
            throws IOException {
        Key input = Key.key(texture);
        byte[] bytes =
                imported.sourceFiles()
                        .get(input.namespace() + "/textures/" + input.value() + ".png");
        if (bytes == null)
            throw new IllegalArgumentException("Missing imported caption box texture: " + texture);
        BufferedImage source = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null)
            throw new IllegalArgumentException("Invalid caption box PNG: " + texture);
        int edge = config.getInt("caption.box.border", 2),
                padding = config.getInt("caption.box.padding", 4);
        if (edge < 1
                || edge * 2 >= Math.min(source.getWidth(), source.getHeight())
                || padding < 0
                || padding > 16)
            throw new IllegalArgumentException("Invalid caption box slicing: " + texture);
        int height = lineHeight * 2 - 1 + padding * 2, ascent = lineHeight * 2 + padding - 55;
        if (height > 256)
            throw new IllegalArgumentException("Caption box exceeds native glyph limits");
        var strips = new TreeMap<Integer, Integer>();
        List<JsonObject> providers = new ArrayList<>();
        int left = 0xf000, right = 0xf001, next = 0xf002;
        for (int width : new int[] {edge, -edge, 1, 2, 4, 8, 16, 32, 64, 128}) {
            boolean side = providers.size() < 2;
            int codepoint = side ? (providers.isEmpty() ? left : right) : next++,
                    outputWidth = Math.abs(width);
            BufferedImage image =
                    new BufferedImage(outputWidth, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < outputWidth; x++) {
                    int sy =
                            y < edge
                                    ? y
                                    : y >= height - edge
                                            ? source.getHeight() - (height - y)
                                            : edge + (y - edge) % (source.getHeight() - edge * 2);
                    int sx =
                            side
                                    ? (width > 0 ? x : source.getWidth() - outputWidth + x)
                                    : edge + x % (source.getWidth() - edge * 2);
                    image.setRGB(x, y, source.getRGB(sx, sy));
                }
            image.setRGB(
                    outputWidth - 1,
                    height - 1,
                    (image.getRGB(outputWidth - 1, height - 1) >>> 24) == 0
                            ? 0x01000000
                            : image.getRGB(outputWidth - 1, height - 1));
            String key = prefix + "/box/" + Integer.toHexString(codepoint);
            png(files, path(key, "textures", ".png"), image);
            JsonObject provider = new JsonObject();
            provider.addProperty("type", "bitmap");
            provider.addProperty("file", key + ".png");
            provider.addProperty("height", height);
            provider.addProperty("ascent", ascent);
            JsonArray chars = new JsonArray();
            chars.add(Character.toString(codepoint));
            provider.add("chars", chars);
            providers.add(provider);
            if (!side) strips.put(outputWidth, codepoint);
        }
        String key = prefix + "/box";
        font(files, key, providers);
        return new CaptionFontAsset.Box(Key.key(key), left, right, edge, padding, strips);
    }

    private static Color color(String text) {
        String hex = text.replace("#", "");
        long value = Long.parseLong(hex, 16);
        if (hex.length() == 6) return new Color((int) value | 0xff000000, true);
        if (hex.length() == 8)
            return new Color((int) ((value & 255) << 24) | (int) (value >>> 8), true);
        throw new IllegalArgumentException("Use #RRGGBB or #RRGGBBAA");
    }

    private static String path(String key, String folder, String suffix) {
        return key.replace(":", "/" + folder + "/") + suffix;
    }

    private static void png(Map<String, byte[]> files, String path, BufferedImage image)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        put(files, path, bytes.toByteArray());
    }

    private static void font(Map<String, byte[]> files, String key, List<JsonObject> providers) {
        JsonArray values = new JsonArray();
        providers.forEach(values::add);
        JsonObject json = new JsonObject();
        json.add("providers", values);
        put(files, path(key, "font", ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void put(Map<String, byte[]> files, String key, byte[] bytes) {
        if (files.putIfAbsent(key, bytes) != null)
            throw new IllegalArgumentException("Generated caption resource collision: " + key);
    }
}
