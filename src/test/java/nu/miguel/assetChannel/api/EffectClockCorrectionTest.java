package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EffectClockCorrectionTest {
    @Test
    void slightlyFutureAnchorsNeverJumpToTheEndpoint() {
        var a = new EffectAnimation(0, 1, .25, 80, EffectAnimation.Easing.LINEAR);
        assertEquals(.24875, a.value(100, 99.9), .00001);
        assertEquals(.25125, a.value(23999.9, 0), .00001);
        assertEquals(.24875, a.value(0, 23999.9), .00001);
        var roll = new EffectAnimation(1, 1, 0, 0, EffectAnimation.Easing.LINEAR, 10, 20, true);
        assertEquals(10, roll.rollAt(100, 99.9), .00001);
    }
}
