package nu.miguel.assetChannel.asset;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FontTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    @Test void readsFontSizingAndShiftSidecar() throws Exception {
        Path source = directory.resolve("hud.ttf");
        java.nio.file.Files.writeString(directory.resolve("hud.yml"), "size: 7\noversample: 4\nshift: [0, 98]\n");
        var provider = new Font("hud", source).provider();
        assertEquals(7, provider.get("size").getAsDouble());
        assertEquals(4, provider.get("oversample").getAsDouble());
        assertEquals(98, provider.getAsJsonArray("shift").get(1).getAsDouble());
    }
    @Test void nestedFontIdUsesNestedDefinitionPath() {
        Font font = new Font("ui.title", Path.of("title.ttf"));

        assertEquals(Path.of("pack", "assets", "assetchannel", "font", "ui", "title.json"),
                font.definitionPath(Path.of("pack", "assets", "assetchannel")));
        assertEquals("assetchannel:ui.title", font.getKey());
        assertEquals("assetchannel:ui/title.ttf", font.providerFile());
    }
}
