# API catalogs and ownership

All consumer contracts live under `nu.miguel.assetChannel.api`. [The API guide](guide.md) provides compile/load, threading and usage examples; [generated JavaDoc](reference.md) lists every public type, constructor and method.

| Catalog / service | Main contracts |
| --- | --- |
| Native/generated model items | `models`, `model`, `ModelAsset.createItem` |
| Fonts and icons | `fonts`, `font`, `icons`, `icon`, `FontAsset`, `IconAsset` |
| Imported caption/screen fonts | `captionFonts`, `screenFonts`, `CaptionFontAsset`, `ScreenFontAsset` |
| Native pack resources | `resources`, `ResourceKind`, `ResourceAsset` |
| Animated rigs | `rigs`, `rig`, `RigAsset` bones/parts/clips/channels |
| Short and native sounds | `sounds`, `playSound`, `playResourceSound`, targeted stops |
| Resumable media | `timelineAudio`, `TimelineAudioAsset`, `AudioLease` |
| Adaptive music | `soundtracks`, `MusicSession`, `SessionSettings`, `SoundOrigin` |
| HUD templates | `huds`, `HudAsset`, `HudRequest`, `HudSession` |
| Presentation reservations | `TitleLease`, `ActionBarLease`, `EffectHandle`, `ClientEffectLease` |
| Cinematic effect imports | `effectCurves`, `EffectCurveAsset`, `ClientEffectAsset`, `EffectAnimation` |
| World placement | `DisplayOptions`, `PlacedAssetBlock`, `BlockOverlay` |
| Pack lifecycle | `packRevision`, `preflight`, `PackRevisionEvent`, `PackReadinessEvent` |

## Readiness and lifetime

Descriptors are immutable snapshots. Lookups do not make a player pack-ready. Check the active revision/readiness before presenting assets and discard revision-bound handles after a successful rebuild. Resolve source IDs rather than derived rig-part, font-row or audio-lane paths.

API mutations, player/world access and lease calls require the Paper server thread. Pure component composition can be kept separate. Close your own leases/sessions/displays; use targeted stop methods so unrelated owners continue. Managed world calls need loaded chunks and retain protection-event semantics.

Keep AssetChannel as a `compileOnly` dependency and a runtime plugin dependency. Do not shade its implementation into a consumer. The `api` JAR excludes internal classes and FFmpeg resources; the unclassified runtime JAR supplies the installed service.
