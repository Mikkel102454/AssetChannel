package nu.miguel.assetChannel.api;

/** Initial display settings; returned Bukkit displays also expose the full native transform API. */
public record DisplayOptions(
        float scale, int interpolationTicks, int teleportTicks, boolean persistent) {
    public DisplayOptions {
        if (!Float.isFinite(scale) || scale <= 0 || scale > 64)
            throw new IllegalArgumentException("Display scale must be in (0,64]");
        if (interpolationTicks < 0
                || interpolationTicks > 1200
                || teleportTicks < 0
                || teleportTicks > 59)
            throw new IllegalArgumentException("Invalid display interpolation/teleport ticks");
    }

    public static DisplayOptions defaults() {
        return new DisplayOptions(1, 0, 0, true);
    }
}
