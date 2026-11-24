package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.Hud;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.*;
import java.util.*;

class TitleLeaseTest {
    @TempDir Path root;

    @Test
    void cinematicLeaseSuspendsNotificationsAndResumesThePriorityQueue() throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            UUID id = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(id);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(any(), any(Runnable.class), eq(1L), eq(1L)))
                    .thenReturn(mock(BukkitTask.class));
            AssetChannelApiImpl api = mock(AssetChannelApiImpl.class);
            when(api.isPackReady(id)).thenReturn(true);
            HudManager manager = new HudManager(api);
            Path file = root.resolve("notice.yml");
            Files.writeString(file, "surface: title\nduration: 0s\nlayout: {content: Notice}\n");
            manager.load(List.of(Hud.load("notice", file)), Map.of());
            manager.show(
                    player,
                    "notice",
                    new HudRequest(
                            Map.of(), Optional.of("first"), Optional.of(0), Optional.of(true)));
            var first = manager.session(player, "first").orElseThrow();
            var lease = manager.reserveTitle(player);
            assertEquals(HudSessionState.SUSPENDED, first.state());
            assertEquals(
                    HudDisplayResult.QUEUED,
                    manager.show(
                            player,
                            "notice",
                            new HudRequest(
                                    Map.of(),
                                    Optional.of("second"),
                                    Optional.of(100),
                                    Optional.of(true))));
            var second = manager.session(player, "second").orElseThrow();
            assertEquals(HudSessionState.QUEUED, second.state());
            lease.close();
            assertEquals(HudSessionState.ACTIVE, second.state());
            manager.hide(player, "second");
            assertEquals(HudSessionState.ACTIVE, first.state());
            manager.clear();
        }
    }
}
