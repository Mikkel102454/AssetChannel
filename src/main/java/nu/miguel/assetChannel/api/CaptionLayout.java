package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;

import java.util.*;

/** Caption layout uses asset-provided glyph metrics and rendering variants. */
public final class CaptionLayout {
    private static final Key SPACING = Key.key("assetchannel:spacing");

    private CaptionLayout() {}

    public static String characters() {
        StringBuilder text = new StringBuilder();
        for (int i = 32; i <= 126; i++) text.appendCodePoint(i);
        for (int i = 160; i <= 255; i++) text.appendCodePoint(i);
        return text + "\u2018\u2019\u201c\u201d\u2013\u2014\u2026\u2022\u2192\u2190\u2191\u2193";
    }

    private record Glyph(int codepoint, Style style) {}

    private static List<Glyph> glyphs(Component component, CaptionFontAsset font) {
        List<Glyph> result = new ArrayList<>();
        flatten(component, Style.style(NamedTextColor.WHITE), font, result);
        return result;
    }

    private static void flatten(
            Component component, Style parent, CaptionFontAsset font, List<Glyph> result) {
        Style style =
                component
                        .style()
                        .merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false)
                        .shadowColor(ShadowColor.none());
        if (component instanceof TextComponent text)
            text.content()
                    .codePoints()
                    .forEach(cp ->
                            result.add(new Glyph(cp == '\n' ? cp : font.normalize(cp), style)));
        for (Component child : component.children()) flatten(child, style, font, result);
    }

    private static Component text(List<Glyph> glyphs, Key key) {
        return text(glyphs, key, null);
    }

    private static Component text(List<Glyph> glyphs, Key key, CaptionFontAsset font) {
        Component result = Component.empty();
        for (var glyph : glyphs) {
            result =
                    result.append(
                            Component.text(Character.toString(glyph.codepoint()))
                                    .style(key == null ? glyph.style() : glyph.style().font(key)));
            if (font != null)
                result = result.append(space(font.spacingCorrection(glyph.codepoint())));
        }
        return key == null ? result : result.font(key);
    }

    public static List<Component> wrap(Component content, int width, CaptionFontAsset font) {
        Objects.requireNonNull(font);
        if (width < 1 || width > 1024)
            throw new IllegalArgumentException("Caption width must be in 1..1024 GUI pixels");
        List<Component> lines = new ArrayList<>();
        List<Glyph> line = new ArrayList<>();
        int pixels = 0;
        for (var glyph : glyphs(content, font)) {
            if (glyph.codepoint() == '\n') {
                lines.add(text(trim(line), null));
                line.clear();
                pixels = 0;
                continue;
            }
            line.add(glyph);
            pixels += font.advance(glyph.codepoint());
            if (pixels > width && line.size() > 1) {
                int space = -1;
                for (int i = 0; i < line.size(); i++) if (line.get(i).codepoint() == ' ') space = i;
                int end = space > 0 ? space : line.size() - 1;
                lines.add(text(trim(line.subList(0, end)), null));
                line = new ArrayList<>(line.subList(space > 0 ? space + 1 : end, line.size()));
                while (!line.isEmpty() && line.getFirst().codepoint() == ' ') line.removeFirst();
                pixels = line.stream().mapToInt(g -> font.advance(g.codepoint())).sum();
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) lines.add(text(trim(line), null));
        return List.copyOf(lines);
    }

    private static List<Glyph> trim(List<Glyph> line) {
        int end = line.size();
        while (end > 0 && line.get(end - 1).codepoint() == ' ') end--;
        return line.subList(0, end);
    }

    public static int width(Component content, CaptionFontAsset font) {
        return glyphs(content, font).stream()
                .filter(g -> g.codepoint() != '\n')
                .mapToInt(g -> font.advance(g.codepoint()))
                .sum();
    }

    public static Component compose(
            Component upper, Component lower, CaptionFontAsset font, CaptionAppearance appearance) {
        return compose(upper, lower, font, appearance, width(upper, font), width(lower, font));
    }

    /** Full-line widths retain stable centring and box size during typewriter reveal. */
    public static Component compose(
            Component upper,
            Component lower,
            CaptionFontAsset font,
            CaptionAppearance appearance,
            int upperWidth,
            int lowerWidth) {
        var variant = font.variant(appearance);
        if (appearance.box() && font.box() == null)
            throw new IllegalArgumentException(
                    "Font " + font.id() + " has no imported caption box texture");
        int a = width(upper, font), b = width(lower, font);
        if (upperWidth < a || lowerWidth < b)
            throw new IllegalArgumentException(
                    "Full caption widths cannot be smaller than visible text");
        int width = Math.max(upperWidth, lowerWidth);
        if (width == 0) return Component.empty();
        int leftA = (width - upperWidth) / 2, leftB = (width - lowerWidth) / 2;
        Component result = Component.empty();
        if (appearance.box()) result = result.append(box(font.box(), width));
        return result.append(space(leftA))
                .append(text(glyphs(upper, font), variant.upper(), font))
                .append(space(-leftA - a))
                .append(space(leftB))
                .append(text(glyphs(lower, font), variant.lower(), font))
                .append(space(width - leftB - b));
    }

    private static Component box(CaptionFontAsset.Box box, int textWidth) {
        int total = Math.max(box.edge() * 2, textWidth + box.padding() * 2),
                remaining = total - box.edge() * 2;
        int leftPadding = (total - textWidth) / 2;
        Component result = space(-leftPadding).append(slice(box.font(), box.left()));
        List<Integer> sizes =
                box.strips().keySet().stream().sorted(Comparator.reverseOrder()).toList();
        for (int size : sizes)
            while (remaining >= size) {
                result = result.append(slice(box.font(), box.strips().get(size)));
                remaining -= size;
            }
        return result.append(slice(box.font(), box.right())).append(space(leftPadding - total));
    }

    private static Component slice(Key font, int codepoint) {
        return Component.text(Character.toString(codepoint))
                .font(font)
                .color(NamedTextColor.WHITE)
                .shadowColor(ShadowColor.none())
                .append(space(-1));
    }

    private static Component space(int pixels) {
        Component result = Component.empty();
        while (pixels != 0) {
            int step = Math.clamp(pixels, -512, 512);
            result =
                    result.append(
                            Component.text(Character.toString(0xE000 + step + 512))
                                    .font(SPACING)
                                    .decoration(TextDecoration.BOLD, false)
                                    .shadowColor(ShadowColor.none()));
            pixels -= step;
        }
        return result;
    }
}
