package nu.miguel.assetChannel.asset;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FontTest {
    @Test void nestedFontIdUsesNestedDefinitionPath() {
        Font font = new Font("ui.title", Path.of("title.ttf"));

        assertEquals(Path.of("pack", "assets", "assetchannel", "font", "ui", "title.json"),
                font.definitionPath(Path.of("pack", "assets", "assetchannel")));
        assertEquals("assetchannel:ui.title", font.getKey());
        assertEquals("assetchannel:ui/title.ttf", font.providerFile());
    }
}
