package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nu.miguel.assetChannel.api.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;

class AudioLeasesTest {
    @Test
    void concurrentLeasesUseDifferentKeysAndOnlyStopTheirOwnPlayback() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getLocation()).thenReturn(new Location(null, 0, 0, 0));
            var api = mock(AssetChannelApi.class);
            when(api.isPackReady(player)).thenReturn(true);
            when(api.packRevision()).thenReturn(UUID.randomUUID());
            var manager = new AudioLeases();
            var media = new TimelineAudioAsset("voice", 2, 1, 2);
            var a = manager.acquire(player, media, api);
            var b = manager.acquire(player, media, api);
            a.play(0, null, 1);
            b.play(0, null, 1);
            clearInvocations(player);
            a.close();
            verify(player).stopSound(media.key(0, 0, false), SoundCategory.VOICE);
            verify(player, never()).stopSound(media.key(0, 1, false), SoundCategory.VOICE);
            b.close();
            var virtual = manager.acquire(player, media, api);
            var emitted = new java.util.ArrayList<String>();
            virtual.emit(1, true, emitted::add);
            assertEquals(List.of(media.key(1, 0, true)), emitted);
            virtual.close();
            verify(player).stopSound(media.key(1, 0, true), SoundCategory.VOICE);
            var music = manager.acquire(player, media, api);
            music.play(0, null, .5f, 1f, SoundCategory.RECORDS);
            verify(player)
                    .playSound(
                            any(Location.class),
                            eq(media.key(0, 0, false)),
                            eq(SoundCategory.RECORDS),
                            eq(.5f),
                            eq(1f));
            music.close();
            verify(player).stopSound(media.key(0, 0, false), SoundCategory.RECORDS);
        }
    }
}
