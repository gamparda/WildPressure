package kr.gamparida.wildpressure.item;

import kr.gamparida.wildpressure.WildPressurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public final class LureService {
    private final NamespacedKey lureKey;
    private final Material material;

    public LureService(WildPressurePlugin plugin) {
        lureKey = new NamespacedKey(plugin, "noise_lure");
        Material configured = Material.matchMaterial(plugin.getConfig().getString("lure.material", "ECHO_SHARD"));
        material = configured == null ? Material.ECHO_SHARD : configured;
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(material, Math.clamp(amount, 1, material.getMaxStackSize()));
        var meta = item.getItemMeta();
        meta.displayName(Component.text("야생 소음 미끼", NamedTextColor.GOLD));
        meta.lore(java.util.List.of(Component.text("우클릭하면 주변 개체군을 유인합니다.", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(lureKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLure(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(lureKey, PersistentDataType.BYTE);
    }

    public void give(Player player, int amount) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(create(amount));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }
}
