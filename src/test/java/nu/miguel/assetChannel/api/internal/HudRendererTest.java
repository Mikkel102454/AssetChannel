package nu.miguel.assetChannel.api.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nu.miguel.assetChannel.asset.Hud;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HudRendererTest {
    @Test void clipsIconsUsingTheirActualWidthAndStopsAtFirstOverflow() throws Exception {
        Path file=directory.resolve("clip.yml"); Files.writeString(file,"""
                surface: action-bar
                layout: {content: "<icon:wide>i", width: 10, overflow: clip}
                """);
        var icon = new nu.miguel.assetChannel.api.IconAsset("wide", net.kyori.adventure.key.Key.key("assetchannel:icons"), 0xE001, 16, 8, 30);
        var renderer = new HudRenderer(Map.of("wide", icon));
        var result = renderer.render(Hud.load("clip", file), Map.of(), false);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(result);
        assertFalse(plain.contains(icon.character()));
        assertFalse(plain.contains("i"));
    }
    @Test void explicitSpacingMovesCursorByExactAdvance() {
        var renderer = new HudRenderer(Map.of());
        assertEquals(5, renderer.width(renderer.parseAssetText("a<space:-6>b")));
    }
    @TempDir Path directory;
    @Test void validatesRequiredAndUnknownVariablesAndInsertsComponentsDirectly()throws Exception{
        Path file=directory.resolve("hud.yml");Files.writeString(file,"""
                surface: action-bar
                variables:
                  heading: {required: true}
                  body: {default: "none"}
                layout: {content: "<red><var:heading></red> <var:body>"}
                """);
        Hud hud=Hud.load("test",file);HudRenderer renderer=new HudRenderer(Map.of());
        assertNull(renderer.resolve(hud,Map.of()));
        assertNull(renderer.resolve(hud,Map.of("heading",Component.text("ok"),"typo",Component.empty())));
        Component injected=Component.text("<bold>literal</bold>",NamedTextColor.BLUE);
        Component rendered=renderer.render(hud,renderer.resolve(hud,Map.of("heading",injected)),false);
        assertTrue(rendered.toString().contains("<bold>literal</bold>"));
        assertTrue(rendered.toString().contains("color=blue"));
    }
}
