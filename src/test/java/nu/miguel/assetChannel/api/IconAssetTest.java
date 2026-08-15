package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IconAssetTest {
    @Test void exposesUnicodeAndRenderingMetadata() {
        IconAsset icon = new IconAsset("ui.rank", Key.key("assetchannel:icons"), 0xE000, 16, 15);

        assertEquals("\uE000", icon.character());
        assertEquals("U+E000", icon.unicode());
        assertEquals(0xE000, icon.codePoint());
        assertEquals(Key.key("assetchannel:icons"), icon.font());
    }

    @Test void supportsSupplementaryCodePoints() {
        IconAsset icon = new IconAsset("ui.test", Key.key("assetchannel:icons"), 0x100000, 8, 7);
        assertEquals(2, icon.character().length());
        assertEquals("U+100000", icon.unicode());
    }

    @Test void rejectsSurrogatesAndInvalidDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new IconAsset("bad", Key.key("assetchannel:icons"), 0xD800, 8, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new IconAsset("bad", Key.key("assetchannel:icons"), 0xE000, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new IconAsset("bad", Key.key("assetchannel:icons"), 0xE000, 8, 9));
    }
}
