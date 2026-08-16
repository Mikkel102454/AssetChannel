package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

import java.util.Objects;

/** Metadata required to render an icon from the generated resource pack. */
public record IconAsset(String id, Key font, int codePoint, int height, int ascent, int width) {
    public IconAsset(String id, Key font, int codePoint, int height, int ascent) {
        this(id, font, codePoint, height, ascent, height);
    }
    public IconAsset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(font, "font");
        if (!Character.isValidCodePoint(codePoint)
                || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
            throw new IllegalArgumentException("Invalid Unicode code point: " + codePoint);
        }
        if (height <= 0) throw new IllegalArgumentException("height must be greater than zero");
        if (width <= 0) throw new IllegalArgumentException("width must be greater than zero");
        if (ascent > height) throw new IllegalArgumentException("ascent cannot be greater than height");
    }

    /** The actual character to put in an Adventure text component. */
    public String character() {
        return Character.toString(codePoint);
    }

    /** The conventional Unicode representation, for example {@code U+E000}. */
    public String unicode() {
        return "U+" + String.format("%04X", codePoint);
    }
}
