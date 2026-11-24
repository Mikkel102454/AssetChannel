package nu.miguel.assetChannel.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record BroadcastPlaybackResult(
        PlaybackResult result, Set<UUID> played, Set<UUID> packNotReady) {
    public BroadcastPlaybackResult {
        Objects.requireNonNull(result, "result");
        played = Set.copyOf(Objects.requireNonNull(played, "played"));
        packNotReady = Set.copyOf(Objects.requireNonNull(packNotReady, "packNotReady"));
    }
}
