package nu.miguel.assetChannel.pack;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class NativePackFixture {
    private NativePackFixture() {}

    public static Path create(Path root) throws Exception {
        for (String name : new String[]{"button", "cube"}) {
            write(root.resolve("demo/items/" + name + ".json"),
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:ui/" + name + "\"}}");
            write(root.resolve("demo/models/ui/" + name + ".json"),
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:ui/" + name + "\"}}");
            Path texture = root.resolve("demo/textures/ui/" + name + ".png");
            Files.createDirectories(texture.getParent());
            ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", texture.toFile());
        }
        write(root.resolve("minecraft/atlases/items.json"),
                "{\"sources\":[{\"type\":\"minecraft:directory\",\"source\":\"ui\",\"prefix\":\"ui/\"}]}");
        return root;
    }

    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }
}
