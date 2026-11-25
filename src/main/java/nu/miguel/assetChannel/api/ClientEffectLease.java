package nu.miguel.assetChannel.api;

/**
 * One viewer's effect channel: one zoom/roll lease, plus one per scalar asset. Server thread only.
 */
public interface ClientEffectLease extends AutoCloseable {
    void show(EffectAnimation animation);

    void show(ClientEffectFrame frame);

    boolean closed();

    @Override
    void close();
}
