package nu.miguel.assetChannel.api;

import java.util.*;

/** Static catalog checks; native compiler/GPU acceptance is a separate development check. */
public record AssetPreflightReport(
        UUID revision, boolean checked, int resources, int shaders, List<String> errors) {
    public AssetPreflightReport {
        Objects.requireNonNull(revision);
        errors = List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
