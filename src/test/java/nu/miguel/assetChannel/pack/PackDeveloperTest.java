package nu.miguel.assetChannel.pack;

import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.asset.Icon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackDeveloperTest {
    @TempDir Path directory;

    @Test void discoversMp3AndOggCaseInsensitively() throws Exception {
        Files.write(directory.resolve("effect.ogg"), new byte[]{1});
        Files.write(directory.resolve("effect.MP3"), new byte[]{1});
        Files.write(directory.resolve("ignored.wav"), new byte[]{1});

        assertEquals(List.of("effect", "effect"), PackDeveloper.scanSoundFiles(directory).stream()
                .map(AssetFile::id).sorted().toList());
    }

    @Test void rejectsSameStemSoundIds() {
        Asset first = new StubSound("effect");
        Asset second = new StubSound("effect");
        assertThrows(IllegalArgumentException.class,
                () -> PackDeveloper.validateAssets(List.of(first, second)));
    }

    @Test void generatesStablePrivateUseUnicodeWhenConfigOmitsIt() throws Exception {
        Icon first=icon("ui.banner",null,"first");
        Icon second=icon("ui.banner",null,"second");
        PackDeveloper.assignIconCodePoints(List.of(first));
        PackDeveloper.assignIconCodePoints(List.of(second));
        assertEquals(first.getCodePoint(),second.getCodePoint());
        assertEquals(true,first.getCodePoint()>=PackDeveloper.GENERATED_ICON_FIRST&&first.getCodePoint()<=PackDeveloper.GENERATED_ICON_LAST);
    }

    @Test void automaticUnicodeAvoidsConfiguredValues() throws Exception {
        String id="ui.auto";int capacity=PackDeveloper.GENERATED_ICON_LAST-PackDeveloper.GENERATED_ICON_FIRST+1;
        int preferred=PackDeveloper.GENERATED_ICON_FIRST+Math.floorMod(id.hashCode(),capacity);
        Icon explicit=icon("ui.explicit",String.format("%04X",preferred),"explicit");
        Icon automatic=icon(id,null,"automatic");
        PackDeveloper.assignIconCodePoints(List.of(explicit,automatic));
        assertEquals(preferred,explicit.getCodePoint());
        org.junit.jupiter.api.Assertions.assertNotEquals(preferred,automatic.getCodePoint());
    }

    private Icon icon(String id,String unicode,String stem)throws Exception{
        Path png=directory.resolve(stem+".png"),yaml=directory.resolve(stem+".yml");
        ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),"png",png.toFile());
        Files.writeString(yaml,(unicode==null?"":"unicode: "+unicode+"\n")+"height: 16\nascent: 15\n");
        return new Icon(id,png,yaml);
    }

    private record StubSound(String id) implements Asset {
        @Override public boolean build(nu.miguel.assetChannel.texturepack.TexturePack texturePack) { return true; }
        @Override public String getId() { return id; }
    }
}
