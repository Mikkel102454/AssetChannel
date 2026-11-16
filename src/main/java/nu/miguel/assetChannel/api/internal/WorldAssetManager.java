package nu.miguel.assetChannel.api.internal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.api.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.*;
import java.util.*;

/** Main-thread world resource lifecycle. Chunk PDC and entity PDC provide native persistence. */
public final class WorldAssetManager implements Listener {
    static final NamespacedKey KIND=new NamespacedKey("assetchannel","display_kind");
    private static final NamespacedKey MODEL=new NamespacedKey("assetchannel","display_model");
    private static final NamespacedKey BLOCKS=new NamespacedKey("assetchannel","placed_blocks_v1");
    private static final NamespacedKey ANCHOR=new NamespacedKey("assetchannel","block_anchor");
    private static final NamespacedKey BLOCK_ITEM=new NamespacedKey("assetchannel","block_item");
    private static final Gson JSON=new Gson();
    private static final java.lang.reflect.Type STORE=new TypeToken<Map<String,PlacedAssetBlock>>(){}.getType();
    private final AssetChannelApiImpl api;
    private record ChunkKey(UUID world,int x,int z){}
    private final Map<ChunkKey,Chunk> pendingChunks=new LinkedHashMap<>();
    private org.bukkit.scheduler.BukkitTask reconcileTask;
    public WorldAssetManager(AssetChannelApiImpl api){this.api=api;}
    private static void thread(){AssetChannelApiImpl.requireMainThread();}
    private static World world(Location at) {
        Objects.requireNonNull(at,"location"); World world=Objects.requireNonNull(at.getWorld(),"world");
        if(!Double.isFinite(at.getX())||!Double.isFinite(at.getY())||!Double.isFinite(at.getZ()))throw new IllegalArgumentException("Non-finite location");
        if(!world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))throw new IllegalStateException("Target chunk must already be loaded");
        return world;
    }
    private ModelAsset model(String id){return api.model(id).orElseThrow(()->new IllegalArgumentException("Unknown item model: "+id));}
    private BlockAsset definition(String id){return api.block(id).orElseThrow(()->new IllegalArgumentException("Unknown block asset: "+id));}
    private void configure(Display display,DisplayOptions options,String kind) {
        display.setPersistent(options.persistent()); display.setVisibleByDefault(false);
        display.setInterpolationDuration(options.interpolationTicks()); display.setTeleportDuration(options.teleportTicks());
        display.setTransformation(new Transformation(new Vector3f(),new AxisAngle4f(),new Vector3f(options.scale()),new AxisAngle4f()));
        display.getPersistentDataContainer().set(KIND,PersistentDataType.STRING,kind);
    }
    private void showReady(Display display) {
        for(Player player:display.getWorld().getPlayers())if(api.isPackReady(player.getUniqueId()))player.showEntity(Main.plugin,display);
    }
    public ItemDisplay spawnItemDisplay(Location at,String id,DisplayOptions options) {
        thread(); World world=world(at); Objects.requireNonNull(options); ItemStack item=model(id).createItem(Material.PAPER);
        ItemDisplay display=world.spawn(at.clone(),ItemDisplay.class,entity->{
            configure(entity,options,"item"); entity.setItemStack(item); entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.getPersistentDataContainer().set(MODEL,PersistentDataType.STRING,id);
        });
        showReady(display);return display;
    }
    public BlockDisplay spawnBlockDisplay(Location at,BlockData data,DisplayOptions options) {
        thread();World world=world(at);Objects.requireNonNull(data);Objects.requireNonNull(options);
        BlockDisplay display=world.spawn(at.clone(),BlockDisplay.class,entity->{configure(entity,options,"block");entity.setBlock(data.clone());});
        showReady(display);return display;
    }
    private static boolean owned(Entity entity){return entity instanceof Display&&entity.getPersistentDataContainer().has(KIND,PersistentDataType.STRING);}
    public boolean removeDisplay(UUID id) {
        thread();Entity entity=Bukkit.getEntity(Objects.requireNonNull(id));if(!owned(entity))return false;
        if("placed".equals(entity.getPersistentDataContainer().get(KIND,PersistentDataType.STRING)))throw new IllegalArgumentException("Use removeBlock for a placed asset block");
        entity.remove();return true;
    }
    public Collection<Display> displays() {
        thread();List<Display> result=new ArrayList<>();
        for(World world:Bukkit.getWorlds())for(Display display:world.getEntitiesByClass(Display.class))if(owned(display))result.add(display);
        return List.copyOf(result);
    }
    public Collection<Display> removableDisplays() {
        return displays().stream().filter(display -> !"placed".equals(display.getPersistentDataContainer().get(KIND, PersistentDataType.STRING))).toList();
    }
    public void playerReadiness(Player player,boolean ready) {
        thread();for(Display display:player.getWorld().getEntitiesByClass(Display.class))if(owned(display)) {
            if(ready)player.showEntity(Main.plugin,display);else player.hideEntity(Main.plugin,display);
        }
    }
    private static String key(Block block){return block.getX()+","+block.getY()+","+block.getZ();}
    private Map<String,PlacedAssetBlock> read(Chunk chunk) {
        String value=chunk.getPersistentDataContainer().get(BLOCKS,PersistentDataType.STRING);
        if(value==null)return new LinkedHashMap<>();
        Map<String,PlacedAssetBlock> records=JSON.fromJson(value,STORE);
        if(records==null)throw new IllegalStateException("Invalid AssetChannel chunk block store");
        return records;
    }
    private void save(Chunk chunk,Map<String,PlacedAssetBlock> records) {
        if(records.isEmpty())chunk.getPersistentDataContainer().remove(BLOCKS);
        else chunk.getPersistentDataContainer().set(BLOCKS,PersistentDataType.STRING,JSON.toJson(records,STORE));
    }
    public Optional<PlacedAssetBlock> placedBlock(Location at) {
        thread();World world=world(at);Block block=world.getBlockAt(at.getBlockX(),at.getBlockY(),at.getBlockZ());
        return Optional.ofNullable(read(block.getChunk()).get(key(block)));
    }
    private static boolean air(Material material){return material==Material.AIR||material==Material.CAVE_AIR||material==Material.VOID_AIR;}
    public PlacedAssetBlock placeBlock(Location at,String id) {
        return placeBlock(at,id,true);
    }
    private PlacedAssetBlock placeBlock(Location at,String id,boolean effects) {
        thread();World world=world(at);Block block=world.getBlockAt(at.getBlockX(),at.getBlockY(),at.getBlockZ());
        if(block.getY()<world.getMinHeight()||block.getY()>=world.getMaxHeight())throw new IllegalArgumentException("Outside world height");
        BlockAsset asset=definition(id);model(asset.model());
        BlockData carrier=Bukkit.createBlockData(asset.blockState() != null ? asset.blockState() : asset.carrier());
        Bukkit.createBlockData(asset.particleBlock()); // Validate before any world mutation.
        if(air(carrier.getMaterial()))throw new IllegalArgumentException("Carrier must be a non-air block");
        Map<String,PlacedAssetBlock> records=read(block.getChunk());
        if(!air(block.getType())||records.containsKey(key(block)))throw new IllegalStateException("Target block is occupied");
        String original=block.getBlockData().getAsString(); ItemDisplay display=null;PlacedAssetBlock placed;
        try {
            block.setBlockData(carrier,false);
            if (asset.blockState() == null) {
                display=spawnItemDisplay(block.getLocation().add(0.5,0.5,0.5),asset.model(),DisplayOptions.defaults());
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.getPersistentDataContainer().set(KIND,PersistentDataType.STRING,"placed");
                display.getPersistentDataContainer().set(ANCHOR,PersistentDataType.STRING,key(block));
            }
            placed=new PlacedAssetBlock(world.getUID(),block.getX(),block.getY(),block.getZ(),asset,display == null ? null : display.getUniqueId(),original,carrier.getAsString());
            records.put(key(block),placed);save(block.getChunk(),records);
        } catch(RuntimeException error) {
            if(display!=null)display.remove();block.setBlockData(Bukkit.createBlockData(original),false);throw error;
        }
        if(effects)try{sound(block.getLocation().add(0.5,0.5,0.5),asset.placeSound());}
        catch(RuntimeException error){Main.plugin.getLogger().warning("Placement sound failed: "+error.getMessage());}
        return placed;
    }
    public boolean removeBlock(Location at,boolean effects) {
        thread();World world=world(at);Block block=world.getBlockAt(at.getBlockX(),at.getBlockY(),at.getBlockZ());
        Map<String,PlacedAssetBlock> records=read(block.getChunk());PlacedAssetBlock placed=records.get(key(block));if(placed==null)return false;
        boolean unchanged=block.getBlockData().getAsString().equals(placed.carrierData());
        BlockData original=unchanged?Bukkit.createBlockData(placed.originalData()):null;
        records.remove(key(block));save(block.getChunk(),records);
        Entity display=placed.displayId() == null ? null : Bukkit.getEntity(placed.displayId());if(owned(display))display.remove();
        if(unchanged)block.setBlockData(original,false);
        if(effects)breakEffects(block.getLocation().add(0.5,0.5,0.5),placed.asset());return true;
    }
    public ItemStack createBlockItem(String id) {
        thread();return blockItem(definition(id));
    }
    private ItemStack blockItem(BlockAsset asset) {
        ItemStack item=model(asset.model()).createItem(Material.PAPER);
        item.editMeta(meta->meta.getPersistentDataContainer().set(BLOCK_ITEM,PersistentDataType.STRING,asset.id()));
        return item;
    }
    private void sound(Location at,String sound) {
        if(sound==null)return;
        for(Player player:at.getWorld().getPlayers())if(api.isPackReady(player.getUniqueId())&&player.getLocation().distanceSquared(at)<=64*64)
            player.playSound(at,sound,SoundCategory.BLOCKS,1,1);
    }
    public <T> void spawnParticle(Location at,Particle particle,int count,T data) {
        thread();world(at);Objects.requireNonNull(particle);if(count<0||count>256)throw new IllegalArgumentException("Particle count must be 0..256");
        if(particle.getDataType()!=Void.class&&(data==null||!particle.getDataType().isInstance(data)))throw new IllegalArgumentException("Particle requires "+particle.getDataType().getName());
        for(Player player:at.getWorld().getPlayers())if(api.isPackReady(player.getUniqueId())&&player.getLocation().distanceSquared(at)<=32*32)
            player.spawnParticle(particle,at,count,0.25,0.25,0.25,0.05,data);
    }
    private void breakEffects(Location at,BlockAsset asset) {
        try {
            sound(at,asset.breakSound());
            if(asset.particleCount()==0)return;
            if(asset.particleModel()!=null)spawnParticle(at,Particle.ITEM,asset.particleCount(),model(asset.particleModel()).createItem(Material.PAPER));
            else spawnParticle(at,Particle.BLOCK,asset.particleCount(),Bukkit.createBlockData(asset.particleBlock()));
        }catch(RuntimeException error){Main.plugin.getLogger().warning("Block effect failed: "+error.getMessage());}
    }
    public void showBlockDamage(Location at,float progress,int sourceId) {
        thread();world(at);
        if(!Float.isFinite(progress)||progress<0||progress>1)throw new IllegalArgumentException("Block damage progress must be 0..1");
        for(Player player:at.getWorld().getPlayers())if(api.isPackReady(player.getUniqueId())&&player.getLocation().distanceSquared(at)<=64*64)
            player.sendBlockDamage(at,progress,sourceId);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(BlockDamageEvent event) {
        if(placedBlock(event.getBlock().getLocation()).isPresent())event.setInstaBreak(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void broken(BlockBreakEvent event) {
        if(event.isCancelled())return;
        var placed=placedBlock(event.getBlock().getLocation());if(placed.isEmpty())return;
        event.setCancelled(true);
        boolean intact=event.getBlock().getBlockData().getAsString().equals(placed.get().carrierData());
        if(removeBlock(event.getBlock().getLocation(),intact)&&intact&&placed.get().asset().drops()&&event.getPlayer().getGameMode()!=GameMode.CREATIVE)
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5,0.5,0.5),blockItem(placed.get().asset()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent event) {
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null||event.getHand()==null)return;
        boolean nativeBlock = event.getClickedBlock().getType() == Material.NOTE_BLOCK
                && placedBlock(event.getClickedBlock().getLocation()).map(p -> p.asset().blockState() != null).orElse(false);
        if (nativeBlock) event.setCancelled(true);
        if(event.getItem()==null)return;
        if(event.getPlayer().getGameMode()==GameMode.ADVENTURE||event.getPlayer().getGameMode()==GameMode.SPECTATOR)return;
        var meta=event.getItem().getItemMeta();if(meta==null)return;
        String id=meta.getPersistentDataContainer().get(BLOCK_ITEM,PersistentDataType.STRING);if(id==null)return;
        if(!nativeBlock&&event.getClickedBlock().getType().isInteractable()&&!event.getPlayer().isSneaking())return;
        event.setCancelled(true);Block target=event.getClickedBlock().getRelative(event.getBlockFace());
        if(!target.getWorld().getWorldBorder().isInside(target.getLocation())||!event.getPlayer().hasPermission("assetchannel.blocks.place"))return;
        var previous=target.getState();
        try {
            var placed=placeBlock(target.getLocation(),id,false);
            var check=new BlockPlaceEvent(target,previous,event.getClickedBlock(),event.getItem(),event.getPlayer(),true,event.getHand());
            Bukkit.getPluginManager().callEvent(check);
            if(check.isCancelled()||!check.canBuild()){removeBlock(target.getLocation(),false);return;}
            if(event.getPlayer().getGameMode()!=GameMode.CREATIVE) {
                ItemStack remainder=event.getItem().clone();remainder.subtract(1);
                event.getPlayer().getInventory().setItem(event.getHand(),remainder);
            }
            sound(target.getLocation().add(0.5,0.5,0.5),placed.asset().placeSound());
        } catch(IllegalArgumentException|IllegalStateException error){event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(error.getMessage()));}
    }
    private boolean managed(Block block){return placedBlock(block.getLocation()).isPresent();}
    private boolean restoreNativeState(Block block, PlacedAssetBlock placed) {
        if (placed.asset().blockState() == null
                || !(block.getBlockData() instanceof org.bukkit.block.data.type.NoteBlock current)) return false;
        var reserved = (org.bukkit.block.data.type.NoteBlock) Bukkit.createBlockData(placed.carrierData());
        // Neighbor updates change instrument/power, never the note. A different note or material is an external replacement.
        if (!current.getNote().equals(reserved.getNote())) return false;
        block.setBlockData(reserved, false);
        return true;
    }
    @EventHandler(ignoreCancelled=true) public void notePlayed(NotePlayEvent event) {
        if (placedBlock(event.getBlock().getLocation()).map(p -> p.asset().blockState() != null).orElse(false)) event.setCancelled(true);
    }
    @EventHandler public void redstone(BlockRedstoneEvent event) {
        if (placedBlock(event.getBlock().getLocation()).map(p -> p.asset().blockState() != null).orElse(false)) event.setNewCurrent(event.getOldCurrent());
    }
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent event){if(event.getBlocks().stream().anyMatch(this::managed)||event.getBlocks().stream().anyMatch(b->managed(b.getRelative(event.getDirection()))))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void retract(BlockPistonRetractEvent event){if(event.getBlocks().stream().anyMatch(this::managed))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void flow(BlockFromToEvent event){if(managed(event.getToBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void physics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Map<String, PlacedAssetBlock> records = read(block.getChunk());
        PlacedAssetBlock placed = records.get(key(block));
        if (placed != null) {
            event.setCancelled(true);
            if (!block.getBlockData().getAsString().equals(placed.carrierData())) restoreNativeState(block, placed);
        }
        // Vertical shape updates run outside the cancellable neighbor event. Repair after that update chain.
        // Nearby sand/support updates share the bounded chunk queue without cancelling their physics.
        for (int dy = -1; dy <= 1; dy++) {
            PlacedAssetBlock nearby = records.get(block.getX() + "," + (block.getY() + dy) + "," + block.getZ());
            if (nearby != null && nearby.asset().blockState() != null) { enqueue(block.getChunk()); break; }
        }
    }
    @EventHandler(ignoreCancelled=true) public void burn(BlockBurnEvent event){if(managed(event.getBlock()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void explode(BlockExplodeEvent event){event.blockList().removeIf(this::managed);}
    @EventHandler(ignoreCancelled=true) public void explode(EntityExplodeEvent event){event.blockList().removeIf(this::managed);}
    @EventHandler public void loaded(EntitiesLoadEvent event){enqueue(event.getChunk());}
    @EventHandler public void unloaded(org.bukkit.event.world.ChunkUnloadEvent event){pendingChunks.remove(chunkKey(event.getChunk()));}
    @EventHandler public void changedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event){playerReadiness(event.getPlayer(),api.isPackReady(event.getPlayer().getUniqueId()));}
    public void initializeLoaded() {
        thread();for(World world:Bukkit.getWorlds())for(Chunk chunk:world.getLoadedChunks())if(chunk.isEntitiesLoaded())enqueue(chunk);
    }
    private static ChunkKey chunkKey(Chunk chunk){return new ChunkKey(chunk.getWorld().getUID(),chunk.getX(),chunk.getZ());}
    private void enqueue(Chunk chunk) {
        thread();pendingChunks.putIfAbsent(chunkKey(chunk),chunk);
        if(reconcileTask==null)reconcileTask=Bukkit.getScheduler().runTaskTimer(Main.plugin,this::drain,1L,1L);
    }
    private void drain() {
        // Wait until the load event has completed; never force chunk/entity loads.
        // One shared task handles at most 32 distinct loaded chunks per tick.
        for(int count=0;count<32&&!pendingChunks.isEmpty();count++) {
            var next=pendingChunks.entrySet().iterator();Chunk chunk=next.next().getValue();next.remove();
            if(!chunk.isLoaded()||!chunk.isEntitiesLoaded())continue;
            try{reconcile(chunk);}catch(RuntimeException error){Main.plugin.getLogger().warning("Chunk asset reconciliation failed: "+error.getMessage());}
        }
        if(pendingChunks.isEmpty()&&reconcileTask!=null){reconcileTask.cancel();reconcileTask=null;}
    }
    public void reconcile(Chunk chunk) {
        thread();if(!chunk.isLoaded()||!chunk.isEntitiesLoaded())return;
        Map<String,PlacedAssetBlock> records=read(chunk);boolean changed=false;
        for(var entry:List.copyOf(records.entrySet())) {
            PlacedAssetBlock placed=entry.getValue();Block block=chunk.getWorld().getBlockAt(placed.x(),placed.y(),placed.z());
            if(!block.getBlockData().getAsString().equals(placed.carrierData()) && !restoreNativeState(block, placed)) {
                Entity old=placed.displayId() == null ? null : Bukkit.getEntity(placed.displayId());if(owned(old))old.remove();records.remove(entry.getKey());changed=true;continue;
            }
            if (placed.asset().blockState() != null) continue;
            if(Bukkit.getEntity(placed.displayId())==null)try {
                ItemDisplay display=spawnItemDisplay(block.getLocation().add(0.5,0.5,0.5),placed.asset().model(),DisplayOptions.defaults());
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.getPersistentDataContainer().set(KIND,PersistentDataType.STRING,"placed");
                display.getPersistentDataContainer().set(ANCHOR,PersistentDataType.STRING,entry.getKey());
                records.put(entry.getKey(),new PlacedAssetBlock(placed.worldId(),placed.x(),placed.y(),placed.z(),placed.asset(),display.getUniqueId(),placed.originalData(),placed.carrierData()));changed=true;
            }catch(RuntimeException error){Main.plugin.getLogger().warning("Could not restore asset block "+entry.getKey()+": "+error.getMessage());}
        }
        if(changed)save(chunk,records);
        for(Entity entity:chunk.getEntities())if(owned(entity)&&entity.isValid()) {
            String anchor=entity.getPersistentDataContainer().get(ANCHOR,PersistentDataType.STRING);
            if(anchor!=null&&(!records.containsKey(anchor)||!Objects.equals(records.get(anchor).displayId(),entity.getUniqueId()))){entity.remove();continue;}
            entity.setVisibleByDefault(false);showReady((Display)entity);
        }
    }
    public void shutdown(){
        thread();pendingChunks.clear();if(reconcileTask!=null){reconcileTask.cancel();reconcileTask=null;}
        for(Display display:displays())if(!display.isPersistent())display.remove();
    }
}
