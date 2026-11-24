# Asset packs

AssetChannel builds assets into a resource pack. Consumer plugins own inventories, navigation and game logic. See [README](../setup.md) for installation and [API](../api/guide.md) for Java usage.

## Models and textures

All paths below are relative to `plugins/AssetChannel/packs/main/`. Add another folder under `packs/` to keep a separate content collection. See [layout and migration](../layout.md) for folder roles and upgrade behavior.

For a placeable model, create a definition under `definitions/blocks/`:

```yaml
render: block
model: myserver:block/warning_block
```

Add `assets/myserver/models/block/warning_block.json` and its referenced textures. AssetChannel generates the item definition and atlas entries during the build. `render: display` uses a display-backed placement instead. [Block setup and particle textures](world.md#block-definitions) covers optional settings.

### Raw native resources

The following manual layout remains available for assets without a simple block definition.

Place native resources under `assets/`:

```text
myserver/
  items/ui/button.json
  models/ui/button.json
  textures/ui/button.png
```

`items/ui/button.json` selects the model:

```json
{"model":{"type":"minecraft:model","model":"myserver:ui/button"}}
```

For a flat icon, `models/ui/button.json` contains:

```json
{"parent":"minecraft:item/generated","textures":{"layer0":"myserver:ui/button"}}
```

For 3D geometry, export a Minecraft Java item/block model from Blockbench into `models/`, preserving texture paths, UVs, parents and display transforms. Animated cube `.bbmodel` files are imported through `sources/models/`; see [cinematic imports](cinematics.md). OBJ, glTF, meshes and arbitrary skeletal-controller formats are not converted by the native JSON importer. Native item definitions can use conditional/variant model trees.

For manual imports, include textures in the items atlas. For these `ui/` paths, add `assets/minecraft/atlases/items.json`:

```json
{"sources":[{"type":"minecraft:directory","source":"ui","prefix":"ui/"}]}
```

Merge sources into one atlas file when importing multiple texture directories. Animated textures use a neighboring `.png.mcmeta` file.

For models using a sprite sheet, set their `textures.particle` to a sprite containing only the block's face. An atlas `minecraft:unstitch` source can select that region without changing the original PNG; `minecraft:single` can also rename a sprite. AssetChannel resolves these sprite IDs and checks their source images. See [Minecraft's atlas format](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-19-3#atlas-configuration-files).

Rebuild with `/asset texturepack generate`, wait for success and accept the refreshed pack:

```text
/asset model list
/asset model give myserver:ui/button
/asset display spawn myserver:ui/button
```

Manual model IDs come from `items/`, retaining namespace and slashes. Simple block definitions generate a missing item definition using the same ID as their model. Manual native blocks also need textures in `assets/minecraft/atlases/blocks.json`; the simple format handles both atlases automatically.

## Fonts, icons and HUDs

Put TTF files under `sources/fonts/`. An optional matching YAML sidecar controls rendering. For `sources/fonts/ui/title.ttf`, use `sources/fonts/ui/title.yml`:

```yaml
size: 11
oversample: 1
shift: [0, 0]
```

These are the defaults. The API key is `assetchannel:ui.title`. Native font definitions go under `assets/<namespace>/font/` and retain their `namespace:path` keys. Use native providers for bitmap sheets, custom advances or multiple shifted variants of one font.

Put icon PNGs under `sources/icons/`, with a matching YAML sidecar containing the required height and ascent:

```yaml
height: 16
ascent: 13
```

`sources/icons/ui/rank.png` registers icon `ui.rank`. See [HUD templates](hud.md) for composition and variables. `<space:N>` applies signed horizontal advances up to 16384 pixels. Font shifts/ascent provide vertical positioning. Native font artwork requires manually chosen widths; avoid automatic clipping around background glyphs.

One HUD session per surface is active at a time. Combine related pieces into one template. A live 3D model cannot appear inside a font HUD; use a raster render there.

## Native metadata and overlays

Source files stay unchanged. Automatic setup adds derived resources and extends atlas entries in the generated pack; other native files retain their paths and bytes. An extracted resource pack can go directly into `packs/main/` or a separate `packs/<name>/` folder, alongside optional AssetChannel definitions and sources:

```text
packs/main/
  pack.mcmeta
  pack.png
  assets/...
  overlay_97/assets/...
```

Example `pack.mcmeta`:

```json
{
  "pack": {"min_format": [97, 1], "max_format": [97, 1], "description": "My assets"},
  "overlays": {
    "entries": [
      {"min_format": [97, 0], "max_format": [97, 99], "directory": "overlay_97"}
    ]
  }
}
```

AssetChannel generates 26.3/97.1 compatibility bounds and preserves other metadata sections. Compatible overlays apply over their own pack's base assets. Duplicate native paths across separate packs fail the build. Collisions with generated AssetChannel files fail the build, keeping the previous pack active. Source files remain unchanged.

All native resource categories are imported, including models, blockstates, item definitions, textures, fonts, sounds, particles, equipment, atlases, languages, GUI resources and shaders. `resources()` exposes file descriptors; Minecraft or consuming plugins use categories without a dedicated AssetChannel API.

Imports do not migrate resource formats or register new server block/entity/particle types. Validation checks supported paths, JSON and dependencies, rather than every client schema. Check rendering in Minecraft after version changes.

## Troubleshooting

| Symptom | Check |
|---|---|
| Missing-glyph boxes | Font key, code point and PNG. Each bitmap source cell must fit 256x256 regardless of rendered height; tile larger images. |
| Inventory title loses its font | The menu plugin must preserve the rich component. Apply `InventoryOpenEvent.titleOverride(component)` for its own inventory holder; avoid legacy-string conversion. |
| Missing model textures | Include parent/texture dependencies and items-atlas sources. `items/` definitions select geometry from `models/`. |
| Client rejects a block model for using the items atlas | Simple block setup generates block-only sprite aliases. For raw imports, use unique sprite IDs across atlases; registering one ID in both blocks and items is ambiguous. |
| Unknown model | Use the item-definition ID, such as `myserver:ui/button`, then rebuild. |
| Old artwork | Wait for a successful rebuild and client pack reload. Check server build errors and client resource-loading logs. |
| Artwork moves or clips | Check GUI scale, aspect ratio, font ascent/shift, shadow styles and packs overriding the same resources. |

Create items and entities on the Paper server thread. Check `isPackReady(player)` before using assets in your own menus; managed displays and HUDs enforce readiness themselves.
