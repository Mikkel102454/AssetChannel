package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class ScreenTextLayoutTest {
    @Test
    void longLabelsGroupRunsInsteadOfCreatingAPacketNodeForEveryLetter() {
        var result =
                ScreenTextLayout.place(
                        Component.text("A".repeat(512)),
                        font(),
                        0,
                        0,
                        ScreenTextLayout.Alignment.CENTER,
                        true,
                        true);
        assertTrue(
                nodes(result) < 128,
                "Keep decorated text components bounded for network transport");
        assertEquals(0, width(result, null), .02);
    }

    int nodes(Component component) {
        return 1 + component.children().stream().mapToInt(this::nodes).sum();
    }

    ScreenFontAsset font() {
        return new ScreenFontAsset(
                "font",
                Map.of(-2, Key.key("test:n2"), 0, Key.key("test:p0"), 2, Key.key("test:p2")),
                Map.of((int) '?', 5.5f, (int) 'A', 6.25f),
                12);
    }

    @Test
    void positionedLayersReturnTheCursorAndDoNotMoveTheirNeighbours() {
        var value =
                ScreenTextLayout.place(
                        Component.text("AA"),
                        font(),
                        80,
                        0,
                        ScreenTextLayout.Alignment.RIGHT,
                        true,
                        true);
        assertFalse(value.equals(Component.empty()));
        assertEquals(0, width(value, null), .02);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScreenTextLayout.place(
                                Component.text("AA"),
                                font(),
                                80,
                                10,
                                ScreenTextLayout.Alignment.LEFT,
                                false,
                                false));
    }

    double width(Component value, Key inherited) {
        Key key = value.font() == null ? inherited : value.font();
        double width = 0;
        if (value instanceof TextComponent text)
            for (int cp : text.content().codePoints().toArray()) {
                if (key.asString().equals("assetchannel:spacing")) width += cp - 0xE000 - 512;
                else if (key.asString().equals("assetchannel:screen_spacing"))
                    width += (cp - 0xF100 - 64) / 64d;
                else width += font().advance(cp);
            }
        for (var child : value.children()) width += width(child, key);
        return width;
    }
}
