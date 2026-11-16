package nu.miguel.assetChannel.api.internal;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.asset.BlockDefinition;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockOverlayManagerTest {
    @TempDir Path root;
    MockedStatic<Bukkit> bukkit;
    Plugin previousPlugin;
    AssetChannelApiImpl api;
    Player player;
    World world;
    Block block;
    BlockData real, warning;
    AtomicReference<BlockData> current;
    Location location;
    BlockOverlayManager manager;
    UUID playerId = UUID.randomUUID();

    @BeforeEach void setup() throws Exception {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        previousPlugin = Main.plugin; Main.plugin = mock(Plugin.class);
        api = mock(AssetChannelApiImpl.class); player = mock(Player.class); world = mock(World.class); block = mock(Block.class);
        real = mock(BlockData.class); warning = mock(BlockData.class); current = new AtomicReference<>(real);
        location = new Location(world, 1, 64, 1);
        when(player.getUniqueId()).thenReturn(playerId); when(player.getWorld()).thenReturn(world); when(player.isOnline()).thenReturn(true);
        when(player.isChunkSent(anyLong())).thenReturn(true); when(api.isPackReady(playerId)).thenReturn(true);
        when(world.getUID()).thenReturn(UUID.randomUUID()); when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(1, 64, 1)).thenReturn(block); when(block.getBlockData()).thenAnswer(i -> current.get());
        var shape = mock(VoxelShape.class); when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, 1, 1)));
        when(block.getCollisionShape()).thenReturn(shape); when(block.getState()).thenReturn(mock(BlockState.class));
        Path yaml = root.resolve("warning.yml");
        Files.writeString(yaml, "model: demo:cube\nblock-state: minecraft:note_block[instrument=pling,note=24,powered=false]\nblock-model: demo:cube\n");
        when(api.block("warning_block")).thenReturn(Optional.of(BlockDefinition.load("warning_block", yaml).descriptor()));
        bukkit.when(() -> Bukkit.createBlockData("minecraft:note_block[instrument=pling,note=24,powered=false]")).thenReturn(warning);
        manager = new BlockOverlayManager(api);
    }

    @AfterEach void restore() { bukkit.close(); Main.plugin = previousPlugin; }

    @Test void showAndBlinkSendOneBlockUpdateWithoutChangingTheWorldOrSpawningEntities() {
        var overlay = manager.show(player, location, "warning_block");
        verify(player).sendBlockChange(location, warning);
        overlay.setVisible(false);
        verify(player).sendBlockChange(location, real);
        overlay.setVisible(true);
        verify(player, times(2)).sendBlockChange(location, warning);
        assertTrue(overlay.visible()); assertFalse(overlay.isClosed());
        verify(block, never()).setBlockData(any(), anyBoolean());
        verify(world, never()).spawn(any(Location.class), any(Class.class), any(java.util.function.Consumer.class));
    }

    @Test void closeRestoresCurrentWorldDataAndBlockEntityStateRatherThanAnOldSnapshot() {
        var overlay = manager.show(player, location, "warning_block");
        BlockData replacement = mock(BlockData.class); current.set(replacement);
        TileState tile = mock(TileState.class); when(block.getState()).thenReturn(tile);
        overlay.close(); overlay.close();
        verify(player, times(1)).sendBlockChange(location, replacement);
        verify(player).sendBlockUpdate(location, tile);
        assertTrue(overlay.isClosed());
    }

    @Test void replacementDoesNotSendAnIntermediateRealBlockAndOldHandleCannotClearIt() {
        var first = manager.show(player, location, "warning_block");
        var second = manager.show(player, location, "warning_block");
        first.close();
        assertTrue(first.isClosed()); assertFalse(second.isClosed());
        verify(player, times(2)).sendBlockChange(location, warning);
        verify(player, never()).sendBlockChange(location, real);
        second.close();
        verify(player).sendBlockChange(location, real);
    }

    @Test void rejectsPackNotReadyUnsentChunksWrongWorldAndNonFullCubeTargets() {
        when(api.isPackReady(playerId)).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> manager.show(player, location, "warning_block"));
        when(api.isPackReady(playerId)).thenReturn(true); when(player.isChunkSent(anyLong())).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> manager.show(player, location, "warning_block"));
        when(player.isChunkSent(anyLong())).thenReturn(true); when(player.getWorld()).thenReturn(mock(World.class));
        assertThrows(IllegalArgumentException.class, () -> manager.show(player, location, "warning_block"));
        when(player.getWorld()).thenReturn(world);
        when(block.getCollisionShape().getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, 0.5, 1)));
        assertThrows(IllegalArgumentException.class, () -> manager.show(player, location, "warning_block"));
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test void packLossClearsAllOverlaysAndWorldChangeCannotSendOldCoordinatesIntoNewWorld() {
        var first = manager.show(player, location, "warning_block");
        manager.clear(player, true);
        assertTrue(first.isClosed()); verify(player).sendBlockChange(location, real);
        var second = manager.show(player, location, "warning_block");
        when(player.getWorld()).thenReturn(mock(World.class));
        second.close();
        verify(player, times(1)).sendBlockChange(location, real);
    }

    @Test void locationIsDefensiveAndMainThreadRequired() {
        var overlay = manager.show(player, location, "warning_block");
        location.setX(99); overlay.location().setX(42);
        assertEquals(1, overlay.location().getBlockX());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> overlay.setVisible(false));
        assertThrows(IllegalStateException.class, overlay::close);
    }
    @Test void publicApiOwnsSessionsAndClearsThemOnPackLossAndReload() throws Exception {
        var service = new AssetChannelApiImpl();
        var definition = BlockDefinition.load("warning_block", root.resolve("warning.yml"));
        service.loadAssets(List.of(definition));
        service.setPackReady(player, true);
        var overlay = service.showBlockOverlay(player, location, "warning_block");
        assertSame(overlay, service.blockOverlay(overlay.id()).orElseThrow());
        assertEquals(List.of(overlay), List.copyOf(service.blockOverlays(player)));
        service.setPackReady(player, false);
        assertTrue(overlay.isClosed()); assertTrue(service.blockOverlays(player).isEmpty());
        service.setPackReady(player, true);
        var reloaded = service.showBlockOverlay(player, location, "warning_block");
        service.stopSessions();
        assertTrue(reloaded.isClosed()); assertTrue(service.blockOverlay(reloaded.id()).isEmpty());
    }
    @Test void blinkPreviewsShareOneTaskAndRestoreWhenTheyExpire() {
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        var task = mock(org.bukkit.scheduler.BukkitTask.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(Main.plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
        var first = manager.previewBlink(player, location, "warning_block", 2, 6);
        Player secondPlayer = mock(Player.class); UUID otherId = UUID.randomUUID();
        when(secondPlayer.getUniqueId()).thenReturn(otherId); when(secondPlayer.getWorld()).thenReturn(world);
        when(secondPlayer.isOnline()).thenReturn(true); when(secondPlayer.isChunkSent(anyLong())).thenReturn(true);
        when(api.isPackReady(otherId)).thenReturn(true);
        var second = manager.previewBlink(secondPlayer, location, "warning_block", 2, 6);
        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(1)).runTaskTimer(eq(Main.plugin), callback.capture(), eq(1L), eq(1L));
        for (int tick = 0; tick < 6; tick++) callback.getValue().run();
        verify(player, times(2)).sendBlockChange(location, warning);
        verify(player, times(2)).sendBlockChange(location, real);
        assertTrue(first.isClosed()); assertTrue(second.isClosed()); verify(task).cancel();
    }
    @Test void playerChunkUnloadClosesSessionsWithoutSendingOldBlockPackets() {
        var overlay = manager.show(player, location, "warning_block");
        var chunk = mock(Chunk.class); when(chunk.getWorld()).thenReturn(world);
        manager.chunkUnloaded(new io.papermc.paper.event.packet.PlayerChunkUnloadEvent(chunk, player));
        assertTrue(overlay.isClosed());
        verify(player, never()).sendBlockChange(location, real);
    }
    @Test void sameWorldRespawnRestoresAStillLoadedClientChunk() {
        var overlay = manager.show(player, location, "warning_block");
        var event = mock(org.bukkit.event.player.PlayerRespawnEvent.class); when(event.getPlayer()).thenReturn(player);
        manager.respawned(event);
        assertTrue(overlay.isClosed()); verify(player).sendBlockChange(location, real);
    }
    @Test void displayOnlyAssetsExplainTheSimpleRenderingChoiceBeforeSendingAnyPacket() {
        when(api.block("display_only")).thenReturn(Optional.of(new nu.miguel.assetChannel.api.BlockAsset(
                "display_only", "demo:cube", "minecraft:barrier", "minecraft:stone", null, null, null, 20, true)));
        var error = assertThrows(IllegalArgumentException.class, () -> manager.show(player, location, "display_only"));
        assertTrue(error.getMessage().contains("render: block"));
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }
}
