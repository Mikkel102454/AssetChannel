package nu.miguel.assetChannel.api.internal;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import nu.miguel.assetChannel.api.IconAsset;
import nu.miguel.assetChannel.asset.Hud;
import nu.miguel.assetChannel.pack.SpacingFont;

import java.util.*;

final class HudRenderer {
    private static final Key ICON_FONT = Key.key("assetchannel:icons");
    private static final Key SPACING_FONT = Key.key(SpacingFont.KEY);
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, IconAsset> icons;
    private final Map<Integer, Integer> iconWidths = new HashMap<>();

    HudRenderer(Map<String, IconAsset> icons) {
        this.icons = icons;
        icons.values().forEach(icon -> iconWidths.put(icon.codePoint(), icon.width()));
    }

    Component render(Hud hud, Map<String, Component> values, boolean fallback) {
        String source = fallback ? hud.fallbackContent() : hud.content();
        Component content = parse(source, values);
        if (fallback || hud.background() == null && hud.width() == null) return content;
        int backgroundWidth =
                hud.width() == null ? icons.get(hud.background()).width() : hud.width();
        int maximum = Math.max(0, backgroundWidth - hud.horizontalPadding() * 2);
        content = overflow(content, maximum, hud.overflow());
        int contentWidth = width(content);
        int left = Math.max(0, (backgroundWidth - contentWidth) / 2);
        Component result = Component.empty();
        if (hud.background() != null)
            result =
                    result.append(icon(icons.get(hud.background())))
                            .append(spacing(-backgroundWidth));
        result =
                result.append(spacing(left))
                        .append(content)
                        .append(spacing(Math.max(0, backgroundWidth - left - contentWidth)));
        return result;
    }

    Component subtitle(Hud hud, Map<String, Component> values, boolean fallback) {
        return fallback ? Component.empty() : parse(hud.subtitle(), values);
    }

    Component parseAssetText(String source) {
        return parse(source, Map.of());
    }

    Map<String, Component> resolve(Hud hud, Map<String, Component> supplied) {
        if (!hud.variables().keySet().containsAll(supplied.keySet())) return null;
        Map<String, Component> result = new LinkedHashMap<>();
        for (var entry : hud.variables().entrySet()) {
            Component value = supplied.get(entry.getKey());
            if (value == null && entry.getValue().defaultValue() != null)
                value = parse(entry.getValue().defaultValue(), Map.of());
            if (value == null && entry.getValue().required()) return null;
            result.put(entry.getKey(), value == null ? Component.empty() : value);
        }
        return Map.copyOf(result);
    }

    private Component parse(String source, Map<String, Component> values) {
        TagResolver variables =
                TagResolver.resolver(
                        "var",
                        (args, context) -> {
                            String name = args.popOr("HUD variable name is required").value();
                            Component value = values.get(name);
                            return value == null ? null : Tag.inserting(value);
                        });
        TagResolver icon =
                TagResolver.resolver(
                        "icon",
                        (args, context) -> {
                            IconAsset value =
                                    icons.get(
                                            args.popOr("Icon id is required")
                                                    .value()
                                                    .toLowerCase(Locale.ROOT));
                            return value == null ? null : Tag.inserting(icon(value));
                        });
        return mini.deserialize(
                source == null ? "" : source, variables, icon, SpacingFont.resolver());
    }

    private static Component icon(IconAsset icon) {
        return Component.text(icon.character()).font(icon.font());
    }

    int width(Component component) {
        return Math.max(0, rawWidth(component, Style.empty()) - 1);
    }

    private int rawWidth(Component component, Style parent) {
        Style style = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        int total = 0;
        if (component instanceof TextComponent text) {
            Key font = style.font();
            for (int cp : text.content().codePoints().toArray()) {
                total += advance(cp, style);
            }
        }
        for (Component child : component.children()) total += rawWidth(child, style);
        return total;
    }

    private int advance(int cp, Style style) {
        if (SPACING_FONT.equals(style.font())) return cp - SpacingFont.BASE + SpacingFont.MIN;
        int width =
                ICON_FONT.equals(style.font()) && iconWidths.containsKey(cp)
                        ? iconWidths.get(cp)
                        : defaultWidth(cp);
        return width
                + 1
                + (cp != ' ' && style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
                        ? 1
                        : 0);
    }

    private static int defaultWidth(int cp) {
        if (cp == ' ') return 3;
        if ("!.,:;|il".indexOf(cp) >= 0) return 1;
        if ("'`".indexOf(cp) >= 0) return 2;
        if ("[]I".indexOf(cp) >= 0) return 3;
        if ("(){}t\"*<>".indexOf(cp) >= 0) return 4;
        if ("fk<>".indexOf(cp) >= 0) return 4;
        if ("@~".indexOf(cp) >= 0) return 6;
        if (cp >= 0x2E80) return 8;
        return 5;
    }

    private Component overflow(Component value, int maximum, Hud.Overflow mode) {
        if (width(value) <= maximum) return value;
        Component suffix =
                mode == Hud.Overflow.ELLIPSIS && width(Component.text("...")) <= maximum
                        ? Component.text("...")
                        : Component.empty();
        int allowed = Math.max(0, maximum + 1 - rawWidth(suffix, Style.empty()));
        Component clipped = truncate(value, allowed, new int[] {0, 0}, Style.empty());
        return clipped.append(suffix);
    }

    private Component truncate(Component component, int maximum, int[] used, Style parent) {
        Style style = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        Component out = component.children(List.of());
        if (component instanceof TextComponent text) {
            StringBuilder kept = new StringBuilder();
            for (int cp : text.content().codePoints().toArray()) {
                int w = advance(cp, style);
                if (used[0] + w > maximum) {
                    used[1] = 1;
                    break;
                }
                kept.appendCodePoint(cp);
                used[0] += w;
            }
            out = text.content(kept.toString()).children(List.of());
        }
        for (Component child : component.children()) {
            if (used[1] != 0) break;
            out = out.append(truncate(child, maximum, used, style));
        }
        return out;
    }

    private static Component spacing(int pixels) {
        return SpacingFont.component(pixels);
    }
}
