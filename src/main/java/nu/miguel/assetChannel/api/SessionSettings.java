package nu.miguel.assetChannel.api;

import java.util.Objects;

public record SessionSettings(boolean loop, boolean synchronizedPlayback,
                              boolean retainOnDisconnect, float volume, String initialStage,
                              SoundOrigin origin) {
    public SessionSettings {
        if (!Float.isFinite(volume) || volume < 0) throw new IllegalArgumentException("volume must be finite and non-negative");
        Objects.requireNonNull(origin, "origin");
    }
    public SessionSettings(boolean loop, boolean synchronizedPlayback, boolean retainOnDisconnect,
                           float volume, String initialStage) {
        this(loop, synchronizedPlayback, retainOnDisconnect, volume, initialStage, SoundOrigin.listenerRelative());
    }
    public SessionSettings(boolean loop, boolean synchronizedPlayback, boolean retainOnDisconnect, float volume) {
        this(loop, synchronizedPlayback, retainOnDisconnect, volume, null, SoundOrigin.listenerRelative());
    }
    public static SessionSettings defaults() { return new SessionSettings(false, false, false, 1.0f); }
}
