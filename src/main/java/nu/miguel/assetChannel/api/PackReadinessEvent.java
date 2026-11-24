package nu.miguel.assetChannel.api;

import org.bukkit.entity.Player;
import org.bukkit.event.*;

import java.util.UUID;

/** Readiness belongs to the specified AssetChannel pack revision. */
public final class PackReadinessEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final UUID revision;
    private final boolean ready;

    public PackReadinessEvent(Player player, UUID revision, boolean ready) {
        this.player = player;
        this.revision = revision;
        this.ready = ready;
    }

    public Player player() {
        return player;
    }

    public UUID revision() {
        return revision;
    }

    public boolean ready() {
        return ready;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
