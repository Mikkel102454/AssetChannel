package nu.miguel.assetChannel.api.internal;

import nu.miguel.assetChannel.api.*;

import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;

final class AudioLeases {
    private final Set<Lease> leases = new HashSet<>();

    AudioLease acquire(Player player, TimelineAudioAsset asset, AssetChannelApi api) {
        AssetChannelApiImpl.requireMainThread();
        if (!api.isPackReady(player)) throw new IllegalStateException("Pack not ready");
        Set<Integer> busy = new HashSet<>();
        for (Lease lease : leases)
            if (lease.player.getUniqueId().equals(player.getUniqueId())
                    && lease.asset.id().equals(asset.id())) busy.add(lease.lane);
        int lane = 0;
        while (busy.contains(lane)) lane++;
        if (lane >= 16) throw new IllegalStateException("All audio lanes are in use");
        Lease lease = new Lease(player, asset, lane, api);
        leases.add(lease);
        return lease;
    }

    void clear() {
        for (Lease lease : List.copyOf(leases)) lease.close();
    }

    void quit(UUID id) {
        for (Lease lease : List.copyOf(leases))
            if (lease.player.getUniqueId().equals(id)) lease.close();
    }

    private final class Lease implements AudioLease {
        private record Playing(String key, SoundCategory category) {}

        final Player player;
        final TimelineAudioAsset asset;
        final int lane;
        final AssetChannelApi api;
        final UUID revision;
        final Set<Playing> playing = new HashSet<>();
        boolean closed;

        Lease(Player player, TimelineAudioAsset asset, int lane, AssetChannelApi api) {
            this.player = player;
            this.asset = asset;
            this.lane = lane;
            this.api = api;
            revision = api.packRevision();
        }

        public TimelineAudioAsset asset() {
            return asset;
        }

        public void play(int segment, Location origin, float volume) {
            play(segment, origin, volume, 1, SoundCategory.VOICE);
        }

        public void play(
                int segment, Location origin, float volume, float pitch, SoundCategory category) {
            AssetChannelApiImpl.requireMainThread();
            if (!Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0)
                throw new IllegalArgumentException("Invalid volume/pitch");
            emit(
                    segment,
                    origin != null,
                    category,
                    key -> {
                        if (origin == null)
                            player.playSound(player.getLocation(), key, category, volume, pitch);
                        else if (player.getWorld().equals(origin.getWorld()))
                            player.playSound(origin, key, category, volume, pitch);
                    });
        }

        public void silence() {
            AssetChannelApiImpl.requireMainThread();
            for (Playing sound : playing) player.stopSound(sound.key(), sound.category());
            playing.clear();
        }

        public void emit(
                int segment, boolean spatial, java.util.function.Consumer<String> emitter) {
            emit(segment, spatial, SoundCategory.VOICE, emitter);
        }

        public void emit(
                int segment,
                boolean spatial,
                SoundCategory category,
                java.util.function.Consumer<String> emitter) {
            AssetChannelApiImpl.requireMainThread();
            if (closed || !api.packRevision().equals(revision))
                throw new IllegalStateException("Expired audio lease");
            Objects.requireNonNull(emitter);
            Objects.requireNonNull(category);
            if (!api.isPackReady(player)) return;
            String key = asset.key(segment, lane, spatial);
            silence();
            emitter.accept(key);
            playing.add(new Playing(key, category));
        }

        public void close() {
            AssetChannelApiImpl.requireMainThread();
            if (closed) return;
            silence();
            closed = true;
            leases.remove(this);
        }
    }
}
