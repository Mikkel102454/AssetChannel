package nu.miguel.assetChannel.api;

import net.kyori.adventure.text.Component;

/** Exclusive main-thread action-bar surface. Refresh static text at least once per second. */
public interface ActionBarLease extends AutoCloseable {
    void show(Component content);

    @Override
    void close();
}
