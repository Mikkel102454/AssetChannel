package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.*;

import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.Font;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class CaptionFontsTest {
    @TempDir Path root;

    @Test
    void decorationPaddingDoesNotIncreaseImportedLetterSpacing() throws Exception {
        Path source = root.resolve("spacing.ttf");
        Files.copy(Path.of("examples/cinematic-pack/sources/fonts/ui/subtitle.ttf"), source);
        Files.writeString(
                root.resolve("spacing.yml"),
                "caption:\n  size: 11\n  outline-width: 2\n  shadow-offset: 3\n");
        var compiled =
                CaptionFonts.compile(
                        ImportedResources.prepared(Map.of()), List.of(new Font("spacing", source)));
        java.awt.Font face;
        try (var input = Files.newInputStream(source)) {
            face = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, input).deriveFont(22f);
        }
        var context = new java.awt.font.FontRenderContext(null, true, true);
        int expected =
                Math.round(
                        face.createGlyphVector(context, "H").getGlyphMetrics(0).getAdvance() / 2);
        assertEquals(
                expected,
                compiled.fonts().get("spacing").advance('H'),
                "Decoration padding is not letter tracking");
        var font = compiled.fonts().get("spacing");
        var appearance = new CaptionAppearance(true, true, false);
        var caption =
                CaptionLayout.compose(
                        net.kyori.adventure.text.Component.empty(),
                        net.kyori.adventure.text.Component.text("HH"),
                        font,
                        appearance);
        assertEquals(
                expected * 2,
                nativeWidth(caption, null, font),
                "The rendered component compensates for padded atlas glyphs");
    }

    private int nativeWidth(
            net.kyori.adventure.text.Component component,
            net.kyori.adventure.key.Key inherited,
            CaptionFontAsset font) {
        var key = component.font() == null ? inherited : component.font();
        int width = 0;
        if (component instanceof net.kyori.adventure.text.TextComponent text)
            for (int cp : text.content().codePoints().toArray())
                width +=
                        key != null && key.asString().equals("assetchannel:spacing")
                                ? cp - 0xE000 - 512
                                : font.renderedAdvances().get(cp);
        for (var child : component.children()) width += nativeWidth(child, key, font);
        return width;
    }

    @Test
    void importsTheFontFileAndProducesSelectableDecorationVariantsWithoutChangingSources()
            throws Exception {
        Path source = root.resolve("subtitle.ttf");
        Files.copy(Path.of("examples/cinematic-pack/sources/fonts/ui/subtitle.ttf"), source);
        Files.writeString(
                root.resolve("subtitle.yml"),
                "size: 12\ncaption:\n  outline-width: 1\n  shadow-offset: 1\n");
        byte[] original = Files.readAllBytes(source);
        var compiled =
                CaptionFonts.compile(
                        ImportedResources.prepared(Map.of()),
                        List.of(new Font("ui.subtitle", source)));
        var font = compiled.fonts().get("ui.subtitle");
        assertNotNull(font);
        assertEquals(4, font.variants().size());
        assertTrue(font.advance('W') > 0);
        assertTrue(font.advance(' ') > 0);
        assertNotEquals(
                font.variant(new CaptionAppearance(false, false, false)),
                font.variant(new CaptionAppearance(true, true, false)));
        var files = compiled.resources().sourceFiles();
        assertTrue(files.keySet().stream().anyMatch(p -> p.endsWith("/outline_shadow.png")));
        assertArrayEquals(original, Files.readAllBytes(source));
        assertTrue(
                CaptionFonts.compile(ImportedResources.prepared(Map.of()), List.of())
                        .fonts()
                        .isEmpty(),
                "No implicit system-font fallback");
    }

    @Test
    void captionGenerationDoesNotInvalidateLegacyUnparseableFonts() throws Exception {
        Path file = root.resolve("legacy.ttf");
        Files.writeString(file, "font bytes");
        assertTrue(
                CaptionFonts.compile(
                                ImportedResources.prepared(Map.of()),
                                List.of(new Font("legacy", file)))
                        .fonts()
                        .isEmpty());
    }

    @Test
    void largeImportedFontsRespectNativeBitmapAscentLimits() throws Exception {
        Path source = root.resolve("large.ttf");
        Files.copy(Path.of("examples/cinematic-pack/sources/fonts/ui/subtitle.ttf"), source);
        Files.writeString(root.resolve("large.yml"), "caption:\n  size: 64\n");
        var compiled =
                CaptionFonts.compile(
                        ImportedResources.prepared(Map.of()), List.of(new Font("large", source)));
        for (var entry : compiled.resources().sourceFiles().entrySet())
            if (entry.getKey().contains("/font/") && entry.getKey().endsWith(".json")) {
                var json =
                        com.google.gson.JsonParser.parseString(
                                        new String(
                                                entry.getValue(),
                                                java.nio.charset.StandardCharsets.UTF_8))
                                .getAsJsonObject();
                for (var value : json.getAsJsonArray("providers")) {
                    var provider = value.getAsJsonObject();
                    assertTrue(
                            provider.get("ascent").getAsInt() <= provider.get("height").getAsInt(),
                            entry.getKey());
                }
            }
    }

    @Test
    void malformedFontSidecarsFailInsteadOfPublishingDefaults() throws Exception {
        Path source = root.resolve("invalid.ttf");
        Files.copy(Path.of("examples/cinematic-pack/sources/fonts/ui/subtitle.ttf"), source);
        Files.writeString(root.resolve("invalid.yml"), "caption: [broken\n");
        assertThrows(
                java.io.IOException.class,
                () ->
                        CaptionFonts.compile(
                                ImportedResources.prepared(Map.of()),
                                List.of(new Font("invalid", source))));
    }
}
