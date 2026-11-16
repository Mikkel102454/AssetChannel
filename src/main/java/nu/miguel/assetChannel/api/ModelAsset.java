package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import java.util.Objects;

/** An imported item definition. Geometry and textures are resolved by the client. */
public record ModelAsset(String id, Key key, boolean internal) {
    /** Preserves the original public-model constructor for consumers. */
    public ModelAsset(String id, Key key) { this(id, key, false); }
    public ModelAsset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        internal = internal || (key.namespace().equals("assetchannel") && key.value().startsWith("generated/blocks/"));
    }
    /** Call on the server thread. The receiving player must have the current pack. */
    public ItemStack createItem(Material material) {
        Objects.requireNonNull(material, "material");
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR || !material.isItem())
            throw new IllegalArgumentException("Expected a non-air item material");
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey(key.namespace(), key.value())));
        return item;
    }
}
