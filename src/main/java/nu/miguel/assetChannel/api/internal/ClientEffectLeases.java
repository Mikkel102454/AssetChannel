package nu.miguel.assetChannel.api.internal;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.*;

import org.bukkit.entity.Player;

import java.util.*;

/** Owns the ordered control bundle, preserving other plugins' native post effects. */
final class ClientEffectLeases {
    private final Map<UUID, Lease> leases = new HashMap<>();

    ClientEffectLease acquire(Player player, ClientEffectAsset asset) {
        AssetChannelApiImpl.requireMainThread();
        if (leases.containsKey(player.getUniqueId()))
            throw new IllegalStateException("Player already has a client zoom lease");
        if (player.postEffects().values().stream().anyMatch(asset::owns))
            throw new IllegalStateException("Client effect control keys are already in use");
        var lease = new Lease(player, asset);
        leases.put(player.getUniqueId(), lease);
        return lease;
    }

    void clear() {
        for (var lease : List.copyOf(leases.values())) lease.close();
    }

    void quit(UUID player) {
        var lease = leases.get(player);
        if (lease != null) lease.close();
    }

    private final class Lease implements ClientEffectLease {
        final Player player;
        final ClientEffectAsset asset;
        List<Key> owned = List.of();
        boolean closed;

        Lease(Player player, ClientEffectAsset asset) {
            this.player = player;
            this.asset = asset;
        }

        public void show(EffectAnimation animation) {
            AssetChannelApiImpl.requireMainThread();
            if (closed) throw new IllegalStateException("Client effect lease closed");
            List<Key> next = asset.keys(asset.encode(animation, player.getWorld().getGameTime()));
            var all = new ArrayList<>(player.postEffects().values());
            all.removeAll(owned);
            all.addAll(next);
            if (!all.equals(player.postEffects().values())) player.postEffects().set(all);
            owned = next;
        }

        public boolean closed() {
            return closed;
        }

        public void close() {
            AssetChannelApiImpl.requireMainThread();
            if (closed) return;
            closed = true;
            if (!leases.remove(player.getUniqueId(), this)) return;
            var all = new ArrayList<>(player.postEffects().values());
            if (all.removeAll(owned)) player.postEffects().set(all);
            owned = List.of();
        }
    }
}
