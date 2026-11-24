package nu.miguel.assetChannel.api;

import org.bukkit.event.*;

import java.util.UUID;

/** Fired on the server thread after a successful catalog publication; never on a failed build. */
public final class PackRevisionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID previous, current;

    public PackRevisionEvent(UUID previous, UUID current) {
        this.previous = previous;
        this.current = current;
    }

    public UUID previous() {
        return previous;
    }

    public UUID current() {
        return current;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
