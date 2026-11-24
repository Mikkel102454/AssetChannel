package nu.miguel.assetChannel.api;

import java.util.UUID;

/**
 * Persistent placement snapshot; displayId is null for native-state blocks. Definition changes
 * affect new placements only.
 */
public record PlacedAssetBlock(
        UUID worldId,
        int x,
        int y,
        int z,
        BlockAsset asset,
        UUID displayId,
        String originalData,
        String carrierData) {}
