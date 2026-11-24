package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TimelineAudioTest {
    @Test
    void resumesAtNextBoundaryAndBoundsLeaseKeys() {
        var audio = new TimelineAudioAsset("dialogue.guide", 2.4, 1, 3);
        assertEquals(1, audio.nextSegment(.2));
        assertEquals(1, audio.nextSegment(1));
        assertEquals(3, audio.nextSegment(2.4));
        assertNotEquals(audio.key(0, 0, false), audio.key(0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> audio.key(0, 16, false));
    }
}
