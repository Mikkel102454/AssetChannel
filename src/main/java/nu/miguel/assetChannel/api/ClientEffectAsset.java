package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

import java.nio.*;
import java.util.*;

/** Imported shaders and the clock-composite-v1 control transport. */
public record ClientEffectAsset(
        Key prefix,
        Key writerVertex,
        Key writerFragment,
        Key copyVertex,
        Key copyShader,
        double minimum,
        double maximum,
        ClientEffectComposition composition) {
    public ClientEffectAsset {
        Objects.requireNonNull(prefix);
        Objects.requireNonNull(writerVertex);
        Objects.requireNonNull(writerFragment);
        Objects.requireNonNull(copyVertex);
        Objects.requireNonNull(copyShader);
        Objects.requireNonNull(composition);
        if (!Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || minimum < 0
                || maximum <= minimum
                || (composition.channel() == EffectCurveAsset.Kind.ZOOM && minimum == 0)
                || composition.neutral() < minimum
                || composition.neutral() > maximum)
            throw new IllegalArgumentException("Invalid effect range or neutral value");
    }

    public int controlBytes() {
        return supportsRoll() ? 22 : 15;
    }

    public boolean isScalar() {
        return !supportsRoll();
    }

    public boolean supportsRoll() {
        return composition.channel() == EffectCurveAsset.Kind.ZOOM;
    }

    public Key renderer() {
        return Key.key(prefix.namespace(), prefix.value() + "/render");
    }

    public Key reset() {
        return Key.key(prefix.namespace(), prefix.value() + "/reset");
    }

    public Key control(int slot, int value) {
        if (slot < 0 || slot >= controlBytes() || value < 1 || value > 255)
            throw new IllegalArgumentException("Invalid effect control byte");
        return Key.key(prefix.namespace(), prefix.value() + "/" + slot + "/" + value);
    }

    /** Clear the channel once and then write only its nonzero bytes. */
    public List<Key> controlKeys(byte[] bytes) {
        if (bytes.length != controlBytes())
            throw new IllegalArgumentException("Invalid effect control data");
        var result = new ArrayList<Key>();
        result.add(reset());
        for (int slot = 0; slot < bytes.length; slot++)
            if (bytes[slot] != 0) result.add(control(slot, Byte.toUnsignedInt(bytes[slot])));
        return List.copyOf(result);
    }

    /**
     * A complete standalone bundle; leases merge its controls when channels can share a renderer.
     */
    public List<Key> keys(byte[] bytes) {
        var result = new ArrayList<>(controlKeys(bytes));
        result.add(renderer());
        return List.copyOf(result);
    }

    public List<Key> ownedPrefixes() {
        return List.of(prefix, composition.prefix());
    }

    /** Includes the shared group renderer as well as this asset's control resources. */
    public boolean owns(Key key) {
        return (key.namespace().equals(prefix.namespace())
                        && key.value().startsWith(prefix.value() + "/"))
                || composition.owns(key);
    }

    /** Moving curves starting at neutral must keep rendering. */
    public boolean isNeutral(ClientEffectFrame frame) {
        var a = frame.animation();
        return a.roll() == 0
                && a.nextRoll() == 0
                && (a.durationTicks() == 0 || a.from() == a.to())
                && Math.abs(a.value(frame.curveTick(), frame.curveTick()) - composition.neutral())
                        < 1e-9;
    }

    public byte[] encode(EffectAnimation animation, long gameTick) {
        return encode(new ClientEffectFrame(animation, gameTick, gameTick, 0));
    }

    public byte[] encode(ClientEffectFrame frame) {
        var animation = frame.animation();
        if (animation.from() < minimum
                || animation.from() > maximum
                || animation.to() < minimum
                || animation.to() > maximum)
            throw new IllegalArgumentException("Effect outside imported asset bounds");
        if (!supportsRoll() && (animation.roll() != 0 || animation.nextRoll() != 0))
            throw new IllegalArgumentException("Camera roll requires a ZOOM channel");
        var buffer = ByteBuffer.allocate(controlBytes()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(
                (short) Math.round((animation.from() - minimum) / (maximum - minimum) * 65535));
        buffer.putShort(
                (short) Math.round((animation.to() - minimum) / (maximum - minimum) * 65535));
        buffer.putShort((short) Math.round(animation.progress() * 65535));
        buffer.putFloat((float) animation.durationTicks());
        buffer.putShort((short) Math.floorMod(frame.curveTick(), 24000));
        buffer.put((byte) animation.easing().ordinal());
        if (supportsRoll()) {
            buffer.putShort(angle(animation.roll()));
            buffer.putShort(angle(animation.nextRoll()));
            buffer.put((byte) (animation.rollInterpolated() ? 1 : 0));
            buffer.putShort((short) Math.floorMod(frame.rollTick(), 24000));
        }
        buffer.putShort((short) Math.round(frame.correctionTicks() * 4096));
        return buffer.array();
    }

    private static short angle(double value) {
        return (short) Math.round((((value % 360 + 540) % 360) - 180) / 180 * 32767);
    }

    public ClientEffectFrame decodeFrame(byte[] bytes) {
        if (bytes.length != controlBytes())
            throw new IllegalArgumentException("Invalid effect control data");
        var b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        double from = minimum + Short.toUnsignedInt(b.getShort()) / 65535d * (maximum - minimum);
        double to = minimum + Short.toUnsignedInt(b.getShort()) / 65535d * (maximum - minimum);
        double progress = Short.toUnsignedInt(b.getShort()) / 65535d;
        double duration = b.getFloat();
        int tick = Short.toUnsignedInt(b.getShort()), easing = Byte.toUnsignedInt(b.get());
        if (easing >= EffectAnimation.Easing.values().length)
            throw new IllegalArgumentException("Invalid effect easing");
        double roll = supportsRoll() ? b.getShort() / 32767d * 180 : 0;
        double next = supportsRoll() ? b.getShort() / 32767d * 180 : 0;
        boolean moving = supportsRoll() && b.get() != 0;
        int rollTick = supportsRoll() ? Short.toUnsignedInt(b.getShort()) : tick;
        double correction = b.getShort() / 4096d;
        return new ClientEffectFrame(
                new EffectAnimation(
                        from,
                        to,
                        progress,
                        duration,
                        EffectAnimation.Easing.values()[easing],
                        roll,
                        next,
                        moving),
                tick,
                rollTick,
                correction);
    }
}
