package nu.miguel.assetChannel.api;

public enum ResourceKind {
    ITEM,
    MODEL,
    BLOCKSTATE,
    TEXTURE,
    ANIMATION_METADATA,
    FONT,
    SOUND,
    SOUND_DEFINITIONS,
    PARTICLE,
    EQUIPMENT,
    ATLAS,
    LANGUAGE,
    SHADER,
    POST_EFFECT,
    TEXT,
    OTHER;

    public static ResourceKind of(String path) {
        if (path.endsWith(".mcmeta")) return ANIMATION_METADATA;
        if (path.equals("sounds.json")) return SOUND_DEFINITIONS;
        String directory = path.contains("/") ? path.substring(0, path.indexOf('/')) : "";
        return switch (directory) {
            case "items" -> ITEM;
            case "models" -> MODEL;
            case "blockstates" -> BLOCKSTATE;
            case "textures" -> TEXTURE;
            case "font" -> FONT;
            case "sounds" -> SOUND;
            case "particles" -> PARTICLE;
            case "equipment" -> EQUIPMENT;
            case "atlases" -> ATLAS;
            case "lang" -> LANGUAGE;
            case "shaders" -> SHADER;
            case "post_effect" -> POST_EFFECT;
            case "texts" -> TEXT;
            default -> OTHER;
        };
    }
}
