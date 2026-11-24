import com.mojang.blaze3d.font.*;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;

import org.lwjgl.system.*;
import org.lwjgl.util.freetype.*;

import java.awt.font.FontRenderContext;
import java.nio.*;
import java.nio.file.*;

/** Uses the actual vanilla FreeType provider without opening a client window. */
public class NativeScreenFontProbe {
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        byte[] bytes = Files.readAllBytes(file);
        java.awt.Font awt;
        try (var in = Files.newInputStream(file)) {
            awt = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in).deriveFont(11f);
        }
        for (int y : new int[] {-256, -80, 0, 64})
            try (var stack = MemoryStack.stackPush()) {
                var memory = MemoryUtil.memAlloc(bytes.length);
                memory.put(bytes).flip();
                var pointer = stack.mallocPointer(1);
                FreeTypeUtil.assertError(
                        FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0, pointer),
                        "Load imported font");
                var face = FT_Face.create(pointer.get(0));
                FreeTypeUtil.assertError(
                        FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE),
                        "Select Unicode");
                try (var provider = new TrueTypeGlyphProvider(memory, face, 11, 2, 0, y, "")) {
                    var glyph = provider.getGlyph('A');
                    float expected =
                            awt.createGlyphVector(new FontRenderContext(null, true, true), "A")
                                    .getGlyphMetrics(0)
                                    .getAdvance();
                    float actual = glyph.info().getAdvance();
                    if (Math.abs(actual - expected) > .03)
                        throw new AssertionError(
                                "Glyph metric mismatch: " + actual + " vs " + expected);
                    glyph.bake(
                            new UnbakedGlyph.Stitcher() {
                                public BakedGlyph getMissing() {
                                    return null;
                                }

                                public BakedGlyph stitch(GlyphInfo info, GlyphBitmap bitmap) {
                                    if (bitmap.getPixelHeight() > 64)
                                        throw new AssertionError("Shift expanded glyph allocation");
                                    System.out.println(
                                            "PASS y="
                                                    + y
                                                    + " advance="
                                                    + actual
                                                    + " bearingTop="
                                                    + bitmap.getBearingTop()
                                                    + " glyphHeight="
                                                    + bitmap.getPixelHeight());
                                    return null;
                                }
                            });
                }
            }
        FreeTypeUtil.destroy();
    }
}
