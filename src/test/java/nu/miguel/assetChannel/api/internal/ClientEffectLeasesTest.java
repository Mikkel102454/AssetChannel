package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.entity.PlayerPostEffects;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class ClientEffectLeasesTest {
    private ClientEffectAsset asset() {
        return new ClientEffectAsset(
                Key.key("test:control"),
                Key.key("test:writer"),
                Key.key("test:value"),
                Key.key("test:screen"),
                Key.key("test:zoom"),
                Key.key("test:copy"),
                1,
                4);
    }

    @Test
    void controlUpdatesAreAtomicOrderedAndPreserveUnrelatedEffects() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var player = mock(Player.class);
            var world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            when(world.getGameTime()).thenReturn(23999L);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var effects = mock(PlayerPostEffects.class);
            when(player.postEffects()).thenReturn(effects);
            var external = Key.key("other:effect");
            var values = new AtomicReference<List<Key>>(List.of(external));
            when(effects.values()).thenAnswer(i -> values.get());
            when(effects.set(any()))
                    .thenAnswer(i -> {
                        values.set(List.copyOf(i.getArgument(0)));
                        return true;
                    });
            var manager = new ClientEffectLeases();
            var lease = manager.acquire(player, asset());
            assertThrows(IllegalStateException.class, () -> manager.acquire(player, asset()));
            var animation = new EffectAnimation(1, 2, .25, 60, EffectAnimation.Easing.SMOOTH);
            lease.show(animation);
            assertEquals(external, values.get().getFirst());
            assertEquals(asset().renderer(), values.get().getLast());
            assertEquals(15, values.get().size());
            verify(effects, times(1)).set(any());
            var changed = new ArrayList<>(values.get());
            var later = Key.key("other:later");
            changed.add(later);
            values.set(changed);
            lease.show(animation);
            assertEquals(List.of(external, later), values.get().subList(0, 2));
            lease.close();
            assertEquals(List.of(external, later), values.get());
            assertTrue(lease.closed());
            assertThrows(IllegalStateException.class, () -> lease.show(animation));
            var old = manager.acquire(player, asset());
            manager.clear();
            var current = manager.acquire(player, asset());
            current.show(animation);
            var before = values.get();
            old.close();
            assertEquals(before, values.get());
            current.close();
        }
    }
}
