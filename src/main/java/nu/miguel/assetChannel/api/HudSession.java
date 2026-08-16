package nu.miguel.assetChannel.api;

import net.kyori.adventure.text.Component;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public interface HudSession {
    UUID id();
    UUID playerId();
    String templateId();
    String slot();
    HudSurface surface();
    HudSessionState state();
    Duration remainingDuration();
    boolean updateVariables(Map<String, Component> variables);
    void close();
}
