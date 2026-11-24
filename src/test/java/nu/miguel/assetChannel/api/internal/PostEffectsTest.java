package nu.miguel.assetChannel.api.internal;

import static org.mockito.Mockito.*;

import io.papermc.paper.entity.PlayerPostEffects;

import net.kyori.adventure.key.Key;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;

class PostEffectsTest {
    @Test
    void closingAnInvalidatedHandleDoesNotRemoveANewerLease() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var effects = mock(PlayerPostEffects.class);
            when(player.postEffects()).thenReturn(effects);
            Key key = Key.key("demo:effect");
            when(effects.add(key)).thenReturn(true);
            var manager = new PostEffects();
            var old = manager.acquire(player, key);
            manager.clear();
            var current = manager.acquire(player, key);
            clearInvocations(effects);
            old.close();
            verify(effects, never()).remove(key);
            current.close();
        }
    }

    @Test
    void leasesReferenceCountAndPreservePreexistingEffects() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            PlayerPostEffects effects = mock(PlayerPostEffects.class);
            when(player.postEffects()).thenReturn(effects);
            Key key = Key.key("demo:grayscale");
            when(effects.add(key)).thenReturn(true);
            var manager = new PostEffects();
            var first = manager.acquire(player, key);
            var second = manager.acquire(player, key);
            first.close();
            verify(effects, never()).remove(key);
            second.close();
            verify(effects).remove(key);
            clearInvocations(effects);
            when(effects.add(key)).thenReturn(false);
            manager.acquire(player, key).close();
            verify(effects, never()).remove(key);
        }
    }
}
