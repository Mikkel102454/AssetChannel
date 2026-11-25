package nu.miguel.assetChannel.api;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.*;

public interface AssetChannelApi {
    Collection<RigAsset> rigs();

    Optional<RigAsset> rig(String id);

    UUID packRevision();

    AssetPreflightReport preflight();

    Collection<TimelineAudioAsset> timelineAudio();

    Optional<TimelineAudioAsset> timelineAudio(String id);

    AudioLease leaseAudio(Player player, String id);

    EffectHandle showPostEffect(Player player, String key);

    TitleLease reserveTitle(Player player);

    ActionBarLease reserveActionBar(Player player);

    Collection<CaptionFontAsset> captionFonts();

    Optional<CaptionFontAsset> captionFont(String id);

    Collection<ScreenFontAsset> screenFonts();

    Optional<ScreenFontAsset> screenFont(String id);

    Collection<EffectCurveAsset> effectCurves();

    Optional<EffectCurveAsset> effectCurve(String id);

    /** Acquires an imported compositor channel: one zoom/roll lease and one per scalar asset. */
    ClientEffectLease leaseClientEffect(Player player, String curveId);

    Collection<ResourceAsset> resources();

    default Collection<ResourceAsset> resources(ResourceKind kind) {
        return resources().stream().filter(resource -> resource.kind() == kind).toList();
    }

    Collection<BlockAsset> blocks();

    Optional<BlockAsset> block(String id);

    boolean isPackReady(Player player);

    org.bukkit.entity.ItemDisplay spawnItemDisplay(
            org.bukkit.Location location, String model, DisplayOptions options);

    org.bukkit.entity.BlockDisplay spawnBlockDisplay(
            org.bukkit.Location location,
            org.bukkit.block.data.BlockData data,
            DisplayOptions options);

    boolean removeDisplay(UUID id);

    Collection<org.bukkit.entity.Display> displays();

    PlacedAssetBlock placeBlock(org.bukkit.Location location, String assetId);

    Optional<PlacedAssetBlock> placedBlock(org.bukkit.Location location);

    /** Removes without drops; effects are optional. Does not overwrite external block changes. */
    boolean removeBlock(org.bukkit.Location location, boolean effects);

    org.bukkit.inventory.ItemStack createBlockItem(String assetId);

    /** Shows a native-state asset to one pack-ready player without changing the real world. */
    BlockOverlay showBlockOverlay(Player player, org.bukkit.Location location, String assetId);

    Optional<BlockOverlay> blockOverlay(UUID id);

    Collection<BlockOverlay> blockOverlays(Player player);

    /** Typed vanilla particles, sent only to nearby players with the active pack. */
    <T> void spawnParticle(
            org.bukkit.Location location, org.bukkit.Particle particle, int count, T data);

    /**
     * Client crack overlay; use a distinct source ID per animation and progress zero to clear it.
     */
    void showBlockDamage(org.bukkit.Location location, float progress, int sourceId);

    /** Plays a native sounds.json event key without requiring a generated SoundAsset. */
    PlaybackResult playResourceSound(
            Player player,
            String key,
            SoundOrigin origin,
            SoundCategory category,
            float volume,
            float pitch);

    /** Native item definitions, indexed by namespace:path. */
    Collection<ModelAsset> models();

    Optional<ModelAsset> model(String id);

    Collection<IconAsset> icons();

    Collection<FontAsset> fonts();

    Collection<SoundAsset> sounds();

    Collection<SoundtrackAsset> soundtracks();

    Collection<HudAsset> huds();

    Optional<IconAsset> icon(String id);

    default Optional<String> iconUnicode(String id) {
        return icon(id).map(IconAsset::unicode);
    }

    Optional<FontAsset> font(String id);

    Optional<SoundAsset> sound(String id);

    Optional<SoundtrackAsset> soundtrack(String id);

    Optional<HudAsset> hud(String id);

    PlaybackResult playSound(
            Player player, String id, SoundCategory category, float volume, float pitch);

    PlaybackResult playSound(
            Player player,
            String id,
            SoundOrigin origin,
            SoundCategory category,
            float volume,
            float pitch);

    BroadcastPlaybackResult broadcastSound(
            String id, SoundOrigin origin, SoundCategory category, float volume, float pitch);

    boolean stopSound(Player player, String id);

    boolean stopSound(Player player, String id, SoundCategory category);

    void stopAllSounds(Player player);

    MusicSession startSoundtrack(
            String id, Collection<? extends Player> players, SessionSettings settings);

    Optional<MusicSession> session(UUID id);

    Collection<MusicSession> sessions();

    HudDisplayResult showHud(Player player, String templateId, HudRequest request);

    Optional<HudSession> hudSession(UUID sessionId);

    Optional<HudSession> hudSession(Player player, String slot);

    Collection<HudSession> hudSessions();

    boolean hideHud(Player player, String slot);
}
