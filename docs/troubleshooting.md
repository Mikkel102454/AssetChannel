# Troubleshooting

## Troubleshooting

### Players cannot download the pack

- Confirm `overrides.pack.host` is reachable from the players' computers.
- Open or forward `overrides.pack.port` through the firewall and router.
- Do not advertise `127.0.0.1` to remote players.
- Check the server log for the pack URL, SHA-1, and HTTP startup errors.

### Music still has small seams

Minecraft starts separate sound events on client receipt and does not expose sample-accurate scheduling. Try these in order:

1. Keep five-second synchronized clips but tune handoff lead between `50` and `100` ms.
2. Use a slightly longer interval if the use case permits it.
3. Disable auto-split for completely smooth streamed playback.

### Pack generation is slow

The first build creates listener and spatial variants. Later builds reuse the content-addressed cache. Changing FFmpeg quality or source content intentionally invalidates the affected entries.

### A generated pack is rejected

Check that every source is a valid MP3 or OGG Vorbis file, every supported audio file appears exactly once in `order`, IDs are lowercase, and staged soundtracks do not mix root audio with a `stages/` directory. Files with other extensions are not audio inputs, and invalid or audio-less MP3 files make pack generation fail.

For native assets, check the reported JSON/dependency/path error. Bitmap font source cells must fit the 256x256 glyph atlas, even when rendered smaller. See [asset troubleshooting](assets/native.md#troubleshooting) for missing glyphs, lost inventory-title fonts and missing item textures.

## Cinematic fonts or models are missing

Check source IDs in the appropriate `rigs`, `timelineAudio`, `captionFonts` or `screenFonts` catalog. A normal font JSON does not automatically have derived caption/screen variants. Fonts need readable source media and enabled profile settings. Rig textures are imported with the cube model; unsupported meshes/controllers/expressions must be baked before import.

## Shader rejection or disconnect

Use Minecraft 26.3 include syntax, `#include <namespace:file.glsl>`. Obsolete `#moj_import`, missing custom shader references, bad post-effect targets or malformed uniforms can reject publication. Structural preflight does not replace compilation on a real client/GPU. Run `clientPreflight` or the native probe against the actual served ZIP; retain the old active pack until the replacement validates.

## Positioned labels are off screen

Check GUI scale, the selected font's Y range/row step and the action-bar coordinate origin. Labels do not use responsive screen-edge anchors. A requested box requires a caption font with imported box metadata. Unsupported characters become `?` until included in the font's configured character set.

## A cinematic reports preparation errors

That is handled by AnimationChannel, not pack generation. Check its saved scene origin, built/generated map, chunk budget and prepared-handle lifetime. `/animation prepare <scene>` exercises terrain loading/retention, while `/animation diagnose` checks renderer/transport construction.

