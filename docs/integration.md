# Integrating with AnimationChannel

AssetChannel owns assets; AnimationChannel owns scene timing and presentation. Their dependency is explicit: install both runtime JARs, import/build the media through AssetChannel, then reference public IDs from AnimationChannel scenes or another plugin.

## End-to-end workflow

1. Put a cube `.bbmodel` in `packs/main/sources/models/characters/guide.bbmodel`.
2. Put voice audio in `sources/scene-audio/voice/intro.ogg`.
3. Put the caption font in `sources/fonts/ui/subtitle.ttf`, with a sidecar for decorations/screen rows.
4. Import any caption-box texture, post-effect JSON/GLSL and numeric effect-curve definitions.
5. Run `/asset texturepack generate`, wait for success, and accept the new pack.
6. In AnimationChannel, choose rig `characters.guide`, its imported clip IDs, voice `voice.intro` and font `ui.subtitle`.
7. Validate/prepare the scene, then preview with pack-ready viewers.

## Division of responsibility

| AssetChannel | AnimationChannel |
| --- | --- |
| Source import, validation, derived files and hosting | Scene files, drafts, sections and editor navigation |
| Rig geometry, textures, bones and clip channels | Actor placement, layers, attachments and lip markers |
| Font metrics, glyph/box variants and shifted rows | Caption paging, speakers, screen label animation |
| Audio preparation and independently leased lanes | Cue timing, trim/seek, gain and actor/bone emitters |
| Imported shader/curve descriptors and effect leases | Zoom/roll/fade timing and camera direction |
| Pack revision and readiness | Scene admission, chunk preparation, restoration |

Both plugins expose public APIs so a gameplay plugin can create a scene or use individual assets. Consumers should compile against both API JARs plus Paper and declare the required runtime plugin dependencies. Scene markers are callbacks for gameplay decisions; they do not embed arbitrary console commands in asset files.

## Rebuilds and ownership

Only a successful build publishes a new catalog. That changes the pack revision and invalidates affected cinematic preparations/leases. AnimationChannel stops/restores its sessions; prepare again against the refreshed revision after clients apply it. Failed builds keep the previous pack.

A cinematic already reserves its title/action-bar surfaces. Use `SceneSession.showText` for transient labels instead of acquiring a competing action bar. Use actor/bone bindings for scene sound/particles rather than manually teleporting unrelated server entities. AnimationChannel's actors are client-side and its block changes do not edit the real world.

## Testing

Check imports through AssetChannel commands, then run `/animation preflight`, `/animation scene validate`, `/animation prepare <scene>` and `/animation diagnose`. Validate real model appearance, font spacing, audio, shaders and restoration in a connected client. Native shader/font probes and detached renderer diagnostics complement that acceptance check.
