package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;

import nu.miguel.assetChannel.api.HudSessionState;
import nu.miguel.assetChannel.asset.Hud;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

class HudSessionLifecycleTest {
    @TempDir Path directory;

    @Test
    void hidingQueuedOrSuspendedSessionDoesNotTouchPlayerSurface() throws Exception {
        Path file = directory.resolve("hud.yml");
        Files.writeString(file, "surface: action-bar\nlayout: {content: test}\n");
        UUID id = UUID.randomUUID();
        Player player =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class[] {Player.class},
                                (proxy, method, args) ->
                                        method.getName().equals("getUniqueId") ? id : null);
        var session =
                new HudSessionImpl(
                        new HudManager(null),
                        player,
                        Hud.load("test", file),
                        "test",
                        0,
                        false,
                        false,
                        Map.of(),
                        0);
        // No Bukkit server exists in this test: an inactive session must not even look up the
        // player.
        assertDoesNotThrow(session::hide);
        session.state = HudSessionState.SUSPENDED;
        assertDoesNotThrow(session::hide);
    }
}
