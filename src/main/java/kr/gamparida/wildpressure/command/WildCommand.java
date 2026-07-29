package kr.gamparida.wildpressure.command;

import kr.gamparida.wildpressure.WildPressurePlugin;
import kr.gamparida.wildpressure.ai.HuntOperation;
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
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "status" -> { status(sender); yield true; }
            case "inspect" -> { inspect(sender); yield true; }
            case "operations" -> { operations(sender); yield true; }
            case "profiler" -> { profiler(sender); yield true; }
            case "lure" -> { lure(sender, args); yield true; }
            case "reload" -> { plugin.reloadRuntime(); sender.sendMessage(plugin.prefix() + "설정을 다시 적용했습니다."); yield true; }
            default -> { sender.sendMessage(plugin.prefix() + "/wild <status|inspect|operations|profiler|lure|reload>"); yield true; }
        };
    }

    private void status(CommandSender sender) {
        EntityIndex index = plugin.index();
        SpawnDirector director = plugin.director();
        sender.sendMessage(plugin.prefix() + ChatColor.GREEN + "개체군 및 AI 상태");
        sender.sendMessage(" §7관리 몹: §f" + index.total() + " §8/ 목표 " + director.desiredPopulation());
        sender.sendMessage(" §7활성 생태 구역: §f" + director.activeRegions().size());
        sender.sendMessage(" §7활성 작전: §f" + plugin.ai().operationCount());
        sender.sendMessage(" §7온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage(" §7평균 MSPT: §f" + String.format(Locale.ROOT, "%.2f", Bukkit.getAverageTickTime()));
    }

    private void inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "플레이어만 현재 구역을 조사할 수 있습니다.");
            return;
        }
        RegionKey key = plugin.index().keyOf(player);
        Map<EntityType, Long> counts = plugin.index().countTypes(key);
        double pressure = plugin.pressure().pressure(key, System.currentTimeMillis());
        sender.sendMessage(plugin.prefix() + "현재 생태 구역 §f(" + key.x() + ", " + key.z() + ")");
        sender.sendMessage(" §7관리 개체 수: §f" + plugin.index().count(key));
        sender.sendMessage(" §7야생 압력: §f" + String.format(Locale.ROOT, "%.1f", pressure));
        counts.entrySet().stream().sorted(Map.Entry.<EntityType, Long>comparingByValue().reversed())
                .limit(12).forEach(e -> sender.sendMessage(" §8- §f" + e.getKey() + "§7: " + e.getValue()));
    }

    private void operations(CommandSender sender) {
        List<HuntOperation> operations = plugin.ai().operations();
        sender.sendMessage(plugin.prefix() + "활성 작전: §f" + operations.size());
        for (HuntOperation operation : operations) {
            sender.sendMessage(" §8- §f" + operation.phase() + " §7구역 " + operation.sourceRegion().x() + ","
                    + operation.sourceRegion().z() + " · 생존 " + operation.members().size() + "/" + operation.initialStrength()
                    + " · 처치 " + operation.totalKills());
        }
    }

    private void profiler(CommandSender sender) {
        SpawnDirector d = plugin.director();
        sender.sendMessage(plugin.prefix() + ChatColor.AQUA + "최근 프로파일");
        sender.sendMessage(" §7개체군 재조정: §f" + String.format(Locale.ROOT, "%.3f ms", d.lastDurationNanos() / 1_000_000.0));
        sender.sendMessage(" §7AI 디렉터: §f" + String.format(Locale.ROOT, "%.3f ms", plugin.ai().lastDurationNanos() / 1_000_000.0));
        sender.sendMessage(" §7생성/정리: §a+" + d.lastSpawned() + " §c-" + d.lastRemoved());
        sender.sendMessage(" §7경로 요청/돌파 블록: §f" + plugin.ai().lastPathRequests() + " / " + plugin.ai().trackedSiegeBlocks());
        sender.sendMessage(" §7활성 고갈 구역: §f" + plugin.depletion().activeDepletions(System.currentTimeMillis()));
    }

    private void lure(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) target = Bukkit.getPlayerExact(args[1]);
        else target = sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage(plugin.prefix() + "플레이어를 찾을 수 없습니다. /wild lure <player>");
            return;
        }
        plugin.lures().give(target, 1);
        sender.sendMessage(plugin.prefix() + target.getName() + "에게 소음 미끼를 지급했습니다.");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String value = args[0].toLowerCase(Locale.ROOT);
            return List.of("status", "inspect", "operations", "profiler", "lure", "reload").stream()
                    .filter(s -> s.startsWith(value)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lure")) {
            String value = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(value)).toList();
        }
        return List.of();
    }
}
