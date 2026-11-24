package nu.miguel.assetChannel.api;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.*;

public interface AssetChannelApi {
    default Collection<RigAsset> rigs() {
        return List.of();
    }

    default Optional<RigAsset> rig(String id) {
        return Optional.empty();
    }

    default UUID packRevision() {
        return new UUID(0, 0);
    }

    default AssetPreflightReport preflight() {
        return new AssetPreflightReport(packRevision(), false, 0, 0, List.of());
    }

    default Collection<TimelineAudioAsset> timelineAudio() {
        return List.of();
    }

    default Optional<TimelineAudioAsset> timelineAudio(String id) {
        return Optional.empty();
    }

    default AudioLease leaseAudio(Player player, String id) {
        throw new UnsupportedOperationException();
    }

    default EffectHandle showPostEffect(Player player, String key) {
        throw new UnsupportedOperationException();
    }

    default TitleLease reserveTitle(Player player) {
        throw new UnsupportedOperationException();
    }

    default ActionBarLease reserveActionBar(Player player) {
        throw new UnsupportedOperationException();
    }

    default Collection<CaptionFontAsset> captionFonts() {
        return List.of();
    }

    default Optional<CaptionFontAsset> captionFont(String id) {
        return Optional.empty();
    }

    default Collection<ScreenFontAsset> screenFonts() {
        return List.of();
    }

    default Optional<ScreenFontAsset> screenFont(String id) {
        return Optional.empty();
    }

    default Collection<EffectCurveAsset> effectCurves() {
        return List.of();
    }

    default Optional<EffectCurveAsset> effectCurve(String id) {
        return Optional.empty();
    }

    /** Acquires an imported clock-driven zoom asset; one lease per player. */
    default ClientEffectLease leaseClientEffect(Player player, String curveId) {
        throw new UnsupportedOperationException();
    }

    default Collection<ResourceAsset> resources() {
        return List.of();
    }

    default Collection<ResourceAsset> resources(ResourceKind kind) {
        return resources().stream().filter(resource -> resource.kind() == kind).toList();
    }

    default Collection<BlockAsset> blocks() {
        return List.of();
    }

    default Optional<BlockAsset> block(String id) {
        return Optional.empty();
    }

    default boolean isPackReady(Player player) {
        return false;
    }

    default org.bukkit.entity.ItemDisplay spawnItemDisplay(
            org.bukkit.Location location, String model, DisplayOptions options) {
        throw new UnsupportedOperationException();
    }

    default org.bukkit.entity.BlockDisplay spawnBlockDisplay(
            org.bukkit.Location location,
            org.bukkit.block.data.BlockData data,
            DisplayOptions options) {
        throw new UnsupportedOperationException();
    }

    default boolean removeDisplay(UUID id) {
        throw new UnsupportedOperationException();
    }

    default Collection<org.bukkit.entity.Display> displays() {
        return List.of();
    }

    default PlacedAssetBlock placeBlock(org.bukkit.Location location, String assetId) {
        throw new UnsupportedOperationException();
    }

    default Optional<PlacedAssetBlock> placedBlock(org.bukkit.Location location) {
        return Optional.empty();
    }

    /** Removes without drops; effects are optional. Does not overwrite external block changes. */
    default boolean removeBlock(org.bukkit.Location location, boolean effects) {
        throw new UnsupportedOperationException();
    }

    default org.bukkit.inventory.ItemStack createBlockItem(String assetId) {
        throw new UnsupportedOperationException();
    }

    /** Shows a native-state asset to one pack-ready player without changing the real world. */
    default BlockOverlay showBlockOverlay(
            Player player, org.bukkit.Location location, String assetId) {
        throw new UnsupportedOperationException();
    }

    default Optional<BlockOverlay> blockOverlay(UUID id) {
        return Optional.empty();
    }

    default Collection<BlockOverlay> blockOverlays(Player player) {
        return List.of();
    }

    /** Typed vanilla particles, sent only to nearby players with the active pack. */
    default <T> void spawnParticle(
            org.bukkit.Location location, org.bukkit.Particle particle, int count, T data) {
        throw new UnsupportedOperationException();
    }

    /**
     * Client crack overlay; use a distinct source ID per animation and progress zero to clear it.
     */
    default void showBlockDamage(org.bukkit.Location location, float progress, int sourceId) {
        throw new UnsupportedOperationException();
    }

    /** Plays a native sounds.json event key without requiring a generated SoundAsset. */
    default PlaybackResult playResourceSound(
            Player player,
            String key,
            SoundOrigin origin,
            SoundCategory category,
            float volume,
            float pitch) {
        throw new UnsupportedOperationException();
    }

    /** Native item definitions, indexed by namespace:path. */
    default Collection<ModelAsset> models() {
        return List.of();
    }

    default Optional<ModelAsset> model(String id) {
        return Optional.empty();
    }

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
