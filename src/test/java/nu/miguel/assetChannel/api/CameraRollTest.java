package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.key.Key;

import org.junit.jupiter.api.Test;

class CameraRollTest {
    @Test
    void versionTwoCarriesRollWhileVersionOneRemainsCompatible() {
        var legacy =
                new ClientEffectAsset(
                        Key.key("test:p"),
                        Key.key("test:w"),
                        Key.key("test:f"),
                        Key.key("test:v"),
                        Key.key("test:r"),
                        Key.key("test:b"),
                        1,
                        4);
        var current =
                new ClientEffectAsset(
                        legacy.prefix(),
                        legacy.writerVertex(),
                        legacy.writerFragment(),
                        legacy.vertexShader(),
                        legacy.fragmentShader(),
                        legacy.copyShader(),
                        1,
                        4,
                        2);
        var animation =
                new EffectAnimation(1, 2, .25, 60, EffectAnimation.Easing.SMOOTH, 170, -170, true);
        assertEquals(18, current.encode(animation, 10).length);
        var decoded = current.decode(current.encode(animation, 10)).animation();
        assertEquals(170, decoded.roll(), .006);
        assertEquals(-170, decoded.nextRoll(), .006);
        assertEquals(180, decoded.rollAt(10, 10.5), .01);
        assertThrows(IllegalArgumentException.class, () -> legacy.encode(animation, 10));
        assertEquals(
                13,
                legacy.encode(new EffectAnimation(1, 2, 0, 60, EffectAnimation.Easing.LINEAR), 10)
                        .length);
    }
}
