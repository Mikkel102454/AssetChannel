package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

import java.util.Objects;

/** Opt-in contract for imported clock-composite-v1 shaders. Channels occupy 22 pixels each. */
public record ClientEffectComposition(
        Key prefix,
        Key vertexShader,
        Key fragmentShader,
        EffectCurveAsset.Kind channel,
        double neutral) {
    public static final int STRIDE = 22;

    public ClientEffectComposition {
        Objects.requireNonNull(prefix);
        Objects.requireNonNull(vertexShader);
        Objects.requireNonNull(fragmentShader);
        Objects.requireNonNull(channel);
        if (!Double.isFinite(neutral))
            throw new IllegalArgumentException("Non-finite neutral value");
    }

    public int offset() {
        return channel.ordinal() * STRIDE;
    }

    public int mask() {
        return 1 << channel.ordinal();
    }

    public Key renderer(int mask) {
        if (mask <= 0 || mask >= 32) throw new IllegalArgumentException("Invalid channel mask");
        return Key.key(prefix.namespace(), prefix.value() + "/render/" + mask);
    }

    public boolean owns(Key key) {
        return key.namespace().equals(prefix.namespace())
                && key.value().startsWith(prefix.value() + "/");
    }
}
