package nu.miguel.assetChannel.api.internal;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class AssetChannelApiImpl implements AssetChannelApi {
    private Map<String, RigAsset> rigs = Map.of();
    private UUID revision = UUID.randomUUID();
    private Map<String, TimelineAudioAsset> timelineAudio = Map.of();
    private final AudioLeases audioLeases = new AudioLeases();
    private final PostEffects postEffects = new PostEffects();
    private final ClientEffectLeases clientEffects = new ClientEffectLeases();

    @Override
    public ClientEffectLease leaseClientEffect(Player player, String id) {
        requireMainThread();
        if (!isPackReady(player)) throw new IllegalStateException("Pack not ready");
        var curve =
                effectCurve(id)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Unknown effect asset: " + id));
        if (curve.client() == null)
            throw new IllegalArgumentException("Effect asset has no client animation: " + id);
        return clientEffects.acquire(player, curve.client());
    }

    @Override
    public EffectHandle showPostEffect(Player player, String id) {
        requireMainThread();
        if (!isPackReady(player)) throw new IllegalStateException("Pack not ready");
        Key key = Key.key(id);
        String path = "post_effect/" + key.value() + ".json";
        if (nativeResources.stream()
                .noneMatch(r ->
                        r.key().namespace().equals(key.namespace())
                                && r.key().value().equals(path)))
            throw new IllegalArgumentException("Unknown post effect: " + id);
        return postEffects.acquire(player, key);
    }

    @Override
    public Collection<TimelineAudioAsset> timelineAudio() {
        return timelineAudio.values();
    }

    @Override
    public Optional<TimelineAudioAsset> timelineAudio(String id) {
        return Optional.ofNullable(timelineAudio.get(normalize(id)));
    }

    @Override
    public AudioLease leaseAudio(Player player, String id) {
        requireMainThread();
        return audioLeases.acquire(
                player,
                timelineAudio(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Unknown timeline audio: " + id)),
                this);
    }

    @Override
    public Collection<RigAsset> rigs() {
        return rigs.values();
    }

    @Override
    public Optional<RigAsset> rig(String id) {
        return Optional.ofNullable(rigs.get(normalize(id)));
    }

    @Override
    public UUID packRevision() {
        return revision;
    }

    @Override
    public AssetPreflightReport preflight() {
        requireMainThread();
        return new AssetPreflightReport(
                revision,
                true,
                nativeResources.size(),
                (int) nativeResources.stream().filter(r -> r.kind() == ResourceKind.SHADER).count(),
                List.of());
    }

    private Map<String, BlockAsset> blockAssets = Map.of();
    private List<ResourceAsset> nativeResources = List.of();
    private final WorldAssetManager worldAssets = new WorldAssetManager(this);

    public WorldAssetManager worldAssets() {
        return worldAssets;
    }

    private final BlockOverlayManager blockOverlays = new BlockOverlayManager(this);

    public BlockOverlayManager blockOverlayManager() {
        return blockOverlays;
    }

    private Map<String, ModelAsset> itemModels = Map.of();
    private List<ModelAsset> publicItemModels = List.of();
    private Map<String, IconAsset> icons = Map.of();
    private Map<String, FontAsset> fonts = Map.of();
    private Map<String, CaptionFontAsset> captionFonts = Map.of();
    private Map<String, ScreenFontAsset> screenFonts = Map.of();

    @Override
    public Collection<ScreenFontAsset> screenFonts() {
        return screenFonts.values().stream().distinct().toList();
    }

    @Override
    public Optional<ScreenFontAsset> screenFont(String id) {
        return Optional.ofNullable(screenFonts.get(normalize(id)));
    }

    private Map<String, EffectCurveAsset> effectCurves = Map.of();

    @Override
    public Collection<EffectCurveAsset> effectCurves() {
        return effectCurves.values();
    }

    @Override
    public Optional<EffectCurveAsset> effectCurve(String id) {
        return Optional.ofNullable(effectCurves.get(normalize(id)));
    }

    @Override
    public Collection<CaptionFontAsset> captionFonts() {
        return captionFonts.values().stream().distinct().toList();
    }

    @Override
    public Optional<CaptionFontAsset> captionFont(String id) {
        return Optional.ofNullable(captionFonts.get(normalize(id)));
    }

    private Map<String, SoundAsset> sounds = Map.of();
    private Map<String, SoundtrackAsset> soundtracks = Map.of();
    private Map<String, Music> musicModels = Map.of();
    private Map<String, String> spatialSoundKeys = Map.of();
    private final Map<UUID, MusicSessionImpl> sessions = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<MusicSessionImpl>> playerSessions = new HashMap<>();
    private final Set<UUID> packReady = new HashSet<>();
    private BukkitTask musicTicker;
    private final HudManager hudManager = new HudManager(this);

    @Override
    public TitleLease reserveTitle(Player player) {
        requireMainThread();
        return hudManager.reserveTitle(player);
    }

    @Override
    public ActionBarLease reserveActionBar(Player player) {
        requireMainThread();
        return hudManager.reserveActionBar(player);
    }

    public void loadAssets(List<Asset> assets) {
        requireMainThread();
        clientEffects.clear();
        Map<String, RigAsset> nextRigs = new TreeMap<>();
        Map<String, TimelineAudioAsset> nextAudio = new TreeMap<>();
        for (Asset asset : assets)
            if (asset instanceof TimelineAudio audio)
                nextAudio.put(audio.getId(), audio.descriptor());
        for (Asset asset : assets)
            if (asset instanceof nu.miguel.assetChannel.pack.RigCompiler compiled)
                nextRigs.put(compiled.rig().id(), compiled.rig());
        blockOverlays.clearAll();
        Map<String, IconAsset> newIcons = new TreeMap<>();
        Map<String, ModelAsset> newItemModels = new TreeMap<>();
        Map<String, BlockAsset> newBlocks = new TreeMap<>();
        List<ResourceAsset> newResources = new ArrayList<>();
        Map<String, FontAsset> newFonts = new TreeMap<>();
        Map<String, CaptionFontAsset> newCaptionFonts = new TreeMap<>();
        Map<String, ScreenFontAsset> newScreenFonts = new TreeMap<>();
        Map<String, SoundAsset> newSounds = new TreeMap<>();
        Map<String, SoundtrackAsset> newTracks = new TreeMap<>();
        Map<String, Music> models = new HashMap<>();
        Map<String, String> newSpatialSoundKeys = new HashMap<>();
        for (Asset asset : assets) {
            if (asset instanceof nu.miguel.assetChannel.pack.ScreenFonts screens)
                screens.fonts()
                        .forEach((id, font) -> {
                            newScreenFonts.put(id, font);
                            newScreenFonts.put("assetchannel:" + id, font);
                            newScreenFonts.put("assetchannel:" + id.replace('.', '/'), font);
                        });
            if (asset instanceof nu.miguel.assetChannel.pack.CaptionFonts captions)
                captions.fonts()
                        .forEach((id, font) -> {
                            newCaptionFonts.put(id, font);
                            newCaptionFonts.put("assetchannel:" + id, font);
                            newCaptionFonts.put("assetchannel:" + id.replace('.', '/'), font);
                        });
            if (asset instanceof nu.miguel.assetChannel.pack.ImportedResources imported) {
                newResources.addAll(imported.resources());
                imported.models().forEach(model -> newItemModels.put(model.id(), model));
                imported.fonts().forEach(font -> newFonts.put(font.id(), font));
            }
            if (asset instanceof BlockDefinition block)
                newBlocks.put(block.getId(), block.descriptor());
            if (asset instanceof Icon icon)
                newIcons.put(
                        icon.getId(),
                        new IconAsset(
                                icon.getId(),
                                Key.key(Icon.FONT_KEY),
                                icon.getCodePoint(),
                                icon.getHeight(),
                                icon.getAscent(),
                                icon.getWidth()));
            else if (asset instanceof Font font)
                newFonts.put(font.getId(), new FontAsset(font.getId(), Key.key(font.getKey())));
            else if (asset instanceof Sound sound) {
                newSounds.put(
                        sound.getId(),
                        new SoundAsset(
                                sound.getId(), Key.key(sound.getKey()), sound.getDuration()));
                newSpatialSoundKeys.put(sound.getId(), sound.getSpatialKey());
            } else if (asset instanceof Music music) {
                Map<String, MusicStageAsset> stages = new LinkedHashMap<>();
                music.getStages()
                        .forEach((stageId, stage) ->
                                stages.put(
                                        stageId,
                                        new MusicStageAsset(
                                                stageId,
                                                stage.duration(),
                                                stage.segments().size(),
                                                stage.autoSplit())));
                newTracks.put(
                        music.getId(),
                        new SoundtrackAsset(
                                music.getId(),
                                music.getDuration(),
                                music.getSegments().size(),
                                music.isAutoSplit(),
                                music.getDefaultStage(),
                                stages,
                                music.isMuteVanillaMusic(),
                                music.isMuteOtherMusic()));
                models.put(music.getId(), music);
            }
        }
        icons = Collections.unmodifiableMap(newIcons);
        itemModels = Collections.unmodifiableMap(newItemModels);
        publicItemModels =
                newItemModels.values().stream().filter(model -> !model.internal()).toList();
        blockAssets = Collections.unmodifiableMap(newBlocks);
        nativeResources = List.copyOf(newResources);
        fonts = Collections.unmodifiableMap(newFonts);
        captionFonts = Map.copyOf(newCaptionFonts);
        screenFonts = Map.copyOf(newScreenFonts);
        effectCurves =
                assets.stream()
                        .filter(EffectCurve.class::isInstance)
                        .map(EffectCurve.class::cast)
                        .map(EffectCurve::descriptor)
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        EffectCurveAsset::id,
                                        java.util.function.Function.identity()));
        sounds = Collections.unmodifiableMap(newSounds);
        soundtracks = Collections.unmodifiableMap(newTracks);
        musicModels = Map.copyOf(models);
        spatialSoundKeys = Map.copyOf(newSpatialSoundKeys);
        hudManager.load(
                assets.stream().filter(Hud.class::isInstance).map(Hud.class::cast).toList(), icons);
        rigs = Map.copyOf(nextRigs);
        audioLeases.clear();
        timelineAudio = Map.copyOf(nextAudio);
        UUID previousRevision = revision;
        revision = UUID.randomUUID();
        if (Main.plugin != null && Main.plugin.isEnabled())
            Bukkit.getPluginManager().callEvent(new PackRevisionEvent(previousRevision, revision));
    }

    public void setPackReady(Player player, boolean ready) {
        requireMainThread();
        boolean changed = packReady.contains(player.getUniqueId()) != ready;
        if (!ready) {
            clientEffects.quit(player.getUniqueId());
            postEffects.quit(player.getUniqueId());
        }
        if (!ready) blockOverlays.clear(player, true);
        if (ready) packReady.add(player.getUniqueId());
        else packReady.remove(player.getUniqueId());
        hudManager.packReadyChanged(player.getUniqueId(), ready);
        worldAssets.playerReadiness(player, ready);
        if (changed && Main.plugin != null && Main.plugin.isEnabled())
            Bukkit.getPluginManager().callEvent(new PackReadinessEvent(player, revision, ready));
    }

    public boolean isPackReady(UUID id) {
        return packReady.contains(id);
    }

    public void onQuit(Player player) {
        requireMainThread();
        audioLeases.quit(player.getUniqueId());
        clientEffects.quit(player.getUniqueId());
        postEffects.quit(player.getUniqueId());
        blockOverlays.clear(player, false);
        packReady.remove(player.getUniqueId());
        Set<MusicSessionImpl> playerMusic = playerSessions.get(player.getUniqueId());
        if (playerMusic != null)
            for (MusicSessionImpl session : List.copyOf(playerMusic)) session.disconnect(player);
        hudManager.onQuit(player.getUniqueId());
    }

    public void stopSessions() {
        requireMainThread();
        audioLeases.clear();
        clientEffects.clear();
        postEffects.clear();
        blockOverlays.clearAll();
        for (MusicSessionImpl session : List.copyOf(sessions.values())) session.stop();
        hudManager.clear();
    }

    void ensureMusicTicker() {
        if (musicTicker != null) return;
        musicTicker =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                Main.plugin,
                                () -> {
                                    for (MusicSessionImpl session : List.copyOf(sessions.values()))
                                        session.tick();
                                },
                                0L,
                                1L);
    }

    void claim(Player player, MusicSessionImpl session) {
        playerSessions
                .computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashSet<>())
                .add(session);
    }

    void release(UUID player, MusicSessionImpl session) {
        LinkedHashSet<MusicSessionImpl> playerMusic = playerSessions.get(player);
        if (playerMusic == null) return;
        playerMusic.remove(session);
        if (playerMusic.isEmpty()) playerSessions.remove(player);
    }

    boolean isMutedByOtherMusic(UUID player, MusicSessionImpl session) {
        Set<MusicSessionImpl> playerMusic = playerSessions.get(player);
        if (playerMusic == null) return false;
        MusicSessionImpl mutingSession = null;
        for (MusicSessionImpl candidate : playerMusic)
            if (candidate.active() && candidate.mutesOtherMusic() && isPackReady(player))
                mutingSession = candidate;
        return mutingSession != null && mutingSession != session;
    }

    void closed(MusicSessionImpl session) {
        sessions.remove(session.id(), session);
        if (sessions.isEmpty() && musicTicker != null) {
            musicTicker.cancel();
            musicTicker = null;
        }
    }

    @Override
    public Collection<IconAsset> icons() {
        return icons.values();
    }

    @Override
    public Collection<BlockAsset> blocks() {
        return blockAssets.values();
    }

    @Override
    public Optional<BlockAsset> block(String id) {
        return Optional.ofNullable(blockAssets.get(normalize(id)));
    }

    @Override
    public List<ResourceAsset> resources() {
        return nativeResources;
    }

    @Override
    public boolean isPackReady(Player player) {
        requireMainThread();
        return isPackReady(player.getUniqueId());
    }

    @Override
    public org.bukkit.entity.ItemDisplay spawnItemDisplay(
            Location at, String model, DisplayOptions options) {
        return worldAssets.spawnItemDisplay(at, model, options);
    }

    @Override
    public org.bukkit.entity.BlockDisplay spawnBlockDisplay(
            Location at, org.bukkit.block.data.BlockData data, DisplayOptions options) {
        return worldAssets.spawnBlockDisplay(at, data, options);
    }

    @Override
    public boolean removeDisplay(UUID id) {
        return worldAssets.removeDisplay(id);
    }

    @Override
    public Collection<org.bukkit.entity.Display> displays() {
        return worldAssets.displays();
    }

    @Override
    public PlacedAssetBlock placeBlock(Location at, String id) {
        return worldAssets.placeBlock(at, id);
    }

    @Override
    public Optional<PlacedAssetBlock> placedBlock(Location at) {
        return worldAssets.placedBlock(at);
    }

    @Override
    public boolean removeBlock(Location at, boolean effects) {
        return worldAssets.removeBlock(at, effects);
    }

    @Override
    public org.bukkit.inventory.ItemStack createBlockItem(String id) {
        return worldAssets.createBlockItem(id);
    }

    @Override
    public BlockOverlay showBlockOverlay(Player player, Location at, String id) {
        return blockOverlays.show(player, at, id);
    }

    @Override
    public Optional<BlockOverlay> blockOverlay(UUID id) {
        return blockOverlays.get(id);
    }

    @Override
    public Collection<BlockOverlay> blockOverlays(Player player) {
        return blockOverlays.all(player);
    }

    @Override
    public <T> void spawnParticle(Location at, org.bukkit.Particle particle, int count, T data) {
        worldAssets.spawnParticle(at, particle, count, data);
    }

    @Override
    public void showBlockDamage(Location at, float progress, int sourceId) {
        worldAssets.showBlockDamage(at, progress, sourceId);
    }

    @Override
    public PlaybackResult playResourceSound(
            Player player,
            String key,
            SoundOrigin origin,
            SoundCategory category,
            float volume,
            float pitch) {
        requireMainThread();
        Objects.requireNonNull(player);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(category);
        if (!Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0)
            throw new IllegalArgumentException("Invalid sound volume/pitch");
        return playKey(player, Key.key(key).asString(), category, origin, volume, pitch);
    }

    @Override
    public Collection<ModelAsset> models() {
        return publicItemModels;
    }

    @Override
    public Optional<ModelAsset> model(String id) {
        return Optional.ofNullable(itemModels.get(normalize(id)));
    }

    @Override
    public Collection<FontAsset> fonts() {
        return fonts.values();
    }

    @Override
    public Collection<SoundAsset> sounds() {
        return sounds.values();
    }

    @Override
    public Collection<SoundtrackAsset> soundtracks() {
        return soundtracks.values();
    }

    @Override
    public Collection<HudAsset> huds() {
        return hudManager.huds();
    }

    @Override
    public Optional<IconAsset> icon(String id) {
        return Optional.ofNullable(icons.get(normalize(id)));
    }

    @Override
    public Optional<FontAsset> font(String id) {
        return Optional.ofNullable(fonts.get(normalize(id)));
    }

    @Override
    public Optional<SoundAsset> sound(String id) {
        return Optional.ofNullable(sounds.get(normalize(id)));
    }

    @Override
    public Optional<SoundtrackAsset> soundtrack(String id) {
        return Optional.ofNullable(soundtracks.get(normalize(id)));
    }

    @Override
    public Optional<HudAsset> hud(String id) {
        return hudManager.hud(id);
    }

    @Override
    public PlaybackResult playSound(
            Player player, String id, SoundCategory category, float volume, float pitch) {
        return playSound(player, id, SoundOrigin.listenerRelative(), category, volume, pitch);
    }

    @Override
    public PlaybackResult playSound(
            Player player,
            String id,
            SoundOrigin origin,
            SoundCategory category,
            float volume,
            float pitch) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(category, "category");
        SoundAsset asset = sounds.get(normalize(id));
        if (asset == null) return PlaybackResult.UNKNOWN_ASSET;
        String key =
                origin instanceof SoundOrigin.ListenerRelative
                        ? asset.key().asString()
                        : spatialSoundKeys.getOrDefault(asset.id(), asset.key().asString());
        return playKey(player, key, category, origin, volume, pitch);
    }

    @Override
    public BroadcastPlaybackResult broadcastSound(
            String id, SoundOrigin origin, SoundCategory category, float volume, float pitch) {
        requireMainThread();
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(category, "category");
        SoundAsset asset = sounds.get(normalize(id));
        if (asset == null)
            return new BroadcastPlaybackResult(PlaybackResult.UNKNOWN_ASSET, Set.of(), Set.of());
        SoundOrigin refreshed = refreshOrigin(origin);
        double radius = 16.0 * Math.max(1.0f, volume);
        double radiusSquared = radius * radius;
        Set<UUID> played = new LinkedHashSet<>();
        Set<UUID> notReady = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            SoundOrigin selected = selectOrigin(refreshed, player.getLocation());
            Location source = sourceLocation(selected);
            if (source == null) continue;
            if (!player.getWorld().equals(source.getWorld())
                    || player.getLocation().distanceSquared(source) > radiusSquared) continue;
            String key =
                    selected instanceof SoundOrigin.ListenerRelative
                            ? asset.key().asString()
                            : spatialSoundKeys.getOrDefault(asset.id(), asset.key().asString());
            PlaybackResult result = playKey(player, key, category, selected, volume, pitch);
            if (result == PlaybackResult.PLAYED) played.add(player.getUniqueId());
            else if (result == PlaybackResult.PACK_NOT_READY) notReady.add(player.getUniqueId());
        }
        return new BroadcastPlaybackResult(PlaybackResult.PLAYED, played, notReady);
    }

    PlaybackResult playKey(
            Player player,
            String key,
            SoundCategory category,
            SoundOrigin origin,
            float volume,
            float pitch) {
        if (!packReady.contains(player.getUniqueId())) return PlaybackResult.PACK_NOT_READY;
        SoundOrigin refreshed = refreshOrigin(origin);
        refreshed = selectOrigin(refreshed, player.getLocation());
        if (refreshed == null) return PlaybackResult.SOURCE_UNAVAILABLE;
        if (refreshed instanceof SoundOrigin.ListenerRelative) {
            player.playSound(player, key, category, volume, pitch);
            return PlaybackResult.PLAYED;
        }
        if (refreshed instanceof SoundOrigin.EntityOrigin entityOrigin) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityOrigin.entityId());
            if (entity != null && entity.isValid()) {
                if (!player.getWorld().equals(entity.getWorld()))
                    return PlaybackResult.SOURCE_UNAVAILABLE;
                player.playSound(entity, key, category, volume, pitch);
                return PlaybackResult.PLAYED;
            }
            refreshed = entityOrigin.fallback();
        }
        SoundOrigin.Fixed fixed = (SoundOrigin.Fixed) refreshed;
        if (!player.getWorld().equals(fixed.world())) return PlaybackResult.SOURCE_UNAVAILABLE;
        player.playSound(fixed.location(), key, category, volume, pitch);
        return PlaybackResult.PLAYED;
    }

    PlaybackResult playKeyAtAllSpeakers(
            Player player,
            String key,
            SoundCategory category,
            SoundOrigin origin,
            float volume,
            float pitch) {
        if (!(origin instanceof SoundOrigin.Speakers speakers))
            return playKey(player, key, category, origin, volume, pitch);
        if (!packReady.contains(player.getUniqueId())) return PlaybackResult.PACK_NOT_READY;

        SoundOrigin refreshed = refreshOrigin(speakers);
        List<SoundOrigin> available =
                speakersInWorld((SoundOrigin.Speakers) refreshed, player.getWorld());
        if (available.isEmpty()) return PlaybackResult.SOURCE_UNAVAILABLE;

        for (SoundOrigin speaker : available)
            playKey(player, key, category, speaker, volume, pitch);
        return PlaybackResult.PLAYED;
    }

    List<SoundOrigin> speakersInWorld(SoundOrigin.Speakers speakers, org.bukkit.World world) {
        List<SoundOrigin> available = new ArrayList<>();
        for (SoundOrigin speaker : speakers.origins()) {
            Location source = sourceLocation(speaker);
            if (source == null || !source.getWorld().equals(world)) continue;
            available.add(
                    speaker instanceof SoundOrigin.EntityOrigin
                            ? SoundOrigin.fixed(source)
                            : speaker);
        }
        return List.copyOf(available);
    }

    SoundOrigin refreshOrigin(SoundOrigin origin) {
        if (origin instanceof SoundOrigin.Speakers speakers) {
            return SoundOrigin.speakers(
                    speakers.origins().stream().map(this::refreshOrigin).toList());
        }
        if (origin instanceof SoundOrigin.EntityOrigin entityOrigin) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityOrigin.entityId());
            if (entity != null && entity.isValid())
                return new SoundOrigin.EntityOrigin(
                        entityOrigin.entityId(), SoundOrigin.fixed(entity.getLocation()));
        }
        return origin;
    }

    SoundOrigin selectOrigin(SoundOrigin origin, Location listener) {
        if (!(origin instanceof SoundOrigin.Speakers speakers)) return origin;
        SoundOrigin closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (SoundOrigin candidate : speakers.origins()) {
            Location source = sourceLocation(candidate);
            if (source == null || !source.getWorld().equals(listener.getWorld())) continue;
            double distance = listener.distanceSquared(source);
            if (distance < closestDistance) {
                closestDistance = distance;
                // Marker entities are server-side positioning helpers and are not
                // guaranteed to be tracked by the client. Grouped entity speakers
                // therefore emit from their current coordinates instead of by ID.
                closest =
                        candidate instanceof SoundOrigin.EntityOrigin
                                ? SoundOrigin.fixed(source)
                                : candidate;
            }
        }
        return closest;
    }

    private Location sourceLocation(SoundOrigin origin) {
        if (origin instanceof SoundOrigin.Fixed fixed) return fixed.location();
        if (origin instanceof SoundOrigin.EntityOrigin entityOrigin) {
            org.bukkit.entity.Entity entity =
                    Bukkit.getServer() == null ? null : Bukkit.getEntity(entityOrigin.entityId());
            return entity != null && entity.isValid()
                    ? entity.getLocation()
                    : entityOrigin.fallback().location();
        }
        return null;
    }

    @Override
    public boolean stopSound(Player player, String id) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        SoundAsset asset = sounds.get(normalize(id));
        if (asset == null) return false;
        player.stopSound(asset.key().asString());
        player.stopSound(spatialSoundKeys.getOrDefault(asset.id(), asset.key().asString()));
        return true;
    }

    @Override
    public boolean stopSound(Player player, String id, SoundCategory category) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(category, "category");
        SoundAsset asset = sounds.get(normalize(id));
        if (asset == null) return false;
        player.stopSound(asset.key().asString(), category);
        player.stopSound(
                spatialSoundKeys.getOrDefault(asset.id(), asset.key().asString()), category);
        return true;
    }

    @Override
    public void stopAllSounds(Player player) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        for (SoundAsset sound : sounds.values()) {
            player.stopSound(sound.key().asString());
            player.stopSound(spatialSoundKeys.getOrDefault(sound.id(), sound.key().asString()));
        }
    }

    @Override
    public MusicSession startSoundtrack(
            String id, Collection<? extends Player> players, SessionSettings settings) {
        requireMainThread();
        Objects.requireNonNull(settings, "settings");
        String normalized = normalize(id);
        Music model = musicModels.get(normalized);
        if (model == null) throw new IllegalArgumentException("Unknown soundtrack: " + id);
        if (players.isEmpty())
            throw new IllegalArgumentException("A session requires at least one player");
        MusicSessionImpl session =
                new MusicSessionImpl(this, soundtracks.get(normalized), model, settings);
        sessions.put(session.id(), session);
        for (Player player : players) session.addInitialPlayer(player);
        session.start();
        return session;
    }

    @Override
    public Optional<MusicSession> session(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public Collection<MusicSession> sessions() {
        return List.copyOf(sessions.values());
    }

    @Override
    public HudDisplayResult showHud(Player player, String templateId, HudRequest request) {
        return hudManager.show(player, templateId, request);
    }

    @Override
    public Optional<HudSession> hudSession(UUID id) {
        return hudManager.session(id);
    }

    @Override
    public Optional<HudSession> hudSession(Player player, String slot) {
        requireMainThread();
        return hudManager.session(player, slot);
    }

    @Override
    public Collection<HudSession> hudSessions() {
        return hudManager.sessions();
    }

    @Override
    public boolean hideHud(Player player, String slot) {
        return hudManager.hide(player, slot);
    }

    private static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    static void requireMainThread() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException(
                    "AssetChannel API must be called from the server thread");
    }
}
