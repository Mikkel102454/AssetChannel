package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nu.miguel.assetChannel.api.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;

class NativeSoundTest {
    @Test
    void nativeSoundKeysRespectPackReadinessAndKeepListenerRelativeOrigin() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var player = mock(Player.class);
            var world = mock(World.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(world.getEntitiesByClass(org.bukkit.entity.Display.class)).thenReturn(List.of());
            var api = new AssetChannelApiImpl();
            assertEquals(
                    PlaybackResult.PACK_NOT_READY,
                    api.playResourceSound(
                            player,
                            "demo:crack",
                            SoundOrigin.listenerRelative(),
                            SoundCategory.BLOCKS,
                            1,
                            1));
            api.setPackReady(player, true);
            assertEquals(
                    PlaybackResult.PLAYED,
                    api.playResourceSound(
                            player,
                            "demo:crack",
                            SoundOrigin.listenerRelative(),
                            SoundCategory.BLOCKS,
                            1,
                            1));
            verify(player).playSound(player, "demo:crack", SoundCategory.BLOCKS, 1, 1);
        }
    }
}
