package nu.miguel.assetChannel.api.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SegmentHandoffTest {
    @Test void advancesPlaybackByConfiguredLead() {
        assertEquals(1_075, SegmentHandoff.playbackElapsed(1_000, 75, false));
    }

    @Test void doesNotAdvanceAcrossPendingTransition() {
        assertEquals(1_000, SegmentHandoff.playbackElapsed(1_000, 75, true));
    }

    @Test void zeroLeadDisablesAnticipation() {
        assertEquals(1_000, SegmentHandoff.playbackElapsed(1_000, 0, false));
    }

    @Test void clampsLeadBelowHalfTheShortestSegment() {
        assertEquals(499, SegmentHandoff.clampLead(700, 1_000));
        assertEquals(0, SegmentHandoff.clampLead(-1, 1_000));
    }
}
