# World assets

See [Assets](ASSETS.md) for source files and [API](API.md) for Java usage.

## Block definitions

Create `packs/main/definitions/blocks/warning_block.yml`:

```yaml
render: block
model: myserver:block/warning_block
```

Put the model at `packs/main/assets/myserver/models/block/warning_block.json` and include the PNGs it references under `assets/myserver/textures/`. Run `/asset texturepack generate` and accept the refreshed pack.

AssetChannel creates the item definition, atlas entries and native block-state mapping automatically. You do not need to write those JSON files. The block ID is the definition's relative filename: `warning_block`.

Use `/asset block give warning_block` for the tagged placeable item. `/asset model give <model-id>` gives its visual item without placement behavior. Generated particle helpers stay out of the public model list.

| Render mode | Placement |
|---|---|
| `block` | A native full-cube block; supports warning overlays without barriers or display entities |
| `display` | An item display with a collision carrier; suitable for detailed decorative models |

For display mode, change `render` to `display`. Its collision carrier defaults to a barrier; `carrier` can override it. Model geometry does not create custom collision meshes.

Native placements recover instrument/power changes caused by neighbor updates, including sand and support-block changes. Recovery uses one shared chunk queue, processing up to 32 chunks per tick; it can take a tick or longer under load. Chunk loading also repairs these states. A replacement material or different note is treated as an external change and is left alone. AssetChannel does not change Paper's global note-block settings.

For seams on a full cube, check both geometry and UVs. Sprite-sheet regions can bleed neighboring pixels at lower mip levels. Use a cropped sprite for the placed model, remap its face UVs to that sprite, and set each full boundary face's `cullface` to its direction. `block-model` can select this world geometry while `model` retains the original held-item appearance.

### Particles

Particles use the model by default. Set `particles: false` to disable them, or supply a separate texture:

```yaml
particles:
  texture: myserver:block/warning_particle
```

For a sprite sheet, select its face using **pixel coordinates** `[x, y, width, height]`:

```yaml
particles:
  texture: myserver:block/sheet
  region: [37, 49, 16, 16]
  count: 20
```

The region must fit an imported PNG. AssetChannel generates the cropped atlas sprite and particle model; the original image and model files stay unchanged. Omit `region` to use the whole texture. These are flat Minecraft item particles.

Other optional settings retain their defaults:

| Setting | Default |
|---|---|
| `particle-count` or `particles.count` | `20` (`0..256`) |
| `place-sound` | `minecraft:block.stone.place` |
| `break-sound` | `minecraft:block.stone.break` |
| `drops` | `true` |

Custom sounds need their native `sounds.json` entries and OGG files. Rebuild after editing assets or definitions. Existing physical placements retain their definition snapshots; remove and replace old placements to change their placement mode.

## Native blocks and warning overlays

`render: block` assigns a stable note-block state automatically. Assignments are kept in `storage/block-states.json`, separate from disposable generated files. Keep this folder with server/world backups. Moving a definition between packs does not change its ID or assignment. Removed IDs remain reserved so their states cannot be reused for unrelated blocks.

Other note-block states retain their normal appearance. An ordinary note block using a reserved state will show the custom model too. The pool supports 800 states. An imported `minecraft/blockstates/note_block.json` conflicts with generated reservations and is rejected.

For blinking over an existing block, use a **visual overlay**. It affects one player's view and leaves the actual server block, inventory and behavior untouched. The target must have full-cube collision, be in a loaded chunk already sent to the player, and the player's pack must be ready.

Look at an ordinary full block within six blocks, then:

```text
/asset block blink warning_block
/asset block blink warning_block 5 200
/asset block overlay warning_block
/asset block clear
```

The blink preview defaults to a five-tick interval and 200-tick duration. Interval accepts `2..200` ticks; duration accepts `2..1200`. One shared task runs previews, with one preview per player. `overlay` remains until cleared or its session ends; `clear` restores your overlays. These commands require `assetchannel.command.test`.

The [overlay API](API.md#visual-block-overlays) lets a game plugin control phases with its own shared timer. Sessions close on pack loss/reload, quit, world change, respawn, chunk unload or shutdown. Server block updates can overwrite a fake appearance; refresh it with `setVisible(true)`. Closing restores current block and block-entity data instead of an old snapshot.

## Physical placement and lifecycle

```text
/asset block give warning_block
/asset block list
/asset block remove
```

Right-click a block face with the tagged item to place it in adjacent empty space. Placement requires `assetchannel.blocks.place` (granted by default), respects cancelled interactions and fires `BlockPlaceEvent`. Breaking respects cancellation and produces configured effects/drops. `/asset block remove` targets a managed block within six blocks without effects or drops.

Managed blocks break instantly and use vanilla collision. Native placements suppress tuning, note playback and redstone state changes. Pistons, fluids, physics, burning and explosions cannot move/destroy managed blocks. API callers provide their own authorization. Custom hardness and arbitrary collision meshes are not implemented.

World API calls run on the Paper server thread and require loaded chunks. Persistent block records and displays survive restart. Chunk loading reconciles missing/orphan displays without overwriting blocks changed by another system. Nonpersistent standalone displays are removed on unload/shutdown. Remove managed blocks and persistent displays before uninstalling AssetChannel.

## Advanced and legacy definitions

Definitions without `render` keep their previous behavior. Existing item definitions and atlas files remain supported. Simple setup fills missing resources and respects manual sprite mappings already present in each atlas.

For a conditional/composite item with a different world model, use `block-model` to name the geometry explicitly. `block-state` can pin a reservation instead of allocating one. For example:

```yaml
render: block
model: myserver:block/warning_block
block-model: myserver:block/warning_block
block-state: minecraft:note_block[instrument=pling,note=24,powered=false]
```

The explicit state requires one of the sixteen musical instruments, a note `0..24` and `powered=true` or `false`. Once recorded, an asset's assignment cannot silently change. Rebuild existing explicit definitions once before removing their state fields when converting an older setup; this records their current assignments.

`particle-model` selects an existing item definition for break particles; `particle-block` selects vanilla block-data particles in legacy definitions. Use `particles.texture` for the simple format. Keep source models and definitions needed by existing persistent placements.
