package nu.miguel.assetChannel.api.internal;

final class SegmentHandoff {
    private SegmentHandoff() {}

    static long clampLead(long requestedNanos, long shortestSegmentNanos) {
        return Math.min(Math.max(0, requestedNanos), Math.max(0, shortestSegmentNanos / 2 - 1));
    }

    static long playbackElapsed(long elapsed, long leadNanos, boolean transitionDue) {
        if (leadNanos <= 0 || transitionDue || elapsed > Long.MAX_VALUE - leadNanos) return elapsed;
        return elapsed + leadNanos;
    }
}
