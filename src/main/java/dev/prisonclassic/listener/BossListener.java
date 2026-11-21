package dev.prisonclassic.listener;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.boss.BossManager;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Players;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BossListener implements Listener {
    private final PrisonClassic plugin;

    public BossListener(PrisonClassic var1) {
        this.plugin = var1;
    }

    @EventHandler(
        priority = EventPriority.HIGHEST,
        ignoreCancelled = true
    )
    public void onDamage(EntityDamageByEntityEvent var1) {
        BossManager.Type var2 = this.plugin.bosses().typeOf(var1.getEntity());
        if (var2 != null) {
            if (this.plugin.bossFights().immune(var1.getEntity())) {
                var1.setCancelled(true);
                Player blocked = Players.attacker(var1.getDamager());
                if (blocked != null) {
                    blocked.sendActionBar(Msg.parse("<light_purple>Щит! Сначала уничтожьте 4 Тотема."));
                }
                return;
            }

            // HP > 1024: урон масштабируется (у сущности 1024 HP, у босса — HP из реестра)
            var1.setDamage(var1.getDamage() * this.plugin.bosses().damageScale(var1.getEntity()));
            Player var3 = Players.attacker(var1.getDamager());
            if (var3 != null) {
                PlayerData var4 = this.plugin.data().get(var3);
                if (var4 != null) {
                    if (var4.level < var2.minLevel()) {
                        var1.setCancelled(true);
                        Msg.send(var3, "boss-locked", Msg.p("level", this.plugin.levels().name(var2.minLevel())));
                    } else {
                        double var5 = ((LivingEntity)var1.getEntity()).getHealth();
                        this.plugin.bosses().addDamage(var1.getEntity(), var3, Math.min(var1.getFinalDamage(), var5));
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onBossHit(EntityDamageByEntityEvent var1) {
        if (var1.getEntity() instanceof Player var2) {
            BossManager.Type var8 = this.plugin.bosses().typeOf(var1.getDamager());
            if (var8 != null && !var8.hitEffect().isEmpty()) {
                String[] var4 = var8.hitEffect().split(":");
                PotionEffectType var5 = PotionEffectType.getByName(var4[0].toUpperCase());
                if (var5 != null) {
                    int var6 = var4.length > 1 ? Integer.parseInt(var4[1]) : 5;
                    int var7 = var4.length > 2 ? Integer.parseInt(var4[2]) : 0;
                    var2.addPotionEffect(new PotionEffect(var5, var6 * 20, var7));
                }
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent var1) {
        if (this.plugin.bosses().typeOf(var1.getEntity()) != null) {
            var1.getDrops().clear();
            var1.setDroppedExp(0);
            this.plugin.bosses().onDeath(var1.getEntity());
        }
    }
}
