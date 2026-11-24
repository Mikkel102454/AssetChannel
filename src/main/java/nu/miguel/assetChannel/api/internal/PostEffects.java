package nu.miguel.assetChannel.api.internal;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.EffectHandle;

import org.bukkit.entity.Player;

import java.util.*;

final class PostEffects {
    private record Target(UUID player, Key key) {}

    private final Map<Target, Entry> entries = new HashMap<>();

    EffectHandle acquire(Player player, Key key) {
        AssetChannelApiImpl.requireMainThread();
        Target target = new Target(player.getUniqueId(), key);
        Entry entry =
                entries.computeIfAbsent(
                        target, t -> new Entry(player, key, player.postEffects().add(key)));
        entry.references++;
        return new EffectHandle() {
            boolean closed;

            public Key key() {
                return key;
            }

            public void close() {
                AssetChannelApiImpl.requireMainThread();
                if (closed) return;
                closed = true;
                if (entries.get(target) != entry) return;
                if (--entry.references == 0) {
                    entries.remove(target, entry);
                    if (entry.added) player.postEffects().remove(key);
                }
            }
        };
    }

    void clear() {
        for (Entry entry : entries.values())
            if (entry.added) entry.player.postEffects().remove(entry.key);
        entries.clear();
    }

    void quit(UUID player) {
        for (var item : List.copyOf(entries.entrySet()))
            if (item.getKey().player.equals(player)) {
                if (item.getValue().added)
                    item.getValue().player.postEffects().remove(item.getValue().key);
                entries.remove(item.getKey());
            }
    }

    private static final class Entry {
        final Player player;
        final Key key;
        final boolean added;
        int references;

        Entry(Player player, Key key, boolean added) {
            this.player = player;
            this.key = key;
            this.added = added;
        }
    }
}
