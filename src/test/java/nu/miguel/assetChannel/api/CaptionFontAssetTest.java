package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

import java.util.*;

class CaptionFontAssetTest {
    static CaptionFontAsset font() {
        var variants =
                new EnumMap<CaptionFontAsset.Decoration, CaptionFontAsset.Lines>(
                        CaptionFontAsset.Decoration.class);
        for (var style : CaptionFontAsset.Decoration.values())
            variants.put(
                    style,
                    new CaptionFontAsset.Lines(
                            Key.key("test:" + style.name().toLowerCase() + "/upper"),
                            Key.key("test:" + style.name().toLowerCase() + "/lower")));
        return new CaptionFontAsset(
                "proportional",
                variants,
                Map.of((int) 'W', 10, (int) 'i', 2, (int) ' ', 3, (int) '?', 5),
                null);
    }

    @Test
    void importedMetricsControlWrappingAndCentring() {
        var font = font();
        assertEquals(14, CaptionLayout.width(Component.text("Wii"), font));
        var lines = CaptionLayout.wrap(Component.text("Wi Wi"), 15, font);
        assertEquals(2, lines.size());
        assertEquals(5, font.advance(0x1f600));
        var composed =
                CaptionLayout.compose(
                        Component.text("Wi"),
                        Component.text("i"),
                        font,
                        new CaptionAppearance(true, false, false));
        assertTrue(
                composed.children().stream()
                        .anyMatch(c ->
                                font.variant(new CaptionAppearance(true, false, false))
                                        .upper()
                                        .equals(c.font())));
        assertThrows(UnsupportedOperationException.class, () -> font.advances().clear());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CaptionLayout.compose(
                                Component.text("W"),
                                Component.empty(),
                                font,
                                new CaptionAppearance(false, false, true)));
    }

    @Test
    void boxAndLinePlacementRetainFullWidthsDuringTypewriterReveal() {
        var original = font();
        var box =
                new CaptionFontAsset.Box(
                        Key.key("test:box"),
                        0xf000,
                        0xf001,
                        2,
                        4,
                        Map.of(1, 0xf002, 2, 0xf003, 4, 0xf004, 8, 0xf005, 16, 0xf006));
        var font =
                new CaptionFontAsset(original.id(), original.variants(), original.advances(), box);
        var appearance = new CaptionAppearance(true, true, true);
        var start =
                CaptionLayout.compose(
                        Component.empty(), Component.empty(), font, appearance, 14, 2);
        var partial =
                CaptionLayout.compose(
                        Component.text("W"), Component.text("i"), font, appearance, 14, 2);
        assertEquals(
                start.children().getFirst(),
                partial.children().getFirst(),
                "Box does not resize during reveal");
        assertTrue(
                start.children().getFirst().children().stream()
                        .anyMatch(c -> box.font().equals(c.font())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CaptionLayout.compose(
                                Component.text("W"), Component.empty(), font, appearance, 2, 0));
    }
}
