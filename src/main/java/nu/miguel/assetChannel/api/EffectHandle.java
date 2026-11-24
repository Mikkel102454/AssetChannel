package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

/** Reference-counted per-player effect lease. Closing preserves pre-existing effects. */
public interface EffectHandle extends AutoCloseable {
    Key key();

    @Override
    void close();
}
