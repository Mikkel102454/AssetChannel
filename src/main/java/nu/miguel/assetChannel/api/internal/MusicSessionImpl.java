package nu.miguel.assetChannel.api.internal;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.Music;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.TimeUnit;

final class MusicSessionImpl implements MusicSession {
    static final SoundCategory SOUNDTRACK_CATEGORY = SoundCategory.RECORDS;
    static final SoundCategory VANILLA_MUSIC_CATEGORY = SoundCategory.MUSIC;
    private final UUID id = UUID.randomUUID();
    private final AssetChannelApiImpl api;
    private final SoundtrackAsset descriptor;
    private final Music model;
    private final SessionSettings settings;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private final long defaultHandoffLeadMilliseconds;
    private final long vanillaMuteRefreshNanos;
    private SoundOrigin origin;
    private String selectedStage;
    private String sessionTimelineStage;
    private long sessionStageStartedAt;
    private PendingTransition pending;
    private boolean active = true;

    MusicSessionImpl(
            AssetChannelApiImpl api,
            SoundtrackAsset descriptor,
            Music model,
            SessionSettings settings) {
        this.api = api;
        this.descriptor = descriptor;
        this.model = model;
        this.settings = settings;
        this.origin = settings.origin();
        String requested = settings.initialStage();
        this.selectedStage =
                requested == null || requested.isBlank()
                        ? descriptor.defaultStage()
                        : requested.trim().toLowerCase(Locale.ROOT);
        if (!descriptor.stages().containsKey(selectedStage))
            throw new IllegalArgumentException(
                    "Unknown stage '" + requested + "' for soundtrack '" + descriptor.id() + "'");
        for (Music.Stage stage : model.getStages().values()) {
            if (stage.segments().isEmpty()
                    || stage.duration().isZero()
                    || stage.duration().isNegative())
                throw new IllegalArgumentException(
                        "Stage '" + stage.id() + "' has no playable segments");
        }
        this.defaultHandoffLeadMilliseconds =
                Math.max(
                        0,
                        Math.min(
                                250,
                                Main.config.getLong(
                                        "music.playback.handoff-lead-milliseconds", 75)));
        this.vanillaMuteRefreshNanos =
                TimeUnit.SECONDS.toNanos(
                        Math.max(
                                5,
                                Main.config.getLong(
                                        "music.playback.vanilla-mute-refresh-seconds", 30)));
        this.sessionTimelineStage = selectedStage;
        this.sessionStageStartedAt = System.nanoTime();
    }

    void start() {
        api.ensureMusicTicker();
    }

    void addInitialPlayer(Player player) {
        add(player, true);
    }

    @Override
    public boolean addPlayer(Player player) {
        AssetChannelApiImpl.requireMainThread();
        return add(player, false);
    }

    private boolean add(Player player, boolean initial) {
        if (!active || members.containsKey(player.getUniqueId())) return false;
        long now = System.nanoTime();
        String joiningStage = pending == null ? selectedStage : sessionTimelineStage;
        Member member = new Member(player.getUniqueId(), joiningStage);
        if (settings.synchronizedPlayback()) {
            member.stageStartedAt =
                    sessionTimelineStage.equals(joiningStage) ? sessionStageStartedAt : now;
            member.eligibleAt =
                    initial ? now : nextBoundary(member.stage, member.stageStartedAt, now);
            if (!initial
                    && pending == null
                    && !settings.loop()
                    && member.eligibleAt >= member.stageStartedAt + durationNanos(member.stage))
                return false;
        } else {
            member.stageStartedAt = now;
            member.eligibleAt = now;
        }
        if (pending != null && pending.timing == StageTransitionTiming.NEXT_PLAYER_BOUNDARY)
            member.transitionAt = nextBoundary(member.stage, member.stageStartedAt, now);
        member.waitingForPack = !api.isPackReady(member.id);
        members.put(member.id, member);
        api.claim(player, this);
        return true;
    }

    void disconnect(Player player) {
        if (!settings.retainOnDisconnect()) {
            remove(player.getUniqueId(), true);
        } else {
            Member member = members.get(player.getUniqueId());
            if (member != null) {
                stopFor(player);
                member.waitingForPack = true;
                member.retainedReconnect = true;
                member.lastToken = Long.MIN_VALUE;
            }
        }
    }

    void tick() {
        if (!active) return;
        long now = System.nanoTime();
        origin = api.refreshOrigin(origin);
        if (pending != null
                && pending.timing == StageTransitionTiming.NEXT_SESSION_BOUNDARY
                && now >= pending.sessionAt) {
            String target = pending.target;
            long boundary = pending.sessionAt;
            pending = null;
            applyAll(target, boundary);
        }

        for (Member member : List.copyOf(members.values())) {
            if (pending != null
                    && pending.timing == StageTransitionTiming.NEXT_PLAYER_BOUNDARY
                    && member.transitionAt != Long.MAX_VALUE
                    && now >= member.transitionAt) {
                applyMember(member, pending.target, member.transitionAt);
            }

            Player player = Bukkit.getPlayer(member.id);
            if (player == null || !player.isOnline()) continue;
            if (!api.isPackReady(member.id)) {
                member.waitingForPack = true;
                continue;
            }
            if (member.waitingForPack) {
                member.waitingForPack = false;
                member.lastToken = Long.MIN_VALUE;
                if (settings.synchronizedPlayback())
                    member.eligibleAt = nextBoundary(member.stage, member.stageStartedAt, now);
                else if (member.retainedReconnect)
                    member.eligibleAt = nextBoundary(member.stage, member.stageStartedAt, now);
                else {
                    member.stageStartedAt = now;
                    member.eligibleAt = now;
                }
                member.retainedReconnect = false;
            }
            if (now < member.eligibleAt) continue;
            long elapsed = now - member.stageStartedAt;
            Position logicalPosition = position(member.stage, elapsed);
            if (logicalPosition.ended) {
                remove(member.id, true);
                continue;
            }
            boolean mutedByOtherMusic = api.isMutedByOtherMusic(member.id, this);
            if (mutedByOtherMusic) {
                if (!member.mutedByOtherMusic) {
                    stopFor(player, member);
                    member.lastToken = Long.MIN_VALUE;
                    member.mutedByOtherMusic = true;
                }
                continue;
            }
            if (member.mutedByOtherMusic) {
                member.mutedByOtherMusic = false;
                member.lastToken = Long.MIN_VALUE;
            }
            Position position = playbackPosition(member, elapsed, now, logicalPosition);
            if (model.isMuteVanillaMusic()
                    && (member.lastVanillaMuteAt == Long.MIN_VALUE
                            || now - member.lastVanillaMuteAt >= vanillaMuteRefreshNanos)) {
                stopVanillaMusic(player);
                member.lastVanillaMuteAt = now;
            }
            if (member.lastToken != position.token) {
                Music.Segment segment = stage(member.stage).segments().get(position.index);
                String key = segment.key(!(origin instanceof SoundOrigin.ListenerRelative));
                api.playKeyAtAllSpeakers(
                        player, key, SOUNDTRACK_CATEGORY, origin, settings.volume(), 1.0f);
                member.playingKeys.add(key);
                while (member.playingKeys.size() > 2) member.playingKeys.removeFirst();
                member.lastToken = position.token;
            }
        }

        if (pending != null
                && pending.timing == StageTransitionTiming.NEXT_PLAYER_BOUNDARY
                && members.values().stream().allMatch(m -> m.stage.equals(pending.target))) {
            sessionTimelineStage = pending.target;
            sessionStageStartedAt =
                    members.values().stream()
                            .mapToLong(member -> member.stageStartedAt)
                            .min()
                            .orElse(now);
            pending = null;
        }
        if (members.isEmpty()) stop();
    }

    private Position playbackPosition(Member member, long elapsed, long now, Position logical) {
        long lead = handoffLeadNanos(member.stage);
        long playbackElapsed =
                SegmentHandoff.playbackElapsed(
                        elapsed, lead, transitionDueWithinLead(member, now, lead));
        if (playbackElapsed == elapsed) return logical;
        Position anticipated = position(member.stage, playbackElapsed);
        return anticipated.ended ? logical : anticipated;
    }

    private boolean transitionDueWithinLead(Member member, long now, long lead) {
        if (pending == null) return false;
        long cutoff = now > Long.MAX_VALUE - lead ? Long.MAX_VALUE : now + lead;
        if (pending.timing == StageTransitionTiming.NEXT_SESSION_BOUNDARY)
            return pending.sessionAt <= cutoff;
        return member.transitionAt <= cutoff;
    }

    private Position position(String stageId, long elapsed) {
        List<Music.Segment> segments = stage(stageId).segments();
        long totalNanos = durationNanos(stageId);
        if (elapsed < 0) return new Position(0, 0, false);
        if (!settings.loop() && elapsed >= totalNanos) return new Position(0, 0, true);
        long cycle = elapsed / totalNanos;
        long within = elapsed % totalNanos;
        long boundary = 0;
        for (int i = 0; i < segments.size(); i++) {
            boundary += segments.get(i).duration().toNanos();
            if (within < boundary) return new Position(i, cycle * segments.size() + i, false);
        }
        return new Position(
                segments.size() - 1, cycle * segments.size() + segments.size() - 1L, false);
    }

    private long nextBoundary(String stageId, long base, long now) {
        long totalNanos = durationNanos(stageId);
        long elapsed = Math.max(0, now - base);
        if (!settings.loop() && elapsed >= totalNanos) return base + totalNanos;
        long cycleStart = base + (elapsed / totalNanos) * totalNanos;
        long within = elapsed % totalNanos;
        long boundary = 0;
        for (Music.Segment segment : stage(stageId).segments()) {
            boundary += segment.duration().toNanos();
            if (boundary > within) return cycleStart + boundary;
        }
        return cycleStart + totalNanos;
    }

    @Override
    public StageChangeResult setStage(
            String requestedStage, StageTransitionSettings transitionSettings) {
        AssetChannelApiImpl.requireMainThread();
        Objects.requireNonNull(transitionSettings, "settings");
        if (!active) return StageChangeResult.INACTIVE;
        if (requestedStage == null) return StageChangeResult.UNKNOWN_STAGE;
        String target = requestedStage.trim().toLowerCase(Locale.ROOT);
        if (!model.getStages().containsKey(target)) return StageChangeResult.UNKNOWN_STAGE;
        if (target.equals(selectedStage)) return StageChangeResult.ALREADY_ACTIVE;

        long now = System.nanoTime();
        selectedStage = target;
        for (Member member : members.values()) member.transitionAt = Long.MAX_VALUE;
        switch (transitionSettings.timing()) {
            case IMMEDIATE -> {
                pending = null;
                applyAll(target, now);
                return StageChangeResult.APPLIED;
            }
            case NEXT_SESSION_BOUNDARY -> {
                long at = nextBoundary(sessionTimelineStage, sessionStageStartedAt, now);
                pending =
                        new PendingTransition(
                                target, StageTransitionTiming.NEXT_SESSION_BOUNDARY, at);
                moveOfflineMembers(target, now);
                return StageChangeResult.SCHEDULED;
            }
            case NEXT_PLAYER_BOUNDARY -> {
                pending =
                        new PendingTransition(
                                target, StageTransitionTiming.NEXT_PLAYER_BOUNDARY, Long.MAX_VALUE);
                for (Member member : members.values()) {
                    Player player = Bukkit.getPlayer(member.id);
                    if (player == null || !player.isOnline()) applyMember(member, target, now);
                    else
                        member.transitionAt =
                                nextBoundary(member.stage, member.stageStartedAt, now);
                }
                return StageChangeResult.SCHEDULED;
            }
        }
        throw new IllegalStateException("Unhandled transition timing");
    }

    private void moveOfflineMembers(String target, long now) {
        for (Member member : members.values()) {
            Player player = Bukkit.getPlayer(member.id);
            if (player == null || !player.isOnline()) applyMember(member, target, now);
        }
    }

    private void applyAll(String target, long at) {
        sessionTimelineStage = target;
        sessionStageStartedAt = at;
        for (Member member : members.values()) applyMember(member, target, at);
    }

    private void applyMember(Member member, String target, long at) {
        Player player = Bukkit.getPlayer(member.id);
        if (player != null && player.isOnline()) stopFor(player);
        member.stage = target;
        member.stageStartedAt = at;
        member.eligibleAt = at;
        member.transitionAt = Long.MAX_VALUE;
        member.lastToken = Long.MIN_VALUE;
    }

    @Override
    public boolean removePlayer(Player player) {
        AssetChannelApiImpl.requireMainThread();
        return remove(player.getUniqueId(), true);
    }

    private boolean remove(UUID id, boolean stopSound) {
        Member removed = members.remove(id);
        if (removed == null) return false;
        Player player = Bukkit.getPlayer(id);
        if (stopSound && player != null) stopFor(player, removed);
        api.release(id, this);
        return true;
    }

    private void stopFor(Player player) {
        Member member = members.get(player.getUniqueId());
        if (member != null) stopFor(player, member);
    }

    private void stopFor(Player player, Member member) {
        for (String key : member.playingKeys) player.stopSound(key, SOUNDTRACK_CATEGORY);
        member.playingKeys.clear();
    }

    private static void stopVanillaMusic(Player player) {
        player.stopSound(VANILLA_MUSIC_CATEGORY);
    }

    @Override
    public void stop() {
        AssetChannelApiImpl.requireMainThread();
        if (!active) return;
        active = false;
        pending = null;
        for (UUID member : List.copyOf(members.keySet())) remove(member, true);
        api.closed(this);
    }

    private Music.Stage stage(String id) {
        return Objects.requireNonNull(model.getStage(id));
    }

    private long handoffLeadNanos(String stageId) {
        Music.Stage stage = stage(stageId);
        long milliseconds =
                stage.handoffLeadMilliseconds() >= 0
                        ? stage.handoffLeadMilliseconds()
                        : defaultHandoffLeadMilliseconds;
        long shortest =
                stage.segments().stream()
                        .mapToLong(segment -> segment.duration().toNanos())
                        .min()
                        .orElse(0);
        return SegmentHandoff.clampLead(TimeUnit.MILLISECONDS.toNanos(milliseconds), shortest);
    }

    private long durationNanos(String id) {
        return stage(id).duration().toNanos();
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public SoundtrackAsset soundtrack() {
        return descriptor;
    }

    @Override
    public SessionSettings settings() {
        return settings;
    }

    @Override
    public Set<UUID> members() {
        return Set.copyOf(members.keySet());
    }

    @Override
    public String stage() {
        return selectedStage;
    }

    @Override
    public Map<UUID, String> memberStages() {
        Map<UUID, String> result = new LinkedHashMap<>();
        members.forEach((memberId, member) -> result.put(memberId, member.stage));
        return Collections.unmodifiableMap(result);
    }

    @Override
    public boolean transitioning() {
        return pending != null
                || members.values().stream()
                        .anyMatch(member -> !member.stage.equals(selectedStage));
    }

    @Override
    public SoundOrigin origin() {
        return origin;
    }

    @Override
    public boolean setOrigin(SoundOrigin newOrigin) {
        AssetChannelApiImpl.requireMainThread();
        Objects.requireNonNull(newOrigin, "origin");
        if (!active) return false;
        origin = api.refreshOrigin(newOrigin);
        for (Member member : members.values()) {
            Player player = Bukkit.getPlayer(member.id);
            if (player != null && player.isOnline()) stopFor(player);
            member.lastToken = Long.MIN_VALUE;
        }
        return true;
    }

    @Override
    public boolean active() {
        return active;
    }

    boolean mutesOtherMusic() {
        return descriptor.muteOtherMusic();
    }

    private static final class Member {
        final UUID id;
        String stage;
        long stageStartedAt;
        long eligibleAt;
        long transitionAt = Long.MAX_VALUE;
        long lastToken = Long.MIN_VALUE;
        boolean waitingForPack;
        boolean retainedReconnect;
        boolean mutedByOtherMusic;
        long lastVanillaMuteAt = Long.MIN_VALUE;
        final LinkedHashSet<String> playingKeys = new LinkedHashSet<>();

        Member(UUID id, String stage) {
            this.id = id;
            this.stage = stage;
        }
    }

    private record PendingTransition(String target, StageTransitionTiming timing, long sessionAt) {}

    private record Position(int index, long token, boolean ended) {}
}
