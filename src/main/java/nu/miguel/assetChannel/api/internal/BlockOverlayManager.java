package nu.miguel.assetChannel.api.internal;

import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.api.BlockOverlay;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Per-player block packets only; overlay sessions never mutate world blocks or spawn entities. */
public final class BlockOverlayManager implements Listener {
    private record Target(UUID player, UUID world, int x, int y, int z) {}

    private final AssetChannelApiImpl api;
    private final Map<UUID, Overlay> sessions = new LinkedHashMap<>();
    private final Map<Target, Overlay> targets = new HashMap<>();
    private final Map<UUID, LinkedHashSet<Overlay>> players = new HashMap<>();
    private final Map<UUID, Blink> previews = new LinkedHashMap<>();
    private BukkitTask ticker;
    private long clock;
    private static final int MAX_PER_PLAYER = 512, MAX_TOTAL = 8192;

    public BlockOverlayManager(AssetChannelApiImpl api) {
        this.api = api;
    }

    public BlockOverlay show(Player player, Location location, String assetId) {
        thread();
        Objects.requireNonNull(player);
        Objects.requireNonNull(location);
        World world = Objects.requireNonNull(location.getWorld(), "world");
        if (!Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ()))
            throw new IllegalArgumentException("Non-finite overlay location");
        Location at =
                new Location(
                        world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (!world.equals(player.getWorld()))
            throw new IllegalArgumentException("Overlay must be in the player's world");
        if (at.getBlockY() < world.getMinHeight() || at.getBlockY() >= world.getMaxHeight())
            throw new IllegalArgumentException("Overlay is outside world height");
        if (!reachable(player, at))
            throw new IllegalStateException("Overlay chunk must be loaded and sent to the player");
        if (!api.isPackReady(player.getUniqueId()))
            throw new IllegalStateException("Wait for the AssetChannel pack to load");
        var asset =
                api.block(assetId)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Unknown block asset: " + assetId));
        if (asset.blockState() == null)
            throw new IllegalArgumentException(
                    "Block " + assetId + " must use render: block for overlays");
        fullCube(blockAt(at));
        BlockData state = Bukkit.createBlockData(asset.blockState());
        Target target =
                new Target(
                        player.getUniqueId(),
                        world.getUID(),
                        at.getBlockX(),
                        at.getBlockY(),
                        at.getBlockZ());
        Overlay previous = targets.get(target);
        if (previous == null
                && (sessions.size() >= MAX_TOTAL
                        || players.getOrDefault(player.getUniqueId(), new LinkedHashSet<>()).size()
                                >= MAX_PER_PLAYER))
            throw new IllegalStateException(
                    "Block overlay limit reached (512 per player, 8192 total)");
        // Transfer ownership only after the replacement packet succeeds. Never insert a real-block
        // packet between overlays.
        player.sendBlockChange(at, state);
        if (previous != null) finish(previous, false);
        Overlay overlay = new Overlay(player, target, at, asset.id(), state);
        sessions.put(overlay.id, overlay);
        targets.put(target, overlay);
        players.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashSet<>())
                .add(overlay);
        return overlay;
    }

    public Optional<BlockOverlay> get(UUID id) {
        thread();
        return Optional.ofNullable(sessions.get(id));
    }

    public Collection<BlockOverlay> all(Player player) {
        thread();
        return List.copyOf(players.getOrDefault(player.getUniqueId(), new LinkedHashSet<>()));
    }

    public void clear(Player player, boolean restore) {
        thread();
        for (Overlay overlay :
                List.copyOf(players.getOrDefault(player.getUniqueId(), new LinkedHashSet<>())))
            safelyFinish(overlay, restore);
    }

    public void clearAll() {
        thread();
        for (Overlay overlay : List.copyOf(sessions.values())) safelyFinish(overlay, true);
    }

    /**
     * Bounded operator preview. Consumers control their own effect timing via
     * BlockOverlay.setVisible.
     */
    public BlockOverlay previewBlink(
            Player player, Location at, String assetId, int intervalTicks, int durationTicks) {
        thread();
        if (intervalTicks < 2 || intervalTicks > 200 || durationTicks < 2 || durationTicks > 1200)
            throw new IllegalArgumentException(
                    "Blink interval must be 2..200 ticks and duration 2..1200 ticks");
        Overlay overlay = (Overlay) show(player, at, assetId);
        for (Blink previous : List.copyOf(previews.values()))
            if (previous.overlay.target.player().equals(player.getUniqueId()))
                safelyFinish(previous.overlay, true);
        previews.put(
                overlay.id,
                new Blink(overlay, intervalTicks, clock + intervalTicks, clock + durationTicks));
        if (ticker == null)
            ticker = Bukkit.getScheduler().runTaskTimer(Main.plugin, this::tick, 1L, 1L);
        return overlay;
    }

    private static final class Blink {
        final Overlay overlay;
        final int interval;
        long next;
        final long expires;

        Blink(Overlay overlay, int interval, long next, long expires) {
            this.overlay = overlay;
            this.interval = interval;
            this.next = next;
            this.expires = expires;
        }
    }

    private void tick() {
        thread();
        clock++;
        for (Blink blink : List.copyOf(previews.values())) {
            if (blink.overlay.closed) continue;
            if (clock >= blink.expires
                    || !reachable(blink.overlay.player, blink.overlay.at)
                    || !api.isPackReady(blink.overlay.target.player())) {
                safelyFinish(blink.overlay, true);
                continue;
            }
            if (clock >= blink.next)
                try {
                    blink.overlay.setVisible(!blink.overlay.visible);
                    blink.next = clock + blink.interval;
                } catch (RuntimeException error) {
                    safelyFinish(blink.overlay, true);
                }
        }
        stopIdleTicker();
    }

    private void finish(Overlay overlay, boolean restore) {
        if (overlay.closed) return;
        overlay.closed = true;
        sessions.remove(overlay.id);
        targets.remove(overlay.target, overlay);
        previews.remove(overlay.id);
        var owned = players.get(overlay.target.player());
        if (owned != null) {
            owned.remove(overlay);
            if (owned.isEmpty()) players.remove(overlay.target.player());
        }
        stopIdleTicker();
        boolean wasVisible = overlay.visible;
        overlay.visible = false;
        if (restore && wasVisible && reachable(overlay.player, overlay.at))
            sendReal(overlay.player, overlay.at);
    }

    private void safelyFinish(Overlay overlay, boolean restore) {
        try {
            finish(overlay, restore);
        } catch (RuntimeException error) {
            java.util.logging.Logger.getLogger("AssetChannel")
                    .log(
                            java.util.logging.Level.WARNING,
                            "Could not restore block overlay " + overlay.id,
                            error);
        }
    }

    private void stopIdleTicker() {
        if (previews.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private static void thread() {
        AssetChannelApiImpl.requireMainThread();
    }

    private static boolean reachable(Player player, Location at) {
        return player.isOnline()
                && at.getWorld().equals(player.getWorld())
                && at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)
                && player.isChunkSent(Chunk.getChunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4));
    }

    private static void fullCube(Block block) {
        var boxes = block.getCollisionShape().getBoundingBoxes();
        if (boxes.size() == 1) {
            var box = boxes.iterator().next();
            if (box.getMinX() == 0
                    && box.getMinY() == 0
                    && box.getMinZ() == 0
                    && box.getMaxX() == 1
                    && box.getMaxY() == 1
                    && box.getMaxZ() == 1) return;
        }
        throw new IllegalArgumentException(
                "Warning overlays require a full-cube target to preserve collision prediction");
    }

    private static void sendReal(Player player, Location at) {
        Block block = blockAt(at);
        player.sendBlockChange(at, block.getBlockData());
        if (block.getState() instanceof TileState tile) player.sendBlockUpdate(at, tile);
    }

    private static Block blockAt(Location at) {
        return at.getWorld().getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    private final class Overlay implements BlockOverlay {
        final UUID id = UUID.randomUUID();
        final Player player;
        final Target target;
        final Location at;
        final String assetId;
        final BlockData state;
        boolean visible = true, closed;

        Overlay(Player player, Target target, Location at, String assetId, BlockData state) {
            this.player = player;
            this.target = target;
            this.at = at;
            this.assetId = assetId;
            this.state = state;
        }

        @Override
        public UUID id() {
            thread();
            return id;
        }

        @Override
        public UUID playerId() {
            thread();
            return target.player();
        }

        @Override
        public String assetId() {
            thread();
            return assetId;
        }

        @Override
        public Location location() {
            thread();
            return at.clone();
        }

        @Override
        public boolean visible() {
            thread();
            return visible;
        }

        @Override
        public boolean isClosed() {
            thread();
            return closed;
        }

        @Override
        public void setVisible(boolean show) {
            thread();
            if (closed) throw new IllegalStateException("Block overlay is closed");
            if (!reachable(player, at) || !api.isPackReady(target.player())) {
                finish(this, true);
                throw new IllegalStateException("Player no longer has the overlay chunk/pack");
            }
            try {
                if (show) {
                    fullCube(blockAt(at));
                    player.sendBlockChange(at, state);
                } else sendReal(player, at);
                visible = show;
            } catch (RuntimeException error) {
                safelyFinish(this, true);
                throw error;
            }
        }

        @Override
        public void close() {
            thread();
            finish(this, true);
        }
    }

    @EventHandler
    public void changedWorld(PlayerChangedWorldEvent event) {
        clear(event.getPlayer(), false);
    }

    @EventHandler
    public void respawned(PlayerRespawnEvent event) {
        clear(event.getPlayer(), true);
    }

    @EventHandler
    public void chunkUnloaded(PlayerChunkUnloadEvent event) {
        clearChunk(event.getPlayer(), event.getChunk());
    }

    @EventHandler
    public void serverChunkUnloaded(ChunkUnloadEvent event) {
        for (Overlay overlay : List.copyOf(sessions.values()))
            if (inChunk(overlay, event.getChunk())) safelyFinish(overlay, false);
    }

    private void clearChunk(Player player, Chunk chunk) {
        thread();
        for (Overlay overlay :
                List.copyOf(players.getOrDefault(player.getUniqueId(), new LinkedHashSet<>())))
            if (inChunk(overlay, chunk)) safelyFinish(overlay, false);
    }

    private static boolean inChunk(Overlay overlay, Chunk chunk) {
        return overlay.target.world().equals(chunk.getWorld().getUID())
                && (overlay.target.x() >> 4) == chunk.getX()
                && (overlay.target.z() >> 4) == chunk.getZ();
    }
}
