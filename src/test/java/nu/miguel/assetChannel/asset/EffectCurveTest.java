package nu.miguel.assetChannel.asset;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class EffectCurveTest {
    @TempDir Path root;

    @Test
    void samplesImportedProfileKeysWithoutAssumingANamespaceOrFilenamePattern() throws Exception {
        Path file = root.resolve("camera.yml");
        Files.writeString(
                file, "kind: ZOOM\nprofiles:\n  '1.00': myfilm:wide\n  '2.00': other:close_up\n");
        var curve = EffectCurve.load("camera", file).descriptor();
        assertEquals("myfilm:wide", curve.key(1).asString());
        assertEquals("other:close_up", curve.key(1.7).asString());
        assertEquals(1, curve.minimum());
        assertEquals(2, curve.maximum());
        assertThrows(IllegalArgumentException.class, () -> curve.key(Double.NaN));
        assertThrows(UnsupportedOperationException.class, () -> curve.profiles().clear());
    }
}
