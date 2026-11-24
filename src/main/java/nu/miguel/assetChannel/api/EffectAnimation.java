package nu.miguel.assetChannel.api;

import java.util.Objects;

/** A zoom curve evaluated by the client between synchronized scene-clock updates. */
public record EffectAnimation(
        double from,
        double to,
        double progress,
        double durationTicks,
        Easing easing,
        double roll,
        double nextRoll,
        boolean rollInterpolated) {
    public EffectAnimation(
            double from, double to, double progress, double durationTicks, Easing easing) {
        this(from, to, progress, durationTicks, easing, 0, 0, false);
    }

    public enum Easing {
        LINEAR,
        SMOOTH,
        SMOOTHER,
        EASE_IN,
        EASE_OUT,
        STEP;

        public double apply(double value) {
            double t = Math.clamp(value, 0, 1);
            return switch (this) {
                case LINEAR -> t;
                case SMOOTH -> t * t * (3 - 2 * t);
                case SMOOTHER -> t * t * t * (t * (t * 6 - 15) + 10);
                case EASE_IN -> t * t;
                case EASE_OUT -> 1 - (1 - t) * (1 - t);
                case STEP -> t >= 1 ? 1 : 0;
            };
        }
    }

    public EffectAnimation {
        Objects.requireNonNull(easing);
        if (!Double.isFinite(from)
                || !Double.isFinite(to)
                || !Double.isFinite(progress)
                || progress < 0
                || progress > 1
                || !Double.isFinite(durationTicks)
                || durationTicks < 0
                || durationTicks > Float.MAX_VALUE
                || !Double.isFinite(roll)
                || !Double.isFinite(nextRoll))
            throw new IllegalArgumentException("Invalid client effect animation");
    }

    /** GameTime wraps every 24,000 ticks. Rebase running animations at least once per cycle. */
    public double value(double synchronizedTick, double clientTick) {
        double elapsed = ((clientTick - synchronizedTick) % 24000 + 24000) % 24000;
        double t = durationTicks == 0 ? progress : progress + elapsed / durationTicks;
        return from + (to - from) * easing.apply(t);
    }

    public double rollAt(double synchronizedTick, double clientTick) {
        double elapsed = ((clientTick - synchronizedTick) % 24000 + 24000) % 24000;
        return roll
                + (((nextRoll - roll) % 360 + 540) % 360 - 180)
                        * (rollInterpolated ? Math.clamp(elapsed, 0, 1) : 0);
    }
}
