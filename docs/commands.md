# Commands and permissions

## Operator commands

Commands require `assetchannel.command.test`, granted to operators by default.

```text
/asset texturepack generate
/asset model list
/asset model give <namespace:path>
/asset display spawn <namespace:path>
/asset display list
/asset display remove <uuid>
/asset block list
/asset block give <block-id>
/asset block remove
/asset block overlay <block-id>
/asset block blink <block-id> [interval-ticks] [duration-ticks]
/asset block clear
/asset resource list
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
/asset hud show @s notification.banner with heading=<gold>Test;body=Hello world
/asset hud update @s notifications heading=<yellow>Updated;body=Still visible
/asset hud hide @s notifications
```

Namespaced IDs accept `:` and `/` without quotes. `/asset display spawn` places a persistent model in front of you and prints its UUID. `/asset block remove` removes the managed block you are looking at within six blocks, without drops or effects. `/asset resource list` prints up to 50 native descriptors and the total count.

Player placement with a tagged block item uses the separate `assetchannel.blocks.place` permission, granted by default. Placement/break handling respects cancelled events; operator API/command removal is administrative. Menus, navigation and game logic belong to the consuming plugin.


## Permission model

`assetchannel.command.test` is granted to operators by default and gates the operator command tree. `assetchannel.blocks.place` defaults to true and permits managed-block placement; normal protection events still apply. Consumers should enforce their own gameplay permissions before invoking APIs.

Commands based on the executor's current player/position require an in-game player. Use selectors and explicit positions on branches that expose those arguments. Completion lists loaded catalog IDs. Native model IDs containing `:` and `/` are accepted as the final model argument without quotes.

## There is no AssetChannel inventory editor

AssetChannel's operator interface is commands plus source files. It does not provide AnimationChannel's cutscene studio. Edit original media/sidecars/definitions, regenerate, then validate the refreshed pack with a client. Other plugins build their own inventories from the public catalog API.
