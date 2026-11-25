package nu.miguel.assetChannel.api;

public record ClientEffectFrame(
        EffectAnimation animation, long curveTick, long rollTick, double correctionTicks) {
    public ClientEffectFrame {
        java.util.Objects.requireNonNull(animation);
        if (!Double.isFinite(correctionTicks) || Math.abs(correctionTicks) > 2)
            throw new IllegalArgumentException("Correction must be within two ticks");
    }

    public double value(double clientTick) {
        return animation.value(curveTick, clientTick - remainingCorrection(clientTick));
    }

    public double remainingCorrection(double clientTick) {
        double t =
                Math.clamp(
                        (EffectAnimation.elapsedTicks(curveTick, clientTick) - 1)
                                / Math.max(2, 1.5 * Math.abs(correctionTicks)),
                        0,
                        1);
        return correctionTicks * (1 - t * t * (3 - 2 * t));
    }

    public double rollAt(double clientTick) {
        return animation.rollAt(rollTick, clientTick);
    }
}
