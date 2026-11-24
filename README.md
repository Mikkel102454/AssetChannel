# AssetChannel

AssetChannel imports Minecraft models, animated Blockbench rigs, textures, fonts, icons, audio, shaders and HUD templates. It builds and hosts their resource pack and exposes a Java API. AnimationChannel consumes these assets to author and play cutscenes.

The complete manual is the **[MkDocs documentation](docs/index.md)**:

- [Install and configure](docs/setup.md)
- [Folder structure and asset IDs](docs/layout.md)
- [Models and native resources](docs/assets/native.md), [world assets](docs/assets/world.md), [cinematic imports](docs/assets/cinematics.md)
- [Fonts and text](docs/assets/fonts.md), [audio](docs/assets/audio.md), [HUD templates](docs/assets/hud.md)
- [Commands and permissions](docs/commands.md)
- [Java API guide](docs/api/guide.md) and [complete generated reference](docs/api/reference.md)
- [AnimationChannel integration](docs/integration.md), [troubleshooting](docs/troubleshooting.md)

## Build

Use Java 25 and Paper 26.3. From this project:

```powershell
.\gradlew.bat test build apiJar
.\gradlew.bat formatJava
.\gradlew.bat checkJavaFormat
```

Install `build/libs/AssetChannel-1.1.0.jar` on the server. Consumers compile against `AssetChannel-1.1.0-api.jar`; the `plain` JAR is not the deployable plugin. Preserve source media and `storage/` across updates.

## Read the website locally

```powershell
python -m pip install -r docs-requirements.txt
.\docs.ps1 -Serve
```

The helper generates JavaDoc, validates the site, then serves it at **http://127.0.0.1:8000**. Use `-Python <python.exe>` to choose an interpreter. For non-Windows steps and publication details, see [building and maintaining](docs/development.md). Generated `site/` and `docs/java/` files are ignored.
