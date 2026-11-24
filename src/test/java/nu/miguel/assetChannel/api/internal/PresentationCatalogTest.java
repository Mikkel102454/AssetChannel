package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nu.miguel.assetChannel.asset.EffectCurve;
import nu.miguel.assetChannel.asset.Font;
import nu.miguel.assetChannel.pack.CaptionFonts;
import nu.miguel.assetChannel.pack.ImportedResources;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

class PresentationCatalogTest {
    @Test
    void importedPresentationAssetsExposeStableIdsAndDisappearOnCatalogReload() throws Exception {
        var imported = ImportedResources.scan(Path.of("examples/cinematic-pack/assets"));
        var font =
                new Font(
                        "ui.subtitle",
                        Path.of("examples/cinematic-pack/sources/fonts/ui/subtitle.ttf"));
        var captions = CaptionFonts.compile(imported, List.of(font));
        var screens =
                nu.miguel.assetChannel.pack.ScreenFonts.compile(
                        captions.resources(), List.of(font));
        var curve =
                EffectCurve.load(
                        "zoom", Path.of("examples/cinematic-pack/definitions/effects/zoom.yml"));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var api = new AssetChannelApiImpl();
            api.loadAssets(List.of(font, captions, screens, curve, screens.resources()));
            assertEquals(1, api.screenFonts().size());
            assertSame(
                    api.screenFont("ui.subtitle").orElseThrow(),
                    api.screenFont("assetchannel:ui/subtitle").orElseThrow());
            var selected = api.captionFont("ui.subtitle").orElseThrow();
            assertSame(selected, api.captionFont("assetchannel:ui/subtitle").orElseThrow());
            assertSame(selected, api.captionFont("assetchannel:ui.subtitle").orElseThrow());
            assertEquals(1, api.captionFonts().size());
            assertNotNull(selected.box());
            assertTrue(api.font("ui.subtitle").isPresent());
            assertTrue(api.fonts().stream().noneMatch(f -> f.id().contains("generated/captions")));
            assertEquals(1, api.effectCurves().size());
            assertEquals(curve.descriptor(), api.effectCurve("zoom").orElseThrow());
            var report = api.preflight();
            assertTrue(report.checked());
            assertTrue(report.valid());
            assertEquals(api.packRevision(), report.revision());
            assertEquals(api.resources().size(), report.resources());
            assertTrue(report.shaders() > 0);
            var oldRevision = api.packRevision();
            api.loadAssets(List.of());
            assertTrue(api.screenFonts().isEmpty());
            assertTrue(api.screenFont("ui.subtitle").isEmpty());
            assertNotEquals(oldRevision, api.packRevision());
            assertTrue(api.captionFonts().isEmpty());
            assertTrue(api.effectCurves().isEmpty());
            assertTrue(api.captionFont("ui.subtitle").isEmpty());
            assertTrue(api.effectCurve("zoom").isEmpty());
        }
    }
}
