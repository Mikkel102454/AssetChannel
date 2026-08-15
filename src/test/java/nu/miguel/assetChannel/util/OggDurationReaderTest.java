package nu.miguel.assetChannel.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class OggDurationReaderTest {
    @TempDir Path directory;

    @Test void readsDurationFromVorbisRateAndLastGranule() throws Exception {
        byte[] bytes = new byte[160];
        oggPage(bytes, 0, 0);
        int packet = 32;
        bytes[packet] = 1;
        System.arraycopy("vorbis".getBytes(), 0, bytes, packet + 1, 6);
        put32(bytes, packet + 12, 48_000);
        oggPage(bytes, 100, 96_000);
        Path file = directory.resolve("two-seconds.ogg");
        Files.write(file, bytes);
        assertEquals(Duration.ofSeconds(2), OggDurationReader.read(file));
    }

    @Test void rejectsMissingVorbisHeader() throws Exception {
        Path file = directory.resolve("invalid.ogg");
        Files.write(file, new byte[64]);
        assertThrows(Exception.class, () -> OggDurationReader.read(file));
    }

    private static void oggPage(byte[] bytes, int offset, long granule) {
        System.arraycopy("OggS".getBytes(), 0, bytes, offset, 4);
        for (int i = 0; i < 8; i++) bytes[offset + 6 + i] = (byte) (granule >>> (8 * i));
    }
    private static void put32(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
    }
}
