package nu.miguel.assetChannel.api;
import net.kyori.adventure.key.Key;
import java.time.Duration;
import java.util.Objects;

public record SoundAsset(String id, Key key, Duration duration) {
    public SoundAsset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative())
            throw new IllegalArgumentException("duration must be positive");
    }
}
