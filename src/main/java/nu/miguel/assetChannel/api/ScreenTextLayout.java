package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;

import java.util.*;

/** Zero-advance layers share the action bar with captions and other screen labels. */
public final class ScreenTextLayout {
    private ScreenTextLayout() {}

    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    public static Component place(
            Component text,
            ScreenFontAsset font,
            double x,
            double y,
            Alignment alignment,
            boolean outline,
            boolean shadow) {
        Objects.requireNonNull(font);
        Objects.requireNonNull(alignment);
        if (!Double.isFinite(x) || Math.abs(x) > 1024)
            throw new IllegalArgumentException("Screen X must be in -1024..1024");
        font.key(y);
        List<Glyph> glyphs = new ArrayList<>();
        flatten(text, Style.style(NamedTextColor.WHITE), font, glyphs);
        Component result = Component.empty();
        var line = new ArrayList<Glyph>();
        int index = 0;
        for (var glyph : glyphs) {
            if (glyph.cp() == '\n') {
                result =
                        result.append(
                                line(
                                        line,
                                        font,
                                        x,
                                        y + index++ * font.lineHeight(),
                                        alignment,
                                        outline,
                                        shadow));
                line.clear();
            } else line.add(glyph);
        }
        return result.append(
                line(line, font, x, y + index * font.lineHeight(), alignment, outline, shadow));
    }

    private record Glyph(int cp, Style style) {}

    private static void flatten(
            Component value, Style parent, ScreenFontAsset font, List<Glyph> out) {
        var style =
                value.style()
                        .merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false)
                        .shadowColor(ShadowColor.none());
        if (value instanceof TextComponent text)
            text.content()
                    .codePoints()
                    .forEach(cp -> out.add(new Glyph(cp == '\n' ? cp : font.normalize(cp), style)));
        for (var child : value.children()) flatten(child, style, font, out);
    }

    private static Component line(
            List<Glyph> glyphs,
            ScreenFontAsset font,
            double x,
            double y,
            Alignment alignment,
            boolean outline,
            boolean shadow) {
        if (glyphs.isEmpty()) return Component.empty();
        double width = glyphs.stream().mapToDouble(g -> font.advance(g.cp())).sum();
        double left =
                x
                        - switch (alignment) {
                            case LEFT -> 0;
                            case CENTER -> width / 2;
                            case RIGHT -> width;
                        };
        Component result = Component.empty();
        if (shadow)
            result =
                    result.append(
                            pass(
                                    glyphs,
                                    font,
                                    left + 2,
                                    Math.min(font.maximumY(), y + 2),
                                    width,
                                    true));
        if (outline)
            for (int[] delta : new int[][] {{-1, 0}, {1, 0}, {0, -2}, {0, 2}})
                result =
                        result.append(
                                pass(
                                        glyphs,
                                        font,
                                        left + delta[0],
                                        Math.clamp(y + delta[1], font.minimumY(), font.maximumY()),
                                        width,
                                        true));
        return result.append(pass(glyphs, font, left, y, width, false));
    }

    private static Component pass(
            List<Glyph> glyphs,
            ScreenFontAsset font,
            double x,
            double y,
            double width,
            boolean black) {
        var key = font.key(y);
        Component result = space(x);
        Style current = null;
        var run = new StringBuilder();
        for (var glyph : glyphs) {
            var style = glyph.style().font(key);
            if (black) style = style.color(NamedTextColor.BLACK);
            if (current != null && !current.equals(style)) {
                result = result.append(Component.text(run.toString()).style(current));
                run.setLength(0);
            }
            current = style;
            run.appendCodePoint(glyph.cp());
        }
        if (current != null) result = result.append(Component.text(run.toString()).style(current));
        return result.append(space(-x - width));
    }

    /** Fractional GUI-pixel cursor movement; the resource provider is generated by AssetChannel. */
    public static Component space(double pixels) {
        long units = Math.round(pixels * 64);
        Component result = Component.empty();
        long integer = units / 64;
        while (integer != 0) {
            int step = (int) Math.clamp(integer, -512, 512);
            result =
                    result.append(
                            Component.text(Character.toString(0xE000 + step + 512))
                                    .font(Key.key("assetchannel:spacing"))
                                    .shadowColor(ShadowColor.none()));
            integer -= step;
        }
        int fraction = (int) (units % 64);
        if (fraction != 0)
            result =
                    result.append(
                            Component.text(Character.toString(0xF100 + fraction + 64))
                                    .font(Key.key("assetchannel:screen_spacing"))
                                    .shadowColor(ShadowColor.none()));
        return result;
    }
}
