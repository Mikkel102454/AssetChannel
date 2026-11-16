package nu.miguel.assetChannel.api.internal;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.api.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldAssetManagerTest {
    MockedStatic<Bukkit> bukkit;
    AssetChannelApiImpl api; World world; Chunk chunk; Block block; ItemDisplay display;
    BlockData air,carrier; WorldAssetManager manager; Plugin oldPlugin;
    AtomicReference<String> stored; AtomicReference<BlockData> current;
    Location location;
    @BeforeEach void setup() {
        bukkit=mockStatic(Bukkit.class); bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        oldPlugin=Main.plugin; Main.plugin=mock(Plugin.class);
        api=mock(AssetChannelApiImpl.class); world=mock(World.class); chunk=mock(Chunk.class); block=mock(Block.class);
        display=mock(ItemDisplay.class); air=mock(BlockData.class); carrier=mock(BlockData.class);
        location=new Location(world,1,64,1); current=new AtomicReference<>(air); stored=new AtomicReference<>();
        when(air.getAsString()).thenReturn("minecraft:air"); when(carrier.getAsString()).thenReturn("minecraft:barrier");
        when(world.getUID()).thenReturn(UUID.randomUUID()); when(world.getPlayers()).thenReturn(List.of());
        when(chunk.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(0,0)).thenReturn(true); when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        when(carrier.getMaterial()).thenReturn(Material.BARRIER);
        when(world.getBlockAt(1,64,1)).thenReturn(block); when(world.getChunkAt(0,0)).thenReturn(chunk);
        when(block.getWorld()).thenReturn(world); when(block.getLocation()).thenAnswer(i->location.clone()); when(block.getChunk()).thenReturn(chunk);
        when(block.getX()).thenReturn(1); when(block.getY()).thenReturn(64); when(block.getZ()).thenReturn(1);
        when(block.getType()).thenReturn(Material.AIR); when(block.getBlockData()).thenAnswer(i->current.get());
        doAnswer(i->{current.set(i.getArgument(0));return null;}).when(block).setBlockData(any(BlockData.class),eq(false));
        var pdc=mock(PersistentDataContainer.class); when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenAnswer(i->stored.get());
        doAnswer(i->{stored.set(i.getArgument(2));return null;}).when(pdc).set(any(),eq(PersistentDataType.STRING),anyString());
        doAnswer(i->{stored.set(null);return null;}).when(pdc).remove(any(NamespacedKey.class));
        when(display.getUniqueId()).thenReturn(UUID.randomUUID()); when(display.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(display.getWorld()).thenReturn(world);
        when(display.getPersistentDataContainer().has(WorldAssetManager.KIND,PersistentDataType.STRING)).thenReturn(true);
        var model=mock(ModelAsset.class); when(model.createItem(Material.PAPER)).thenReturn(mock(ItemStack.class));
        when(api.model("demo:cube")).thenReturn(Optional.of(model));
        when(api.block("demo.cube")).thenReturn(Optional.of(new BlockAsset("demo.cube","demo:cube","minecraft:barrier","minecraft:stone",null,"minecraft:block.stone.break","minecraft:block.stone.place",20,true)));
        bukkit.when(()->Bukkit.createBlockData("minecraft:barrier")).thenReturn(carrier);
        bukkit.when(()->Bukkit.createBlockData("minecraft:air")).thenReturn(air);
        bukkit.when(()->Bukkit.createBlockData("minecraft:stone")).thenReturn(carrier);
        bukkit.when(()->Bukkit.getEntity(display.getUniqueId())).thenReturn(display);
        var scheduler=mock(org.bukkit.scheduler.BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(Main.plugin),any(Runnable.class),eq(1L),eq(1L))).thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
        when(world.spawn(any(Location.class),eq(ItemDisplay.class),any(Consumer.class))).thenAnswer(i->{((Consumer<ItemDisplay>)i.getArgument(2)).accept(display);return display;});
        manager=new WorldAssetManager(api);
    }
    @AfterEach void teardown(){ bukkit.close();Main.plugin=oldPlugin; }
    @Test void nativePlacementUsesOneRealBlockWithoutABarrierOrDisplay(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var yaml = root.resolve("native.yml");
        java.nio.file.Files.writeString(yaml, "model: demo:cube\nblock-state: minecraft:note_block[instrument=pling,note=24,powered=false]\nblock-model: demo:cube\n");
        when(api.block("warning_block")).thenReturn(Optional.of(nu.miguel.assetChannel.asset.BlockDefinition.load("warning_block", yaml).descriptor()));
        BlockData note = mock(BlockData.class);
        when(note.getAsString()).thenReturn("minecraft:note_block[instrument=pling,note=24,powered=false]"); when(note.getMaterial()).thenReturn(Material.NOTE_BLOCK);
        bukkit.when(() -> Bukkit.createBlockData("minecraft:note_block[instrument=pling,note=24,powered=false]")).thenReturn(note);
        var placed = manager.placeBlock(location, "warning_block");
        verify(block).setBlockData(note, false); verify(block, never()).setBlockData(carrier, false);
        assertNull(placed.displayId());
        verify(world, never()).spawn(any(Location.class), eq(ItemDisplay.class), any(Consumer.class));
        when(chunk.isLoaded()).thenReturn(true); when(chunk.isEntitiesLoaded()).thenReturn(true); when(chunk.getWorld()).thenReturn(world);
        when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[0]);
        manager.reconcile(chunk);
        assertTrue(new WorldAssetManager(api).placedBlock(location).isPresent());
        verify(world, never()).spawn(any(Location.class), eq(ItemDisplay.class), any(Consumer.class));
        var sound = mock(org.bukkit.event.block.NotePlayEvent.class); when(sound.getBlock()).thenReturn(block);
        manager.notePlayed(sound); verify(sound).setCancelled(true);
        var power = mock(org.bukkit.event.block.BlockRedstoneEvent.class); when(power.getBlock()).thenReturn(block); when(power.getOldCurrent()).thenReturn(0);
        manager.redstone(power); verify(power).setNewCurrent(0);
        assertTrue(manager.removeBlock(location, false)); assertSame(air, current.get());
    }
    @Test void doesNotReplaceAnOccupiedBlock() {
        when(block.getType()).thenReturn(Material.STONE);
        assertThrows(IllegalStateException.class,()->manager.placeBlock(location,"demo.cube"));
        verify(block,never()).setBlockData(any(),anyBoolean());
    }
    private BlockData placeNativeBlock() {
        String state = "minecraft:note_block[instrument=pling,note=24,powered=false]";
        var nativeData = mock(org.bukkit.block.data.type.NoteBlock.class);
        when(nativeData.getAsString()).thenReturn(state);
        when(nativeData.getMaterial()).thenReturn(Material.NOTE_BLOCK);
        when(nativeData.getNote()).thenReturn(new Note(24));
        bukkit.when(() -> Bukkit.createBlockData(state)).thenReturn(nativeData);
        when(api.block("warning_block")).thenReturn(Optional.of(new BlockAsset("warning_block", "demo:cube",
                "minecraft:barrier", "minecraft:stone", null, null, null, 0, true, state, "demo:cube")));
        manager.placeBlock(location, "warning_block");
        when(block.getType()).thenReturn(Material.NOTE_BLOCK);
        return nativeData;
    }
    private void loadedChunk() {
        when(chunk.isLoaded()).thenReturn(true); when(chunk.isEntitiesLoaded()).thenReturn(true);
        when(chunk.getWorld()).thenReturn(world); when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[0]);
    }
    private void instrumentDrift() {
        var drifted = mock(org.bukkit.block.data.type.NoteBlock.class);
        when(drifted.getAsString()).thenReturn("minecraft:note_block[instrument=snare,note=24,powered=false]");
        when(drifted.getNote()).thenReturn(new Note(24)); current.set(drifted);
    }
    @Test void chunkReconciliationRecoversANativeInstrumentChangeWithoutLosingItsRecord() {
        var reserved = placeNativeBlock(); instrumentDrift(); loadedChunk();
        manager.reconcile(chunk);
        assertSame(reserved, current.get());
        assertEquals("warning_block", manager.placedBlock(location).orElseThrow().asset().id());
        verify(world, never()).spawn(any(Location.class), eq(ItemDisplay.class), any(Consumer.class));
    }
    @Test void physicsRepairsAnAlreadyDriftedNativeInstrument() {
        var reserved = placeNativeBlock(); instrumentDrift();
        var event = new org.bukkit.event.block.BlockPhysicsEvent(block, current.get());
        manager.physics(event);
        assertTrue(event.isCancelled()); assertSame(reserved, current.get());
    }
    @Test void repairsTheShapeUpdateThatHappensAfterThePhysicsEvent() {
        var reserved = placeNativeBlock(); loadedChunk();
        manager.physics(new org.bukkit.event.block.BlockPhysicsEvent(block, current.get()));
        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(Bukkit.getScheduler()).runTaskTimer(eq(Main.plugin), callback.capture(), eq(1L), eq(1L));
        instrumentDrift(); // Paper's vertical updateShape runs after the cancellable neighbor event.
        callback.getValue().run();
        assertSame(reserved, current.get()); assertTrue(manager.placedBlock(location).isPresent());
    }
    @Test void aSandNeighborQueuesNativeRecoveryWithoutCancellingTheSandPhysics() {
        var reserved = placeNativeBlock(); loadedChunk();
        Block sand = mock(Block.class); when(sand.getChunk()).thenReturn(chunk);
        when(world.getBlockAt(1, 65, 1)).thenReturn(sand);
        when(sand.getLocation()).thenReturn(new Location(world, 1, 65, 1));
        when(sand.getX()).thenReturn(1); when(sand.getY()).thenReturn(65); when(sand.getZ()).thenReturn(1);
        when(sand.getBlockData()).thenReturn(mock(BlockData.class));
        var event = new org.bukkit.event.block.BlockPhysicsEvent(sand, sand.getBlockData());
        manager.physics(event); manager.physics(event);
        assertFalse(event.isCancelled());
        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(Bukkit.getScheduler(), times(1)).runTaskTimer(eq(Main.plugin), callback.capture(), eq(1L), eq(1L));
        instrumentDrift(); callback.getValue().run();
        assertSame(reserved, current.get());
    }
    @Test void nativeRecoveryDoesNotOverwriteAReplacementBlockOrAnUnrelatedNote() {
        placeNativeBlock(); loadedChunk();
        var changed = mock(org.bukkit.block.data.type.NoteBlock.class);
        when(changed.getAsString()).thenReturn("minecraft:note_block[instrument=harp,note=0,powered=false]");
        when(changed.getNote()).thenReturn(new Note(0)); current.set(changed);
        manager.reconcile(chunk);
        assertSame(changed, current.get()); assertTrue(manager.placedBlock(location).isEmpty());
    }
    @Test void failedDisplaySpawnRestoresCarrierAndDoesNotPersistPlacement() {
        when(world.spawn(any(Location.class),eq(ItemDisplay.class),any(Consumer.class))).thenThrow(new IllegalStateException("spawn failed"));
        assertThrows(IllegalStateException.class,()->manager.placeBlock(location,"demo.cube"));
        assertSame(air,current.get()); assertNull(stored.get());
        verify(block).setBlockData(carrier,false);
    }
    @Test void displaysAreHiddenUntilTheReceivingPlayerHasThePack() {
        var ready=mock(org.bukkit.entity.Player.class);var waiting=mock(org.bukkit.entity.Player.class);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();when(ready.getUniqueId()).thenReturn(a);when(waiting.getUniqueId()).thenReturn(b);
        when(world.getPlayers()).thenReturn(List.of(ready,waiting));when(api.isPackReady(a)).thenReturn(true);
        manager.spawnItemDisplay(location,"demo:cube",DisplayOptions.defaults());
        verify(display).setVisibleByDefault(false);verify(ready).showEntity(Main.plugin,display);verify(waiting,never()).showEntity(any(),any());
    }
    @Test void cancelledBlockBreakDoesNotRemovePlacement() {
        manager.placeBlock(location,"demo.cube");
        var event=new org.bukkit.event.block.BlockBreakEvent(block,mock(org.bukkit.entity.Player.class));event.setCancelled(true);
        manager.broken(event);
        assertTrue(manager.placedBlock(location).isPresent());verify(display,never()).remove();
    }
    @Test void aProtectionPluginCancellingPlacementRollsBackWithoutConsumingTheItem() {
        var clicked=mock(Block.class);var material=mock(Material.class);when(clicked.getType()).thenReturn(material);
        when(clicked.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(block);
        when(world.getWorldBorder()).thenReturn(mock(WorldBorder.class));when(world.getWorldBorder().isInside(any(Location.class))).thenReturn(true);
        var player=mock(org.bukkit.entity.Player.class);when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);when(player.hasPermission("assetchannel.blocks.place")).thenReturn(true);
        var item=mock(ItemStack.class);var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);when(item.getItemMeta()).thenReturn(meta);
        var itemPdc=mock(PersistentDataContainer.class);when(meta.getPersistentDataContainer()).thenReturn(itemPdc);
        when(itemPdc.get(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenReturn("demo.cube");
        var event=mock(org.bukkit.event.player.PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);when(event.getClickedBlock()).thenReturn(clicked);
        when(event.getHand()).thenReturn(org.bukkit.inventory.EquipmentSlot.HAND);when(event.getItem()).thenReturn(item);
        when(event.getPlayer()).thenReturn(player);when(event.getBlockFace()).thenReturn(org.bukkit.block.BlockFace.UP);
        var plugins=mock(org.bukkit.plugin.PluginManager.class);bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        doAnswer(i->{((org.bukkit.event.block.BlockPlaceEvent)i.getArgument(0)).setCancelled(true);return null;}).when(plugins).callEvent(any());
        manager.interact(event);
        assertSame(air,current.get());assertTrue(manager.placedBlock(location).isEmpty());verify(display).remove();verify(item,never()).subtract(anyInt());
    }
    @Test void persistsPlacementAndNeverOverwritesAnExternalBlockChangeDuringRemoval() {
        var placed=manager.placeBlock(location,"demo.cube");
        assertEquals(display.getUniqueId(),placed.displayId());
        assertEquals("demo.cube",new WorldAssetManager(api).placedBlock(location).orElseThrow().asset().id());
        var external=mock(BlockData.class); when(external.getAsString()).thenReturn("minecraft:gold_block");current.set(external);
        assertTrue(manager.removeBlock(location,false));
        assertSame(external,current.get()); assertTrue(manager.placedBlock(location).isEmpty()); verify(display).remove();
    }
    @Test void reconciliationDoesNotDuplicateAnExistingDisplay() {
        manager.placeBlock(location,"demo.cube");
        when(chunk.isLoaded()).thenReturn(true);when(chunk.isEntitiesLoaded()).thenReturn(true);when(chunk.getWorld()).thenReturn(world);
        when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[]{display});
        manager.reconcile(chunk);
        verify(world,times(1)).spawn(any(Location.class),eq(ItemDisplay.class),any(Consumer.class));
        assertEquals(display.getUniqueId(),manager.placedBlock(location).orElseThrow().displayId());
    }
    @Test void placedModelsDoNotUseTheItemFrameDisplayScale() {
        manager.placeBlock(location,"demo.cube");
        verify(display).setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
    }
    @Test void chunkReconciliationIsDeferredAndUsesOneSharedTask() {
        var scheduler=mock(org.bukkit.scheduler.BukkitScheduler.class);var task=mock(org.bukkit.scheduler.BukkitTask.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(Main.plugin),any(Runnable.class),eq(1L),eq(1L))).thenReturn(task);
        when(chunk.getWorld()).thenReturn(world);when(chunk.isLoaded()).thenReturn(true);when(chunk.isEntitiesLoaded()).thenReturn(true);
        when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[0]);
        var event=mock(org.bukkit.event.world.EntitiesLoadEvent.class);when(event.getChunk()).thenReturn(chunk);
        manager.loaded(event);manager.loaded(event);
        var callback=org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler,times(1)).runTaskTimer(eq(Main.plugin),callback.capture(),eq(1L),eq(1L));
        callback.getValue().run();verify(task).cancel();
    }
    @Test void blockDamageOverlaysAreTargetedToPackReadyPlayers() {
        var ready=mock(org.bukkit.entity.Player.class);var waiting=mock(org.bukkit.entity.Player.class);
        UUID id=UUID.randomUUID();when(ready.getUniqueId()).thenReturn(id);when(api.isPackReady(id)).thenReturn(true);
        when(ready.getLocation()).thenReturn(location.clone());when(world.getPlayers()).thenReturn(List.of(ready,waiting));
        manager.showBlockDamage(location,0.5f,42);
        verify(ready).sendBlockDamage(location,0.5f,42);verify(waiting,never()).sendBlockDamage(any(Location.class),anyFloat(),anyInt());
        assertThrows(IllegalArgumentException.class,()->manager.showBlockDamage(location,2,42));
    }
    @Test void reconciliationCleansUpAChangedCarrierWithoutRestoringOverIt() {
        manager.placeBlock(location,"demo.cube");
        var external=mock(BlockData.class);when(external.getAsString()).thenReturn("minecraft:gold_block");current.set(external);
        when(chunk.isLoaded()).thenReturn(true);when(chunk.isEntitiesLoaded()).thenReturn(true);when(chunk.getWorld()).thenReturn(world);
        when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[0]);
        manager.reconcile(chunk);
        assertSame(external,current.get());assertTrue(manager.placedBlock(location).isEmpty());verify(display).remove();
    }
}
