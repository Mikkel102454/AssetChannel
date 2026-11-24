package nu.miguel.assetChannel.api;

/** Prepared one-second clip timeline. Sixteen independent playback lanes per asset/player. */
public record TimelineAudioAsset(String id, double duration, double interval, int segments) {
    public TimelineAudioAsset {
        if (id == null
                || !id.matches("[a-z0-9._-]+")
                || !Double.isFinite(duration)
                || duration <= 0
                || !Double.isFinite(interval)
                || interval <= 0
                || segments < 1) throw new IllegalArgumentException("Invalid timeline audio");
    }

    public int nextSegment(double offset) {
        if (!Double.isFinite(offset) || offset < 0)
            throw new IllegalArgumentException("Invalid offset");
        return offset >= duration
                ? segments
                : Math.min(segments, (int) Math.ceil(offset / interval - 1e-9));
    }

    public String key(int segment, int lane, boolean spatial) {
        if (segment < 0 || segment >= segments || lane < 0 || lane >= 16)
            throw new IllegalArgumentException("Segment/lane out of bounds");
        return "assetchannel_scene:"
                + id
                + "."
                + segment
                + "."
                + lane
                + (spatial ? ".spatial" : "");
    }
}
