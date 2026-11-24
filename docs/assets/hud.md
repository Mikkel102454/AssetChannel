# HUD templates and reservations

HUD templates under `definitions/hud/` compose imported icons/fonts, variables and spacing on native presentation surfaces. The consumer supplies values and owns its session. Use [the API guide's HUD examples](../api/guide.md#fonts-icons-and-hud-sessions) for complete request/update calls and template examples.

## Surfaces and arbitration

| Surface | Use |
| --- | --- |
| `BOSS_BAR` | Persistent labelled bars |
| `TITLE` | Central title/subtitle messages |
| `ACTION_BAR` | Compact overlays, captions and composed screen labels |

One managed HUD session per player/surface is active. Slots, priority and conflict policy control replacement, queueing or rejection. Related pieces should be composed into one template. A title/action-bar reservation suspends or queues AssetChannel HUDs on that surface until its lease closes. Other plugins should use those APIs rather than directly overwriting an owned cinematic surface.

## Template workflow

1. Import the font/icon media and any required sidecars.
2. Create a template under `definitions/hud/`, naming its surface, slot, priority and text.
3. Declare the variables the consumer must supply; use the API's typed Adventure components for values.
4. Rebuild, accept the pack, then use `/asset hud list` and the show/update/hide commands.
5. Close or hide the session when its owner is finished.

`<space:N>` applies a signed horizontal cursor advance, bounded to ±16384. Font shifts/ascent provide vertical placement. Raster backgrounds need deliberately authored metrics rather than automatic clipping. A font HUD cannot contain a live 3D model; use an imported raster image for that surface.

## Programmatic composition

`reserveTitle(player)` and `reserveActionBar(player)` return exclusive leases. `CaptionLayout` and `ScreenTextLayout` build components from imported descriptors; the lease owns the surface, not the layout helper. Keep layout calculations separate from server-thread presentation calls. [Cinematic imports](cinematics.md) show caption/effect lease examples; [integration](../integration.md) explains how AnimationChannel shares them.

## Complete template example

Save as `packs/main/definitions/hud/notification/banner.yml` (ID `notification.banner`). The icon IDs below must exist in your imported catalog; replace them with your own IDs or remove those references.

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

`surface` and `layout.content` are required. IDs/slots use lowercase letters, digits, dots, underscores and hyphens. Variables must be declared, and a required variable cannot also have a default. Template text accepts MiniMessage, `<var:name>`, imported icons and spacing tags. API-supplied Adventure components are inserted directly without reparsing.

| Field | Default / meaning |
| --- | --- |
| `slot` | `default`; replacement and lookup identity on this surface |
| `duration` | `4s`; `0` is persistent; accepts ticks or `ms`/`s`/`m` suffixes |
| `priority` | `0`; higher priorities interrupt |
| `resume-interrupted` | `false`; whether an interrupted request may resume |
| `variables.<name>.required` | `false` |
| `variables.<name>.default` | Optional template text/component value |
| `layout.background` | Optional imported icon ID |
| `layout.subtitle` | Empty; secondary title content |
| `layout.horizontal-padding` | `0`; nonnegative spacing |
| `layout.width` | Optional positive width for overflow handling |
| `layout.overflow` | `ellipsis` or `clip` |
| `boss-bar.color` | `white` |
| `boss-bar.overlay` | `progress` |
| `boss-bar.progress` | `1.0`, between `0` and `1` |
| `title.fade-in` / `title.fade-out` | `500ms` each |
| `title.stay` | Template duration; persistent templates use a long stay |
| `fallback.mode` | `fail` or `text-only` when the pack is not ready |
| `fallback.content` | `layout.content` |

Try `/asset hud show @s notification.banner with heading=<gold>Test;body=Hello world`, then update/hide the `notifications` slot. See [commands](../commands.md) and [API sessions](../api/guide.md#fonts-icons-and-hud-sessions) for exact calls.
