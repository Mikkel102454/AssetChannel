package nu.miguel.assetChannel.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageTransitionSettingsTest {
    @Test void factoriesSelectTheirDocumentedTiming() {
        assertEquals(StageTransitionTiming.IMMEDIATE, StageTransitionSettings.immediate().timing());
        assertEquals(StageTransitionTiming.NEXT_SESSION_BOUNDARY, StageTransitionSettings.nextSessionBoundary().timing());
        assertEquals(StageTransitionTiming.NEXT_PLAYER_BOUNDARY, StageTransitionSettings.nextPlayerBoundary().timing());
    }
}
