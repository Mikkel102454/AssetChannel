package nu.miguel.assetChannel.util;

import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/** Reads the source timeline of supported audio without decoding OGG through FFmpeg. */
public final class AudioDurationReader {
    private AudioDurationReader() {}

    public static Duration read(Path path) throws IOException {
        String extension = extension(path);
        Duration duration =
                switch (extension) {
                    case "ogg" -> OggDurationReader.read(path);
                    case "mp3" -> readMp3(path);
                    default -> throw new IOException("Unsupported audio format: " + path);
                };
        if (duration.isZero() || duration.isNegative())
            throw new IOException("Audio has no positive duration: " + path);
        return duration;
    }

    public static boolean isSupported(Path path) {
        String extension = extension(path);
        return extension.equals("ogg") || extension.equals("mp3");
    }

    public static boolean isOgg(Path path) {
        return extension(path).equals("ogg");
    }

    private static Duration readMp3(Path path) throws IOException {
        try {
            MultimediaInfo info =
                    new MultimediaObject(path.toFile(), new DefaultFFMPEGLocator()).getInfo();
            if (info.getAudio() == null)
                throw new IOException("MP3 contains no audio stream: " + path);
            return Duration.ofMillis(info.getDuration());
        } catch (EncoderException | RuntimeException e) {
            throw new IOException("Unable to read MP3 audio: " + path, e);
        }
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
