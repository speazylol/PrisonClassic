package dev.prisonclassic.enchant;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Players;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Боевая логика кастомных чар и навыков тренера.
 * Порядок применения урона (по атакующему): +урон по мобам (тренер) → крит по игроку (тренер) → Проклятие (тренер) →
 * чары меча (Вампа/Пожиратель/Слепота/Окаменение). По жертве: Уклонение → Несокрушимый/Тяжёлые → Просвещение → Вуду/Кактус/Волна.
 * Тотем бессмертия срабатывает на смертельный урон (EntityDamageEvent, HIGHEST).
 */
public final class EnchantListener implements Listener {
    private final PrisonClassic plugin;
    private final EnchantService ench;
    private static final PotionEffectType[] NEGATIVE = new PotionEffectType[]{
        PotionEffectType.POISON,
        PotionEffectType.WITHER,
        PotionEffectType.BLINDNESS,
        PotionEffectType.WEAKNESS,
        PotionEffectType.HUNGER,
        PotionEffectType.NAUSEA,
        PotionEffectType.MINING_FATIGUE,
        PotionEffectType.DARKNESS,
        PotionEffectType.SLOWNESS
    };

    public EnchantListener(PrisonClassic plugin, EnchantService ench) {
        this.plugin = plugin;
        this.ench = ench;
        Bukkit.getScheduler().runTaskTimer(plugin, this::passiveTick, 40L, 40L);
    }

    private static void fx(Player p, PotionEffectType t, int ticks, int amp) {
        p.addPotionEffect(new PotionEffect(t, ticks, amp, true, false, false));
    }

    private void passiveTick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.ench.armorLevel(p, Ench.CLEANSE) > 0 && r.nextDouble() < 0.25) {
                for (PotionEffectType t : NEGATIVE) {
                    p.removePotionEffect(t);
                }
            }
            int ninja = this.ench.armorLevel(p, Ench.NINJA);
            if (ninja >= 1) {
                fx(p, PotionEffectType.SPEED, 100, ninja >= 3 ? 1 : 0);
            }
            if (ninja >= 2) {
                fx(p, PotionEffectType.REGENERATION, 100, ninja >= 3 ? 2 : 0);
            }
            int speed = this.ench.armorLevel(p, Ench.SPEED);
            if (speed > 0) {
                fx(p, PotionEffectType.SPEED, 100, speed - 1);
            }
            int jump = this.ench.armorLevel(p, Ench.JUMP);
            if (jump > 0) {
                fx(p, PotionEffectType.JUMP_BOOST, 100, jump - 1);
            }
            if (this.ench.armorLevel(p, Ench.HEAVY) > 0) {
                fx(p, PotionEffectType.SLOWNESS, 100, 1);
            }
        }
    }

    // ===================== Книги и пыль (перетаскивание) =====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        ItemStack cursor = e.getCursor();
        ItemStack target = e.getCurrentItem();
        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != p.getInventory()) {
            return;
        }
        Object[] book = this.ench.readBook(cursor);
        int dust = this.ench.dustPct(cursor);
        if (book != null) {
            e.setCancelled(true);
            Ench en = (Ench)book[0];
            int lvl = (Integer)book[1];
            int chance = (Integer)book[2];
            if (en == Ench.TOTEM) {
                for (ItemStack worn : p.getInventory().getArmorContents()) {
                    if (worn != null && worn != target && !worn.equals(target) && this.ench.level(worn, Ench.TOTEM) > 0) {
                        p.sendMessage(Component.text("Тотем бессмертия: лимит — 1 книга на сет брони.", NamedTextColor.RED));
                        return;
                    }
                }
            }
            String err = this.ench.apply(target.clone(), en, lvl);
            if (err != null) {
                p.sendMessage(Component.text(err, NamedTextColor.RED));
                return;
            }
            this.consumeOne(e);
            if (ThreadLocalRandom.current().nextInt(100) < chance) {
                ItemStack done = target.clone();
                this.ench.apply(done, en, lvl);
                e.setCurrentItem(done);
                p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.2F);
                p.sendMessage(Component.text("Зачарование успешно: " + en.title + " " + EnchantService.roman(lvl), NamedTextColor.GREEN));
            } else {
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                p.sendMessage(Component.text("Не получилось — книга рассыпалась (предмет цел).", NamedTextColor.RED));
            }
        } else if (dust > 0 && this.ench.readBook(target) != null) {
            e.setCancelled(true);
            Object[] tb = this.ench.readBook(target);
            int next = Math.min(this.ench.maxBookChance(), (Integer)tb[2] + dust);
            if (next <= (Integer)tb[2]) {
                p.sendMessage(Component.text("Шанс книги уже максимальный.", NamedTextColor.RED));
                return;
            }
            this.consumeOne(e);
            e.setCurrentItem(this.ench.bookWithChance(target, next));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        }
    }

    private void consumeOne(InventoryClickEvent e) {
        ItemStack c = e.getCursor().clone();
        c.setAmount(c.getAmount() - 1);
        e.setCursor(c.getAmount() > 0 ? c : null);
    }

    // ===================== Урон =====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Player attacker = Players.attacker(e.getDamager());

        // --- Жертва-игрок: защитные чары ---
        if (e.getEntity() instanceof Player victim) {
            boolean precise = attacker != null && this.ench.handLevel(attacker, Ench.PRECISION) > 0;
            double evasion = this.ench.armorLevel(victim, Ench.EVASION) > 0 ? this.plugin.getConfig().getDouble("combat.evasion", 0.17) : 0.0;
            if (!precise && evasion > 0.0 && r.nextDouble() < evasion) {
                e.setCancelled(true);
                victim.sendActionBar(Component.text("Уклонение!", NamedTextColor.AQUA));
                return;
            }
            double reduce = this.ench.armorLevel(victim, Ench.NESOKRA) * 0.04 + (this.ench.armorLevel(victim, Ench.HEAVY) > 0 ? 0.16 : 0.0);
            if (reduce > 0.0) {
                e.setDamage(e.getDamage() * (1.0 - Math.min(0.6, reduce)));
            }
            int enlight = this.ench.armorSum(victim, Ench.ENLIGHT);
            if (enlight > 0) {
                // Суммируется со всех частей брони (мета — 5 книг на сет); после 6 — убывающая отдача.
                double eff = enlight <= 6 ? enlight : 6.0 + (enlight - 6) * (2.0 / 3.0);
                double heal = e.getDamage() * 0.04 * eff;
                double max = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (!victim.isDead()) {
                        victim.setHealth(Math.min(max, victim.getHealth() + heal));
                    }
                });
            }
            int voodoo = this.ench.armorLevel(victim, Ench.VOODOO);
            if (voodoo > 0 && r.nextDouble() < 0.1 * voodoo && e.getDamager() instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0));
            }
            int cactus = this.ench.armorLevel(victim, Ench.CACTUS);
            if (cactus > 0 && e.getDamager() instanceof LivingEntity src && !src.equals(victim)) {
                double[] chance = {0.0, 0.10, 0.15, 0.20};
                double[] part = {0.0, 0.25, 0.35, 0.50};
                if (r.nextDouble() < chance[cactus]) {
                    double back = e.getDamage() * part[cactus];
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        if (src.isValid() && !src.isDead()) {
                            src.damage(back, victim);
                        }
                    });
                    victim.sendActionBar(Component.text("Кактус: урон возвращён!", NamedTextColor.GREEN));
                }
            }
            int wave = this.ench.armorLevel(victim, Ench.WAVE);
            if (wave > 0) {
                double[] chance = {0.0, 0.06, 0.10, 0.15};
                double[] dmg = {0.0, 3.0, 5.0, 7.0};
                if (r.nextDouble() < chance[wave]) {
                    this.wave(victim, dmg[wave]);
                }
            }
        }

        // --- Атакующий-игрок: навыки тренера и чары оружия ---
        if (attacker != null && e.getEntity() instanceof LivingEntity target && !target.equals(attacker)) {
            PlayerData ad = this.plugin.data().get(attacker);
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            boolean melee = e.getDamager() instanceof Player;
            if (ad != null) {
                if (!(target instanceof Player)) {
                    double mob = this.plugin.trainer().mobDamage(ad);
                    if (mob > 0.0) {
                        e.setDamage(e.getDamage() + mob);
                    }
                } else {
                    double crit = this.plugin.trainer().critChance(ad);
                    if (crit > 0.0 && r.nextDouble() < crit) {
                        e.setDamage(e.getDamage() * (1.5 + ad.perk("crit_damage") * 0.05));
                        attacker.sendActionBar(Component.text("Критический удар!", NamedTextColor.RED));
                        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 20, 0.3, 0.5, 0.3, 0.2);
                    }
                    double curse = this.plugin.trainer().curseChance(ad);
                    if (melee && curse > 0.0 && r.nextDouble() < curse) {
                        int sec = this.plugin.getConfig().getInt("trainer.curse.seconds", 4);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, sec * 20, 0));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, sec * 20, 0));
                        target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.5, 0.3, 0.02);
                        target.sendMessage(Component.text("На вас наложено Проклятие!", NamedTextColor.DARK_PURPLE));
                        attacker.sendActionBar(Component.text("Проклятие!", NamedTextColor.DARK_PURPLE));
                    }
                }
            }
            if (melee) {
                int vamp = this.ench.level(hand, Ench.VAMPIRE);
                if (vamp > 0) {
                    double max = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
                    attacker.setHealth(Math.min(max, attacker.getHealth() + e.getDamage() * 0.05 * vamp));
                }
                int dev = this.ench.level(hand, Ench.DEVOURER);
                if (dev > 0) {
                    double[] part = {0.0, 0.06, 0.09, 0.12};
                    double max = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
                    attacker.setHealth(Math.min(max, attacker.getHealth() + e.getDamage() * part[dev]));
                    if (r.nextDouble() < 0.2) {
                        attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 + 20 * dev, 0));
                    }
                }
                int blind = this.ench.level(hand, Ench.BLIND);
                if (blind > 0) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    if (blind >= 2) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, blind - 2));
                    }
                }
                if (this.ench.level(hand, Ench.PETRIFY) > 0 && r.nextDouble() < 0.06) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                }
            } else if (e.getDamager() instanceof AbstractArrow) {
                int poison = this.ench.level(hand, Ench.POISON_ARROW);
                if (poison > 0 && r.nextDouble() < 0.15 * poison) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                }
            }
            if (this.ench.armorLevel(attacker, Ench.GLUTTON) > 0 && target.getHealth() - e.getFinalDamage() <= 0.0) {
                attacker.setHealth(Math.min(attacker.getAttribute(Attribute.MAX_HEALTH).getValue(), attacker.getHealth() + 2.0));
            }
        }
    }

    private void wave(Player src, double dmg) {
        Location c = src.getLocation();
        c.getWorld().spawnParticle(Particle.SPLASH, c.clone().add(0.0, 0.5, 0.0), 120, 2.5, 0.3, 2.5, 0.2);
        c.getWorld().playSound(c, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0F, 0.8F);
        for (Entity en : src.getNearbyEntities(4.0, 2.0, 4.0)) {
            if (!(en instanceof LivingEntity le) || le.equals(src)) {
                continue;
            }
            if (le instanceof Player op && !this.plugin.canPvp(src, op)) {
                continue;
            }
            le.damage(dmg, src);
            Vector push = le.getLocation().toVector().subtract(c.toVector()).setY(0.0);
            if (push.lengthSquared() > 0.01) {
                le.setVelocity(push.normalize().multiply(0.9).setY(0.35));
            }
        }
        src.sendActionBar(Component.text("Волна воды!", NamedTextColor.AQUA));
    }

    // ===================== Тотем бессмертия =====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.getHealth() - e.getFinalDamage() > 0.0) {
            return;
        }
        int lvl = this.ench.armorMax(p, Ench.TOTEM);
        PlayerData d = this.plugin.data().get(p);
        if (lvl <= 0 || d == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < d.totemCooldown) {
            return;
        }
        e.setCancelled(true);
        d.totemCooldown = now + this.plugin.getConfig().getLong("enchants.totem.cooldown-seconds-" + lvl, lvl >= 2 ? 180L : 300L) * 1000L;
        p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), lvl >= 2 ? 8.0 : 4.0));
        p.setFireTicks(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, lvl >= 2 ? 200 : 140, lvl >= 2 ? 3 : 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, lvl >= 2 ? 200 : 120, 1));
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0.0, 1.0, 0.0), 80, 0.5, 1.0, 0.5, 0.4);
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
        p.sendMessage(Component.text("Тотем бессмертия спас вас! Перезарядка: " + (d.totemCooldown - now) / 1000L + " сек.", NamedTextColor.GOLD));
    }

    // ===================== Луки =====================

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getProjectile() instanceof Arrow main)) {
            return;
        }
        int triple = this.ench.level(e.getBow(), Ench.TRIPLE);
        if (triple <= 0) {
            return;
        }
        double chance = triple >= 2 ? 0.30 : 0.15;
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        Vector v = main.getVelocity();
        for (double yaw : new double[]{-10.0, 10.0}) {
            Arrow extra = p.launchProjectile(Arrow.class, v.clone().rotateAroundY(Math.toRadians(yaw)));
            extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            extra.setCritical(main.isCritical());
            extra.setDamage(main.getDamage());
            extra.setFireTicks(main.getFireTicks());
        }
        p.sendActionBar(Component.text("Триствольник!", NamedTextColor.GOLD));
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        if (this.ench.level(bow, Ench.BOMBER) > 0) {
            Location at = arrow.getLocation();
            TNTPrimed tnt = at.getWorld().spawn(at, TNTPrimed.class);
            tnt.setFuseTicks(6);
            tnt.setSource(shooter);
        }
        int leap = this.ench.level(bow, Ench.LEAP);
        if (leap > 0 && e.getHitEntity() instanceof LivingEntity t && !t.equals(shooter)) {
            if (t instanceof Player tp && !this.plugin.canPvp(shooter, tp)) {
                return;
            }
            if (ThreadLocalRandom.current().nextDouble() < (leap >= 2 ? 0.20 : 0.10)) {
                Vector dir = t.getLocation().getDirection().setY(0.0);
                if (dir.lengthSquared() < 0.01) {
                    dir = new Vector(0, 0, 1);
                }
                Location behind = t.getLocation().clone().subtract(dir.normalize().multiply(1.5));
                behind.setDirection(t.getLocation().toVector().subtract(behind.toVector()));
                if (behind.getBlock().isPassable() && behind.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
                    shooter.teleport(behind);
                    shooter.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.3F);
                    shooter.sendActionBar(Component.text("Скачок!", NamedTextColor.LIGHT_PURPLE));
                }
            }
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (this.plugin.getConfig().getBoolean("combat.protect-blocks", true)) {
            e.blockList().clear();
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (this.plugin.getConfig().getBoolean("combat.protect-blocks", true)) {
            e.blockList().clear();
        }
    }
}
