import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;

import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Compiles unmodified imported shaders through the installed Minecraft client's compiler. */
public final class NativeShaderProbe {
    public static void main(String[] args) throws Exception {
        // Compilation only consults device workaround flags. Supply those without
        // launching a game window; the separate GPU probe checks actual rendering.
        var info =
                new com.mojang.renderpearl.api.device.DeviceInfo(
                        "compiler probe",
                        "",
                        "",
                        false,
                        "probe",
                        1,
                        null,
                        null,
                        Set.of(),
                        new com.mojang.renderpearl.api.device.HintsAndWorkarounds(
                                false, false, false, false),
                        null);
        var device =
                java.lang.reflect.Proxy.newProxyInstance(
                        NativeShaderProbe.class.getClassLoader(),
                        new Class[] {com.mojang.renderpearl.api.device.GpuDevice.class},
                        (p, m, a) -> {
                            if (m.getName().equals("getDeviceInfo")) return info;
                            throw new UnsupportedOperationException(m.getName());
                        });
        var field = com.mojang.blaze3d.systems.RenderSystem.class.getDeclaredField("DEVICE");
        field.setAccessible(true);
        field.set(null, device);
        Path assets = Path.of(args[0]);
        try (var vanilla = new ZipFile(args[1]);
                var source = new Sources(assets, vanilla)) {
            for (boolean zeroToOne : new boolean[] {false, true})
                try (var compiler = new GlslCompiler(zeroToOne, true)) {
                    for (var stage : source.stages()) {
                        String shader = stage.shader();
                        var type = stage.type();
                        var id = Identifier.parse(shader);
                        try (var module =
                                compiler.compileToSpv(
                                        shader,
                                        source.getShader(id, type),
                                        type,
                                        ShaderDefines.EMPTY,
                                        source)) {
                            module.reflect();
                            System.out.println(
                                    "PASS Minecraft compiler: "
                                            + shader
                                            + " (zero-to-one="
                                            + zeroToOne
                                            + ", "
                                            + module.spv().remaining()
                                            + " SPIR-V bytes)");
                        }
                    }
                }
        }
    }

    private static final class Sources implements ShaderSource {
        final Path assets;
        final ZipFile vanilla, pack;
        final Map<Identifier, CachedIncludeSource> includes = new HashMap<>();

        Sources(Path assets, ZipFile vanilla) throws Exception {
            this.assets = assets;
            this.vanilla = vanilla;
            pack = Files.isRegularFile(assets) ? new ZipFile(assets.toFile()) : null;
        }

        String resource(Identifier id, String folder, String extension) {
            String path = id.getNamespace() + "/shaders/" + folder + id.getPath() + extension;
            try {
                if (pack != null && pack.getEntry("assets/" + path) != null)
                    return new String(
                            pack.getInputStream(pack.getEntry("assets/" + path)).readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                if (pack == null && Files.isRegularFile(assets.resolve(path)))
                    return Files.readString(assets.resolve(path));
                if (id.getNamespace().equals("minecraft"))
                    return new String(
                            vanilla.getInputStream(
                                            Objects.requireNonNull(
                                                    vanilla.getEntry("assets/" + path), path))
                                    .readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                throw new java.io.FileNotFoundException(path);
            } catch (Exception error) {
                throw new IllegalStateException("Cannot read shader resource " + path, error);
            }
        }

        public String getShader(Identifier id, ShaderType type) {
            return resource(id, "", type == ShaderType.VERTEX ? ".vsh" : ".fsh");
        }

        public CachedIncludeSource getInclude(Identifier id) {
            return includes.computeIfAbsent(
                    id, key -> CachedIncludeSource.create(key, resource(key, "include/", "")));
        }

        record Stage(String shader, ShaderType type) {}

        List<Stage> stages() throws Exception {
            var stages = new LinkedHashSet<Stage>();
            var documents = new ArrayList<String>();
            if (pack != null) {
                var entries = pack.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.getName().startsWith("assets/")
                            && entry.getName().contains("/post_effect/")
                            && entry.getName().endsWith(".json"))
                        documents.add(
                                new String(
                                        pack.getInputStream(entry).readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                }
            } else
                try (var paths = Files.walk(assets)) {
                    for (var path :
                            paths.filter(p ->
                                            p.toString()
                                                            .replace('\\', '/')
                                                            .contains("/post_effect/")
                                                    && p.toString().endsWith(".json"))
                                    .toList()) documents.add(Files.readString(path));
                }
            for (String document : documents) {
                var json = com.google.gson.JsonParser.parseString(document).getAsJsonObject();
                for (var raw : json.getAsJsonArray("passes")) {
                    var pass = raw.getAsJsonObject();
                    stages.add(
                            new Stage(pass.get("vertex_shader").getAsString(), ShaderType.VERTEX));
                    stages.add(
                            new Stage(
                                    pass.get("fragment_shader").getAsString(),
                                    ShaderType.FRAGMENT));
                }
            }
            if (pack == null)
                try (var paths = Files.walk(assets)) {
                    for (var path :
                            paths.filter(p ->
                                            p.toString()
                                                            .replace((char) 92, '/')
                                                            .contains("/shaders/post/")
                                                    && (p.toString().endsWith(".vsh")
                                                            || p.toString().endsWith(".fsh")))
                                    .toList()) {
                        String relative =
                                assets.relativize(path).toString().replace((char) 92, '/');
                        int slash = relative.indexOf('/');
                        String id =
                                relative.substring(0, slash)
                                        + ":"
                                        + relative.substring(slash + 9, relative.length() - 4);
                        stages.add(
                                new Stage(
                                        id,
                                        path.toString().endsWith(".vsh")
                                                ? ShaderType.VERTEX
                                                : ShaderType.FRAGMENT));
                    }
                }
            return List.copyOf(stages);
        }

        public void close() {
            includes.values().forEach(CachedIncludeSource::close);
            if (pack != null)
                try {
                    pack.close();
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
        }
    }
}
