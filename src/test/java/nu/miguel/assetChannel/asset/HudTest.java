package nu.miguel.assetChannel.asset;

import static org.junit.jupiter.api.Assertions.*;

import nu.miguel.assetChannel.api.HudSurface;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class HudTest {
    @TempDir Path directory;

    @Test
    void loadsVariableContractAndDuration() throws Exception {
        Path file =
                write(
                        """
                        surface: action-bar
                        slot: notifications
                        duration: 4s
                        priority: 12
                        resume-interrupted: true
                        variables:
                          heading:
                            required: true
                          body:
                            default: ""
                        layout:
                          content: "<yellow><var:heading> <var:body>"
                        fallback:
                          mode: text-only
                          content: "<var:heading>"
                        """);
        Hud hud = Hud.load("notification.banner", file);
        assertEquals(HudSurface.ACTION_BAR, hud.surface());
        assertEquals(80, hud.durationTicks());
        assertTrue(hud.variables().get("heading").required());
        assertEquals("", hud.variables().get("body").defaultValue());
    }

    @Test
    void rejectsUndeclaredVariablesAndBadFallback() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Hud.load(
                                "bad",
                                write(
                                        """
                                        surface: title
                                        variables: {}
                                        layout: {content: "<var:typo>"}
                                        """)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Hud.load(
                                "bad",
                                write(
                                        """
                                        surface: title
                                        layout: {content: ok}
                                        fallback: {mode: magic}
                                        """)));
    }

    private Path write(String value) throws Exception {
        Path path = directory.resolve(java.util.UUID.randomUUID() + ".yml");
        Files.writeString(path, value);
        return path;
    }
}
