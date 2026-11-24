package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DisplayOptionsTest {
    @Test
    void rejectsInvalidScaleAndClientTeleportRange() {
        assertThrows(
                IllegalArgumentException.class, () -> new DisplayOptions(Float.NaN, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new DisplayOptions(1, 0, 60, true));
        assertEquals(1, DisplayOptions.defaults().scale());
    }
}
