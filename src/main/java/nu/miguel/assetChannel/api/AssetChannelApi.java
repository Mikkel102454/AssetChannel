package nu.miguel.assetChannel.api;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import java.util.*;

public interface AssetChannelApi {
    Collection<IconAsset> icons();
    Collection<FontAsset> fonts();
    Collection<SoundAsset> sounds();
    Collection<SoundtrackAsset> soundtracks();
    Optional<IconAsset> icon(String id);
    Optional<FontAsset> font(String id);
    Optional<SoundAsset> sound(String id);
    Optional<SoundtrackAsset> soundtrack(String id);
    PlaybackResult playSound(Player player, String id, SoundCategory category, float volume, float pitch);
    PlaybackResult playSound(Player player, String id, SoundOrigin origin, SoundCategory category, float volume, float pitch);
    BroadcastPlaybackResult broadcastSound(String id, SoundOrigin origin, SoundCategory category, float volume, float pitch);
    boolean stopSound(Player player, String id);
    boolean stopSound(Player player, String id, SoundCategory category);
    void stopAllSounds(Player player);
    MusicSession startSoundtrack(String id, Collection<? extends Player> players, SessionSettings settings);
    Optional<MusicSession> session(UUID id);
    Collection<MusicSession> sessions();
}
