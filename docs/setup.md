# Installation and configuration

## Requirements

- Paper 26.3
- Java 25
- A reachable HTTP port for the generated resource pack

FFmpeg is bundled in the shaded plugin JAR. A system FFmpeg installation is optional.


## Quick start

1. Build the plugin and place its shaded JAR in the server's `plugins` directory.
2. Start the server once so AssetChannel creates its folders and default configuration.
3. Put content under `plugins/AssetChannel/packs/main/` using the layout below.
4. Run `/asset texturepack generate` after changing assets.
5. Wait for the success message. The build happens in the background and the previous pack remains active if it fails.

For remote players, set `overrides.pack.host` in `config.yml` to a hostname or address they can reach. `127.0.0.1` works only when the Minecraft client runs on the server machine.


## Plugin configuration

Default `config.yml`:

```yaml
music:
  ffmpeg:
    executable: bundled
    timeout-seconds: 300
    vorbis-quality: 6
  playback:
    handoff-lead-milliseconds: 75
    vanilla-mute-refresh-seconds: 30
```

Common optional pack-host overrides:

```yaml
overrides:
  pack:
    host: play.example.com
    port: 25566
    required: true
    replace: true
```

`handoff-lead-milliseconds` accepts `0` through `250`; zero disables early handoff. A stage-level value overrides the global value. Vorbis quality accepts `0` through `10`.

Converted audio is cached under `plugins/AssetChannel/generated/cache/audio/`. Cache keys include the complete source content and encoding settings, so changed inputs are rebuilt while unchanged inputs are reused. Unsplit listener OGG files bypass the conversion cache because they are copied unchanged.


## Reliability behavior

- Pack commands build in the background rather than blocking the server tick.
- A second build cannot start while another is running.
- Assets are generated in unique staging directories.
- Every source, cached file, and newly generated OGG must contain readable audio with a positive duration.
- `generated/pack.zip` is replaced atomically after the complete build succeeds.
- A failed build leaves the previous pack and active playback untouched.
- Resource-pack readiness events are accepted only for AssetChannel's pack UUID.
- All music sessions share one tick scheduler, and stop operations target only recently active clips.
- Managed displays retain ownership metadata; block records persist in chunk data.
- Chunk/entity reconciliation uses one shared task, handling at most 32 chunks per tick.

Successful publication reloads the pack for online players. Active sessions stop because their sound keys belong to the previous client pack; start new sessions after players report that the new pack loaded.


## Build artifacts

Run `./gradlew build apiJar` (Windows: `./gradlew.bat build apiJar`). Install `build/libs/AssetChannel-1.1.0.jar`, the unclassified shaded runtime, on the server. Consumers compile against `AssetChannel-1.1.0-api.jar` and Paper. Do not deploy the `plain` or `api` JAR as the server runtime.

## Pack readiness

Players must download and successfully apply the active pack before pack-dependent visuals or audio are used. A successful server build is not client readiness. The API exposes `isPackReady`, `PackReadinessEvent` and `PackRevisionEvent`; consumers should release stale handles after a revision change. Reconnect or accept the refreshed pack after rebuilding.

Only local clients can reach a host set to `127.0.0.1`. Remote clients need a reachable host and port in `overrides.pack`; hosting overrides require a restart. Protect the original asset folders and persistent `storage/` data with backups.
