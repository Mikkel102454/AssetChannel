package nu.miguel.assetChannel.api;

import org.bukkit.Location;
import java.util.UUID;

/** A temporary per-player block appearance. All methods require the Paper server thread. */
public interface BlockOverlay extends AutoCloseable {
    UUID id();
    UUID playerId();
    String assetId();
    Location location();
    boolean visible();
    boolean isClosed();
    /** Sends the warning state, or the current real block when false. Repeated values refresh the view. */
    void setVisible(boolean visible);
    /** Ends this overlay and restores the current real block if the player still has the chunk. */
    @Override void close();
}
