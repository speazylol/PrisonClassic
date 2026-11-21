package dev.prisonclassic.boss;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Механики боёв с мировыми боссами: агрессия на ближайшего игрока и трёхстадийный бой со Злым Тыковым.
 *
 * Злой Тыков (шахта 20). Вход на арену — строго по Боевому Пропуску (тратится при первом входе за жизнь босса).
 *  1 стадия (HP 100–66%): пол арены превращается в магма-блоки на 1 минуту — АОЕ-урон по ногам.
 *  2 стадия (66–33%): настенные дропперы ведут непрерывный обстрел отравленными стрелами.
 *  3 стадия (&lt;33%): спавн 4 Тотемов (эндермены). Пока жив хоть один тотем — босс неуязвим и регенерирует HP.
 */
public final class BossFightService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey totemKey;
    private Fight fight;

    private static final class Fight {
        UUID boss;
        Location center;
        int phase = 0;
        final Set<UUID> participants = new HashSet<>();
        final Map<Location, BlockData> floor = new HashMap<>();
        long magmaUntil = 0L;
        final List<UUID> totems = new ArrayList<>();
        boolean totemsSpawned = false;
    }

    public BossFightService(PrisonClassic plugin) {
        this.plugin = plugin;
        this.totemKey = new NamespacedKey(plugin, "pumpkin_totem");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private String pumpkinId() {
        return this.plugin.getConfig().getString("bosses.evil-pumpkin.type", "evil_pumpkin");
    }

    private double radius() {
        return this.plugin.getConfig().getDouble("bosses.evil-pumpkin.arena-radius", 13.0);
    }

    public void onSpawn(BossManager.Type t, LivingEntity e, Location point) {
        if (t.id().equalsIgnoreCase(this.pumpkinId())) {
            this.endFight();
            Fight f = new Fight();
            f.boss = e.getUniqueId();
            f.center = point.clone();
            this.fight = f;
            Bukkit.broadcast(Msg.parse("<gold>🎃 Злой Тыков пробудился на шахте 20! <gray>Вход на арену — только с Боевым пропуском."));
        }
    }

    public void onGone(BossManager.Type t, UUID id) {
        if (this.fight != null && id.equals(this.fight.boss)) {
            this.endFight();
        }
    }

    private void endFight() {
        if (this.fight == null) {
            return;
        }
        this.restoreFloor(this.fight);
        for (UUID t : this.fight.totems) {
            Entity e = Bukkit.getEntity(t);
            if (e != null) {
                e.remove();
            }
        }
        this.fight = null;
    }

    /** Неуязвимость Злого Тыкова на 3-й стадии, пока живы тотемы. */
    public boolean immune(Entity e) {
        Fight f = this.fight;
        return f != null && e.getUniqueId().equals(f.boss) && f.phase >= 3 && this.totemsAlive(f) > 0;
    }

    private int totemsAlive(Fight f) {
        int n = 0;
        for (UUID t : f.totems) {
            Entity e = Bukkit.getEntity(t);
            if (e != null && e.isValid() && !e.isDead()) {
                n++;
            }
        }
        return n;
    }

    private void tick() {
        // Агрессия всех мировых боссов на ближайшего игрока
        for (BossManager.Type t : this.plugin.bosses().types()) {
            if (t.minion()) {
                continue;
            }
            LivingEntity boss = this.plugin.bosses().alive(t.id());
            if (boss == null) {
                continue;
            }
            Player near = null;
            double best = 16.0 * 16.0;
            for (Player p : boss.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                double d = p.getLocation().distanceSquared(boss.getLocation());
                if (d < best) {
                    best = d;
                    near = p;
                }
            }
            if (near != null) {
                this.plugin.bosses().aggro(boss, near);
            }
        }
        Fight f = this.fight;
        if (f == null) {
            return;
        }
        Entity raw = Bukkit.getEntity(f.boss);
        if (!(raw instanceof LivingEntity boss) || !boss.isValid()) {
            this.endFight();
            return;
        }
        this.gate(f);
        double max = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        double frac = boss.getHealth() / max;
        int phase = frac > 2.0 / 3.0 ? 1 : (frac > 1.0 / 3.0 ? 2 : 3);
        boolean engaged = !f.participants.isEmpty();
        if (!engaged) {
            return;
        }
        if (phase > f.phase || f.phase == 0) {
            f.phase = Math.max(f.phase, phase);
            this.announce(f, f.phase);
        }
        long now = System.currentTimeMillis();
        // Стадия 1: магма на минуту
        if (f.magmaUntil > 0L && now >= f.magmaUntil) {
            this.restoreFloor(f);
            f.magmaUntil = -1L;
            this.tell(f, "<gray>Магма остыла — пол арены восстановлен.");
        }
        if (f.magmaUntil > 0L) {
            for (Player p : this.inArena(f)) {
                if (p.isOnGround()) {
                    p.damage(this.plugin.getConfig().getDouble("bosses.evil-pumpkin.magma-damage", 2.0));
                }
            }
        }
        // Стадия 2: отравленные стрелы из настенных дропперов
        if (f.phase == 2) {
            this.volley(f);
        }
        // Стадия 3: тотемы, щит и регенерация
        if (f.phase >= 3) {
            if (!f.totemsSpawned) {
                this.spawnTotems(f);
            }
            int alive = this.totemsAlive(f);
            if (alive > 0) {
                boss.setHealth(Math.min(max, boss.getHealth() + max * this.plugin.getConfig().getDouble("bosses.evil-pumpkin.regen-per-second", 0.01)));
                boss.getWorld().spawnParticle(Particle.ENCHANT, boss.getLocation().add(0.0, 1.5, 0.0), 30, 0.6, 1.0, 0.6, 0.5);
                for (Player p : this.inArena(f)) {
                    p.sendActionBar(Msg.parse("<gold>Тыков под щитом! <white>Уничтожьте тотемы: " + alive + "/4"));
                }
            }
        }
    }

    private void announce(Fight f, int phase) {
        switch (phase) {
            case 1 -> {
                this.tell(f, "<gold><bold>Стадия 1:</bold> <red>арена превращается в магму! <gray>(1 минута)");
                this.magma(f);
            }
            case 2 -> this.tell(f, "<gold><bold>Стадия 2:</bold> <dark_green>настенные дропперы открыли огонь отравленными стрелами!");
            default -> this.tell(f, "<gold><bold>Стадия 3:</bold> <light_purple>Тыков призвал 4 Тотема — он неуязвим и лечится, пока они стоят!");
        }
    }

    private void tell(Fight f, String msg) {
        for (Player p : this.inArena(f)) {
            p.sendMessage(Msg.parse(msg));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8F, 0.7F);
        }
    }

    private List<Player> inArena(Fight f) {
        List<Player> out = new ArrayList<>();
        double r = this.radius();
        for (Player p : f.center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(f.center) <= r * r && p.getGameMode() != GameMode.SPECTATOR) {
                out.add(p);
            }
        }
        return out;
    }

    /** Вход на арену строго по Боевому пропуску. */
    private void gate(Fight f) {
        double r = this.radius() + 1.0;
        for (Player p : f.center.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || f.participants.contains(p.getUniqueId())) {
                continue;
            }
            if (p.getLocation().distanceSquared(f.center) > r * r) {
                continue;
            }
            if (this.plugin.specials().take(p, "battle_pass", 1)) {
                f.participants.add(p.getUniqueId());
                p.sendMessage(Msg.parse("<gold>Боевой пропуск принят. <gray>Вы участник боя со Злым Тыковым."));
            } else {
                Vector out = p.getLocation().toVector().subtract(f.center.toVector()).setY(0.0);
                if (out.lengthSquared() < 0.01) {
                    out = new Vector(1, 0, 0);
                }
                Location back = f.center.clone().add(out.normalize().multiply(r + 4.0));
                back.setY(f.center.getY() + 1.0);
                back.setDirection(f.center.toVector().subtract(back.toVector()));
                p.teleport(back);
                p.sendMessage(Msg.parse("<red>Вход на арену Злого Тыкова — только с Боевым пропуском! <gray>(выкапывается на шахте 20 или с пыльных слизней шахты 11)"));
            }
        }
    }

    private void magma(Fight f) {
        int r = (int)this.radius();
        Location c = f.center;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                Block b = c.getWorld().getBlockAt(c.getBlockX() + x, c.getBlockY() - 1, c.getBlockZ() + z);
                if (b.getType().isSolid() && b.getType() != Material.BEDROCK && b.getType() != Material.MAGMA_BLOCK) {
                    f.floor.put(b.getLocation(), b.getBlockData());
                    b.setType(Material.MAGMA_BLOCK, false);
                }
            }
        }
        f.magmaUntil = System.currentTimeMillis() + this.plugin.getConfig().getLong("bosses.evil-pumpkin.magma-seconds", 60L) * 1000L;
    }

    private void restoreFloor(Fight f) {
        for (Map.Entry<Location, BlockData> e : f.floor.entrySet()) {
            e.getKey().getBlock().setBlockData(e.getValue(), false);
        }
        f.floor.clear();
    }

    private void volley(Fight f) {
        List<Player> targets = this.inArena(f);
        if (targets.isEmpty()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double rad = this.radius();
        int shooters = this.plugin.getConfig().getInt("bosses.evil-pumpkin.dispensers", 8);
        for (int i = 0; i < shooters; i++) {
            double ang = Math.PI * 2.0 * i / shooters;
            Location from = f.center.clone().add(Math.cos(ang) * (rad - 0.5), 2.0, Math.sin(ang) * (rad - 0.5));
            Player t = targets.get(r.nextInt(targets.size()));
            Vector dir = t.getEyeLocation().toVector().subtract(from.toVector());
            if (dir.lengthSquared() < 0.01) {
                continue;
            }
            Arrow a = f.center.getWorld().spawnArrow(from, dir.normalize(), 1.6F, 6.0F);
            a.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 0), true);
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.setDamage(2.0);
            from.getWorld().playSound(from, Sound.BLOCK_DISPENSER_LAUNCH, 0.6F, 1.0F);
        }
    }

    private void spawnTotems(Fight f) {
        f.totemsSpawned = true;
        double off = this.radius() - 3.0;
        double[][] pos = {{off, 0}, {-off, 0}, {0, off}, {0, -off}};
        for (double[] p : pos) {
            Location l = f.center.clone().add(p[0], 0.0, p[1]);
            Enderman e = l.getWorld().spawn(l, Enderman.class);
            e.customName(Msg.parse("<light_purple><bold>Тотем"));
            e.setCustomNameVisible(true);
            e.setAI(false);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(this.plugin.getConfig().getDouble("bosses.evil-pumpkin.totem-health", 60.0));
            e.setHealth(e.getAttribute(Attribute.MAX_HEALTH).getValue());
            e.getPersistentDataContainer().set(this.totemKey, PersistentDataType.BYTE, (byte)1);
            f.totems.add(e.getUniqueId());
        }
    }

    private boolean isTotem(Entity e) {
        return e.getPersistentDataContainer().has(this.totemKey, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTotemTeleport(EntityTeleportEvent e) {
        if (this.isTotem(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTotemWater(EntityDamageEvent e) {
        if (this.isTotem(e.getEntity()) && (e.getCause() == EntityDamageEvent.DamageCause.DROWNING || e.getCause() == EntityDamageEvent.DamageCause.FALL)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onTotemDeath(EntityDeathEvent e) {
        if (!this.isTotem(e.getEntity())) {
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        Fight f = this.fight;
        if (f != null) {
            int left = this.totemsAlive(f) - (e.getEntity().isValid() ? 1 : 0);
            if (left <= 0) {
                this.tell(f, "<green><bold>Все тотемы уничтожены!</bold> <gray>Щит Злого Тыкова снят — добивайте!");
            } else {
                this.tell(f, "<yellow>Тотем уничтожен. <gray>Осталось: " + left);
            }
        }
    }
}
