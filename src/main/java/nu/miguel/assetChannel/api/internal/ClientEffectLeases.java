package nu.miguel.assetChannel.api.internal;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.*;

import org.bukkit.entity.Player;

import java.util.*;

/** Owns each player's complete control bundle, including shared compositor resources. */
final class ClientEffectLeases {
    private final Map<UUID, Group> groups = new HashMap<>();

    ClientEffectLease acquire(Player player, ClientEffectAsset asset) {
        AssetChannelApiImpl.requireMainThread();
        var existing = groups.get(player.getUniqueId());
        var group = existing == null ? new Group(player) : existing;
        if (group.leases.stream()
                .filter(l -> !l.closed)
                .anyMatch(l ->
                        asset.isScalar()
                                ? l.asset.prefix().equals(asset.prefix())
                                : !l.asset.isScalar()))
            throw new IllegalStateException(
                    "Player already has a lease for this client effect channel");
        if (player.postEffects().values().stream()
                .filter(k -> !group.owned.contains(k) && !group.uncertain.contains(k))
                .anyMatch(k -> asset.owns(k)))
            throw new IllegalStateException("Client effect control keys are already in use");
        var lease = new Lease(group, asset);
        group.leases.add(lease);
        groups.putIfAbsent(player.getUniqueId(), group);
        return lease;
    }

    void clear() {
        for (var group : List.copyOf(groups.values()))
            for (var lease : List.copyOf(group.leases)) lease.close();
    }

    void quit(UUID player) {
        var group = groups.get(player);
        if (group != null) for (var lease : List.copyOf(group.leases)) lease.close();
    }

    private final class Group {
        final Player player;
        final List<Lease> leases = new ArrayList<>();
        List<Key> owned = List.of();
        final Set<Key> uncertain = new HashSet<>();

        Group(Player player) {
            this.player = player;
        }

        List<Key> bundle() {
            var active =
                    leases.stream()
                            .filter(l ->
                                    !l.closed && l.frame != null && !l.asset.isNeutral(l.frame))
                            .sorted(
                                    Comparator.comparingInt((Lease l) -> l.asset.isScalar() ? 1 : 0)
                                            .thenComparing(l -> l.asset.prefix().asString()))
                            .toList();
            if (active.isEmpty()) return List.of();
            var composition = active.getFirst().asset.composition();
            var channels = EnumSet.noneOf(EffectCurveAsset.Kind.class);
            boolean compatible = true;
            for (var lease : active) {
                var next = lease.asset.composition();
                if (!next.prefix().equals(composition.prefix())
                        || !next.vertexShader().equals(composition.vertexShader())
                        || !next.fragmentShader().equals(composition.fragmentShader())
                        || !lease.asset.copyShader().equals(active.getFirst().asset.copyShader())
                        || !lease.asset.copyVertex().equals(active.getFirst().asset.copyVertex())
                        || !channels.add(next.channel())) compatible = false;
            }
            var keys = new ArrayList<Key>();
            if (compatible) {
                int mask = 0;
                for (var lease : active) {
                    keys.addAll(lease.controls);
                    mask |= lease.asset.composition().mask();
                }
                keys.add(composition.renderer(mask));
            } else
                for (var lease : active) {
                    keys.addAll(lease.controls);
                    keys.add(lease.asset.renderer());
                }
            return List.copyOf(keys);
        }

        void publish() {
            var next = bundle();
            var all = new ArrayList<>(player.postEffects().values());
            all.removeAll(owned);
            all.removeAll(uncertain);
            all.addAll(next);
            try {
                if (!all.equals(player.postEffects().values())) player.postEffects().set(all);
                owned = next;
                uncertain.clear();
                leases.removeIf(l -> l.closed);
                if (leases.isEmpty()) groups.remove(player.getUniqueId(), this);
            } catch (RuntimeException error) {
                // A transport may apply its update before throwing. Retain both bundles for
                // cleanup.
                uncertain.addAll(next);
                throw error;
            }
        }
    }

    private final class Lease implements ClientEffectLease {
        final Group group;
        final ClientEffectAsset asset;
        ClientEffectFrame frame;
        List<Key> controls = List.of();
        boolean closed;

        Lease(Group group, ClientEffectAsset asset) {
            this.group = group;
            this.asset = asset;
        }

        public void show(EffectAnimation animation) {
            AssetChannelApiImpl.requireMainThread();
            long tick = group.player.getWorld().getGameTime();
            show(new ClientEffectFrame(animation, tick, tick, 0));
        }

        public void show(ClientEffectFrame next) {
            AssetChannelApiImpl.requireMainThread();
            if (closed) throw new IllegalStateException("Client effect lease closed");
            byte[] bytes = asset.encode(next);
            var previous = frame;
            var previousControls = controls;
            frame = next;
            controls = asset.controlKeys(bytes);
            try {
                group.publish();
            } catch (RuntimeException error) {
                frame = previous;
                controls = previousControls;
                throw error;
            }
        }

        public boolean closed() {
            return closed;
        }

        public void close() {
            AssetChannelApiImpl.requireMainThread();
            if (closed && !group.leases.contains(this)) return;
            closed = true;
            group.publish();
            frame = null;
            controls = List.of();
        }
    }
}
