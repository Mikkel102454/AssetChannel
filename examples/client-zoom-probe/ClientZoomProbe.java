import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.*;
import java.nio.file.*;
import java.util.zip.ZipFile;

/** Standalone GPU acceptance probe. Uses the installed client's LWJGL, without modifying it. */
public class ClientZoomProbe {
    static final int W = 128, H = 64;
    static String globals, screen;
    static Path shaders;
    static int globalUbo, configUbo, writer, renderer, input, output, fbo;
    static int controlCount = 13;

    public static void main(String[] args) throws Exception {
        shaders = Path.of(args[0]);
        try (var zip = new ZipFile(args[1])) {
            globals =
                    new String(
                            zip.getInputStream(
                                            zip.getEntry(
                                                    "assets/minecraft/shaders/include/globals.glsl"))
                                    .readAllBytes());
            screen =
                    new String(
                            zip.getInputStream(
                                            zip.getEntry(
                                                    "assets/minecraft/shaders/core/screenquad.vsh"))
                                    .readAllBytes());
        }
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW unavailable");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(W, H, "Client zoom probe", 0, 0);
        if (window == 0) throw new IllegalStateException("Hidden GPU context unavailable");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GPU: " + glGetString(GL_RENDERER));
            glBindVertexArray(glGenVertexArrays());
            globalUbo = glGenBuffers();
            configUbo = glGenBuffers();
            writer =
                    program(
                            Files.readString(shaders.resolve("control_pixel.vsh")),
                            Files.readString(shaders.resolve("control_value.fsh")),
                            "ControlConfig");
            renderer =
                    program(
                            screen,
                            Files.readString(shaders.resolve("interpolated_zoom.fsh")),
                            "ClientZoomConfig");
            input = texture();
            output = texture();
            fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, W, H);
            double previous = Double.NaN;
            for (double tick : new double[] {0, .25, .5, .75, 1, 1.5, 2}) {
                prepare(1, 3, 0, 4, 0, 0);
                float actual = render(tick);
                double zoom = 1 + 2 * tick / 4;
                assertRed(actual, zoom);
                if (!Double.isNaN(previous) && actual <= previous)
                    throw new AssertionError("Zoom did not advance between server ticks");
                previous = actual;
                System.out.printf("tick %.2f: zoom %.4f, sampled red %.6f%n", tick, zoom, actual);
            }
            prepare(1, 3, .25, 0, 23999, 0);
            float pausedA = render(23999.5), pausedB = render(500);
            if (Math.abs(pausedA - pausedB) > .00001) throw new AssertionError("Paused zoom moved");
            assertRed(pausedA, 1.5);
            prepare(1, 3, 0, 4, 23999, 0);
            assertRed(render(.5), 1.75);
            prepare(1, 3, .5, 4, 123, 2);
            assertRed(render(123), 2);
            prepareRoll(0, 90, true);
            assertRoll(render(.5), 45);
            prepareRoll(45, 90, false);
            assertRoll(render(.75), 45);
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
            System.out.println(
                    "PASS: native shader compilation, transport pixels, sub-tick zoom, pause,"
                            + " seek/easing, clock rollover and interpolated camera roll");
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    static int program(String vertex, String fragment, String config) {
        int p = glCreateProgram();
        glAttachShader(p, shader(GL_VERTEX_SHADER, vertex));
        glAttachShader(p, shader(GL_FRAGMENT_SHADER, fragment));
        glLinkProgram(p);
        if (glGetProgrami(p, GL_LINK_STATUS) == 0) throw new AssertionError(glGetProgramInfoLog(p));
        int index = glGetUniformBlockIndex(p, "Globals");
        if (index != GL_INVALID_INDEX) glUniformBlockBinding(p, index, 0);
        glUniformBlockBinding(p, glGetUniformBlockIndex(p, config), 1);
        return p;
    }

    static int shader(int kind, String source) {
        if (source.contains("#moj_import"))
            throw new AssertionError(
                    "Unsupported legacy shader directive; run the native compiler probe");
        int s = glCreateShader(kind);
        glShaderSource(
                s,
                source.replace("#include <minecraft:globals.glsl>", globals)
                        .replace("gl_VertexIndex", "gl_VertexID"));
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == 0)
            throw new AssertionError(glGetShaderInfoLog(s));
        return s;
    }

    static int texture() {
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return t;
    }

    static void globals(double tick) {
        ByteBuffer b = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
        b.putFloat(28, (float) (tick / 24000));
        b.putFloat(32, W);
        b.putFloat(36, H);
        glBindBuffer(GL_UNIFORM_BUFFER, globalUbo);
        glBufferData(GL_UNIFORM_BUFFER, b, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, globalUbo);
    }

    static void config(float a, float b, float c, float d) {
        glBindBuffer(GL_UNIFORM_BUFFER, configUbo);
        glBufferData(GL_UNIFORM_BUFFER, new float[] {a, b, c, d}, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, 1, configUbo);
    }

    static void prepare(
            double from, double to, double progress, float duration, int start, int easing) {
        controlCount = 13;
        ByteBuffer pixels = ByteBuffer.allocateDirect(W * H * 4);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                pixels.put((byte) Math.round(x * 255f / (W - 1)))
                        .put((byte) Math.round(y * 255f / (H - 1)))
                        .put((byte) 80)
                        .put((byte) 255);
        pixels.flip();
        glBindTexture(GL_TEXTURE_2D, input);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        ByteBuffer controls = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN);
        controls.putShort((short) Math.round((from - 1) / 3 * 65535))
                .putShort((short) Math.round((to - 1) / 3 * 65535))
                .putShort((short) Math.round(progress * 65535))
                .putFloat(duration)
                .putShort((short) start)
                .put((byte) easing);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, input, 0);
        globals(0);
        glUseProgram(writer);
        for (int slot = 0; slot < 13; slot++) {
            config(slot, Byte.toUnsignedInt(controls.array()[slot]), 0, 0);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }
        ByteBuffer check = ByteBuffer.allocateDirect(13 * 4);
        glReadPixels(0, 0, 13, 1, GL_RGBA, GL_UNSIGNED_BYTE, check);
        for (int i = 0; i < 13; i++) {
            if (check.get(i * 4 + 3) != controls.array()[i]
                    || Byte.toUnsignedInt(check.get(i * 4)) != 255
                    || Byte.toUnsignedInt(check.get(i * 4 + 1)) != 0)
                throw new AssertionError("Control byte failed: " + i);
        }
    }

    static void prepareRoll(double from, double to, boolean moving) {
        prepare(1, 1, 0, 0, 0, 0);
        controlCount = 18;
        var bytes =
                ByteBuffer.allocate(5)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) Math.round(from / 180 * 32767))
                        .putShort((short) Math.round(to / 180 * 32767))
                        .put((byte) (moving ? 1 : 0))
                        .array();
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, input, 0);
        glUseProgram(writer);
        for (int i = 0; i < 5; i++) {
            config(13 + i, Byte.toUnsignedInt(bytes[i]), 0, 0);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }
    }

    static float render(double tick) {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, output, 0);
        globals(tick);
        glUseProgram(renderer);
        config(1, 4, controlCount, 24000);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, input);
        glUniform1i(glGetUniformLocation(renderer, "InSampler"), 0);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        float[] sample = new float[4];
        glReadPixels(20, 32, 1, 1, GL_RGBA, GL_FLOAT, sample);
        return sample[0];
    }

    static void assertRoll(float actual, double degrees) {
        double radians = Math.toRadians(degrees),
                x = (20.5 / W - .5) * 2,
                y = 32.5 / H - .5,
                uv = (Math.cos(radians) * x + Math.sin(radians) * y) / 2 + .5,
                expected = (uv * W - .5) / (W - 1);
        if (Math.abs(actual - expected) > .006)
            throw new AssertionError(
                    "Roll " + degrees + ": expected " + expected + ", got " + actual);
    }

    static void assertRed(float actual, double zoom) {
        double uv = ((20.5 / W) - .5) / zoom + .5, expected = (uv * W - .5) / (W - 1);
        if (Math.abs(actual - expected) > .006)
            throw new AssertionError("Zoom " + zoom + ": expected " + expected + ", got " + actual);
    }
}
