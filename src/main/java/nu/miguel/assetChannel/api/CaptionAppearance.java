package nu.miguel.assetChannel.api;

/** Scene-level decoration choices; rendering data comes from an imported font/style asset. */
public record CaptionAppearance(boolean outline, boolean shadow, boolean box) {
    public static final CaptionAppearance DEFAULT = new CaptionAppearance(true, false, false);
}
