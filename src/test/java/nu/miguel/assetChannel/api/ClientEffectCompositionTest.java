package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.key.Key;

import org.junit.jupiter.api.Test;

import java.util.List;

class ClientEffectCompositionTest {
    @Test
    void recoveryIncludesSharedResourcesAndOnlyFrozenNeutralCurvesCanBeCulled() {
        var k = Key.key("test:effect");
        var c =
                new ClientEffectComposition(
                        Key.key("test:shared"), k, k, EffectCurveAsset.Kind.FADE, 0);
        var asset = new ClientEffectAsset(k, k, k, k, k, 0, 1, c);
        assertEquals(List.of(k, c.prefix()), asset.ownedPrefixes());
        assertTrue(asset.isNeutral(frame(0, 0, 0, 40, 0)));
        assertTrue(asset.isNeutral(frame(1, 0, 1, 0, 0)));
        assertFalse(
                asset.isNeutral(frame(0, 1, 0, 40, 0)),
                "A fade starting at zero must keep running");
        assertFalse(
                asset.isNeutral(frame(0, 1, .5, 0, 0)), "Paused non-neutral values remain visible");
        assertFalse(asset.isNeutral(frame(0, 0, 0, 0, 1)), "Camera roll still needs a renderer");
    }

    private ClientEffectFrame frame(
            double from, double to, double progress, double duration, double roll) {
        return new ClientEffectFrame(
                new EffectAnimation(
                        from,
                        to,
                        progress,
                        duration,
                        EffectAnimation.Easing.LINEAR,
                        roll,
                        roll,
                        false),
                0,
                0,
                0);
    }
}
