package nu.miguel.assetChannel.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.time.Duration;

public final class OggDurationReader {
    private OggDurationReader() {}

    public static Duration read(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            byte[] header = new byte[(int) Math.min(file.length(), 65536)];
            file.readFully(header);
            int vorbis = find(header, new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'});
            if (vorbis < 0 || vorbis + 16 > header.length) throw new IOException("Missing Vorbis identification header: " + path);
            long sampleRate = uint32le(header, vorbis + 12);
            if (sampleRate <= 0) throw new IOException("Invalid Vorbis sample rate: " + path);

            long start = Math.max(0, file.length() - 131072);
            file.seek(start);
            byte[] tail = new byte[(int) (file.length() - start)];
            file.readFully(tail);
            long lastGranule = -1;
            for (int i = 0; i + 14 <= tail.length; i++) {
                if (tail[i] == 'O' && tail[i + 1] == 'g' && tail[i + 2] == 'g' && tail[i + 3] == 'S')
                    lastGranule = int64le(tail, i + 6);
            }
            if (lastGranule < 0) throw new IOException("Missing OGG granule position: " + path);
            return Duration.ofNanos((long) ((lastGranule * 1_000_000_000d) / sampleRate));
        }
    }

    private static int find(byte[] data, byte[] needle) {
        outer: for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
    private static long uint32le(byte[] b, int o) { return (b[o]&255L)|((b[o+1]&255L)<<8)|((b[o+2]&255L)<<16)|((b[o+3]&255L)<<24); }
    private static long int64le(byte[] b, int o) { long v=0; for(int i=7;i>=0;i--) v=(v<<8)|(b[o+i]&255L); return v; }
}
