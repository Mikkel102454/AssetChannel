package nu.miguel.assetChannel.api;

import net.kyori.adventure.text.Component;

/**
 * Exclusive title/subtitle surface lease; AssetChannel title HUDs queue until it closes. Main
 * thread only.
 */
public interface TitleLease extends AutoCloseable {
    void show(Component title, Component subtitle);

    @Override
    void close();
}
