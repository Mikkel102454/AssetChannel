package nu.miguel.assetChannel.api;

import java.util.Objects;

/** An imported, continuously animated screen-effect channel. */
public record EffectCurveAsset(String id, ClientEffectAsset client) {
    public enum Kind {
        ZOOM,
        FADE,
        LETTERBOX,
        DESATURATION,
        VIGNETTE
    }

    public EffectCurveAsset {
        Objects.requireNonNull(id);
        Objects.requireNonNull(client);
    }

    public Kind kind() {
        return client.composition().channel();
    }

    public double minimum() {
        return client.minimum();
    }

    public double maximum() {
        return client.maximum();
    }
}
