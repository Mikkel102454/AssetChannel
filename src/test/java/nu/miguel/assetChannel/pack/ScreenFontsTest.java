package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import nu.miguel.assetChannel.asset.Font;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class ScreenFontsTest {
    @TempDir Path root;

    @Test
    void derivesPositionedVariantsFromImportedMediaAndRetainsSource() throws Exception {
        Path source = root.resolve("text.ttf");
        Files.copy(Path.of("examples/cinematic-pack/sources/fonts/ui/subtitle.ttf"), source);
        byte[] before = Files.readAllBytes(source);
        Files.writeString(
                root.resolve("text.yml"),
                "size: 12\nscreen:\n  min-y: -10\n  max-y: 10\n  step: 2\n");
        var compiled =
                ScreenFonts.compile(
                        ImportedResources.prepared(Map.of()), List.of(new Font("ui.text", source)));
        var font = compiled.fonts().get("ui.text");
        assertNotNull(font);
        assertEquals(11, font.rows().size());
        assertEquals(-10, font.minimumY());
        assertEquals(10, font.maximumY());
        assertThrows(IllegalArgumentException.class, () -> font.key(-11));
        assertTrue(font.advance('W') > 0);
        var resource =
                compiled.resources()
                        .sourceFiles()
                        .get(font.key(-6).asString().replace(":", "/font/") + ".json");
        var provider =
                JsonParser.parseString(
                                new String(resource, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject()
                        .getAsJsonArray("providers")
                        .get(0)
                        .getAsJsonObject();
        assertEquals(-6, provider.getAsJsonArray("shift").get(1).getAsDouble());
        assertEquals("ttf", provider.get("type").getAsString());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test
    void invalidRangesFailAndDisabledFontsHaveNoImplicitFallback() throws Exception {
        Path source = root.resolve("text.ttf");
        Files.copy(Path.of("examples/cinematic-pack/sources/fonts/ui/subtitle.ttf"), source);
        Files.writeString(root.resolve("text.yml"), "screen:\n  min-y: 10\n  max-y: -10\n");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScreenFonts.compile(
                                ImportedResources.prepared(Map.of()),
                                List.of(new Font("text", source))));
        Files.writeString(root.resolve("text.yml"), "screen:\n  enabled: false\n");
        assertTrue(
                ScreenFonts.compile(
                                ImportedResources.prepared(Map.of()),
                                List.of(new Font("text", source)))
                        .fonts()
                        .isEmpty());
    }
}
