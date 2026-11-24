package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;

import java.nio.*;
import java.util.*;

/** Imported shader references and the clock-zoom-v1 control protocol. */
public record ClientEffectAsset(
        Key prefix,
        Key writerVertex,
        Key writerFragment,
        Key vertexShader,
        Key fragmentShader,
        Key copyShader,
        double minimum,
        double maximum,
        int version) {
    public ClientEffectAsset(
            Key prefix,
            Key writerVertex,
            Key writerFragment,
            Key vertexShader,
            Key fragmentShader,
            Key copyShader,
            double minimum,
            double maximum) {
        this(
                prefix,
                writerVertex,
                writerFragment,
                vertexShader,
                fragmentShader,
                copyShader,
                minimum,
                maximum,
                1);
    }

    public static final int CONTROL_BYTES = 13;

    public ClientEffectAsset {
        Objects.requireNonNull(prefix);
        Objects.requireNonNull(writerVertex);
        Objects.requireNonNull(writerFragment);
        Objects.requireNonNull(vertexShader);
        Objects.requireNonNull(fragmentShader);
        Objects.requireNonNull(copyShader);
        if (!Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || minimum <= 0
                || maximum <= minimum
                || version < 1
                || version > 2)
            throw new IllegalArgumentException("Invalid client zoom bounds or protocol");
    }

    public int controlBytes() {
        return version == 2 ? 18 : CONTROL_BYTES;
    }

    public Key renderer() {
        return Key.key(prefix.namespace(), prefix.value() + "/render");
    }

    public Key control(int slot, int value) {
        if (slot < 0 || slot >= controlBytes() || value < 0 || value > 255)
            throw new IllegalArgumentException("Invalid effect control byte");
        return Key.key(prefix.namespace(), prefix.value() + "/" + slot + "/" + value);
    }

    public List<Key> keys(byte[] bytes) {
        if (bytes.length != controlBytes())
            throw new IllegalArgumentException("Invalid effect control data");
        var result = new ArrayList<Key>();
        for (int i = 0; i < bytes.length; i++) result.add(control(i, Byte.toUnsignedInt(bytes[i])));
        result.add(renderer());
        return List.copyOf(result);
    }

    public boolean owns(Key key) {
        return key.namespace().equals(prefix.namespace())
                && key.value().startsWith(prefix.value() + "/");
    }

    public byte[] encode(EffectAnimation animation, long gameTick) {
        if (animation.from() < minimum
                || animation.from() > maximum
                || animation.to() < minimum
                || animation.to() > maximum)
            throw new IllegalArgumentException("Zoom outside imported asset bounds");
        if (version == 1 && (animation.roll() != 0 || animation.nextRoll() != 0))
            throw new IllegalArgumentException("Camera roll requires clock-zoom-v2");
        var buffer = ByteBuffer.allocate(controlBytes()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(
                (short) Math.round((animation.from() - minimum) / (maximum - minimum) * 65535));
        buffer.putShort(
                (short) Math.round((animation.to() - minimum) / (maximum - minimum) * 65535));
        buffer.putShort((short) Math.round(animation.progress() * 65535));
        buffer.putFloat((float) animation.durationTicks());
        buffer.putShort((short) Math.floorMod(gameTick, 24000));
        buffer.put((byte) animation.easing().ordinal());
        if (version == 2) {
            buffer.putShort(angle(animation.roll()));
            buffer.putShort(angle(animation.nextRoll()));
            buffer.put((byte) (animation.rollInterpolated() ? 1 : 0));
        }
        return buffer.array();
    }

    public record Decoded(EffectAnimation animation, int gameTick) {}

    private static short angle(double value) {
        return (short) Math.round((((value % 360 + 540) % 360) - 180) / 180 * 32767);
    }

    public Decoded decode(byte[] bytes) {
        if (bytes.length != controlBytes())
            throw new IllegalArgumentException("Invalid effect control data");
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        double
                from =
                        minimum
                                + Short.toUnsignedInt(buffer.getShort())
                                        / 65535d
                                        * (maximum - minimum),
                to =
                        minimum
                                + Short.toUnsignedInt(buffer.getShort())
                                        / 65535d
                                        * (maximum - minimum),
                progress = Short.toUnsignedInt(buffer.getShort()) / 65535d;
        double duration = buffer.getFloat();
        int tick = Short.toUnsignedInt(buffer.getShort()), ease = Byte.toUnsignedInt(buffer.get());
        double roll = version == 2 ? buffer.getShort() / 32767d * 180 : 0,
                next = version == 2 ? buffer.getShort() / 32767d * 180 : 0;
        boolean moving = version == 2 && buffer.get() != 0;
        return new Decoded(
                new EffectAnimation(
                        from,
                        to,
                        progress,
                        duration,
                        EffectAnimation.Easing.values()[ease],
                        roll,
                        next,
                        moving),
                tick);
    }
}
