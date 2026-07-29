package kr.gamparida.wildpressure;

import kr.gamparida.wildpressure.ai.*;
import kr.gamparida.wildpressure.command.WildCommand;
import kr.gamparida.wildpressure.item.LureService;
import kr.gamparida.wildpressure.listener.ContentListener;
import kr.gamparida.wildpressure.listener.PopulationListener;
import kr.gamparida.wildpressure.population.*;
import kr.gamparida.wildpressure.pressure.PressureTracker;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class WildPressurePlugin extends JavaPlugin {
    private EntityIndex index;
    private DepletionTracker depletion;
    private SpawnDirector director;
    private PressureTracker pressure;
    private WildAiDirector ai;
    private LureService lures;

    @Override public void onEnable() {
        saveDefaultConfig();
        initialiseRuntime();
        WildCommand wildCommand = new WildCommand(this);
        var command = getCommand("wild");
        if (command == null) throw new IllegalStateException("plugin.yml에 wild 명령어가 없습니다.");
        command.setExecutor(wildCommand);
        command.setTabCompleter(wildCommand);
        getLogger().info("WildPressure가 Paper 26.2 개체군 및 콘텐츠 AI를 시작했습니다.");
    }

    @Override public void onDisable() { shutdownRuntime(); }

    public void reloadRuntime() {
        shutdownRuntime();
        reloadConfig();
        initialiseRuntime();
    }

    private void shutdownRuntime() {
        if (ai != null) ai.stop();
        if (director != null) director.stop();
        HandlerList.unregisterAll(this);
    }

    private void initialiseRuntime() {
        int regionSize = Math.max(1, getConfig().getInt("population.region-size-chunks", 8));
        index = new EntityIndex(this, regionSize);
        depletion = new DepletionTracker(
                Math.max(1, getConfig().getInt("depletion.deaths-to-deplete", 25)),
                Math.max(1, getConfig().getLong("depletion.death-window-seconds", 120)) * 1000L,
                Math.max(1, getConfig().getLong("depletion.refill-cooldown-seconds", 300)) * 1000L);
        pressure = new PressureTracker(
                Math.max(0, getConfig().getDouble("pressure.decay-per-second", 0.15)),
                Math.max(1, getConfig().getDouble("pressure.maximum", 100)));
        lures = new LureService(this);
        SiegeService siege = new SiegeService(this);
        RewardService rewards = new RewardService(this);
        recoverLoadedEntities();
        director = new SpawnDirector(this, index, depletion);
        ai = new WildAiDirector(this, index, pressure, siege, rewards);
        Bukkit.getPluginManager().registerEvents(new PopulationListener(index, depletion, ai, blockedReasons()), this);
        Bukkit.getPluginManager().registerEvents(new ContentListener(this, ai, lures), this);
        director.start();
        ai.start();
    }

    private Set<CreatureSpawnEvent.SpawnReason> blockedReasons() {
        if (!getConfig().getBoolean("natural-spawn-control.enabled", true)) return Set.of();
        EnumSet<CreatureSpawnEvent.SpawnReason> result = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String name : getConfig().getStringList("natural-spawn-control.blocked-reasons")) {
            try { result.add(CreatureSpawnEvent.SpawnReason.valueOf(name.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ex) { getLogger().warning("알 수 없는 SpawnReason: " + name); }
        }
        return result;
    }

    private void recoverLoadedEntities() {
        boolean adopt = getConfig().getBoolean("population.adopt-existing-on-enable", true);
        Set<String> eligible = getConfig().getStringList("spawn.species").stream()
                .map(s -> s.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        int recovered = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                if (index.isManaged(mob) || (adopt && eligible.contains(mob.getType().name()))) {
                    if (!index.isManaged(mob)) index.markAndRegister(mob); else index.register(mob);
                    if (getConfig().getBoolean("spawn.keep-managed-mobs-from-vanilla-despawn", true)) {
                        mob.setRemoveWhenFarAway(false);
                    }
                    recovered++;
                }
            }
        }
        getLogger().info("로드된 관리 몹 " + recovered + "마리를 인덱스에 등록했습니다.");
    }

    public String prefix() {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "&2[WildPressure]&r "));
    }
    public EntityIndex index() { return index; }
    public DepletionTracker depletion() { return depletion; }
    public SpawnDirector director() { return director; }
    public PressureTracker pressure() { return pressure; }
    public WildAiDirector ai() { return ai; }
    public LureService lures() { return lures; }
}
