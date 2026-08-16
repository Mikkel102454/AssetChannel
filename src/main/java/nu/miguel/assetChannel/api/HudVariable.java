package nu.miguel.assetChannel.api;

import net.kyori.adventure.text.Component;
import java.util.Optional;

public record HudVariable(String name, boolean required, Optional<Component> defaultValue) {
    public HudVariable { defaultValue = defaultValue == null ? Optional.empty() : defaultValue; }
}
