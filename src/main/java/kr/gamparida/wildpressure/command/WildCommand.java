package kr.gamparida.wildpressure.command;

import kr.gamparida.wildpressure.WildPressurePlugin;
import kr.gamparida.wildpressure.population.EntityIndex;
import kr.gamparida.wildpressure.population.SpawnDirector;
import kr.gamparida.wildpressure.region.RegionKey;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;

public final class WildCommand implements CommandExecutor, TabCompleter {
    private final WildPressurePlugin plugin;

    public WildCommand(WildPressurePlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("wildpressure.admin")) {
            sender.sendMessage(prefix() + ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "status" -> { status(sender); yield true; }
            case "inspect" -> { inspect(sender); yield true; }
            case "profiler" -> { profiler(sender); yield true; }
            case "reload" -> { plugin.reloadRuntime(); sender.sendMessage(prefix() + "설정을 다시 적용했습니다."); yield true; }
            default -> { sender.sendMessage(prefix() + "/wild <status|inspect|profiler|reload>"); yield true; }
        };
    }

    private void status(CommandSender sender) {
        EntityIndex index = plugin.index();
        SpawnDirector director = plugin.director();
        sender.sendMessage(prefix() + ChatColor.GREEN + "개체군 상태");
        sender.sendMessage(" §7관리 몹: §f" + index.total() + " §8/ 목표 " + director.desiredPopulation());
        sender.sendMessage(" §7활성 생태 구역: §f" + director.activeRegions().size());
        sender.sendMessage(" §7온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage(" §7평균 MSPT: §f" + String.format(Locale.ROOT, "%.2f", Bukkit.getAverageTickTime()));
    }

    private void inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "플레이어만 현재 구역을 조사할 수 있습니다.");
            return;
        }
        int size = Math.max(1, plugin.getConfig().getInt("population.region-size-chunks", 8));
        RegionKey key = RegionKey.fromChunk(player.getWorld().getUID(), player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4, size);
        Map<EntityType, Long> counts = plugin.index().countTypes(key);
        sender.sendMessage(prefix() + "현재 생태 구역 §f(" + key.x() + ", " + key.z() + ")");
        sender.sendMessage(" §7관리 개체 수: §f" + plugin.index().count(key));
        counts.entrySet().stream().sorted(Map.Entry.<EntityType, Long>comparingByValue().reversed())
                .limit(12).forEach(e -> sender.sendMessage(" §8- §f" + e.getKey() + "§7: " + e.getValue()));
    }

    private void profiler(CommandSender sender) {
        SpawnDirector d = plugin.director();
        sender.sendMessage(prefix() + ChatColor.AQUA + "최근 재조정 프로파일");
        sender.sendMessage(" §7실행 시간: §f" + String.format(Locale.ROOT, "%.3f ms", d.lastDurationNanos() / 1_000_000.0));
        sender.sendMessage(" §7생성/정리: §a+" + d.lastSpawned() + " §c-" + d.lastRemoved());
        sender.sendMessage(" §7활성 고갈 구역: §f" + plugin.depletion().activeDepletions(System.currentTimeMillis()));
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "&2[WildPressure]&r "));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String value = args[0].toLowerCase(Locale.ROOT);
        return List.of("status", "inspect", "profiler", "reload").stream().filter(s -> s.startsWith(value)).toList();
    }
}
