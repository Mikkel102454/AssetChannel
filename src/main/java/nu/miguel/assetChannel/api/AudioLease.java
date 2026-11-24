package nu.miguel.assetChannel.api;

import org.bukkit.Location;

/** Main-thread, player-specific audio lane. Closing stops only this lease's keys. */
public interface AudioLease extends AutoCloseable {
    TimelineAudioAsset asset();

    void play(int segment, Location spatialOrigin, float volume);

    void play(
            int segment,
            Location spatialOrigin,
            float volume,
            float pitch,
            org.bukkit.SoundCategory category);

    /** Emits a leased key through a caller's transport, e.g. an entity-bound sound packet. */
    void emit(int segment, boolean spatial, java.util.function.Consumer<String> emitter);

    void emit(
            int segment,
            boolean spatial,
            org.bukkit.SoundCategory category,
            java.util.function.Consumer<String> emitter);

    void silence();

    @Override
    void close();
}
