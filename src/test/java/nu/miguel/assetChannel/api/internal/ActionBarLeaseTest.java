package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;

import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.Hud;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.*;
import java.util.*;

class ActionBarLeaseTest {
    @TempDir Path root;

    @Test
    void captionsSuspendActionBarNotificationsAndNeverClearAResumedHud() throws Exception {
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
            var api = mock(AssetChannelApiImpl.class);
            when(api.isPackReady(id)).thenReturn(true);
            var manager = new HudManager(api);
            Path file = root.resolve("notice.yml");
            Files.writeString(
                    file, "surface: action_bar\nduration: 0s\nlayout: {content: Notice}\n");
            manager.load(List.of(Hud.load("notice", file)), Map.of());
            manager.show(
                    player,
                    "notice",
                    new HudRequest(
                            Map.of(), Optional.of("notice"), Optional.of(0), Optional.of(true)));
            var notice = manager.session(player, "notice").orElseThrow();
            var lease = manager.reserveActionBar(player);
            assertEquals(HudSessionState.SUSPENDED, notice.state());
            lease.show(Component.text("Caption"));
            lease.close();
            assertEquals(HudSessionState.ACTIVE, notice.state());
            clearInvocations(player);
            lease.close();
            verifyNoInteractions(player);
            var stale = manager.reserveActionBar(player);
            manager.clear();
            assertThrows(IllegalStateException.class, () -> stale.show(Component.text("stale")));
        }
    }
}
