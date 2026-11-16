# AssetChannel agent guide

Paper plugin/API for resource-pack generation/hosting plus models, textures, fonts, icons, audio, synchronized music, origins, and HUD sessions. Java 25; Paper 26.3.

## Find things

- Lifecycle/events: `src/main/java/nu/miguel/assetChannel/{Main,EventListener}.java`
- Consumer-facing contracts: `api/`; implementations, HUD, and music sessions: `api/internal/`
- Source asset parsing/models: `asset/`
- Pack assembly, web serving, spacing font: `pack/`; pack delivery: `texturepack/`
- Content folders: `packs/<name>/{assets,definitions,sources}/`; derived files: `generated/`. Layout and upgrade migration: `util/{PathResolver,LegacyLayoutMigration}.java`.
- Operator commands: `commands/`; config, paths, FFmpeg/audio readers: `util/`
- Plugin/default config: `src/main/resources/`; tests mirror packages in `src/test/java/`.
- Setup and operations: `README.md`; imports: `ASSETS.md`; Java API: `API.md`; displays and blocks: `WORLD-ASSETS.md`.

## Work rules

- Pack publication is transactional: build in staging and keep the previous pack active on failure.
- Never modify source media; generated/cache files are derived data.
- Native block assignments in `storage/block-states.json` are persistent server data; retain existing assignments and never recycle removed IDs automatically.
- Preserve listener-relative channel layout and positional mono behavior.
- Playback/session work must respect Paper thread rules, bounded shared scheduling, pack readiness, and targeted stop semantics.
- Public API changes need focused API tests and compatibility awareness.
- Never edit `build/` or `.gradle/` as source.

## Test-driven development

- For behavior-changing code, write the smallest meaningful automated test before changing production code.
- Run that exact test first and confirm it fails for the expected reason. Only then implement the change and rerun the test until it passes; do not weaken or rewrite the test merely to make the implementation pass.
- Add tests only where they protect meaningful behavior, regressions, contracts, or edge cases. Documentation, formatting, mechanical refactors, and other changes with no behavior to verify do not need tests.
- During development, run only the narrowest relevant test target. Before finishing, run the smallest affected test set needed to catch integration regressions; do not run unrelated full suites by default.

## Verify

```sh
./gradlew test
./gradlew build       # includes shaded JAR with bundled FFmpeg resources
./gradlew runServer   # manual Paper smoke test
```

For one test: `./gradlew test --tests 'fully.qualified.TestName'`. The deployable unclassified JAR is under `build/libs/`; the `plain` JAR is not deployable.
