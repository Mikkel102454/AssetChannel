package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class BroadcastPlaybackResultTest {
    @Test
    void defensivelyCopiesRecipientSets() {
        UUID player = UUID.randomUUID();
        Set<UUID> input = new HashSet<>(Set.of(player));
        BroadcastPlaybackResult result =
                new BroadcastPlaybackResult(PlaybackResult.PLAYED, input, Set.of());
        input.clear();

        assertEquals(Set.of(player), result.played());
        assertThrows(UnsupportedOperationException.class, () -> result.played().clear());
    }
}
