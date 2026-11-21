package dev.prisonclassic.listener;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.faction.Faction;
import dev.prisonclassic.quest.QuestType;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class GeneralListener implements Listener {
    private final PrisonClassic plugin;

    public GeneralListener(PrisonClassic var1) {
        this.plugin = var1;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent var1) {
        this.plugin.data().load(var1.getUniqueId(), var1.getName());
    }

    /** Мобы сами не спавнятся: только боссы/мобы плагина (CUSTOM), команды и яйца админа. */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (!this.plugin.getConfig().getBoolean("block-natural-mobs", true)) return;
        switch (e.getSpawnReason()) {
            case CUSTOM, COMMAND, SPAWNER_EGG -> {
            }
            default -> e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent var1) {
        Player var2 = var1.getPlayer();
        PlayerData var3 = this.plugin.data().get(var2);
        if (var3 == null) {
            var3 = this.plugin.data().load(var2.getUniqueId(), var2.getName());
        }

        var3.name = var2.getName();
        if (var3.stars > 0L) { // старый баланс звёзд (до 4.0) -> звёзды Незера в инвентарь
            long old = var3.stars;
            var3.stars = 0L;
            this.plugin.specials().giveStars(var2, old);
            var2.sendMessage(Msg.parse("<yellow>Ваши звёзды (" + old + ") теперь предметы — они в инвентаре."));
        }
        this.plugin.quests().ensureDaily(var3);
        this.plugin.levels().applyBonuses(var2, var3);
        this.plugin.board().add(var2);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            PlayerData now = this.plugin.data().get(var2);
            if (var2.isOnline() && now != null) {
                this.plugin.sixes().report(var2, now);
            }
        }, 60L);
        this.plugin.upgrades().giveStarterKit(var2, var3);
        if (var3.lastSeen > 0L) {
            // Сытость сохраняется в PlayerData (ТЗ: параметры сытости).
            var2.setFoodLevel(Math.max(0, Math.min(20, var3.food)));
            var2.setSaturation(Math.max(0.0F, var3.saturation));
        }
        if (!var2.hasPlayedBefore()) {
            Location var4 = this.plugin.spawn();
            if (var4 != null) {
                var2.teleport(var4);
            }
        }

        if (var3.faction == null && var3.level >= this.plugin.getConfig().getInt("factions.min-level", 5)) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (var2.isOnline()) {
                    Msg.send(var2, "faction-choose");
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent var1) {
        this.plugin.board().remove(var1.getPlayer());
        this.plugin.data().unload(var1.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent var1) {
        Location var2 = this.plugin.spawn();
        if (var2 != null) {
            var1.setRespawnLocation(var2);
        }

        Player var3 = var1.getPlayer();
        PlayerData var4 = this.plugin.data().get(var3);
        if (var4 != null) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.levels().applyBonuses(var3, var4));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent var1) {
        Player var2 = var1.getEntity();
        PlayerData var3 = this.plugin.data().get(var2);
        if (var3 != null) {
            var3.deaths++;
        }

        Player var4 = var2.getKiller();
        if (var4 != null && !var4.equals(var2)) {
            PlayerData var5 = this.plugin.data().get(var4);
            if (var5 != null) {
                var5.kills++;
                this.plugin.quests().progress(var4, var5, QuestType.KILL, 1.0);
            }
        }

        if (this.plugin.getConfig().getBoolean("keep-inventory", true)) {
            var1.setKeepInventory(true);
            var1.getDrops().clear();
            var1.setKeepLevel(true);
            var1.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent var1) {
        if (this.plugin.getConfig().getBoolean("no-hunger", false)) {
            var1.setCancelled(true);
        } else if (var1.getEntity() instanceof Player var2) {
            PlayerData var4 = this.plugin.data().get(var2);
            if (var4 != null && this.plugin.donate().noNeeds(var4.donate) && var1.getFoodLevel() < var2.getFoodLevel()) {
                var1.setCancelled(true);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onPvp(EntityDamageByEntityEvent var1) {
        if (var1.getEntity() instanceof Player var2) {
            Player var12 = Players.attacker(var1.getDamager());
            if (var12 != null && !var12.equals(var2)) {
                Location var4 = this.plugin.spawn();
                double var5 = this.plugin.getConfig().getDouble("spawn.protect-radius", 30.0);
                if (var4 != null && var4.getWorld() == var2.getWorld() && var4.distanceSquared(var2.getLocation()) <= var5 * var5) {
                    var1.setCancelled(true);
                    Msg.send(var12, "spawn-pvp");
                } else {
                    PlayerData var7 = this.plugin.data().get(var12);
                    PlayerData var8 = this.plugin.data().get(var2);
                    if (var7 != null && var8 != null) {
                        if (this.plugin.gangs().sameGang(var7, var8) || this.plugin.families().married(var7, var8)) {
                            var1.setCancelled(true);
                            var12.sendMessage(Msg.parse("<red>Нельзя бить свою банду и семью!"));
                        } else if (var7.faction != null && var7.faction.equals(var8.faction)) {
                            var1.setCancelled(true);
                            Msg.send(var12, "faction-friendly");
                        } else {
                            Faction var9 = this.plugin.factions().get(var7.faction);
                            // Базовый урон уровня (+1 каждые 5 ур.) уже в атрибуте ATTACK_DAMAGE; здесь — только фракционный бонус.
                            double var10 = var9 == null ? 1.0 : var9.damageBonus();
                            var1.setDamage(var1.getDamage() * var10);
                        }
                    }
                }
            }
        }
    }
}
