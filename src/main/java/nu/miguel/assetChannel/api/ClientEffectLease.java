package nu.miguel.assetChannel.api;

/** Exclusive client zoom control for one viewer. Calls require the server thread. */
public interface ClientEffectLease extends AutoCloseable {
    void show(EffectAnimation animation);

    boolean closed();

    @Override
    void close();
}
