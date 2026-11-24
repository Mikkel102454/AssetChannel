package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

class CaptionLayoutTest {
    private CaptionFontAsset font() {
        var styles =
                new java.util.EnumMap<CaptionFontAsset.Decoration, CaptionFontAsset.Lines>(
                        CaptionFontAsset.Decoration.class);
        for (var style : CaptionFontAsset.Decoration.values())
            styles.put(
                    style,
                    new CaptionFontAsset.Lines(
                            net.kyori.adventure.key.Key.key(
                                    "test:" + style.name().toLowerCase() + "/upper"),
                            net.kyori.adventure.key.Key.key(
                                    "test:" + style.name().toLowerCase() + "/lower")));
        var widths = new java.util.HashMap<Integer, Integer>();
        CaptionLayout.characters().codePoints().forEach(cp -> widths.put(cp, 7));
        return new CaptionFontAsset("test", styles, widths, null);
    }

    private String plain(Component value) {
        return PlainTextComponentSerializer.plainText().serialize(value);
    }

    @Test
    void wrapsAtWordsPreservingColourAndHandlesNewlines() {
        var lines =
                CaptionLayout.wrap(
                        Component.text("Hello world\nÆØÅ", NamedTextColor.YELLOW), 42, font());
        assertEquals(
                java.util.List.of("Hello", "world", "ÆØÅ"),
                lines.stream().map(this::plain).toList());
        assertEquals(NamedTextColor.YELLOW, lines.getFirst().children().getFirst().color());
        assertThrows(
                IllegalArgumentException.class,
                () -> CaptionLayout.wrap(Component.text("test"), 0, font()));
    }

    @Test
    void layoutUsesBothFontLinesAndHasAnExplicitSingleLineMode() {
        Component result =
                CaptionLayout.compose(
                        Component.text("First"),
                        Component.text("Second"),
                        font(),
                        CaptionAppearance.DEFAULT);
        assertTrue(
                result.children().stream()
                        .anyMatch(c ->
                                font().variant(CaptionAppearance.DEFAULT)
                                        .upper()
                                        .equals(c.font())));
        assertTrue(
                result.children().stream()
                        .anyMatch(c ->
                                font().variant(CaptionAppearance.DEFAULT)
                                        .lower()
                                        .equals(c.font())));
        assertEquals(35, CaptionLayout.width(Component.text("First"), font()));
        assertEquals(
                Component.empty(),
                CaptionLayout.compose(
                        Component.empty(), Component.empty(), font(), CaptionAppearance.DEFAULT));
        assertEquals(
                "A?B", plain(CaptionLayout.wrap(Component.text("A😀B"), 280, font()).getFirst()));
    }
}
