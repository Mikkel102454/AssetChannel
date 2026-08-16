# AssetChannel

AssetChannel turns fonts, icons, sounds, adaptive music, and reusable HUD templates into a Minecraft resource pack and exposes them through a Paper API.

## What it provides

- Automatic resource-pack generation and hosting
- Listener-relative stereo and positional mono audio variants
- Short synchronized music clips for fast late joins
- Smooth streamed tracks when synchronization is not required
- Looping soundtracks, named music stages, and controlled stage transitions
- Fixed, entity-based, and multi-speaker sound origins
- Content-addressed FFmpeg caching and transactional pack publication
- An API for other Paper plugins and operator test commands

## Requirements

- Paper 26.2
- Java 25
- A reachable HTTP port for the generated resource pack

FFmpeg is bundled in the shaded plugin JAR. A system FFmpeg installation is optional.

## Quick start

1. Build the plugin and place its shaded JAR in the server's `plugins` directory.
2. Start the server once so AssetChannel creates its folders and default configuration.
3. Put assets under `plugins/AssetChannel/` using the layout below.
4. Run `/asset texturepack generate` after changing assets.
5. Wait for the success message. The build happens in the background and the previous pack remains active if it fails.

For remote players, set `overrides.pack.host` in `config.yml` to a hostname or address they can reach. `127.0.0.1` works only when the Minecraft client runs on the server machine.

## Asset layout

All asset IDs are lowercase and dot-separated versions of their paths.

```text
plugins/AssetChannel/
|-- config.yml
|-- font/
|   `-- ui/title.ttf
|-- icons/
|   |-- ui/rank.png
|   `-- ui/rank.yml
|-- sound/
|   `-- npc/talk.mp3
|-- music/
|   `-- lobby/
|       |-- config.yml
|       `-- main.mp3
`-- hud/
    `-- notification/banner.yml
```

Examples:

- `font/ui/title.ttf` becomes `assetchannel:ui.title`.
- `icons/ui/rank.png` becomes icon ID `ui.rank`.
- `sound/npc/talk.mp3` becomes sound ID `npc.talk`.
- `music/lobby/` becomes soundtrack ID `lobby`.
- `hud/notification/banner.yml` becomes HUD template ID `notification.banner`.

### HUD templates

HUDs are domain-neutral templates. Callers supply Adventure components for declared `<var:name>` inputs; supplied components are inserted directly and are never reparsed as MiniMessage.

```yaml
surface: boss-bar
slot: notifications
duration: 4s
priority: 100
resume-interrupted: true
variables:
  heading: {required: true}
  body: {default: ""}
  icon: {default: "<icon:ui.info>"}
layout:
  background: ui.banner
  content: "<var:icon> <yellow><bold><var:heading></bold> <var:body>"
  horizontal-padding: 16
  overflow: ellipsis
boss-bar: {color: yellow, overlay: progress, progress: 1.0}
fallback:
  mode: text-only
  content: "<var:heading>: <var:body>"
```

Supported surfaces are `boss-bar`, `title`, and `action-bar`. A zero duration is persistent. Each player has one active request per surface; higher priorities interrupt, while equal/lower priorities queue. Slots support replacement, updates, and removal.

### Icons

Every PNG icon needs a YAML sidecar with the same base name:

```yaml
unicode: E000
height: 16
ascent: 15
```

`unicode` is optional. When omitted, AssetChannel deterministically assigns an available character from the Unicode private-use range `U+E000`–`U+F8FF`. Existing explicit values remain supported and are reserved before automatic assignment.

```yaml
height: 16
ascent: 15
```

Asset IDs and explicit Unicode values must be unique. Plugins can retrieve either an explicit or generated value by ID:

```java
IconAsset icon = assets.icon("ui.banner").orElseThrow();
int codePoint = icon.codePoint();
String unicode = icon.unicode(); // for example U+E5A2
String direct = assets.iconUnicode("ui.banner").orElseThrow();
```

### Standalone sounds

Put MP3 or OGG Vorbis files anywhere under `sound/`. AssetChannel generates:

- A channel-preserving variant for listener-relative playback
- A mono variant for fixed locations, entities, and spatial broadcasts

The API chooses the correct variant from the supplied `SoundOrigin`.

## Music playback modes

Each soundtrack or stage chooses between synchronized clips and smooth streaming through `auto-split`.

### Synchronized mode

Use this when late-joining players, reconnects, speaker updates, or stage changes need a short wait:

```yaml
order:
  - main.mp3

mute-vanilla-music: true
mute-other-music: true

auto-split:
  enabled: true
  interval-seconds: 5

playback:
  handoff-lead-milliseconds: 75
```

Five seconds is the recommended default. A synchronized late join waits at most approximately one clip. AssetChannel schedules clips using the requested five-second source timeline, so encoder padding cannot accumulate timing drift.

Short clips are preloaded by Minecraft. Adjacent clips start slightly early to cover server-tick, network, and client-decoder latency. Minecraft still cannot guarantee sample-perfect playback between separate sound events.

### Smooth mode

Use this when uninterrupted music matters more than fast synchronized joins:

```yaml
order:
  - main.mp3

auto-split:
  enabled: false
```

The track is streamed as one event and therefore has no artificial five-second handoffs. A synchronized player joining midway must wait for the next natural file or loop boundary.

### Audio variants and quality

Music is generated twice:

- Listener-relative sessions preserve stereo or the source channel layout.
- Fixed, entity, and speaker origins use mono so Minecraft can apply panning and attenuation.

Valid unsplit listener-relative OGG Vorbis is copied byte-for-byte without a lossy second encode. MP3 listener audio, split clips, and positional variants are cached and encoded as 48 kHz OGG Vorbis at configurable variable quality. Source files are never modified, and generated resource-pack paths always end in `.ogg`.

## Ordered tracks

A soundtrack folder requires `config.yml`. It may mix MP3 and OGG Vorbis sources. When it contains one supported audio file, `order` may be omitted. Multiple supported files must all appear exactly once, using their full filenames and extensions:

```yaml
order:
  - intro.mp3
  - loop.ogg

auto-split:
  enabled: false
```

Auto-splitting accepts exactly one source file. Use a single assembled source when you need short synchronization boundaries.

## Named stages

A staged soundtrack puts no MP3 or OGG files at its root. Its root configuration selects a default stage:

```text
music/boss/
|-- config.yml
`-- stages/
    |-- phase-1/
    |   |-- config.yml
    |   `-- calm.mp3
    `-- enraged/
        |-- config.yml
        `-- intense.ogg
```

Root `music/boss/config.yml`:

```yaml
default-stage: phase-1
mute-vanilla-music: true
mute-other-music: true
```

Each stage has its own `order`, `auto-split`, and optional `playback.handoff-lead-milliseconds`. Stage IDs must be lowercase and match `[a-z0-9][a-z0-9._-]*`.

Stage changes are controlled by the consuming plugin:

- `immediate` stops the current clip and starts the target stage now.
- `session-boundary` moves every member together at the next shared clip boundary.
- `player-boundary` moves each member when their current clip finishes.

## Plugin configuration

Default `config.yml`:

```yaml
music:
  ffmpeg:
    executable: bundled
    timeout-seconds: 300
    vorbis-quality: 6
  playback:
    handoff-lead-milliseconds: 75
    vanilla-mute-refresh-seconds: 30
```

Common optional pack-host overrides:

```yaml
overrides:
  pack:
    host: play.example.com
    port: 25566
    required: true
    replace: true
```

`handoff-lead-milliseconds` accepts `0` through `250`; zero disables early handoff. A stage-level value overrides the global value. Vorbis quality accepts `0` through `10`.

Converted audio is cached under `plugins/AssetChannel/cache/audio/`. Cache keys include the complete source content and encoding settings, so changed inputs are rebuilt while unchanged inputs are reused. Unsplit listener OGG files bypass the conversion cache because they are copied unchanged.

## Java API

Declare AssetChannel as a plugin dependency, then load its Paper service after both plugins are enabled:

```java
AssetChannelApi assets = Bukkit.getServicesManager().load(AssetChannelApi.class);
if (assets == null) throw new IllegalStateException("AssetChannel is unavailable");
```

All API calls that mutate or start playback must run on the server thread.

### Start a soundtrack

```java
SessionSettings settings = new SessionSettings(
        true,                            // loop
        true,                            // synchronized playback
        true,                            // retain across reconnects
        1.0f,                            // volume
        null,                            // configured default stage
        SoundOrigin.listenerRelative()  // stereo playback
);

MusicSession session = assets.startSoundtrack(
        "lobby",
        List.of(playerOne, playerTwo),
        settings
);

session.addPlayer(latePlayer);
session.setStage("enraged", StageTransitionSettings.nextPlayerBoundary());
session.stop();
```

`session.stage()` returns the requested target stage. `memberStages()` shows each member's current stage during staggered changes, and `transitioning()` reports whether work remains.

A player may belong to multiple music sessions at the same time. Set `mute-other-music: true` in a soundtrack's root `config.yml` when that soundtrack should silence the player's other AssetChannel music sessions while it is active. If multiple such sessions contain the same player, the most recently joined session takes priority; earlier sessions keep their timelines and resume when the muting session ends. `mute-vanilla-music` remains independent and controls Minecraft's background music.

### Positional music and speakers

```java
SoundOrigin speakerGroup = SoundOrigin.speakers(List.of(
        SoundOrigin.fixed(new Location(world, 120.5, 64, -32.5)),
        SoundOrigin.fixed(new Location(world, 145.5, 64, -32.5)),
        SoundOrigin.entity(movingSpeakerEntity)
));

SessionSettings settings = new SessionSettings(
        true, true, false, 1.0f, null, speakerGroup
);

MusicSession session = assets.startSoundtrack("lobby", players, settings);
```

Every available speaker in the listener's world emits simultaneously. Minecraft applies distance attenuation independently. Entity speaker positions are refreshed when each synchronized clip begins.

### Standalone sounds

```java
assets.playSound(player, "npc.talk", SoundCategory.VOICE, 1.0f, 1.0f);
assets.playSound(player, "enemy.growl", SoundOrigin.fixed(enemyLocation), SoundCategory.HOSTILE, 1.0f, 1.0f);
assets.playSound(player, "animal.call", SoundOrigin.entity(animal), SoundCategory.NEUTRAL, 1.0f, 1.0f);

Duration effectLength = assets.sound("npc.talk").orElseThrow().duration();
```

`PlaybackResult` distinguishes successful playback, unknown assets, unavailable origins, and players whose pack is not ready. `broadcastSound` returns both players who heard the sound and nearby players still loading the pack.

Every sound-effect playback call requires a Bukkit `SoundCategory`. Available categories are `MASTER`, `MUSIC`, `RECORDS`, `WEATHER`, `BLOCKS`, `HOSTILE`, `NEUTRAL`, `PLAYERS`, `AMBIENT`, `VOICE`, and `UI`. `HOSTILE` uses the Hostile Creatures slider, while `NEUTRAL` uses Friendly Creatures. Calling `stopSound(player, id)` stops that AssetChannel sound in every category; pass a category to stop only that category.

AssetChannel soundtracks use `RECORDS`, so players control them with the Jukebox/Note Blocks slider. Vanilla background-music muting continues to target `MUSIC` independently.

### Fonts and icons

```java
IconAsset rank = assets.icon("ui.rank").orElseThrow();
player.sendMessage(Component.text(rank.character()).font(rank.font()));
```

Sound assets expose their Adventure key and detected source-audio `Duration`. Font assets expose Adventure keys. Icon assets expose their font, code point, rendered character, height, ascent, and scaled rendered width.

### Show and update a HUD

```java
HudDisplayResult result = assets.showHud(player, "notification.banner",
        new HudRequest(Map.of(
                "heading", Component.text("Server Restart"),
                "body", Component.text("Restarting in 5 minutes")
        )));

assets.hudSession(player, "notifications")
        .ifPresent(session -> session.updateVariables(Map.of("body", Component.text("Restarting now"))));
assets.hideHud(player, "notifications");
```

Missing required variables and unknown supplied variables return `INVALID_VARIABLES`. When the pack is unavailable, a template either displays its text-only fallback or returns `PACK_NOT_READY`.

## Operator commands

Commands require `assetchannel.command.test`, granted to operators by default.

```text
/asset texturepack generate
/asset icon test <id>
/asset icon info <id>
/asset icon list
/asset font test <id> [text]
/asset font list
/asset sound play <id> <category> [volume] [pitch]
/asset sound stop <id|all> [category]
/asset sound playat <id> <category> <players> <position> [volume] [pitch]
/asset sound broadcastat <id> <category> <position> [volume] [pitch]
/asset sound playfrom <id> <category> <players> <entity> [volume] [pitch]
/asset sound broadcastfrom <id> <category> <entity> [volume] [pitch]
/asset music start <id> <players> [loop] [synchronized] [retain] [volume]
/asset music add <session-uuid> <players>
/asset music remove <session-uuid> <players>
/asset music stop <session-uuid>
/asset music stages <soundtrack>
/asset music stage <session-uuid> <stage> <immediate|session-boundary|player-boundary>
/asset music origin <session-uuid> <listener|location|entity|speakers> [origin]
/asset music list
/asset hud list
/asset hud show <players> <template>
/asset hud show <players> <template> with <name=value;name=value...>
/asset hud show <players> <template> <slot> <priority> <resume> [name=value;name=value...]
/asset hud update <players> <slot> <name=value;name=value...>
/asset hud hide <players> <slot>
/asset hud active <players>
```

HUD command values use MiniMessage and are separated with semicolons. For example:

```text
/asset hud show @s demo.quest-notification with heading=<gold>Test;body=Hello world
/asset hud update @s quest-notification heading=<yellow>Updated;body=Still visible
/asset hud hide @s quest-notification
```

## Reliability behavior

- Pack commands build in the background rather than blocking the server tick.
- A second build cannot start while another is running.
- Assets are generated in unique staging directories.
- Every source, cached file, and newly generated OGG must contain readable audio with a positive duration.
- `pack.zip` is replaced atomically after the complete build succeeds.
- A failed build leaves the previous pack and active playback untouched.
- Resource-pack readiness events are accepted only for AssetChannel's pack UUID.
- All music sessions share one tick scheduler, and stop operations target only recently active clips.

Successful publication reloads the pack for online players. Active sessions stop because their sound keys belong to the previous client pack; start new sessions after players report that the new pack loaded.

## Troubleshooting

### Players cannot download the pack

- Confirm `overrides.pack.host` is reachable from the players' computers.
- Open or forward `overrides.pack.port` through the firewall and router.
- Do not advertise `127.0.0.1` to remote players.
- Check the server log for the pack URL, SHA-1, and HTTP startup errors.

### Music still has small seams

Minecraft starts separate sound events on client receipt and does not expose sample-accurate scheduling. Try these in order:

1. Keep five-second synchronized clips but tune handoff lead between `50` and `100` ms.
2. Use a slightly longer interval if the use case permits it.
3. Disable auto-split for completely smooth streamed playback.

### Pack generation is slow

The first build creates listener and spatial variants. Later builds reuse the content-addressed cache. Changing FFmpeg quality or source content intentionally invalidates the affected entries.

### A generated pack is rejected

Check that every source is a valid MP3 or OGG Vorbis file, every supported audio file appears exactly once in `order`, IDs are lowercase, and staged soundtracks do not mix root audio with a `stages/` directory. Files with other extensions are not audio inputs, and invalid or audio-less MP3 files make pack generation fail.
