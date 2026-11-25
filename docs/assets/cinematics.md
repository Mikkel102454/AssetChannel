# Cinematic assets

AssetChannel owns imports, validation, pack publication, delivery, immutable asset descriptors and reusable audio/effect leases. AnimationChannel consumes these assets for scene playback and editing. No reverse dependency is required.

Static preflight now checks imported post-effect shader references, nested includes/cycles, current include syntax, uniform lengths/finite values, sampler names and local render targets before publication. A failed check retains the previous pack. `AssetChannelApi.preflight()` reports the active catalog's revision, resource/shader counts and errors; call it on the server thread. This is structural validation, with native shader compilation and connected-client/GPU acceptance kept distinct.

On the development machine, `./gradlew.bat clientPreflight` invokes Minecraft 26.3's installed compiler. `-PpreflightPack=<pack.zip>` checks an actual published ZIP. The probe discovers every shader stage referenced by its post-effect definitions, resolves includes from the pack before vanilla fallback, and checks SPIR-V reflection. The GPU probe additionally checks sample pixels and fractional-tick behavior.

| Source | Catalog |
| --- | --- |
| `packs/<pack>/sources/models/characters/guide.bbmodel` | `rig("characters.guide")` |
| `packs/<pack>/sources/scene-audio/dialogue/guide.ogg` | `timelineAudio("dialogue.guide")` |
| `packs/<pack>/sources/fonts/ui/subtitle.ttf` | `font("ui.subtitle")`, `captionFont("ui.subtitle")` |
| `packs/<pack>/definitions/effects/zoom.yml` | `effectCurve("zoom")` |
| `packs/<pack>/assets/demo/post_effect/grayscale.json` | native resource `demo:post_effect/grayscale.json`, playback effect `demo:grayscale` |

Run `/asset texturepack generate` after importing. Rig geometry, embedded PNGs and independent native item parts join the normal native-resource validation and collision checks. Neither `.bbmodel` sources nor scene-audio sources ship to clients. Failed builds leave the old pack active.

Supported rigs use cubes, nested bones, pivots, static rotations, textures/UVs, and numeric position/rotation/scale keyframes. Linear, step, Catmull–Rom and Bézier curves are represented in immutable `RigAsset` records. Values use Blockbench pixels and degrees. Unsupported expressions, meshes, controllers, pre/post frames and effect animators fail import with their source path; bake them first. Render parts can be more numerous than bones. The current cube compiler does not yet cover every Blockbench export feature; test your production models visually before deployment.

Scene audio is converted into one-second OGG clips in listener-preserving and spatial mono variants. Generated event aliases provide sixteen independently leased lanes per asset/player. The original source files remain unchanged, and the existing content-addressed FFmpeg cache is reused. `TimelineAudioAsset.nextSegment` returns the next prepared boundary; reaching the segment count means playback is finished.

```java
AssetChannelApi assets = Bukkit.getServicesManager().load(AssetChannelApi.class);
RigAsset rig = assets.rig("characters.guide").orElseThrow();
AudioLease voice = assets.leaseAudio(player, "dialogue.guide");
voice.play(0, null, 1.0f); // listener-relative; Location selects mono positional playback
voice.silence();
voice.close();

EffectHandle effect = assets.showPostEffect(player, "demo:grayscale");
effect.close();

TitleLease titles = assets.reserveTitle(player);
titles.show(Component.empty(), Component.text("Guide: Follow me!"));
titles.close(); // resumes suspended/queued AssetChannel title HUDs
```

Lease operations require the Paper server thread and a matching loaded pack. Callers must close leases. Audio leases stop only their own generated keys and categories; VOICE is the default, and overloads accept category/pitch settings. `AudioLease.emit(segment, spatial, emitter)` supplies a leased key to a caller's transport for virtual-entity sound playback while retaining targeted cleanup. Effect leases reference-count per player/key and preserve effects present before acquisition. `stopSessions`, pack publication, quit and pack loss invalidate owned playback as appropriate.

`TitleLease` exclusively reserves the title/subtitle surface. Existing title HUDs suspend, subsequent requests queue, and closing resumes the priority queue with its remaining durations intact. Action bars and boss bars remain independent. Another title reservation for the same player is rejected until the existing lease is released. Direct Bukkit title calls bypass this coordination.

## Animated screen effects

The current effect format is `clock-composite-v1`. Each definition declares a channel, numeric range, neutral value and imported shader references. AssetChannel exposes it through `effectCurve(id)` and generates the native transport/render definitions during publication. The authored example pack contains two independent native effects (grayscale and letterbox) plus five small animated-effect YAML definitions. GLSL, fonts, textures and audio remain imported assets.

```yaml
kind: ZOOM
range: {min: 1, max: 4}
neutral: 1
client:
  protocol: clock-composite-v1
  group: cinematic:screen
  writer-vertex: cinematic:post/control_pixel
  writer-fragment: cinematic:post/control_value
  vertex-shader: cinematic:post/composite
  fragment-shader: cinematic:post/composite
  copy-vertex: minecraft:core/screenquad
  copy-fragment: minecraft:post/blit
```

Resource IDs above belong to the sample import; choose your own group and shader assets. The importer requires every field, a finite increasing range and a neutral value inside that range. Zoom ranges must be positive. Scalar kinds are FADE, LETTERBOX, DESATURATION and VIGNETTE. The supplied scalar definitions use range 0..1 with neutral 0; zoom uses 1..4 with neutral 1. The supplied letterbox shader reaches 12% of the image height at each edge.

`EffectCurveAsset(id, client)` exposes `kind()`, `minimum()` and `maximum()` from its required `ClientEffectAsset`. The descriptor contains writer/copy references and required `ClientEffectComposition` metadata for the compositor, channel and neutral value.

```java
ClientEffectLease zoom = assets.leaseClientEffect(player, "zoom");
zoom.show(new EffectAnimation(1, 2, 0, 60, EffectAnimation.Easing.SMOOTHER));
// Hold 50% of the curve; progress is linear time before easing.
zoom.show(new EffectAnimation(1, 2, .5, 0, EffectAnimation.Easing.SMOOTHER));
zoom.close();
```

Lease operations require the server thread and the active pack. One zoom/roll lease and one lease per scalar asset may be active for a viewer. Close leases when playback ends and on owner shutdown. Updates atomically replace owned controls while preserving unrelated post effects. Pack revision changes, disconnects and session cleanup invalidate leases.

`packRevision()` identifies the active catalog snapshot. `PackRevisionEvent` follows successful publication; `PackReadinessEvent` identifies viewer readiness for that revision. Consumers should close stale handles and prepare again when revisions change. Readiness for an unrelated pack does not satisfy AssetChannel readiness. `./gradlew apiJar` builds the consumer API JAR; install the shaded unclassified runtime JAR on the server.

### Cube import details

Loose cubes are assigned a real root bone. Cube `inflate` expands geometry before pivot transforms, and `export: false` on cubes or groups excludes those parts. Source files are never rewritten. Bake cube `rescale`, nonzero animation `start_delay`/`loop_delay`, and nondefault intrinsic `blend_weight` before import; unsupported values fail the transactional build instead of silently changing the animation. Scene layers provide explicit timing and weights. Meshes, animation controllers and expression/Molang channels remain outside the supported cube-rig format.

Blockbench v5 files can keep bone metadata in a separate `groups` table; outliner references resolve names, pivots and rotations from that table while retaining their hierarchy. Zero-length clips with no keyframes are empty authoring placeholders and are omitted from the rig catalog. The supplied `learning.bbmodel` is covered by a regression fixture and native pack validation: 20 parts, 25 bones, and `idle`/`walk` clips.

### Imported caption fonts and decorations

Put a TTF/OTF under `sources/fonts/` with its optional YAML sidecar. AssetChannel imports the native font and derives caption glyph atlases from the same file. There is no system-font fallback. Native font JSON imports remain usable for title text; caption derivation currently requires an imported TTF/OTF readable by Java's font loader. Invalid font bytes fail caption derivation.

The sidecar supports `caption.enabled`, `caption.size`, `caption.characters`, `caption.outline-width`, `caption.outline-color`, `caption.shadow-offset`, and `caption.shadow-color`. Colours accept `#RRGGBB` or `#RRGGBBAA`. An optional `caption.box` selects an imported `texture` key plus `border` and `padding` values. See the [complete sample](../examples/subtitle.yml). Letter outline, shadow, and bordered box are independently selectable through `CaptionAppearance` and AnimationChannel's subtitle form. The default character set covers Latin-1 and common punctuation; unsupported glyphs become `?`.

```java
CaptionFontAsset font = assets.captionFont("ui.subtitle").orElseThrow();
CaptionAppearance appearance = new CaptionAppearance(true, true, true);
List<Component> lines = CaptionLayout.wrap(Component.text("Guide: Follow me!"), 280, font);
Component caption = CaptionLayout.compose(Component.empty(), lines.getFirst(), font, appearance);
ActionBarLease lease = assets.reserveActionBar(player);
lease.show(caption);
lease.close();
```

`captionFonts()` lists profiles with immutable glyph metrics, rendering variants and optional box descriptors. Layout uses the imported font's advances. The composition overload accepting full-line widths keeps typewriter text and box placement stable. All caption layout calls require an imported font descriptor.

Caption layout separates natural character advances from padded bitmap advances. It inserts spacing compensation when composing decorated glyphs, so increasing an outline or shadow does not spread the letters apart. Generated profiles supply separate natural and padded advance maps.

Refresh static action-bar captions at least once per second, and close the lease to resume queued AssetChannel HUDs. Lease calls require the server thread; composition is pure. GUI scale and Minecraft's global text-background setting affect appearance. No core shaders are overridden.

Compiled cube textures are explicitly registered in the native item atlas. AnimationChannel cancels the native item-display half-turn before applying each cube transform. Regression tests cover atlas publication and a rotated cube with a nonzero pivot; a client must still confirm the final model appearance.


## Positioned screen-font assets

`screenFonts()` / `screenFont(id)` expose font variants derived from imported TTF/OTF media. `ScreenFontAsset` supplies bounded Y rows, character advances and line height. `ScreenTextLayout.place(component, font, x, y, alignment, outline, shadow)` produces a zero-advance layer; several labels can be combined on one reserved action bar without pushing their neighbours. `ScreenTextLayout.space(pixels)` provides fractional cursor movement for mixing these layers with a nonzero-width caption. Positioning uses GUI pixels relative to the action-bar origin, with X centred and negative Y moving upward. Font size and the row range come from the import sidecar (`screen.size`, `min-y`, `max-y`, `step`, `characters`, `enabled` under `screen`). Defaults are size inherited from the font, Y -256..64, step 2, and enabled true. Text shadows/outlines use the selected imported font. No client shader or embedded font is required.

The native probe `examples/client-probe/run-screen-font-probe.ps1` exercises Minecraft 26.3's actual FreeType glyph provider for shifted font metrics and bounded glyph allocations. It is separate from connected-client visual acceptance.

## Shared rendering and transport

Effects in one group agree on compositor/copy shader references and channel bounds. Conflicting imports reject publication transactionally. Distinct visible channels in a group share one full-screen render and one copy. Curve decoding and easing run at the vertices; the fragment shader samples the scene once. Different groups or duplicate channels use separate current-protocol compositors in deterministic order. The supplied shader applies zoom/roll, desaturation, fade, letterbox and vignette.

Held neutral values contribute no passes. A moving curve starting at neutral remains active; paused non-neutral values stay visible. Scene cues remain available for seek and replay even while a neutral channel is culled.

The transport reserves five 22-pixel strips in the first image row, ordered ZOOM, FADE, LETTERBOX, DESATURATION, VIGNETTE. `ControlConfig.Parameters` contains pixel offset, byte value and span width. Each channel resets its strip once, then writes nonzero bytes. Zero-value writer definitions are omitted. Minecraft receives generated native JSON definitions because resource-pack shader uniforms are selected through native effect resources; these derived files belong in the generated pack.

`CompositorConfig.Parameters` supplies the active-channel bitmask and 24,000-tick clock period. `ChannelBounds` has vec4 members `Channel0` through `Channel4`, containing minimum, maximum, payload size and pixel offset. Zoom carries 22 bytes; scalar channels carry 15. Endpoints and progress retain 16-bit precision; duration is float32. Both carry easing and signed Q12 clock correction. Zoom additionally carries current/next roll, its interpolation flag and an independent roll timestamp. The sample writer supports spans; copy stages use the imported copy vertex and fragment shaders.

`ClientEffectAsset.controlKeys(bytes)` builds sparse controls; `keys(bytes)` adds a standalone renderer. `composition()` exposes group metadata. `owns(key)` recognizes both the asset and its shared group; persist `ownedPrefixes()` for recovery. `decodeFrame` exposes the complete timing contract.

## Clock synchronization

`durationTicks` is the full curve duration; zero freezes the supplied progress. The shader evaluates fractional client ticks. Keep running anchors less than 12,000 ticks old. AnimationChannel rebases once per second and reconstructs immediately on activation, pause/resume, seek, profile changes and hard cuts.

`ClientEffectFrame(animation, curveTick, rollTick, correctionTicks)` supplies independent timestamps and correction bounded to ?2 ticks. Small routine phase differences ease away over about 100 ms after a one-tick delivery lead; the largest use up to 150 ms to preserve forward progress. Intentional discontinuities use zero correction. Roll updates preserve the curve anchor. `ClientEffectLease.show(frame)` accepts explicit timing; `show(animation)` uses current world time. `/animation diagnose` reports update/rebase/correction counters for active sessions.

## Native and GPU verification

Minecraft 26.3 shaders use `#include <namespace:file.glsl>`. Structural validation checks imported shader dependencies before publication. `./gradlew.bat clientPreflight` invokes the installed Minecraft compiler; `-PpreflightPack=<pack.zip>` checks a published ZIP. Includes resolve from the pack before vanilla resources and both depth modes are compiled.

Run `python examples/client-probe/run-probes.py` from AssetChannel to compile the shaders, compare compositor output against CPU reference values, and measure the complete current pipeline at 1920?1080 and 2560?1440. `--pack path/to/pack.zip` tests published files. It requires Java 25, Minecraft 26.3 and local LWJGL libraries. Logs go to `build/reports/effect-optimization`.

GPU checks cover sparse controls, channel masks, fractional ticks, easing, reverse curves, pauses, rollover, corrections and independent roll timing. Timings include writer/render/copy draws, using the median of 30 GPU queries after eight warmup frames. A fresh image is restored outside each query. Game world rendering, compilation/loading and networking are excluded; connected-client FPS and final appearance require an in-game check.
