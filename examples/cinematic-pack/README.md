# Cinematic demonstration assets

Copy this folder's `sources`, `assets`, `definitions`, and `font-licenses` directories into `plugins/AssetChannel/packs/main/`, then rebuild the pack. Keep this README outside the content pack. The sample registers rig `demo.guide`, prepared audio `demo.line`, font `ui.subtitle`, and effect curves `zoom`, `fade`, `letterbox`, `desaturation` and `vignette`.

The font is Inconsolata Regular from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/inconsolata), distributed under the SIL Open Font License in `font-licenses/inconsolata.txt`. Replace `sources/fonts/ui/subtitle.ttf` with your own licensed font and adjust its YAML sidecar to change the look. AssetChannel derives plain, outlined, shadowed, and outlined/shadowed caption variants from that file. The optional caption box comes from `assets/cinematic/textures/gui/caption_box.png`; the sidecar selects its texture, border slice and padding.

Shaders and native post-effect profiles are ordinary files under `assets/`. The `definitions/effects/*.yml` files declare continuous ranges and compositor shader references. These files can be replaced or omitted; production code supplies no authored font, box or shader fallback.

The zoom definition's `client` section selects the imported control and interpolation shaders under `assets/cinematic/shaders/post/`. AnimationChannel uses these for per-frame zoom, including easing, pause and seek. Caption decoration padding is compensated separately from normal letter spacing.

The cube character includes idle, walk and talk clips. Its embedded textures are original procedural demo assets. The audio consists of original generated talk tones as a placeholder; replace it with your own voice recording to match the subtitles.

Install AnimationChannel's `examples/scene-demo.yml` under `plugins/AnimationChannel/scenes/` and restart the server. Stand at the desired scene origin and run `/animation scene play cinematic-demo`. A stone block at origin-relative `(3, 0, 5)` demonstrates the temporary break overlay; the server map remains unchanged.

The example combines an animated walking/talking guide, actor-bound audio, typewriter subtitles, camera movement/look-at, zoom, grayscale, letterboxing, a block effect, a marker, and a fade. Its appearance and timing still require a vanilla-client acceptance pass.

All animated effect definitions use clock-composite-v1. Each declares its range, neutral value, group, writer shaders, compositor shaders and copy shaders. Compatible channels share one render/copy pair, decode curves at the vertices and omit held neutral effects. AnimationChannel's client-effects-demo.yml demonstrates all five channels. The two remaining native JSON files are standalone grayscale and letterbox assets; animated effects need no authored preset JSON files. AssetChannel generates their control definitions during pack publication.
