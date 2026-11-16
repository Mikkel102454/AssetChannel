package nu.miguel.assetChannel.api;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import java.util.*;

public interface AssetChannelApi {
    default Collection<ResourceAsset> resources() { return List.of(); }
    default Collection<ResourceAsset> resources(ResourceKind kind) { return resources().stream().filter(resource->resource.kind()==kind).toList(); }
    default Collection<BlockAsset> blocks() { return List.of(); }
    default Optional<BlockAsset> block(String id) { return Optional.empty(); }
    default boolean isPackReady(Player player) { return false; }
    default org.bukkit.entity.ItemDisplay spawnItemDisplay(org.bukkit.Location location,String model,DisplayOptions options) { throw new UnsupportedOperationException(); }
    default org.bukkit.entity.BlockDisplay spawnBlockDisplay(org.bukkit.Location location,org.bukkit.block.data.BlockData data,DisplayOptions options) { throw new UnsupportedOperationException(); }
    default boolean removeDisplay(UUID id) { throw new UnsupportedOperationException(); }
    default Collection<org.bukkit.entity.Display> displays() { return List.of(); }
    default PlacedAssetBlock placeBlock(org.bukkit.Location location,String assetId) { throw new UnsupportedOperationException(); }
    default Optional<PlacedAssetBlock> placedBlock(org.bukkit.Location location) { return Optional.empty(); }
    /** Removes without drops; effects are optional. Does not overwrite external block changes. */
    default boolean removeBlock(org.bukkit.Location location,boolean effects) { throw new UnsupportedOperationException(); }
    default org.bukkit.inventory.ItemStack createBlockItem(String assetId) { throw new UnsupportedOperationException(); }
    /** Typed vanilla particles, sent only to nearby players with the active pack. */
    default <T> void spawnParticle(org.bukkit.Location location,org.bukkit.Particle particle,int count,T data) { throw new UnsupportedOperationException(); }
    /** Client crack overlay; use a distinct source ID per animation and progress zero to clear it. */
    default void showBlockDamage(org.bukkit.Location location,float progress,int sourceId) { throw new UnsupportedOperationException(); }
    /** Plays a native sounds.json event key without requiring a generated SoundAsset. */
    default PlaybackResult playResourceSound(Player player,String key,SoundOrigin origin,SoundCategory category,float volume,float pitch) { throw new UnsupportedOperationException(); }
    /** Native item definitions, indexed by namespace:path. */
    default Collection<ModelAsset> models() { return List.of(); }
    default Optional<ModelAsset> model(String id) { return Optional.empty(); }
    Collection<IconAsset> icons();
    Collection<FontAsset> fonts();
    Collection<SoundAsset> sounds();
    Collection<SoundtrackAsset> soundtracks();
    Collection<HudAsset> huds();
    Optional<IconAsset> icon(String id);
    default Optional<String> iconUnicode(String id) { return icon(id).map(IconAsset::unicode); }
    Optional<FontAsset> font(String id);
    Optional<SoundAsset> sound(String id);
    Optional<SoundtrackAsset> soundtrack(String id);
    Optional<HudAsset> hud(String id);
    PlaybackResult playSound(Player player, String id, SoundCategory category, float volume, float pitch);
    PlaybackResult playSound(Player player, String id, SoundOrigin origin, SoundCategory category, float volume, float pitch);
    BroadcastPlaybackResult broadcastSound(String id, SoundOrigin origin, SoundCategory category, float volume, float pitch);
    boolean stopSound(Player player, String id);
    boolean stopSound(Player player, String id, SoundCategory category);
    void stopAllSounds(Player player);
    MusicSession startSoundtrack(String id, Collection<? extends Player> players, SessionSettings settings);
    Optional<MusicSession> session(UUID id);
    Collection<MusicSession> sessions();
    HudDisplayResult showHud(Player player, String templateId, HudRequest request);
    Optional<HudSession> hudSession(UUID sessionId);
    Optional<HudSession> hudSession(Player player, String slot);
    Collection<HudSession> hudSessions();
    boolean hideHud(Player player, String slot);
}
