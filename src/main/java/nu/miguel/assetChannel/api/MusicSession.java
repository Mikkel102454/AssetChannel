package nu.miguel.assetChannel.api;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface MusicSession {
    UUID id();
    SoundtrackAsset soundtrack();
    SessionSettings settings();
    Set<UUID> members();
    String stage();
    Map<UUID, String> memberStages();
    boolean transitioning();
    StageChangeResult setStage(String stage, StageTransitionSettings settings);
    SoundOrigin origin();
    boolean setOrigin(SoundOrigin origin);
    boolean active();
    boolean addPlayer(Player player);
    boolean removePlayer(Player player);
    void stop();
}
