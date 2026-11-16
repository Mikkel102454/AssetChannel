package nu.miguel.assetChannel.pack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class PackExtrasTest {
    @TempDir Path root;
    void put(String file,String value)throws Exception{Path p=root.resolve(file);Files.createDirectories(p.getParent());Files.writeString(p,value);}
    @Test void compatibleOverlayResolvesDependenciesAgainstBaseAssets()throws Exception{
        put("pack/assets/demo/models/thing.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:base\"}}");
        put("pack/assets/demo/textures/base.png","texture");
        put("pack/variant/assets/demo/items/thing.json","{\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:thing\"}}");
        put("pack/pack.mcmeta","{\"pack\":{\"pack_format\":75},\"overlays\":{\"entries\":[{\"min_format\":97,\"max_format\":97,\"directory\":\"variant\"}]}}");
        assertEquals("demo:thing",PackExtras.scan(root.resolve("pack"),root.resolve("assets")).resources().models().getFirst().id());
    }
    @Test void ignoresIncompatibleOverlayForRuntimeCatalogAndRejectsTraversal()throws Exception{
        put("pack/old/assets/demo/items/broken.json","not json");
        put("pack/pack.mcmeta","{\"overlays\":{\"entries\":[{\"min_format\":75,\"max_format\":75,\"directory\":\"old\"}]}}");
        assertTrue(PackExtras.scan(root.resolve("pack"),root.resolve("assets")).resources().models().isEmpty());
        put("pack/pack.mcmeta","{\"overlays\":{\"entries\":[{\"min_format\":97,\"max_format\":97,\"directory\":\"../escape\"}]}}");
        assertThrows(IllegalArgumentException.class,()->PackExtras.scan(root.resolve("pack"),root.resolve("assets")));
    }
}
