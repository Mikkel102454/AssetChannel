# Asset imports

AssetChannel builds assets into a resource pack. Consumer plugins own inventories, navigation and game logic. See [README](README.md) for installation and [API](API.md) for Java usage.

## Models and textures

Place native resources under `plugins/AssetChannel/imports/assets/`:

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

For 3D geometry, export a Minecraft Java item/block model from Blockbench into `models/`, preserving texture paths, UVs, parents and display transforms. AssetChannel does not convert `.bbmodel`, OBJ, glTF or skeletal animations. Native item definitions can use conditional/variant model trees.

Include the textures in the items atlas. For these `ui/` paths, add `imports/assets/minecraft/atlases/items.json`:

```json
{"sources":[{"type":"minecraft:directory","source":"ui","prefix":"ui/"}]}
```

Merge sources into one atlas file when importing multiple texture directories. Animated textures use a neighboring `.png.mcmeta` file.

Rebuild with `/asset texturepack generate`, wait for success and accept the refreshed pack:

```text
/asset model list
/asset model give myserver:ui/button
/asset display spawn myserver:ui/button
```

The model ID comes from `items/`, retaining its namespace and slashes. Geometry under `models/` alone does not register an item. See [World assets](WORLD-ASSETS.md) for placeable blocks.

## Fonts, icons and HUDs

Put TTF files under `font/`. An optional matching YAML sidecar controls rendering. For `font/ui/title.ttf`, use `font/ui/title.yml`:

```yaml
size: 11
oversample: 1
shift: [0, 0]
```

These are the defaults. The API key is `assetchannel:ui.title`. Native font definitions go under `imports/assets/<namespace>/font/` and retain their `namespace:path` keys. Use native providers for bitmap sheets, custom advances or multiple shifted variants of one font.

Put icon PNGs under `icons/`, with a matching YAML sidecar containing the required height and ascent:

```yaml
height: 16
ascent: 13
```

`icons/ui/rank.png` registers icon `ui.rank`. See [HUD templates](README.md#hud-templates) for composition and variables. `<space:N>` applies signed horizontal advances up to 16384 pixels. Font shifts/ascent provide vertical positioning. Native font artwork requires manually chosen widths; avoid automatic clipping around background glyphs.

One HUD session per surface is active at a time. Combine related pieces into one template. A live 3D model cannot appear inside a font HUD; use a raster render there.

## Native pack roots

Loose imports preserve their paths and bytes. To include pack metadata, an icon or version overlays, extract a pack into `imports/pack/`:

```text
imports/pack/
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

AssetChannel generates 26.3/97.1 compatibility bounds and preserves other metadata sections. Loose assets override the pack's base assets; compatible overlays apply last. Collisions with generated AssetChannel files fail the build, keeping the previous pack active. Source files remain unchanged.

All native resource categories are imported, including models, blockstates, item definitions, textures, fonts, sounds, particles, equipment, atlases, languages, GUI resources and shaders. `resources()` exposes file descriptors; Minecraft or consuming plugins use categories without a dedicated AssetChannel API.

Imports do not migrate resource formats or register new server block/entity/particle types. Validation checks supported paths, JSON and dependencies, rather than every client schema. Check rendering in Minecraft after version changes.

## Troubleshooting

| Symptom | Check |
|---|---|
| Missing-glyph boxes | Font key, code point and PNG. Each bitmap source cell must fit 256x256 regardless of rendered height; tile larger images. |
| Inventory title loses its font | The menu plugin must preserve the rich component. Apply `InventoryOpenEvent.titleOverride(component)` for its own inventory holder; avoid legacy-string conversion. |
| Missing model textures | Include parent/texture dependencies and items-atlas sources. `items/` definitions select geometry from `models/`. |
| Unknown model | Use the item-definition ID, such as `myserver:ui/button`, then rebuild. |
| Old artwork | Wait for a successful rebuild and client pack reload. Check server build errors and client resource-loading logs. |
| Artwork moves or clips | Check GUI scale, aspect ratio, font ascent/shift, shadow styles and packs overriding the same resources. |

Create items and entities on the Paper server thread. Check `isPackReady(player)` before using assets in your own menus; managed displays and HUDs enforce readiness themselves.
