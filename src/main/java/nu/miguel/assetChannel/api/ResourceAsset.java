package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

/**
 * Native file resource. Key includes directory and extension; bytes remain in the source tree/pack.
 */
public record ResourceAsset(Key key, ResourceKind kind, long bytes) {}
