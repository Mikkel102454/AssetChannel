package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

import java.util.*;

/** Numeric presentation controls mapped to imported native post-effect resources. */
public record EffectCurveAsset(
        String id, Kind kind, NavigableMap<Double, Key> profiles, ClientEffectAsset client) {
    public EffectCurveAsset(String id, Kind kind, NavigableMap<Double, Key> profiles) {
        this(id, kind, profiles, null);
    }

    public enum Kind {
        ZOOM,
        FADE
    }

    public EffectCurveAsset {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        profiles = Collections.unmodifiableNavigableMap(new TreeMap<>(profiles));
        if (profiles.isEmpty() || profiles.keySet().stream().anyMatch(v -> !Double.isFinite(v)))
            throw new IllegalArgumentException("Effect curve needs finite profiles");
    }

    public double minimum() {
        return profiles.firstKey();
    }

    public double maximum() {
        return profiles.lastKey();
    }

    public Key key(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid effect value");
        var before = profiles.floorEntry(value);
        var after = profiles.ceilingEntry(value);
        if (before == null) return after.getValue();
        if (after == null) return before.getValue();
        return value - before.getKey() < after.getKey() - value
                ? before.getValue()
                : after.getValue();
    }
}
