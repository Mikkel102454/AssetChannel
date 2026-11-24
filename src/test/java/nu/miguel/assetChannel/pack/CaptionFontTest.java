package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.*;

import nu.miguel.assetChannel.api.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;

class CaptionFontTest {
    @Test
    void decorationsUseImportedFontAndBoxTextureWithoutCoreShaderOverrides() throws Exception {
        var imported = ImportedResources.scan(Path.of("examples/cinematic-pack/assets"));
        var compiled =
                CaptionFonts.compile(
                        imported,
                        List.of(
                                new nu.miguel.assetChannel.asset.Font(
                                        "ui.subtitle",
                                        Path.of(
                                                "examples/cinematic-pack/sources/fonts/ui/subtitle.ttf"))));
        var font = compiled.fonts().get("ui.subtitle");
        assertNotNull(font.box());
        assertEquals(4, font.variants().size());
        var resources = compiled.resources();
        byte[] outline =
                resources
                        .sourceFiles()
                        .get("assetchannel/textures/generated/captions/ui/subtitle/outline.png");
        var image = javax.imageio.ImageIO.read(new ByteArrayInputStream(outline));
        boolean black = false, white = false;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) > 200) {
                    black |= (argb & 0xffffff) == 0;
                    white |= (argb & 0xffffff) == 0xffffff;
                }
            }
        assertTrue(black);
        assertTrue(white);
        assertTrue(
                resources.sourceFiles().keySet().stream()
                        .noneMatch(p -> p.contains("shaders/core/")));
        assertNotEquals(
                new String(
                        resources
                                .sourceFiles()
                                .get(
                                        "assetchannel/font/generated/captions/ui/subtitle/plain/lower.json")),
                new String(
                        resources
                                .sourceFiles()
                                .get(
                                        "assetchannel/font/generated/captions/ui/subtitle/outline/lower.json")));
    }
}
