package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.key.Key;

import org.junit.jupiter.api.Test;

class ClientEffectAssetTest {
    static ClientEffectAsset asset() {
        return new ClientEffectAsset(
                Key.key("test:control"),
                Key.key("test:writer"),
                Key.key("test:value"),
                Key.key("test:screen"),
                Key.key("test:zoom"),
                Key.key("test:copy"),
                1,
                4);
    }

    @Test
    void encodesFractionalZoomAndLongTransitionsWithoutOnePercentSteps() {
        var asset = asset();
        var value =
                new EffectAnimation(1.12345, 2.6789, .42, 72000, EffectAnimation.Easing.SMOOTHER);
        byte[] bytes = asset.encode(value, 24007);
        var decoded = asset.decode(bytes);
        assertEquals(value.from(), decoded.animation().from(), .00005);
        assertEquals(value.to(), decoded.animation().to(), .00005);
        assertEquals(.42, decoded.animation().progress(), .00002);
        assertEquals(72000, decoded.animation().durationTicks());
        assertEquals(7, decoded.gameTick());
        assertEquals(value.easing(), decoded.animation().easing());
        assertEquals(ClientEffectAsset.CONTROL_BYTES + 1, asset.keys(bytes).size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        asset.encode(
                                new EffectAnimation(.5, 2, 0, 20, EffectAnimation.Easing.LINEAR),
                                0));
    }

    @Test
    void clientSamplingHandlesFractionalTicksClockRolloverAndPausedProgress() {
        var animation = new EffectAnimation(1, 3, .25, 100, EffectAnimation.Easing.LINEAR);
        assertEquals(1.51, animation.value(23999, 23999.5), 1e-6);
        assertEquals(1.53, animation.value(23999, .5), 1e-6);
        var paused = new EffectAnimation(1, 3, .25, 0, EffectAnimation.Easing.LINEAR);
        assertEquals(1.5, paused.value(23999, 500));
    }
}
