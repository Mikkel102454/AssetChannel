# Fonts, icons, captions and screen text

Import font media under `sources/fonts/`. A matching `.yml` sidecar controls its rendering; the same source can provide a normal font, decorated captions and positioned screen labels. Imported native font JSON under `assets/<namespace>/font/` is also cataloged, but deriving captions/screen rows requires readable TTF/OTF source media.

```text
sources/fonts/ui/subtitle.ttf
sources/fonts/ui/subtitle.yml
sources/icons/ui/guide.png
sources/icons/ui/guide.yml
assets/myserver/textures/gui/caption_box.png
```

## Complete example sidecar

```yaml
size: 11
oversample: 2
shift: [0, 0]
caption:
  enabled: true
  size: 11
  outline-width: 1
  outline-color: '#000000'
  shadow-offset: 1
  shadow-color: '#00000099'
  box:
    texture: myserver:gui/caption_box
    border: 2
    padding: 4
screen:
  enabled: true
  size: 11
  min-y: -256
  max-y: 64
  step: 2
```

The source ID is `ui.subtitle`. Native font aliases include `assetchannel:ui.subtitle` and its canonical nested path. Use the catalog to resolve descriptors instead of guessing generated font keys.

## Caption variants

`CaptionFontAsset` supplies natural character advances, rendering variants and an optional sliced box. `CaptionAppearance` selects outline, shadow and box independently. A box requires an imported texture in the sidecar; selecting an unavailable decoration fails validation. `CaptionLayout.wrap` measures imported glyphs, and `compose` combines up to two caption lines. Pass full-line widths when revealing characters so the box and centring remain stable. Unsupported characters normalize to `?`; extend `caption.characters` if needed.

Caption sizes are 4–64, outline/shadow widths 0–4. Colours use `#RRGGBB` or `#RRGGBBAA`. The bitmap atlas derives from the imported font without rewriting it. Decoration padding is compensated rather than inserted as extra letter spacing.

## Positioned labels

`ScreenFontAsset` provides bounded shifted variants and character advances. `ScreenTextLayout.place` creates a zero-advance layer that can be combined with other labels on one reserved action bar. X is relative to screen centre; negative Y moves upward from the action-bar text origin. These are GUI pixels, not responsive edge anchors. Size is controlled by the font import. Defaults are Y -256..64 with a 2-pixel step; rows snap to that step. Configured ranges are bounded to -1024..1024 and at most 513 rows.

`screen.characters` defaults to the caption character set. `screen.enabled: false` disables these derived variants. Screen outlines/shadows use the chosen imported font; no core shader is overridden.

## Icons

An icon sidecar must provide `height` and `ascent`:

```yaml
height: 16
ascent: 13
```

`sources/icons/ui/guide.png` becomes icon `ui.guide`. Its descriptor exposes the assigned character and font. Consumers should use those fields, not hardcoded Unicode slots. Combine icons with text using HUD templates or Adventure components.

## Ownership and client checks

Reserve one action-bar/title lease and combine your layers there. Refresh static action-bar content at least once per second and close the lease when done. A cinematic session already owns its caption surface; use its screen-text API rather than acquiring a competing lease.

GUI scale and Minecraft's text-background option affect appearance. Test proportional letters, unsupported glyphs, outlines, boxes and multi-line labels with the actual accepted pack. See [cinematic API examples](cinematics.md) and [HUD templates](hud.md).
