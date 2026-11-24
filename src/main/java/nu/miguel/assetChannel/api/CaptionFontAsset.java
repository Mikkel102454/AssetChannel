package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

import java.util.*;

/** Immutable caption rendering variants and measured advances derived from an imported font. */
public record CaptionFontAsset(
        String id,
        Map<Decoration, Lines> variants,
        Map<Integer, Integer> advances,
        Box box,
        Map<Integer, Integer> renderedAdvances) {
    public CaptionFontAsset(
            String id, Map<Decoration, Lines> variants, Map<Integer, Integer> advances, Box box) {
        this(id, variants, advances, box, advances);
    }

    public enum Decoration {
        PLAIN,
        OUTLINE,
        SHADOW,
        OUTLINE_SHADOW
    }

    public record Lines(Key upper, Key lower) {
        public Lines {
            Objects.requireNonNull(upper);
            Objects.requireNonNull(lower);
        }
    }

    /** Border/centre slices generated from the asset's imported box texture. */
    public record Box(
            Key font, int left, int right, int edge, int padding, Map<Integer, Integer> strips) {
        public Box {
            Objects.requireNonNull(font);
            strips = Map.copyOf(strips);
            if (edge < 1 || padding < 0 || !strips.containsKey(1))
                throw new IllegalArgumentException("Invalid caption box");
        }
    }

    public CaptionFontAsset {
        Objects.requireNonNull(id);
        variants = Map.copyOf(variants);
        advances = Map.copyOf(advances);
        renderedAdvances = Map.copyOf(renderedAdvances);
        if (variants.isEmpty()
                || !advances.containsKey((int) '?')
                || advances.values().stream().anyMatch(v -> v < 1 || v > 1024)
                || !renderedAdvances.keySet().containsAll(advances.keySet()))
            throw new IllegalArgumentException("Invalid caption metrics");
    }

    public int normalize(int codepoint) {
        return advances.containsKey(codepoint) ? codepoint : '?';
    }

    public int advance(int codepoint) {
        return advances.get(normalize(codepoint));
    }

    public int spacingCorrection(int codepoint) {
        int cp = normalize(codepoint);
        return advances.get(cp) - renderedAdvances.get(cp);
    }

    public Lines variant(CaptionAppearance appearance) {
        var key =
                appearance.outline()
                        ? (appearance.shadow() ? Decoration.OUTLINE_SHADOW : Decoration.OUTLINE)
                        : (appearance.shadow() ? Decoration.SHADOW : Decoration.PLAIN);
        var value = variants.get(key);
        if (value == null)
            throw new IllegalArgumentException("Font " + id + " has no " + key + " variant");
        return value;
    }
}
