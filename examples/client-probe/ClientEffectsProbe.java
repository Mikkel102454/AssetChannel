import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Checks the current imported compositor against CPU reference values and measures its GPU cost.
 */
public final class ClientEffectsProbe extends GpuProbeSupport {
    static int compositor, copy, boundsUbo;
    static final byte[][] data = new byte[5][];

    public static void main(String[] args) throws Exception {
        shaders = Path.of(args[0]);
        String blit;
        try (var zip = new ZipFile(args[1])) {
            globals = source(zip, "include/globals.glsl");
            screen = source(zip, "core/screenquad.vsh");
            blit = source(zip, "post/blit.fsh");
        }
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW unavailable");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(W, H, "Current compositor probe", 0, 0);
        if (window == 0) throw new IllegalStateException("GPU context unavailable");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GPU: " + glGetString(GL_RENDERER));
            glBindVertexArray(glGenVertexArrays());
            globalUbo = glGenBuffers();
            configUbo = glGenBuffers();
            boundsUbo = glGenBuffers();
            writer =
                    program(
                            Files.readString(shaders.resolve("control_pixel.vsh")),
                            Files.readString(shaders.resolve("control_value.fsh")),
                            "ControlConfig");
            copy = program(screen, blit, "BlitConfig");
            compositor =
                    program(
                            Files.readString(shaders.resolve("composite.vsh")),
                            Files.readString(shaders.resolve("composite.fsh")),
                            "CompositorConfig");
            glUniformBlockBinding(
                    compositor, glGetUniformBlockIndex(compositor, "ChannelBounds"), 2);
            glBindBuffer(GL_UNIFORM_BUFFER, boundsUbo);
            glBufferData(
                    GL_UNIFORM_BUFFER,
                    new float[] {
                        1, 4, 22, 0, 0, 1, 15, 22, 0, 1, 15, 44, 0, 1, 15, 66, 0, 1, 15, 88
                    },
                    GL_STATIC_DRAW);
            glBindBufferBase(GL_UNIFORM_BUFFER, 2, boundsUbo);
            input = texture();
            output = texture();
            fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, W, H);
            correctness();
            for (int[] size : new int[][] {{1920, 1080}, {2560, 1440}}) {
                glDeleteTextures(input);
                glDeleteTextures(output);
                W = size[0];
                H = size[1];
                input = texture();
                output = texture();
                glViewport(0, 0, W, H);
                demoFrame();
                globals(100.5);
                int passes = 2;
                for (int channel : new int[] {0, 2, 3, 4}) {
                    passes++;
                    for (byte value : data[channel]) if (value != 0) passes++;
                }
                System.out.printf(
                        Locale.ROOT,
                        "%dx%d: %.3f ms GPU, %d passes, two full-screen passes%n",
                        W,
                        H,
                        measure(29),
                        passes);
            }
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
            System.out.println(
                    "PASS: current compositor, sparse transport, fractional ticks, pause, reverse,"
                            + " easing, rollover, clock correction and independent roll");
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    static String source(ZipFile zip, String path) throws Exception {
        return new String(
                zip.getInputStream(zip.getEntry("assets/minecraft/shaders/" + path)).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    static void correctness() {
        for (int mask : new int[] {1, 2, 4, 8, 16, 31})
            for (int anchor : new int[] {100, 23999})
                for (int mode = 0; mode < 4; mode++)
                    for (double offset : new double[] {-.1, 0, .25, .5, 1, 2.5, 4}) {
                        for (int channel = 0; channel < 5; channel++) {
                            data[channel] =
                                    frame(
                                            mode == 2 ? 1 : 0,
                                            mode == 2 ? 0 : 1,
                                            mode == 1 ? .7 : .35,
                                            mode == 1 ? 0 : 60,
                                            anchor,
                                            channel == 0,
                                            mode == 2 ? 170 : .2,
                                            mode == 2 ? -170 : .4,
                                            mode == 1 ? 0 : (channel % 2 == 0 ? -2 : 1));
                            data[channel][12] = (byte) ((mode + channel) % 6);
                        }
                        if (mode == 1) data[0][17] = 0;
                        if (mode == 3)
                            ByteBuffer.wrap(data[0])
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .putShort(18, (short) ((anchor + 5) % 24000));
                        double tick = (anchor + offset + 24000) % 24000;
                        globals(tick);
                        gradient();
                        pipeline(mask);
                        assertImage(mask, tick);
                    }
        demoFrame();
        globals(100.5);
        gradient();
        pipeline(29);
        assertImage(29, 100.5);
        System.out.println(
                "PASS: rendered images match the CPU reference across channel masks and timing"
                        + " cases");
    }

    static void assertImage(int mask, double tick) {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, input, 0);
        float[] pixels = new float[W * H * 4];
        glReadPixels(0, 0, W, H, GL_RGBA, GL_FLOAT, pixels);
        double[] values = {1, 0, 0, 0, 0};
        for (int channel = 0; channel < 5; channel++)
            if ((mask & 1 << channel) != 0)
                values[channel] = value(data[channel], channel == 0, tick);
        double roll = (mask & 1) == 0 ? 0 : roll(tick);
        for (int y = 2; y < H; y += 3)
            for (int x = 0; x < W; x += 3) {
                double[] expected = pixel(x, y, values, roll, mask);
                for (int c = 0; c < 3; c++)
                    if (Math.abs(pixels[(y * W + x) * 4 + c] - expected[c]) > .009)
                        throw new AssertionError(
                                "Image mismatch: mask="
                                        + mask
                                        + " tick="
                                        + tick
                                        + " pixel="
                                        + x
                                        + ","
                                        + y
                                        + " channel="
                                        + c
                                        + " actual="
                                        + pixels[(y * W + x) * 4 + c]
                                        + " expected="
                                        + expected[c]);
            }
    }

    static double[] pixel(int x, int y, double[] values, double roll, int mask) {
        double tx = (x + .5) / W, ty = (y + .5) / H, aspect = (double) W / H;
        double px = (tx - .5) / values[0] * aspect, py = (ty - .5) / values[0];
        double u = (Math.cos(roll) * px + Math.sin(roll) * py) / aspect + .5;
        double v = -Math.sin(roll) * px + Math.cos(roll) * py + .5;
        double[] colour = {0, 0, 0};
        if (u >= 0 && u <= 1 && v >= 0 && v <= 1) {
            int channel = (int) (u * W) / 22;
            if (v * H < 1.5 && channel < 5 && (mask & 1 << channel) != 0) v = 1.5 / H;
            colour = new double[] {gradientAt(u, W), gradientAt(v, H), 80d / 255};
        }
        double grey = colour[0] * .2126 + colour[1] * .7152 + colour[2] * .0722;
        double bars =
                values[2] > 0
                        ? smooth(
                                values[2] * .12 - .5 / H,
                                values[2] * .12 + .5 / H,
                                Math.min(ty, 1 - ty))
                        : 1;
        double edge = 1 - values[4] * smooth(.15, .71, Math.hypot(tx - .5, ty - .5));
        for (int c = 0; c < 3; c++)
            colour[c] =
                    (colour[c] + (grey - colour[c]) * values[3]) * (1 - values[1]) * bars * edge;
        return colour;
    }

    static double gradientAt(double uv, int size) {
        double pixel = uv * size - .5, blend = pixel - Math.floor(pixel);
        int before = Math.clamp((int) Math.floor(pixel), 0, size - 1),
                after = Math.clamp((int) Math.floor(pixel) + 1, 0, size - 1);
        return (Math.round(before * 255d / (size - 1)) * (1 - blend)
                        + Math.round(after * 255d / (size - 1)) * blend)
                / 255;
    }

    static double value(byte[] data, boolean zoom, double tick) {
        var b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        double progress = Short.toUnsignedInt(b.getShort(4)) / 65535d, duration = b.getFloat(6);
        double elapsed = elapsed(Short.toUnsignedInt(b.getShort(10)), tick);
        double correction = b.getShort(zoom ? 20 : 13) / 4096d;
        correction *= 1 - smooth(1, 1 + Math.max(2, 1.5 * Math.abs(correction)), elapsed);
        if (duration > 0) progress += (elapsed - correction) / duration;
        double t = Math.clamp(progress, 0, 1);
        t =
                switch (data[12]) {
                    case 1 -> t * t * (3 - 2 * t);
                    case 2 -> t * t * t * (t * (t * 6 - 15) + 10);
                    case 3 -> t * t;
                    case 4 -> 1 - (1 - t) * (1 - t);
                    case 5 -> t >= 1 ? 1 : 0;
                    default -> t;
                };
        double from = Short.toUnsignedInt(b.getShort(0)) / 65535d,
                to = Short.toUnsignedInt(b.getShort(2)) / 65535d;
        double normalized = from + (to - from) * t;
        return zoom ? 1 + 3 * normalized : normalized;
    }

    static double roll(double tick) {
        var b = ByteBuffer.wrap(data[0]).order(ByteOrder.LITTLE_ENDIAN);
        double from = b.getShort(13) / 32767d * 180, to = b.getShort(15) / 32767d * 180;
        double blend =
                data[0][17] != 0
                        ? Math.clamp(elapsed(Short.toUnsignedInt(b.getShort(18)), tick), 0, 1)
                        : 0;
        return Math.toRadians(from + ((to - from + 540) % 360 - 180) * blend);
    }

    static double elapsed(double anchor, double tick) {
        return ((tick - anchor + 12000) % 24000 + 24000) % 24000 - 12000;
    }

    static double smooth(double low, double high, double value) {
        double t = Math.clamp((value - low) / (high - low), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static byte[] frame(
            double from,
            double to,
            double progress,
            float duration,
            int tick,
            boolean zoom,
            double roll,
            double next,
            double correction) {
        var b = ByteBuffer.allocate(zoom ? 22 : 15).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) Math.round(from * 65535))
                .putShort((short) Math.round(to * 65535))
                .putShort((short) Math.round(progress * 65535))
                .putFloat(duration)
                .putShort((short) tick)
                .put((byte) 1);
        if (zoom)
            b.putShort((short) Math.round(roll / 180 * 32767))
                    .putShort((short) Math.round(next / 180 * 32767))
                    .put((byte) 1)
                    .putShort((short) tick);
        b.putShort((short) Math.round(correction * 4096));
        return b.array();
    }

    static void demoFrame() {
        data[0] = frame(0, 1d / 12, 5d / 6, 120, 100, true, -5.5, -5.72, 0);
        data[1] = frame(1, 0, 1, 0, 100, false, 0, 0, 0);
        data[2] = frame(0, 1, 1, 0, 100, false, 0, 0, 0);
        data[3] = frame(0, 1, 1, 0, 100, false, 0, 0, 0);
        data[4] = frame(0, 1, 1d / 3, 60, 100, false, 0, 0, 0);
        for (var bytes : data) bytes[12] = 2;
    }

    static void pipeline(int mask) {
        for (int channel = 0; channel < 5; channel++) {
            if ((mask & 1 << channel) == 0) continue;
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, input, 0);
            glUseProgram(writer);
            int offset = channel * 22;
            config(offset, 0, 22, 0);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            for (int slot = 0; slot < data[channel].length; slot++) {
                int value = Byte.toUnsignedInt(data[channel][slot]);
                if (value == 0) continue;
                config(offset + slot, value, 1, 0);
                glDrawArrays(GL_TRIANGLES, 0, 3);
            }
        }
        draw(compositor, input, output, mask, 24000, 0, 0);
        draw(copy, output, input, 1, 1, 1, 1);
    }

    static void draw(int program, int source, int target, float a, float b, float c, float d) {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        glUseProgram(program);
        config(a, b, c, d);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, source);
        glUniform1i(glGetUniformLocation(program, "InSampler"), 0);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    static double measure(int mask) {
        gradient();
        int scene = texture();
        copyImage(input, scene);
        for (int i = 0; i < 8; i++) {
            copyImage(scene, input);
            pipeline(mask);
        }
        glFinish();
        int[] queries = new int[30];
        for (int i = 0; i < queries.length; i++) {
            copyImage(scene, input);
            queries[i] = glGenQueries();
            glBeginQuery(GL_TIME_ELAPSED, queries[i]);
            pipeline(mask);
            glEndQuery(GL_TIME_ELAPSED);
        }
        glFinish();
        double[] times = new double[queries.length];
        for (int i = 0; i < queries.length; i++) {
            times[i] = glGetQueryObjectui64(queries[i], GL_QUERY_RESULT) / 1_000_000d;
            glDeleteQueries(queries[i]);
        }
        Arrays.sort(times);
        glDeleteTextures(scene);
        return times[times.length / 2];
    }

    static void copyImage(int source, int target) {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, source, 0);
        glBindTexture(GL_TEXTURE_2D, target);
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, W, H);
    }

    static void gradient() {
        var pixels = ByteBuffer.allocateDirect(W * H * 4);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                pixels.put((byte) Math.round(x * 255f / (W - 1)))
                        .put((byte) Math.round(y * 255f / (H - 1)))
                        .put((byte) 80)
                        .put((byte) 255);
        pixels.flip();
        glBindTexture(GL_TEXTURE_2D, input);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }
}
