import static org.lwjgl.opengl.GL33.*;

import java.nio.*;
import java.nio.file.*;

/** OpenGL setup shared by the standalone effect acceptance probe. */
class GpuProbeSupport {
    static int W = 128, H = 64;
    static String globals, screen;
    static Path shaders;
    static int globalUbo, configUbo, writer, input, output, fbo;

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
}
