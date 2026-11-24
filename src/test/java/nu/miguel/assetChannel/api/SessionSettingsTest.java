package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SessionSettingsTest {
    @Test
    void defaultsAreOneShotAndIndependent() {
        assertEquals(new SessionSettings(false, false, false, 1.0f), SessionSettings.defaults());
    }

    @Test
    void rejectsInvalidVolume() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionSettings(false, false, false, -0.01f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionSettings(false, false, false, Float.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionSettings(false, false, false, Float.POSITIVE_INFINITY));
    }

    @Test
    void acceptsZeroAndAmplifiedVolume() {
        assertDoesNotThrow(() -> new SessionSettings(true, true, true, 0));
        assertDoesNotThrow(() -> new SessionSettings(true, true, true, 2.5f));
    }

    @Test
    void fourArgumentConstructorLeavesInitialStageUnset() {
        SessionSettings legacy = new SessionSettings(true, false, false, 1);
        assertNull(legacy.initialStage());
        assertEquals(SoundOrigin.listenerRelative(), legacy.origin());
        assertEquals(
                "enraged", new SessionSettings(true, false, false, 1, "enraged").initialStage());
    }

    @Test
    void originIsRequired() {
        assertThrows(
                NullPointerException.class,
                () -> new SessionSettings(false, false, false, 1, null, null));
    }
}
