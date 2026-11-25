package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

/** Small valid imported descriptors shared by the effect contract tests. */
public final class EffectTestAssets {
    private EffectTestAssets() {}

    public static ClientEffectAsset zoom() {
        return client("zoom", EffectCurveAsset.Kind.ZOOM);
    }

    public static ClientEffectAsset client(String name, EffectCurveAsset.Kind kind) {
        var key = Key.key("test:" + name);
        boolean zoom = kind == EffectCurveAsset.Kind.ZOOM;
        return new ClientEffectAsset(
                key,
                Key.key("test:writer"),
                Key.key("test:value"),
                Key.key("test:screen"),
                Key.key("test:copy"),
                zoom ? 1 : 0,
                zoom ? 4 : 1,
                new ClientEffectComposition(
                        Key.key("test:composite"),
                        Key.key("test:vertex"),
                        Key.key("test:fragment"),
                        kind,
                        zoom ? 1 : 0));
    }
}
