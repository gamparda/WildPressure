package kr.gamparida.wildpressure.ai;

import kr.gamparida.wildpressure.WildPressurePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public final class RewardService {
    private final WildPressurePlugin plugin;
    private final int experiencePerKill;
    private final int itemPerKills;
    private final Material rewardMaterial;

    public RewardService(WildPressurePlugin plugin) {
        this.plugin = plugin;
        var c = plugin.getConfig();
        experiencePerKill = Math.max(0, c.getInt("ai.rewards.experience-per-operation-kill", 2));
        itemPerKills = Math.max(1, c.getInt("ai.rewards.one-item-per-kills", 10));
        Material parsed = Material.matchMaterial(c.getString("ai.rewards.material", "IRON_NUGGET"));
        rewardMaterial = parsed == null ? Material.IRON_NUGGET : parsed;
    }

    public void settle(HuntOperation operation) {
        for (Map.Entry<UUID, Integer> entry : operation.playerKills().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            int kills = entry.getValue();
            if (experiencePerKill > 0) player.giveExp(kills * experiencePerKill);
            int items = kills / itemPerKills;
            if (items > 0) {
                var leftovers = player.getInventory().addItem(new ItemStack(rewardMaterial, items));
                leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            player.sendMessage(plugin.prefix() + "야생 작전 기여도 " + kills + "회가 정산되었습니다.");
        }
    }
}
