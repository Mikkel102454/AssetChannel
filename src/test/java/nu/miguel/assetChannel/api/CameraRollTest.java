package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CameraRollTest {
    @Test
    void rollUsesTheShortestAngleAndItsOwnTimestamp() {
        var asset = EffectTestAssets.zoom();
        var animation =
                new EffectAnimation(1, 2, .25, 60, EffectAnimation.Easing.SMOOTH, 170, -170, true);
        var frame = new ClientEffectFrame(animation, 10, 12, .5);
        byte[] bytes = asset.encode(frame);
        assertEquals(22, bytes.length);
        var decoded = asset.decodeFrame(bytes);
        assertEquals(170, decoded.animation().roll(), .006);
        assertEquals(-170, decoded.animation().nextRoll(), .006);
        assertEquals(180, decoded.rollAt(12.5), .01);
        assertEquals(.5, decoded.correctionTicks(), .0003);
    }
}
