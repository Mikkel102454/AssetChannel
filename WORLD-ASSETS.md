# World assets

AssetChannel manages item/block displays and model-backed blocks. See [Assets](ASSETS.md) for model imports and [API](API.md) for dependency setup and the complete method reference.

## Block definitions

`plugins/AssetChannel/blocks/decorations/crystal.yml` registers block asset `decorations.crystal`:

```yaml
model: myserver:blocks/crystal
carrier: minecraft:barrier
particle-block: minecraft:amethyst_block
# particle-model: myserver:particles/crystal_shard
particle-count: 20
place-sound: minecraft:block.amethyst_block.place
break-sound: minecraft:block.amethyst_block.break
drops: true
```

`model` references an imported item definition under `assets/<namespace>/items/`. Custom sound keys require native `sounds.json` entries and their OGG files. `particle-model` selects item particles using that item's particle texture; it does not emit miniature 3D models.

| Setting | Default | Meaning |
|---|---|---|
| `model` | required | Registered native item-definition ID |
| `carrier` | `minecraft:barrier` | Non-air vanilla block data providing collision/physical behavior |
| `particle-block` | `minecraft:stone` | Block data for break particles |
| `particle-model` | absent | Optional item-definition ID; selects item particles instead of block particles |
| `particle-count` | `20` | `0..256`; zero disables break particles |
| `place-sound` | `minecraft:block.stone.place` | Native placement sound-event key |
| `break-sound` | `minecraft:block.stone.break` | Native break sound-event key |
| `drops` | `true` | Drop the tagged block item after a normal non-creative player break |

Run `/asset texturepack generate` after changing definitions or imports. New placements use the rebuilt definition; existing placements retain a snapshot. Plugin configuration changes require a restart.

## Commands and placement

After accepting the pack:

```text
/asset display spawn myserver:blocks/crystal
/asset display list
/asset display remove <uuid>
/asset block give decorations.crystal
/asset block list
/asset block remove
```

Right-click a block face with the tagged item to place it in adjacent empty space. Player placement requires `assetchannel.blocks.place` (granted by default), respects cancelled interactions and fires `BlockPlaceEvent`. Breaking respects cancellation and produces configured effects/drops. `/asset block remove` targets a managed block within six blocks, without effects or drops. Operator commands require `assetchannel.command.test`.

Blocks break instantly and use the configured vanilla carrier for collision. Custom mining hardness, arbitrary collision meshes and automatic reservation of vanilla block states are not implemented. Managed carriers are protected from pistons, fluids, physics, burning and explosions. API callers handle their own authorization.

## API and lifecycle

World methods run on the Paper server thread and require loaded chunks. Displays, sounds and particles are sent only to eligible pack-ready players. Bukkit display objects expose native transforms, brightness and billboard settings.

```java
ItemDisplay display = assets.spawnItemDisplay(location, "myserver:blocks/crystal",
    new DisplayOptions(1.5f, 10, 5, true));
assets.removeDisplay(display.getUniqueId());

PlacedAssetBlock block = assets.placeBlock(emptyLocation, "decorations.crystal");
assets.removeBlock(emptyLocation, true); // effects, no drops
ItemStack item = assets.createBlockItem("decorations.crystal");

assets.spawnParticle(location, Particle.BLOCK, 20,
    Bukkit.createBlockData("minecraft:amethyst_block"));
assets.showBlockDamage(location, 0.5f, 42);
assets.showBlockDamage(location, 0, 42); // clear this animation source
```

`showBlockDamage` renders vanilla crack stages; it does not implement timed mining. Use distinct source IDs for independent animations. Native destroy-stage textures can be replaced through imports.

Persistent displays and block records survive restart. Chunk loading reconciles missing/orphan block displays without overwriting carriers changed by another plugin. Keep resource dependencies used by existing placements in the pack. Nonpersistent standalone displays are removed on chunk unload/shutdown. `displays()` lists loaded owned entities. Remove placed blocks through `removeBlock`, keeping their carriers and records consistent.

Remove managed blocks and persistent displays before uninstalling AssetChannel; shutdown deliberately preserves them.
