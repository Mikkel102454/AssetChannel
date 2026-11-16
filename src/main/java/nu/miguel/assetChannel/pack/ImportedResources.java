package nu.miguel.assetChannel.pack;

import com.google.gson.*;
import net.kyori.adventure.key.Key;
import nu.miguel.assetChannel.api.ModelAsset;
import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.texturepack.TexturePack;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Native resource tree, kept separate from generated resources. Source bytes are never modified. */
public final class ImportedResources implements Asset {
    private final Map<String, byte[]> files;
    private final Map<String, JsonObject> json;
    private ImportedResources(Map<String, byte[]> files, Map<String, JsonObject> json) {
        this.files = files; this.json = json;
    }
    public static ImportedResources scan(Path assetsRoot) throws IOException {
        return scanLayers(List.of(assetsRoot));
    }
    static ImportedResources scanLayers(List<Path> roots) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        Map<String, JsonObject> json = new TreeMap<>();
        for(Path assetsRoot:roots) {
        if (Files.exists(assetsRoot)) try (var paths = Files.walk(assetsRoot)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Import symlinks are unsupported: " + path);
                if (!Files.isRegularFile(path)) continue;
                String name = assetsRoot.relativize(path).toString().replace('\\', '/');
                if (!name.matches("[a-z0-9_-]+/[a-z0-9/._-]+") || Arrays.asList(name.split("/")).contains(".."))
                    throw new IllegalArgumentException("Invalid import resource path: " + name);
                byte[] bytes = Files.readAllBytes(path); files.put(name, bytes);
                if (name.endsWith(".json") || name.endsWith(".mcmeta")) try {
                    json.put(name, JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (RuntimeException error) { throw new IllegalArgumentException("Invalid import JSON: " + name, error); }
            }
        }
        }
        ImportedResources result = new ImportedResources(Collections.unmodifiableMap(files), json);
        result.validate(); return result;
    }
    public List<ModelAsset> models() {
        return json.keySet().stream().filter(p -> isKind(p, "items") && p.endsWith(".json"))
                .map(p -> p.substring(0, p.indexOf('/')) + ":" + p.substring(p.indexOf("/items/") + 7, p.length() - 5))
                .map(id -> new ModelAsset(id, Key.key(id))).toList();
    }
    public List<nu.miguel.assetChannel.api.ResourceAsset> resources() {
        return files.entrySet().stream().map(entry -> {
            String path=entry.getKey(); int slash=path.indexOf('/'); String relative=path.substring(slash+1);
            return new nu.miguel.assetChannel.api.ResourceAsset(Key.key(path.substring(0,slash),relative),
                    nu.miguel.assetChannel.api.ResourceKind.of(relative),entry.getValue().length);
        }).toList();
    }
    public List<nu.miguel.assetChannel.api.FontAsset> fonts() {
        return json.keySet().stream().filter(p -> isKind(p, "font") && p.endsWith(".json"))
                .map(p -> p.substring(0, p.indexOf('/')) + ":" + p.substring(p.indexOf("/font/") + 6, p.length() - 5))
                .map(id -> new nu.miguel.assetChannel.api.FontAsset(id, Key.key(id))).toList();
    }
    private void validate() {
        for (var entry : json.entrySet()) {
            String path = entry.getKey(); JsonObject value = entry.getValue();
            if (isKind(path, "items") && (!value.has("model") || !value.get("model").isJsonObject()))
                throw new IllegalArgumentException("Item definition needs a model object: " + path);
            if (isKind(path, "models") || isKind(path, "items")) validateReferences(value, path);
            if (isKind(path, "models")) visitParent(path, new HashSet<>());
            if (isKind(path, "font") && value.has("providers")) for (JsonElement element : value.getAsJsonArray("providers")) {
                JsonObject provider = element.getAsJsonObject();
                String type = provider.get("type").getAsString().replace("minecraft:", "");
                if (type.equals("bitmap") || type.equals("ttf"))
                    require(provider.get("file").getAsString(), type.equals("bitmap") ? "textures" : "font", "", path);
                if (type.equals("bitmap")) validateBitmap(provider, path);
                if (type.equals("reference")) require(provider.get("id").getAsString(), "font", ".json", path);
            }
        }
    }
    private void validateBitmap(JsonObject provider, String source) {
        String path = resource(provider.get("file").getAsString(), "textures", "");
        byte[] bytes = files.get(path);
        if (bytes == null) return; // Vanilla texture supplied by the client.
        try {
            var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null) throw new IllegalArgumentException("Unreadable bitmap font texture: " + path);
            var chars = provider.getAsJsonArray("chars");
            int rows = chars.size(), columns = rows == 0 ? 0 : chars.get(0).getAsString().codePointCount(0, chars.get(0).getAsString().length());
            if (rows == 0 || columns == 0) throw new IllegalArgumentException("Empty bitmap glyph grid: " + source);
            for (var row : chars) {
                String text = row.getAsString();
                if (text.codePointCount(0, text.length()) != columns) throw new IllegalArgumentException("Unequal bitmap glyph rows: " + source);
            }
            if (image.getWidth() % columns != 0 || image.getHeight() % rows != 0)
                throw new IllegalArgumentException("Bitmap glyph grid does not divide texture: " + source);
            if (image.getWidth() / columns > 256 || image.getHeight() / rows > 256)
                throw new IllegalArgumentException("Bitmap glyph exceeds Minecraft's 256x256 pixel font atlas: " + source + "; split it into tiles (render height does not shrink source pixels)");
        } catch (IOException error) { throw new IllegalArgumentException("Invalid bitmap font texture: " + path, error); }
    }
    private static boolean isKind(String path, String kind) {
        int slash = path.indexOf('/');
        return slash >= 0 && path.startsWith(kind + "/", slash + 1);
    }
    private void validateReferences(JsonElement value, String source) {
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(e -> validateReferences(e, source)); return; }
        if (!value.isJsonObject()) return;
        JsonObject object = value.getAsJsonObject();
        if (object.has("parent") && object.get("parent").isJsonPrimitive()) require(object.get("parent").getAsString(), "models", ".json", source);
        if (object.has("type") && object.get("type").getAsString().equals("minecraft:model") && object.has("model"))
            require(object.get("model").getAsString(), "models", ".json", source);
        if (object.has("textures") && object.get("textures").isJsonObject()) {
            for (JsonElement texture : object.getAsJsonObject("textures").asMap().values()) {
                String ref = texture.isJsonObject() ? texture.getAsJsonObject().get("sprite").getAsString() : texture.getAsString();
                if (!ref.startsWith("#")) require(ref, "textures", ".png", source);
            }
        }
        object.asMap().values().forEach(e -> validateReferences(e, source));
    }
    private String resource(String ref, String folder, String suffix) {
        Key key = Key.key(ref);
        if (key.value().contains("..")) throw new IllegalArgumentException("Invalid resource reference: " + ref);
        return key.namespace() + "/" + folder + "/" + key.value() + suffix;
    }
    private void require(String ref, String folder, String suffix, String source) {
        String path = resource(ref, folder, suffix);
        // Vanilla dependencies are supplied by Minecraft, unless explicitly overridden here.
        if (!path.startsWith("minecraft/") && !files.containsKey(path))
            throw new IllegalArgumentException(source + " references missing " + path);
    }
    private void visitParent(String path, Set<String> visiting) {
        if (!visiting.add(path)) throw new IllegalArgumentException("Model parent cycle: " + path);
        JsonObject value = json.get(path);
        if (value != null && value.has("parent")) visitParent(resource(value.get("parent").getAsString(), "models", ".json"), visiting);
        visiting.remove(path);
    }
    public void copyTo(Path assetsRoot) throws IOException {
        for (String name : files.keySet()) if (Files.exists(assetsRoot.resolve(name)))
            throw new IllegalArgumentException("Imported resource conflicts with generated resource: " + name);
        for (var entry : files.entrySet()) {
            Path target = assetsRoot.resolve(entry.getKey()); Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue(), StandardOpenOption.CREATE_NEW);
        }
    }
    @Override public String getId() { return "native-imports"; }
    @Override public boolean build(TexturePack pack) {
        try { copyTo(pack.getRootPath().resolve("assets")); return true; }
        catch (IOException error) { throw new IllegalStateException("Could not stage imports", error); }
    }
}
