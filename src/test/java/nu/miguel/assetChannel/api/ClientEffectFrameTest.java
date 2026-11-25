package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.key.Key;

import org.junit.jupiter.api.Test;

class ClientEffectFrameTest {
    @Test
    void negativeCorrectionsDoNotReverseAnAdvancingCurve() {
        var f =
                new ClientEffectFrame(
                        new EffectAnimation(0, 1, .3, 80, EffectAnimation.Easing.LINEAR),
                        100,
                        100,
                        -2);
        double previous = f.value(100);
        for (double tick = 100.1; tick <= 105; tick += .1) {
            double value = f.value(tick);
            assertTrue(value >= previous - 1e-10);
            previous = value;
        }
    }

    @Test
    void correctionStartsOnPreviousTrajectoryAndConvergesWithoutChangingRollClock() {
        var a = new EffectAnimation(0, 1, .3, 80, EffectAnimation.Easing.LINEAR, 10, 20, true);
        var frame = new ClientEffectFrame(a, 100, 102, 1);
        assertEquals(.2875, frame.value(100), .00001);
        assertEquals(.3375, frame.value(103), .00001);
        assertEquals(15, frame.rollAt(102.5), .00001);
        assertEquals(frame.value(102), new ClientEffectFrame(a, 100, 103, 1).value(102));
    }

    @Test
    void transportCarriesSeparateAnchorsAndCorrection() {
        var k = Key.key("test:shader");
        var asset = EffectTestAssets.zoom();
        assertEquals(22, asset.controlBytes());
        var frame =
                new ClientEffectFrame(
                        new EffectAnimation(
                                1, 2, .3, 80, EffectAnimation.Easing.LINEAR, 10, 20, true),
                        23999,
                        24001,
                        .5);
        var decoded = asset.decodeFrame(asset.encode(frame));
        assertEquals(23999, decoded.curveTick());
        assertEquals(1, decoded.rollTick());
        assertEquals(.5, decoded.correctionTicks(), .0003);
        assertEquals(
                15, EffectTestAssets.client("fade", EffectCurveAsset.Kind.FADE).controlBytes());
    }
}
