# Folder structure and asset IDs

## Asset layout

Each folder under `packs/` is a content pack. Asset IDs are relative to their category, so moving an asset between packs does not rename it. Generated assets and block definitions use lowercase, dot-separated IDs; native models and fonts retain `namespace:path` IDs.

```text
plugins/AssetChannel/
|-- config.yml
|-- packs/
|   `-- main/
|       |-- assets/myserver/
|       |   |-- models/block/warning_block.json
|       |   `-- textures/block/warning_block.png
|       |-- definitions/
|       |   |-- blocks/warning_block.yml
|       |   `-- hud/notification/banner.yml
|       |-- sources/
|       |   |-- fonts/ui/title.ttf
|       |   |-- icons/ui/rank.png
|       |   |-- icons/ui/rank.yml
|       |   |-- sounds/npc/talk.mp3
|       |   `-- music/lobby/
|       |       |-- config.yml
|       |       `-- main.mp3
|       |-- pack.mcmeta           # optional native metadata and overlays
|       `-- pack.png              # optional pack icon
`-- generated/
    |-- pack.zip
    |-- pack/
    `-- cache/audio/
```

- `assets/` contains native Minecraft resource files.
- `definitions/` contains AssetChannel block behavior and HUD configuration.
- `sources/` contains icons, fonts and audio that AssetChannel converts into resources.
- `generated/` contains derived output and caches; edit the source files instead.

Native block assignments are stored in `storage/block-states.json`. Keep `storage/` with server/world backups; it is persistent data.

For paths relative to `packs/main/`:

| File | Registered ID |
|---|---|
| `sources/fonts/ui/title.ttf` | font key `assetchannel:ui.title` |
| `sources/icons/ui/rank.png` | icon `ui.rank` |
| `sources/sounds/npc/talk.mp3` | sound `npc.talk` |
| `sources/music/lobby/` | soundtrack `lobby` |
| `definitions/hud/notification/banner.yml` | HUD `notification.banner` |
| `definitions/blocks/warning_block.yml` | block `warning_block` |
| `assets/myserver/models/block/warning_block.json` with the block definition | model `myserver:block/warning_block` |

All packs contribute to one catalog and generated resource pack. Native references can cross packs. Duplicate IDs within the same asset category and duplicate native resource paths fail the build with both pack locations. Collisions with generated AssetChannel resources also fail; the previous pack stays active.

`main` is read first, followed by other pack folders alphabetically. Pack names must match `[a-z0-9][a-z0-9_-]*`. The first available description and icon are used. Compatible overlays apply within their own pack; their output directories are kept separate. Nonconflicting metadata fields are combined; differing values for the same field fail the build. Files under `definitions/` and `sources/` are never shipped to clients.

### Migration

Startup automatically moves the previous layout into `packs/main/` and `generated/`, preserving file bytes and asset IDs:

| Previous location | New location |
|---|---|
| `imports/assets/` | `packs/main/assets/` |
| `imports/pack/` | `packs/main/` |
| `blocks/`, `hud/` | `packs/main/definitions/blocks/`, `packs/main/definitions/hud/` |
| `icons/`, `font/` | `packs/main/sources/icons/`, `packs/main/sources/fonts/` |
| `sound/`, `music/` | `packs/main/sources/sounds/`, `packs/main/sources/music/` |
| `pack.zip`, `pack/`, `cache/` | the same names under `generated/` |

Migration checks all destinations first. Identical files are coalesced; differing files stop migration before anything is moved. This includes conflicts between loose imports and an extracted native pack. Resolve the reported files before restarting. `config.yml`, worlds and persistent block IDs remain in place. The HTTP URL remains `/pack.zip`.

### Models and placeable blocks

Put a model and its textures under `packs/main/assets/`, then create `packs/main/definitions/blocks/warning_block.yml`:

```yaml
render: block
model: myserver:block/warning_block
```

AssetChannel generates the item definition, atlas entries and stable native state assignment. Use `render: display` for display-backed models. See [block setup](assets/world.md#block-definitions) for particles and optional settings. Rebuild with `/asset texturepack generate`, accept the refreshed pack, then:

```text
/asset model give myserver:block/warning_block
/asset display spawn myserver:block/warning_block
/asset block give warning_block
```

The model command gives a visual item. The block command gives a tagged placeable item: right-click a block face to place it in adjacent empty space. Managed blocks break instantly. For blinking over existing blocks without changing the world, see [warning overlays](assets/world.md#native-blocks-and-warning-overlays).

For other import types, follow the [font/icon](assets/fonts.md), [HUD](assets/hud.md) and [audio](assets/audio.md) guides. Each documents its own sidecars and options.

## Cinematic input folders

These optional folders can be created alongside the standard layout:

```text
packs/main/
  sources/models/characters/guide.bbmodel
  sources/scene-audio/voices/intro.ogg
  sources/fonts/ui/subtitle.ttf
  sources/fonts/ui/subtitle.yml
  definitions/effects/zoom.yml
  assets/myserver/shaders/post/zoom.fsh
  assets/myserver/post_effect/zoom.json
```

| Input | Consumer ID |
| --- | --- |
| `sources/models/characters/guide.bbmodel` | rig `characters.guide` |
| `sources/scene-audio/voices/intro.ogg` | timeline audio `voices.intro` |
| `sources/fonts/ui/subtitle.ttf` | imported font/profile `ui.subtitle` |
| `definitions/effects/zoom.yml` | effect curve `zoom` |
| `assets/myserver/post_effect/zoom.json` | native effect `myserver:zoom` |

Blockbench clip and bone IDs are scoped to the imported rig. Native resources retain namespaces and slash-separated paths; generated source IDs use dots. Pack folder names organize content and do not become part of these IDs. Avoid duplicate IDs across packs.

## Persistent and generated data

`storage/block-states.json` records native block assignments. Retain it with world backups; deleting or recycling assignments can change the appearance of existing placed blocks. Generated ZIPs, resource trees and audio caches are derived outputs. A rebuild can replace them, so they are not authoring locations.

Font decoration atlases, screen-row variants, rig parts and audio playback lanes are generated implementation resources. Reference the public source IDs in plugin code and scenes, not the generated paths.
