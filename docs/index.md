# AssetChannel

AssetChannel is the asset and resource-pack layer for Paper 26.3 / Java 25. It imports source media, validates references, builds and hosts a pack, tracks player acceptance, and exposes immutable catalogs plus managed playback/display APIs.

AnimationChannel uses those catalogs to author and replay cutscenes. Other plugins can use AssetChannel independently for inventories, world models, placeable blocks, audio and HUDs.

| You want to… | Start here |
| --- | --- |
| Install and publish your first pack | [Installation](setup.md) |
| Find where a file belongs and its asset ID | [Folders and IDs](layout.md) |
| Import a native model or existing pack | [Native assets](assets/native.md) |
| Import an animated `.bbmodel` | [Cinematic assets](assets/cinematics.md) |
| Add fonts, captions or positioned labels | [Fonts and icons](assets/fonts.md) |
| Add sounds, voice clips or adaptive music | [Audio](assets/audio.md) |
| Use assets from another plugin | [Java API guide](api/guide.md) |
| Build cutscenes with AnimationChannel | [Integration](integration.md) |

## Ownership

AssetChannel owns imported media, derived resources, pack delivery/readiness, and the objects or leases created through its API. Your plugin owns gameplay, menus, scene timing and the lifetime of its handles. AnimationChannel owns its scenes, actor movement, cameras, captions and editor.

Authored media belongs under a content pack's `assets/`, `sources/` or `definitions/`. Edit those inputs rather than `generated/`. Publication is transactional: invalid imports leave the previous active pack intact.

## First useful commands

```text
/asset texturepack generate
/asset model list
/asset font list
/asset resource list
```

Use [the complete command guide](commands.md) for arguments, selectors and permissions.
