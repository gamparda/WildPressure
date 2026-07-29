package kr.gamparida.wildpressure.listener;

import kr.gamparida.wildpressure.WildPressurePlugin;
import kr.gamparida.wildpressure.ai.WildAiDirector;
import kr.gamparida.wildpressure.item.LureService;
import kr.gamparida.wildpressure.pressure.PressureKind;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ContentListener implements Listener {
    private final WildPressurePlugin plugin;
    private final WildAiDirector ai;
    private final LureService lures;
    private final double blockBreak;
    private final double blockPlace;
    private final double explosion;
    private final double door;
    private final double damageMultiplier;
    private final double combatHit;
    private final double lureStrength;

    public ContentListener(WildPressurePlugin plugin, WildAiDirector ai, LureService lures) {
        this.plugin = plugin;
        this.ai = ai;
        this.lures = lures;
        var c = plugin.getConfig();
        blockBreak = Math.max(0, c.getDouble("pressure.block-break", 1.2));
        blockPlace = Math.max(0, c.getDouble("pressure.block-place", 0.4));
        explosion = Math.max(0, c.getDouble("pressure.explosion", 25));
        door = Math.max(0, c.getDouble("pressure.door", 0.5));
        damageMultiplier = Math.max(0, c.getDouble("pressure.player-damage-multiplier", 0.8));
        combatHit = Math.max(0, c.getDouble("pressure.combat-hit", 1.5));
        lureStrength = Math.max(0, c.getDouble("lure.pressure", 60));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        add(event.getBlock().getLocation(), blockBreak, PressureKind.NOISE, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        add(event.getBlock().getLocation(), blockPlace, PressureKind.NOISE, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        Player source = event.getEntity() instanceof Player player ? player : null;
        add(event.getLocation(), explosion, PressureKind.NOISE, source);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        add(player.getLocation(), event.getFinalDamage() * damageMultiplier, PressureKind.SCENT, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            add(event.getEntity().getLocation(), combatHit, PressureKind.NOISE, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (lures.isLure(item)) {
            event.setCancelled(true);
            Location target = event.getClickedBlock() == null ? event.getPlayer().getLocation()
                    : event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
            add(target, lureStrength, PressureKind.LURE, event.getPlayer());
            consumeLure(event.getPlayer(), item);
            event.getPlayer().sendMessage(plugin.prefix() + "소음 미끼를 작동시켰습니다.");
            return;
        }
        if (event.getClickedBlock() != null && Tag.DOORS.isTagged(event.getClickedBlock().getType())) {
            add(event.getClickedBlock().getLocation(), door, PressureKind.NOISE, event.getPlayer());
        }
    }

    private void consumeLure(Player player, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (item.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else item.setAmount(item.getAmount() - 1);
    }

    private void add(Location location, double strength, PressureKind kind, Player source) {
        ai.addPressure(location, strength, kind, source == null ? null : source.getUniqueId(), System.currentTimeMillis());
    }
}
