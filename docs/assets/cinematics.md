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

Post effects and GLSL are imported assets. The sample content pack supplies grayscale, letterboxing, zoom profiles from 1 to 4 in 0.01 increments, and fade profiles from 0 to 1. No authored shaders or post-effect profiles are embedded in Java. `definitions/effects/<id>.yml` declares `kind: ZOOM` or `FADE` and a `profiles` map from numerical values to imported post-effect keys. Missing references reject publication. `EffectCurveAsset` exposes the immutable curve, its bounds and nearest-profile lookup. Actual GLSL/GPU compatibility and appearance require vanilla-client testing.

The sample zoom definition also selects imported shaders through `client.protocol: clock-zoom-v2`. This enables per-frame client interpolation. Its `writer-vertex`, `writer-fragment`, `vertex-shader`, `fragment-shader`, and `copy-shader` fields reference resource-pack assets. AssetChannel derives native transport definitions from those references; missing imported shaders reject publication. The existing numeric profiles remain a compatibility path for curves without a client protocol.

Minecraft 26.3 shaders use `#include <namespace:file.glsl>`. The obsolete `#moj_import` directive is rejected when validating client-effect entry shaders. Run `examples/client-zoom-probe/run-native-probe.ps1` to compile the actual asset files through the installed Minecraft compiler; pass `-AssetsPath <pack.zip>` to check the published ZIP. The separate GPU probe runs this native check first, then verifies rendering. It must not treat manually expanded includes as evidence that the Minecraft loader accepts the source.

```java
ClientEffectLease zoom = assets.leaseClientEffect(player, "zoom");
zoom.show(new EffectAnimation(1, 2, 0, 60, EffectAnimation.Easing.SMOOTHER));
// Freeze at 50% of the curve. Progress is linear time before easing.
zoom.show(new EffectAnimation(1, 2, .5, 0, EffectAnimation.Easing.SMOOTHER));
zoom.close();
```

`durationTicks` describes the full curve; zero freezes the supplied progress. Running effects advance from the synchronized progress using Minecraft's fractional-tick shader clock. Rebase at least once per 24,000-tick clock cycle; AnimationChannel rebases once per second and immediately for pause, resume and seek. Bounds are encoded at 16-bit precision rather than one-percent zoom steps. Only one client zoom lease can control a player at once. Each update atomically installs its ordered control bundle while preserving unrelated native effects. Close the lease on completion. Pack loss/reload, disconnect and session shutdown invalidate it.

The version-one protocol uses thirteen pixels at one image corner for control data. Its final shader replaces those samples from the adjacent row; at magnifications above 1 the corner normally lies outside the sampled image. This is a resource-pack image magnification effect, with client/GPU acceptance still required.

`clock-zoom-v2` adds camera roll using eighteen control pixels. `EffectAnimation` has additive roll/current-next fields while its original constructor remains available. The selected imported descriptor advertises its protocol version and control size. Version-one descriptors reject roll instead of silently dropping it. AnimationChannel samples authored roll keys and sends adjacent values for client interpolation, including immediate holds on pause and reconstruction on seek.

`packRevision()` identifies the active catalog snapshot. `PackRevisionEvent` is emitted after a successful catalog publication, and `PackReadinessEvent` identifies player readiness for that revision. Consumers should close stale handles and prepare again when revisions change. Readiness changes for unrelated resource packs do not satisfy AssetChannel readiness.

`./gradlew apiJar` creates `AssetChannel-1.1.0-api.jar`, excluding implementation packages and bundled FFmpeg. Compile consumers against the `api` classifier plus Paper; install the shaded unclassified runtime JAR on the server. Existing API entry points remain available.

### Cube import details

Loose cubes are assigned a real root bone. Cube `inflate` expands geometry before pivot transforms, and `export: false` on cubes or groups excludes those parts. Source files are never rewritten. Bake cube `rescale`, nonzero animation `start_delay`/`loop_delay`, and nondefault intrinsic `blend_weight` before import; unsupported values fail the transactional build instead of silently changing the animation. Scene layers provide explicit timing and weights. Meshes, animation controllers and expression/Molang channels remain outside the supported cube-rig format.

Blockbench v5 files can keep bone metadata in a separate `groups` table; outliner references resolve names, pivots and rotations from that table while retaining their hierarchy. Zero-length clips with no keyframes are empty authoring placeholders and are omitted from the rig catalog. The supplied `learning.bbmodel` is covered by a regression fixture and native pack validation: 20 parts, 25 bones, and `idle`/`walk` clips.

### Imported caption fonts and decorations

Put a TTF/OTF under `sources/fonts/` with its optional YAML sidecar. AssetChannel imports the native font and derives caption glyph atlases from the same file. There is no system-font fallback. Native font JSON imports remain usable for title text; caption derivation currently requires an imported TTF/OTF readable by Java's font loader. Invalid legacy font bytes do not gain a caption profile.

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

`captionFonts()` lists profiles with immutable glyph metrics, rendering variants and optional box descriptors. Layout uses the imported font's advances. The composition overload accepting full-line widths keeps typewriter text and box placement stable. Deprecated layout overloads without a font now throw: callers must select an imported asset.

Caption layout separates natural character advances from padded bitmap advances. It inserts spacing compensation when composing decorated glyphs, so increasing an outline or shadow does not spread the letters apart. Existing four-argument `CaptionFontAsset` constructors remain supported; generated profiles supply both metric maps.

Refresh static action-bar captions at least once per second, and close the lease to resume queued AssetChannel HUDs. Lease calls require the server thread; composition is pure. GUI scale and Minecraft's global text-background setting affect appearance. No core shaders are overridden.

Compiled cube textures are explicitly registered in the native item atlas. AnimationChannel cancels the native item-display half-turn before applying each cube transform. Regression tests cover atlas publication and a rotated cube with a nonzero pivot; a client must still confirm the final model appearance.


## Positioned screen-font assets

`screenFonts()` / `screenFont(id)` expose font variants derived from imported TTF/OTF media. `ScreenFontAsset` supplies bounded Y rows, character advances and line height. `ScreenTextLayout.place(component, font, x, y, alignment, outline, shadow)` produces a zero-advance layer; several labels can be combined on one reserved action bar without pushing their neighbours. `ScreenTextLayout.space(pixels)` provides fractional cursor movement for mixing these layers with a nonzero-width caption. Positioning uses GUI pixels relative to the action-bar origin, with X centred and negative Y moving upward. Font size and the row range come from the import sidecar (`screen.size`, `min-y`, `max-y`, `step`, `characters`, `enabled` under `screen`). Defaults are size inherited from the font, Y -256..64, step 2, and enabled true. Text shadows/outlines use the selected imported font. No client shader or embedded font is required.

The native probe `examples/client-zoom-probe/run-screen-font-probe.ps1` exercises Minecraft 26.3's actual FreeType glyph provider for shifted font metrics and bounded glyph allocations. It is separate from connected-client visual acceptance.
