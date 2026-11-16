# AssetChannel Java API (Paper 26.3 / Java 25)

Use `nu.miguel.assetChannel.api` for consumer-facing contracts. AssetChannel owns imported assets, pack generation/delivery, managed displays/blocks and playback sessions. Your plugin owns menus, navigation, game rules and the objects it asks AssetChannel to create.

Related guides: [installation and configuration](README.md), [asset imports](ASSETS.md), [world assets and block configuration](WORLD-ASSETS.md).

## Compile and load

Build AssetChannel with `./gradlew build`. Copy `build/libs/AssetChannel-1.0-SNAPSHOT-plain.jar` into your consumer project's `libs/` folder and compile against it. The plain JAR is a compile-time dependency; install the **unclassified shaded JAR** on the server. No published Maven coordinate is assumed here.

In your consumer's `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.242-stable")
    compileOnly(files("libs/AssetChannel-1.0-SNAPSHOT-plain.jar"))
}
java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}
```

Do not bundle/shade AssetChannel into your consumer plugin. Paper supplies Bukkit and Adventure at runtime; AssetChannel supplies its own API service.

Your consumer's `plugin.yml`:

```yaml
name: MyAssetConsumer
version: '1.0'
main: example.MyAssetConsumer
api-version: '26.3'
depend: [AssetChannel]
```

Load the service in `onEnable()`:

```java
package example;

import nu.miguel.assetChannel.api.AssetChannelApi;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyAssetConsumer extends JavaPlugin {
    private AssetChannelApi assets;

    @Override
    public void onEnable() {
        assets = getServer().getServicesManager().load(AssetChannelApi.class);
        if (assets == null) throw new IllegalStateException("AssetChannel service unavailable");
    }
}
```

The examples below are handler snippets: `assets` is this service, and the referenced players, inventory, world and locations come from your command/listener. Import API types from `nu.miguel.assetChannel.api`, Bukkit types from `org.bukkit` and its subpackages, `List`/`Map`/`UUID` from `java.util`, and `Component` from `net.kyori.adventure.text`.

## Thread, chunk and readiness rules

- Call the API and create model items on the Paper server thread. From asynchronous work, schedule the API call with `Bukkit.getScheduler().runTask(yourPlugin, () -> { ... })`.
- World calls require an already loaded target chunk. Check `world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)` or use a location in a player's loaded area. Do not load every chunk to search for assets.
- Check `assets.isPackReady(player)` before opening your own model-based inventory or handing out a pack-dependent visual. It reports readiness for AssetChannel's current pack, not a different server pack.
- Managed displays hide themselves from players whose pack is not ready. Particle/crack effects and sound playback also honor readiness. A descriptor lookup or `createItem()` does not itself check a receiving player's pack.
- Use immutable descriptors as snapshots. Fetch descriptors again after a successful pack rebuild when you need the current catalog. Do not edit returned collections.

## IDs and discovery

| Asset | Example lookup ID | Source |
|---|---|---|
| Generated icon | `ui.rank` | `icons/ui/rank.png` plus `rank.yml` |
| Generated font | `ui.title` | `font/ui/title.ttf` |
| Generated sound | `npc.talk` | `sound/npc/talk.ogg` |
| Soundtrack | `lobby` | `music/lobby/config.yml` and audio |
| HUD template | `notification.banner` | `hud/notification/banner.yml` |
| Block definition | `decorations.crystal` | `blocks/decorations/crystal.yml` |
| Imported item model | `myserver:ui/button` | `imports/assets/myserver/items/ui/button.json` |
| Imported font | `myserver:ui/panel` | `imports/assets/myserver/font/ui/panel.json` |
| Native file descriptor | `myserver:models/ui/button.json` | the actual native file, including directory/extension |

```java
assets.models();                    // Collection<ModelAsset>
assets.model("myserver:ui/button"); // Optional<ModelAsset>
assets.blocks();                    // Collection<BlockAsset>
assets.block("decorations.crystal");
assets.icons(); assets.fonts(); assets.sounds(); assets.soundtracks(); assets.huds();

for (ResourceAsset resource : assets.resources(ResourceKind.TEXTURE)) {
    getLogger().info(resource.key().asString() + " (" + resource.bytes() + " bytes)");
}
```

Collection and optional lookup pairs are `icons()/icon(id)`, `fonts()/font(id)`, `sounds()/sound(id)`, `soundtracks()/soundtrack(id)`, `huds()/hud(id)`, `models()/model(id)` and `blocks()/block(id)`. `iconUnicode(id)` returns an optional conventional Unicode label such as `U+E000`.

`ResourceAsset` exposes `key()`, `kind()` and `bytes()` (the byte count, not file contents). `resources()` catalogs imported native files in the effective asset layers. It does not list `pack.mcmeta`, the pack icon, inactive overlays or generated audio/font/cache outputs. Kinds include `ITEM`, `MODEL`, `BLOCKSTATE`, `TEXTURE`, `ANIMATION_METADATA`, `FONT`, `SOUND`, `SOUND_DEFINITIONS`, `PARTICLE`, `EQUIPMENT`, `ATLAS`, `LANGUAGE`, `SHADER`, `POST_EFFECT`, `TEXT` and `OTHER`. The metadata kind currently groups `.mcmeta` files. Unknown native categories are retained as `OTHER`.

## Model-backed items

```java
if (!assets.isPackReady(player)) {
    player.sendMessage(Component.text("Wait for the resource pack to load."));
    return;
}

ModelAsset model = assets.model("myserver:ui/button").orElseThrow();
ItemStack button = model.createItem(Material.PAPER);
button.editMeta(meta -> meta.displayName(Component.text("Open settings")));
inventory.setItem(13, button);
```

`ModelAsset.id()` and `key()` identify a native **item definition**, which resolves geometry and textures on the client. `createItem(Material)` returns a new stack with `item_model` set and requires a non-air item material. It does not make a placeable managed block; use `createBlockItem()` for that. Your plugin handles its inventory's click/drag events.

## Managed world displays

```java
DisplayOptions options = new DisplayOptions(1.5f, 10, 5, true);
ItemDisplay statue = assets.spawnItemDisplay(location, "myserver:statue", options);
UUID statueId = statue.getUniqueId();

statue.setBrightness(new Display.Brightness(15, 15));
statue.setBillboard(Display.Billboard.FIXED);
// Set native Transformation/matrix/rotation properties for further visual changes.

BlockDisplay stone = assets.spawnBlockDisplay(
        location.clone().add(2, 0, 0),
        Bukkit.createBlockData("minecraft:stone"),
        DisplayOptions.defaults());

assets.displays(); // currently loaded AssetChannel-owned Display entities
assets.removeDisplay(statueId);
assets.removeDisplay(stone.getUniqueId());
```

`DisplayOptions(scale, interpolationTicks, teleportTicks, persistent)` uses these bounds:

| Field | Allowed | Default |
|---|---|---|
| `scale` | finite, greater than 0, at most 64 | `1` |
| `interpolationTicks` | `0..1200` | `0` |
| `teleportTicks` | `0..59` | `0` |
| `persistent` | boolean | `true` |

Initial item displays use the native `FIXED` item transform; set `setItemDisplayTransform(...)` yourself when needed. Placed managed blocks use `NONE` to avoid the item's frame scale. Interpolation settings do not animate anything by themselves: change the entity's location/transformation through Bukkit to animate it.

Keep the UUIDs your plugin creates. `removeDisplay()` returns false for a missing/unloaded/foreign entity and refuses a display belonging to a placed block; use `removeBlock()` for the latter. Do not remove every entry in `assets.displays()` when disabling your plugin, since other consumers may own some of those objects. Nonpersistent displays disappear on unload/shutdown; persistent displays intentionally remain until removed. Native display metadata/transforms persist with the entity.

## Managed blocks

First configure a block definition and import its referenced item model; see [block definitions](WORLD-ASSETS.md#block-definitions).

```java
ItemStack placeable = assets.createBlockItem("decorations.crystal");
// Your plugin decides how to deliver the item. Player placement honors the block placement permission/events.

PlacedAssetBlock placed = assets.placeBlock(emptyLocation, "decorations.crystal");
UUID visualEntityId = placed.displayId();
BlockAsset definitionSnapshot = placed.asset();

assets.placedBlock(emptyLocation).ifPresent(existing ->
        getLogger().info("Managed block: " + existing.asset().id()));

assets.removeBlock(emptyLocation, true); // presentation effects, no item drop
```

`placeBlock()` requires an empty air block, valid world height, a loaded chunk and a registered definition/model. It restores the original block if display creation fails. `PlacedAssetBlock` contains `worldId()`, `x()`, `y()`, `z()`, `asset()`, `displayId()`, `originalData()` and `carrierData()`.

The player-item path fires `BlockPlaceEvent` and rolls back on cancellation or denied build without consuming the item. Cancelled block-break events are respected. A normal successful break drops the block item in non-creative mode when `drops` is enabled. API `placeBlock()`/`removeBlock()` are trusted plugin operations: implement your own authorization/protection checks before calling them. API removal never drops an item.

Block records persist in chunk PDC; current placements keep their definition snapshots across asset reloads. Chunk reconciliation repairs missing visuals and removes orphan visuals, and removal does not restore over a carrier another system has changed. Keep the models used by persistent placements in the pack. Remove placements through AssetChannel before uninstalling, since their collision carriers and persistent displays survive shutdown. Directly removing the display entity bypasses block lifecycle bookkeeping.

The current block implementation uses instant breaking and vanilla carrier collision. Model geometry does not generate new collision meshes or server block IDs.

## Particles, sounds and breaking overlays

```java
assets.spawnParticle(location, Particle.END_ROD, 10, (Void) null);
assets.spawnParticle(location, Particle.BLOCK, 20,
        Bukkit.createBlockData("minecraft:amethyst_block"));
assets.spawnParticle(location, Particle.ITEM, 12,
        assets.model("myserver:particles/crystal_shard").orElseThrow().createItem(Material.PAPER));

assets.showBlockDamage(location, 0.5f, 42);
assets.showBlockDamage(location, 0, 42); // clear this source ID's overlay

PlaybackResult result = assets.playResourceSound(player, "myserver:crystal.break",
        SoundOrigin.fixed(location), SoundCategory.BLOCKS, 1, 1);
```

Particle count is `0..256`; the data argument must match `Particle.getDataType()` (use null for a no-data type). Particle effects go to pack-ready players within 32 blocks, with fixed spread `0.25` per axis and speed `0.05`. Native particle definitions/textures replace existing particle visuals; ITEM particles use the model's particle texture rather than animated 3D copies of its geometry.

Damage progress must be finite in `0..1`. Overlays go to pack-ready players within 64 blocks. Use a distinct `sourceId` for each independently controlled crack animation and clear the same ID when finished. This API changes the visual overlay, not mining hardness, damage or block state. Native destroy-stage textures can be imported at their normal paths.

`playResourceSound()` accepts a native sound-event key from an imported `sounds.json` or Minecraft, independent of generated `SoundAsset` discovery. Volume must be finite and nonnegative; pitch finite and positive. The existing `SoundOrigin` routing and pack-readiness rules apply. Stop an imported raw event narrowly with Bukkit's `player.stopSound(key, category)`; `assets.stopSound(player, id)` looks up a generated sound asset instead.

## Generated sounds and music

```java
assets.playSound(player, "npc.talk", SoundCategory.VOICE, 1, 1);
assets.playSound(player, "enemy.growl", SoundOrigin.fixed(location), SoundCategory.HOSTILE, 1, 1);
assets.stopSound(player, "npc.talk", SoundCategory.VOICE);

MusicSession music = assets.startSoundtrack("lobby", List.of(player),
        new SessionSettings(true, true, false, 1, null, SoundOrigin.listenerRelative()));
music.addPlayer(latePlayer);
music.setStage("enraged", StageTransitionSettings.nextPlayerBoundary());
music.stop();
```

Playback results are `PLAYED`, `UNKNOWN_ASSET`, `PACK_NOT_READY` and `SOURCE_UNAVAILABLE`. `broadcastSound()` also reports played/not-ready player sets. Generated listener-relative sounds preserve channel layout; positional variants use mono. Raw imported sounds play their supplied event definitions without that conversion. Music sessions expose `id()`, `soundtrack()`, `settings()`, `members()`, `stage()`, `memberStages()`, `transitioning()`, `origin()` and `active()`, with player/stage/origin mutators and `stop()`. Look up a music session with `session(UUID)` or list them with `sessions()`; remove a member with `music.removePlayer(player)` and update origins with `music.setOrigin(origin)`. See [music configuration](README.md#music-playback-modes) for synchronization, stream/clip choices and stage semantics.

## Fonts, icons and HUD sessions

```java
IconAsset icon = assets.icon("ui.rank").orElseThrow();
player.sendMessage(Component.text(icon.character()).font(icon.font()));

FontAsset font = assets.font("myserver:ui/panel").orElseThrow();
Component panel = Component.text("\uE000").font(font.key());

HudDisplayResult result = assets.showHud(player, "notification.banner",
        HudRequest.of(Map.of("heading", Component.text("Quest complete"),
                             "body", Component.text("Return to spawn"))));
assets.hudSession(player, "notifications").ifPresent(session ->
        session.updateVariables(Map.of("body", Component.text("Reward ready"))));
assets.hideHud(player, "notifications");
```

Declare HUD variables in YAML; supplied components are inserted without MiniMessage reparsing. Unknown/missing variables return `INVALID_VARIABLES`. Surfaces are `ACTION_BAR`, `TITLE` and `BOSS_BAR`, with one active session per player/surface; priority determines interruption/queuing, while slots determine replacement/lookup/removal. Slots are not independent screen regions. A template controls text fallback versus `PACK_NOT_READY`. A zero duration is persistent.

`HudRequest` accepts variables and optional slot, priority and resume-interrupted overrides. A `HudSession` exposes `id()`, `playerId()`, `templateId()`, `slot()`, `surface()`, `state()`, `remainingDuration()`, `updateVariables()` and `close()`. Use `hudSession(UUID)`/`hudSessions()` to inspect sessions.

Native bitmap glyph source cells must fit the 256x256 font atlas. Tile larger images and combine glyphs with spacing. For inventory titles, preserve the font-styled component through `InventoryOpenEvent.titleOverride(component)` for your own holder. See [asset troubleshooting](ASSETS.md#troubleshooting).

## Reload and failure handling

`/asset texturepack generate` scans/validates assets and builds in staging. A failed build retains the previous pack. A successful rebuild replaces catalogs, stops current HUD/music sessions, refreshes the served pack and asks online players to load it. Wait for the new pack to become ready before resuming pack-dependent UI/playback. Existing world placements retain their snapshots; new placements use the new definitions.

Missing lookups return `Optional.empty()`. Invalid IDs/configuration, unavailable model/block definitions, invalid option/data ranges and invalid world locations can throw `IllegalArgumentException`; wrong-thread calls, unloaded chunks and occupied placement locations can throw `IllegalStateException`. Handle failures at your command/event boundary and report them to the caller. Do not use exception handling as a reason to overwrite an occupied block or remove another consumer's entities.

Native imports cover resource-pack assets, not datapack recipes or mod-only registries. Models and textures do not register new server entity/block/particle types. Other native resource categories are rendered by Minecraft or configured by their consuming system; catalog discovery does not promise a dedicated runtime API for every category.
