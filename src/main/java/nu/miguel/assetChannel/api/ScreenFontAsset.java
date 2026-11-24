package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

import java.util.*;

/**
 * Imported font and bounded vertical variants, in GUI pixels relative to the action-bar text
 * origin.
 */
public record ScreenFontAsset(
        String id, Map<Integer, Key> rows, Map<Integer, Float> advances, int lineHeight) {
    public ScreenFontAsset {
        Objects.requireNonNull(id);
        rows = Collections.unmodifiableNavigableMap(new TreeMap<>(rows));
        advances = Map.copyOf(advances);
        if (rows.isEmpty()
                || !advances.containsKey((int) '?')
                || lineHeight < 1
                || advances.values().stream().anyMatch(n -> !Float.isFinite(n) || n < 0))
            throw new IllegalArgumentException("Invalid screen font");
    }

    public int row(double y) {
        if (!Double.isFinite(y) || y < minimumY() || y > maximumY())
            throw new IllegalArgumentException(
                    "Screen Y must be in " + minimumY() + ".." + maximumY() + " for " + id);
        var sorted = (NavigableMap<Integer, Key>) rows;
        Integer a = sorted.floorKey((int) Math.floor(y)), b = sorted.ceilingKey((int) Math.ceil(y));
        return a == null ? b : b == null || y - a <= b - y ? a : b;
    }

    public Key key(double y) {
        return rows.get(row(y));
    }

    public int minimumY() {
        return rows.keySet().iterator().next();
    }

    public int maximumY() {
        return ((SortedMap<Integer, Key>) rows).lastKey();
    }

    public int normalize(int cp) {
        return advances.containsKey(cp) ? cp : '?';
    }

    public float advance(int cp) {
        return advances.get(normalize(cp));
    }
}
