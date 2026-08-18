# AssetChannel agent guide

Paper plugin/API for resource-pack generation/hosting plus fonts, icons, audio, synchronized music, origins, and HUD sessions. Java 25; Paper 26.2.

## Find things

- Lifecycle/events: `src/main/java/nu/miguel/assetChannel/{Main,EventListener}.java`
- Consumer-facing contracts: `api/`; implementations, HUD, and music sessions: `api/internal/`
- Source asset parsing/models: `asset/`
- Pack assembly, web serving, spacing font: `pack/`; pack delivery: `texturepack/`
- Operator commands: `commands/`; config, paths, FFmpeg/audio readers: `util/`
- Plugin/default config: `src/main/resources/`; tests mirror packages in `src/test/java/`.
- Asset layout, API examples, configuration, and operations: `README.md`.

## Work rules

- Pack publication is transactional: build in staging and keep the previous pack active on failure.
- Never modify source media; generated/cache files are derived data.
- Preserve listener-relative channel layout and positional mono behavior.
- Playback/session work must respect Paper thread rules, bounded shared scheduling, pack readiness, and targeted stop semantics.
- Public API changes need focused API tests and compatibility awareness.
- Never edit `build/` or `.gradle/` as source.

## Verify

```sh
./gradlew test
./gradlew build       # includes shaded JAR with bundled FFmpeg resources
./gradlew runServer   # manual Paper smoke test
```

For one test: `./gradlew test --tests 'fully.qualified.TestName'`. The deployable unclassified JAR is under `build/libs/`; the `plain` JAR is not deployable.

