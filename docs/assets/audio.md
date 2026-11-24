# Sounds and soundtracks

Choose an import type according to who controls playback:

| Type | Input | Use |
| --- | --- | --- |
| Generated sound | `sources/sounds/npc/talk.mp3` | Short events through the sound API |
| Timeline audio | `sources/scene-audio/voice/intro.ogg` | Resumable, leased scene voice/music/effects |
| Soundtrack | `sources/music/lobby/` | Long tracks, synchronization, stages and speakers |
| Native sound | `assets/<namespace>/sounds.json` and sound files | Existing resource-pack event keys |

Source files remain unchanged. AssetChannel derives listener-relative channel-preserving audio and positional mono variants. FFmpeg is bundled in the shaded runtime; an external executable can be configured. Invalid input or conversion failure leaves the previous pack active.

## Timeline audio

Import OGG Vorbis or MP3 under `sources/scene-audio/`. `voice/intro.ogg` becomes `voice.intro`. AssetChannel prepares one-second media boundaries and sixteen independently leased lanes per media asset/player, so one owner can stop its playback without stopping another owner's lane. `timelineAudio(id)` resolves duration/segments; `leaseAudio` owns playback. Seeking rejoins at the next prepared boundary, not an arbitrary sample.

Use AnimationChannel SOUND cues for cutscene scheduling, gain envelopes, root/bone emitters and lip-sync markers. Ordinary sounds and existing soundtrack sessions retain their separate ownership rules.

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
packs/main/sources/music/boss/
|-- config.yml
`-- stages/
    |-- phase-1/
    |   |-- config.yml
    |   `-- calm.mp3
    `-- enraged/
        |-- config.yml
        `-- intense.ogg
```

Root `packs/main/sources/music/boss/config.yml`:

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
