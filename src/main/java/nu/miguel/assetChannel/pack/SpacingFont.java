package nu.miguel.assetChannel.pack;

import com.google.gson.*;
import nu.miguel.assetChannel.texturepack.TexturePack;
import java.nio.file.*;

/** Resource-pack space provider used by the HUD compositor for exact cursor movement. */
public final class SpacingFont {
    public static final String KEY = "assetchannel:spacing";
    public static final int MIN = -512, MAX = 512, BASE = 0xE000;
    private SpacingFont() {}
    public static String character(int advance) {
        if (advance < MIN || advance > MAX || advance == 0) throw new IllegalArgumentException("Unsupported spacing advance " + advance);
        return Character.toString(BASE + advance - MIN);
    }
    /** Shared MiniMessage resolver for HUDs and consuming plugins' inventory titles. */
    public static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver resolver() {
        return net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver("space", (args, context) -> {
            int pixels = Integer.parseInt(args.popOr("Pixel advance required").value());
            if (Math.abs((long)pixels) > 16384) throw new IllegalArgumentException("Spacing must be within +/-16384");
            return net.kyori.adventure.text.minimessage.tag.Tag.inserting(component(pixels));
        });
    }
    public static net.kyori.adventure.text.Component component(int pixels) {
        if (Math.abs((long)pixels) > 16384) throw new IllegalArgumentException("Spacing must be within +/-16384");
        var result = net.kyori.adventure.text.Component.empty();
        while (pixels != 0) {
            int step = Math.max(MIN, Math.min(MAX, pixels));
            result = result.append(net.kyori.adventure.text.Component.text(character(step)).font(net.kyori.adventure.key.Key.key(KEY)));
            pixels -= step;
        }
        return result;
    }
    public static boolean build(TexturePack pack) {
        try {
            JsonObject advances = new JsonObject();
            for (int i=MIN; i<=MAX; i++) if (i != 0) advances.addProperty(character(i), i);
            JsonObject provider = new JsonObject(); provider.addProperty("type", "space"); provider.add("advances", advances);
            JsonArray providers = new JsonArray(); providers.add(provider);
            JsonObject root = new JsonObject(); root.add("providers", providers);
            Path path = pack.getAssetPath().resolve("font/spacing.json"); Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root)); return true;
        } catch (Exception e) { return false; }
    }
}
