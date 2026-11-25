# Building, formatting and maintaining

## Build and test

Use Java 25. On Windows replace `./gradlew` with `./gradlew.bat`. AnimationChannel uses the sibling AssetChannel build when available; keep the two projects side by side while developing their integration.

```sh
./gradlew test
./gradlew build apiJar
./gradlew formatJava
./gradlew checkJavaFormat
```

`formatJava` uses pinned google-java-format 2.0.0 with AOSP four-space indentation. It formats production/tests/example Java and removes unused imports; `.editorconfig` supplies matching editor defaults. `checkJavaFormat` is read-only and fails when a source needs formatting. Compile tasks run after formatting when both are requested. Catalog contracts live under `api/`; source parsers, pack assembly, playback/session implementations and operator commands have separate packages. AnimationChannel owns the cutscene editor.

Follow the project's applicable agent/contributor guidance. Add focused regressions for behaviour changes, run the relevant checks, then broaden when shared code warrants it. Pure formatting does not need tests that restate the formatter. Keep the current API contract explicit; remove obsolete protocol branches and placeholder implementations. Do not rewrite source media or use generated/cache directories as authoring inputs.

## Build these MkDocs pages

Requirements are pinned in `docs-requirements.txt`. Install them into your chosen Python environment:

```sh
python -m pip install -r docs-requirements.txt
./gradlew stageApiDocs
python -m mkdocs build --strict
python -m mkdocs serve
```

The Windows helper `./docs.ps1` runs the API-generation step and strict MkDocs build. Use `./docs.ps1 -Serve` to preview the generated site, or `-Python <path-to-python.exe>` to select an interpreter. An installed local Python runtime is discovered when the WindowsApps command is only an alias. The script does not install global packages. It serves the validated static site using Python HTTP on localhost; use `-Port <number>` to override the port. Run the build helper again and refresh your browser after editing. The standard `mkdocs serve` command provides live reload where temporary-directory permissions allow it. `-SkipApi` reuses an already generated reference.

Development address: `http://127.0.0.1:8000`. The generated static site is `site/`; directory-style URLs are disabled so its HTML files can also be opened directly. JavaDoc is generated from the real public API into `build/docs/api/`, then staged under ignored `docs/java/` as static site content. Do not edit generated reference files. Regenerate after API changes.

Both sites use [MkDocs](https://www.mkdocs.org/user-guide/configuration/) with strict navigation/link/anchor validation, the bundled Read the Docs theme, local CSS and search. The API reference uses the JDK's standard JavaDoc generator. Formatting uses [google-java-format](https://github.com/google/google-java-format).

## Runtime acceptance

Unit tests do not prove connected-client appearance. Use a separate test server and the correct accepted resource pack. Confirm model textures/placement, font metrics and decorations, audio timing, shaders, pause/seek, audience isolation, and restoration after stop/error/disconnect. For editor changes, test a paginated timeline: time stepping, form save/back, invalid values, chat cancel, and changing drafts while a response is pending.

AssetChannel's `clientPreflight` task compiles imported shaders through an installed Minecraft 26.3 client. Its native/GPU/font probe scripts are under `examples/client-probe/`. AnimationChannel's `diagnose` probes native renderer/transport construction, while `prepare <scene>` checks real chunk loading and delayed retention. These are different checks. Detached benchmarks omit connected-client serialization, network/chunk load and client FPS.

## Publication and ownership

Build documentation locally before publishing it. This project does not automatically publish the website or modify a remote host. The runtime and API artifacts are different: deploy the runtime plugin, compile consumers against the API artifact, and preserve server-owned persistent storage. In gameplay integrations, close the resources your plugin owns and respect the current pack revision/readiness.
