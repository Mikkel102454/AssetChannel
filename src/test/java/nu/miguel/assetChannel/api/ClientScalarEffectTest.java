package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.key.Key;

import org.junit.jupiter.api.Test;

class ClientScalarEffectTest {
    @Test
    void scalarCurvesSupportZeroReversePauseAndClockRollover() {
        var key = Key.key("demo:shader");
        var asset = EffectTestAssets.client("fade", EffectCurveAsset.Kind.FADE);
        var animation = new EffectAnimation(1, 0, .25, 40, EffectAnimation.Easing.LINEAR);
        var decoded = asset.decodeFrame(asset.encode(animation, 23999));
        assertEquals(.725, decoded.animation().value(decoded.curveTick(), 0), .0001);
        assertEquals(15, asset.controlBytes());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        asset.encode(
                                new EffectAnimation(
                                        0, 1, 0, 20, EffectAnimation.Easing.LINEAR, 1, 2, true),
                                0));
    }
}
