package dev.prisonclassic.treasure;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Players;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public final class TreasureService implements Listener {
    private static final Map<String, TreasureService.Def> DEFS = new LinkedHashMap<>();
    private final PrisonClassic plugin;
    private final NamespacedKey key;
    private final NamespacedKey bleedKey;

    private static void def(String var0, String var1, Material var2, String var3, boolean var4) {
        DEFS.put(var0, new TreasureService.Def(var0, var1, var2, var3, var4));
    }

    public TreasureService(PrisonClassic var1) {
        this.plugin = var1;
        this.key = new NamespacedKey(var1, "treasure");
        this.bleedKey = new NamespacedKey(var1, "bleed");
    }

    public Collection<TreasureService.Def> all() {
        return DEFS.values();
    }

    public ItemStack item(String var1, int var2) {
        TreasureService.Def var3 = DEFS.get(var1);
        if (var3 == null) {
            return null;
        } else {
            ItemStack var4 = Items.of(
                var3.material(),
                var3.name() + " <gray>(сокровище)",
                "<gray>" + var3.desc(),
                var3.throwable() ? "<yellow>ПКМ — бросить" : (var1.equals("frozen_blood") ? "<yellow>Перетащите на меч" : "<yellow>ПКМ — использовать")
            );
            var4.setAmount(var2);
            ItemMeta var5 = var4.getItemMeta();
            var5.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, var1);
            var4.setItemMeta(var5);
            return var4;
        }
    }

    private String idOf(ItemStack var1) {
        return var1 != null && var1.hasItemMeta() ? var1.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING) : null;
    }

    public void roll(Player var1, Mine var2) {
        ConfigurationSection var3 = this.plugin.getConfig().getConfigurationSection("treasures.mines");
        if (var3 != null && var3.isString(var2.name)) {
            String var4 = var3.getString(var2.name);
            if (DEFS.containsKey(var4)) {
                if (!(ThreadLocalRandom.current().nextDouble() >= this.plugin.getConfig().getDouble("treasures.chance", 6.0E-4))) {
                    Items.give(var1, this.item(var4, 1));
                    var1.playSound(var1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 2.0F);
                    var1.sendMessage(Msg.parse("<gold>✦ Вы нашли сокровище: " + DEFS.get(var4).name() + "<gray>!"));
                }
            }
        }
    }

    private static int rnd(int var0, int var1) {
        return var0 + ThreadLocalRandom.current().nextInt(var1 - var0 + 1);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent var1) {
        if (var1.getHand() == EquipmentSlot.HAND) {
            if (var1.getAction() == Action.RIGHT_CLICK_AIR || var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
                String var2 = this.idOf(var1.getItem());
                if (var2 != null) {
                    var1.setCancelled(true);
                    Player var3 = var1.getPlayer();
                    PlayerData var4 = this.plugin.data().get(var3);
                    if (var4 != null && !var2.equals("frozen_blood")) {
                        TreasureService.Def var5 = DEFS.get(var2);
                        if (var5.throwable()) {
                            Snowball var6 = var3.launchProjectile(Snowball.class);
                            var6.setItem(new ItemStack(var5.material()));
                            var6.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, var2);
                        } else {
                            long var11 = System.currentTimeMillis();
                            switch (var2) {
                                case "haste":
                                    fx(var3, PotionEffectType.HASTE, rnd(60, 180), 1);
                                    break;
                                case "rooster":
                                    var4.roosterUntil = var11 + rnd(60, 180) * 1000L;
                                    break;
                                case "cheetah":
                                    fx(var3, PotionEffectType.SPEED, rnd(30, 60), 2);
                                    break;
                                case "hellfire":
                                    fx(var3, PotionEffectType.FIRE_RESISTANCE, 2400, 0);
                                    break;
                                case "wolf_heart":
                                    fx(var3, PotionEffectType.REGENERATION, rnd(15, 30), 2);
                                    break;
                                case "death_gifts":
                                    fx(var3, PotionEffectType.RESISTANCE, rnd(10, 20), 1);
                                    break;
                                case "spider_essence":
                                    var4.critBonusUntil = var11 + rnd(10, 20) * 1000L;
                                    break;
                                case "mushrooms":
                                    PotionEffectType[] var10 = new PotionEffectType[]{
                                        PotionEffectType.SPEED,
                                        PotionEffectType.HASTE,
                                        PotionEffectType.JUMP_BOOST,
                                        PotionEffectType.REGENERATION,
                                        PotionEffectType.STRENGTH,
                                        PotionEffectType.NIGHT_VISION,
                                        PotionEffectType.NAUSEA,
                                        PotionEffectType.SLOWNESS,
                                        PotionEffectType.LEVITATION,
                                        PotionEffectType.GLOWING
                                    };
                                    // Случайные эффекты зелий II уровня на 1,5 минуты
                                    int first = ThreadLocalRandom.current().nextInt(var10.length);
                                    int second = (first + 1 + ThreadLocalRandom.current().nextInt(var10.length - 1)) % var10.length;
                                    fx(var3, var10[first], 90, 1);
                                    fx(var3, var10[second], 90, 1);
                                    break;
                                default:
                                    return;
                            }

                            var3.sendMessage(Msg.parse("<gold>✦ " + var5.name() + "<gray>: " + var5.desc()));
                        }

                        var3.playSound(var3.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8F, 1.4F);
                        var1.getItem().setAmount(var1.getItem().getAmount() - 1);
                    } else {
                        if (var2.equals("frozen_blood")) {
                            var3.sendMessage(Msg.parse("<gray>Откройте инвентарь и перетащите кровь на меч."));
                        }
                    }
                }
            }
        }
    }

    private static void fx(Player var0, PotionEffectType var1, int var2, int var3) {
        var0.addPotionEffect(new PotionEffect(var1, var2 * 20, var3, false, true, true));
    }

    public boolean critBonus(PlayerData var1) {
        return System.currentTimeMillis() < var1.critBonusUntil;
    }

    @EventHandler
    public void onHit(ProjectileHitEvent var1) {
        if (var1.getEntity() instanceof Snowball var2) {
            String var9 = var2.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
            if (var9 != null && var1.getHitEntity() instanceof LivingEntity var4) {
                Player var10 = var2.getShooter() instanceof Player var6 ? var6 : null;
                if (!(var4 instanceof Player var11 && !this.plugin.canPvp(var10, var11))) {
                    switch (var9) {
                        case "snow":
                            var4.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));
                            break;
                        case "brick":
                            // 7 сердец чистого моментального урона (сквозь броню)
                            if (var4.getHealth() > 14.0) {
                                var4.setHealth(var4.getHealth() - 14.0);
                                var4.playHurtAnimation(0.0F);
                                if (var10 != null && this.plugin.bosses().typeOf(var4) != null) {
                                    this.plugin.bosses().addDamage(var4, var10, 14.0);
                                }
                            } else {
                                var4.damage(1000.0, var10);
                            }
                            break;
                        case "paradise":
                            // 100 единиц моментального урона мобам и боссам в радиусе
                            for (org.bukkit.entity.Entity near : var4.getWorld().getNearbyEntities(var4.getLocation(), 5.0, 3.0, 5.0)) {
                                if (near instanceof LivingEntity le && !(le instanceof Player) && !(le instanceof org.bukkit.entity.Villager)) {
                                    le.damage(100.0, var10);
                                }
                            }
                            var4.getWorld().spawnParticle(Particle.END_ROD, var4.getLocation().add(0.0, 1.0, 0.0), 60, 2.5, 1.0, 2.5, 0.05);
                            break;
                        case "jack_pumpkin":
                            if (var4 instanceof Player var8) {
                                this.pumpkin(var8);
                            }
                    }

                    var4.getWorld().spawnParticle(Particle.CRIT, var4.getLocation().add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.1);
                }
            }
        }
    }

    private void pumpkin(Player var1) {
        var1.sendEquipmentChange(var1, EquipmentSlot.HEAD, new ItemStack(Material.CARVED_PUMPKIN));
        var1.sendMessage(Msg.parse("<gold>В вас попала Тыква Джека!"));
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (var1.isOnline()) {
                ItemStack var1x = var1.getInventory().getHelmet();
                var1.sendEquipmentChange(var1, EquipmentSlot.HEAD, var1x == null ? new ItemStack(Material.AIR) : var1x);
            }
        }, 600L);
    }

    public String loreLine(ItemStack var1) {
        if (var1 != null && var1.hasItemMeta()) {
            return var1.getItemMeta().getPersistentDataContainer().has(this.bleedKey, PersistentDataType.BYTE)
                ? "<dark_red>Застывшая кровь: следующий удар — кровотечение"
                : null;
        } else {
            return null;
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onApply(InventoryClickEvent var1) {
        if (var1.getWhoClicked() instanceof Player var2) {
            if ("frozen_blood".equals(this.idOf(var1.getCursor()))) {
                ItemStack var6 = var1.getCurrentItem();
                if (var6 != null && var6.getType().name().endsWith("_SWORD")) {
                    var1.setCancelled(true);
                    ItemMeta var4 = var6.getItemMeta();
                    if (var4.getPersistentDataContainer().has(this.bleedKey, PersistentDataType.BYTE)) {
                        var2.sendMessage(Msg.parse("<red>На мече уже есть застывшая кровь."));
                    } else {
                        var4.getPersistentDataContainer().set(this.bleedKey, PersistentDataType.BYTE, (byte)1);
                        var6.setItemMeta(var4);
                        this.plugin.enchants().refreshLore(var6);
                        var1.setCurrentItem(var6);
                        ItemStack var5 = var1.getCursor().clone();
                        var5.setAmount(var5.getAmount() - 1);
                        var1.setCursor(var5.getAmount() > 0 ? var5 : null);
                        var2.playSound(var2.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0F, 0.6F);
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onStrike(EntityDamageByEntityEvent var1) {
        if (var1.getDamager() instanceof Player var2 && var1.getEntity() instanceof LivingEntity var3) {
            ItemStack var9 = var2.getInventory().getItemInMainHand();
            if (var9.hasItemMeta() && var9.getItemMeta().getPersistentDataContainer().has(this.bleedKey, PersistentDataType.BYTE)) {
                ItemMeta var5 = var9.getItemMeta();
                var5.getPersistentDataContainer().remove(this.bleedKey);
                var9.setItemMeta(var5);
                this.plugin.enchants().refreshLore(var9);
                final int var6 = rnd(15, 25);
                if (var3 instanceof Player var7) {
                    var7.sendMessage(Msg.parse("<dark_red>У вас кровотечение!"));
                }

                (new BukkitRunnable() {
                    int left = var6;

                    @Override
                    public void run() {
                        if (this.left-- > 0 && !var3.isDead() && var3.isValid()) {
                            var3.damage(1.0);
                            var3.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, var3.getLocation().add(0.0, 1.0, 0.0), 3, 0.2, 0.3, 0.2, 0.0);
                        } else {
                            this.cancel();
                        }
                    }
                }).runTaskTimer(this.plugin, 20L, 20L);
            }
        }
    }

    private static Player attacker(Entity var0) {
        return Players.attacker(var0);
    }

    static {
        def("haste", "<yellow>Спешка", Material.SUGAR, "Ускорение копания на 1–3 минуты", false);
        def("rooster", "<gold>Золотой петушок", Material.GOLDEN_CARROT, "Бустер монет x2 (+100%) на 1–3 минуты", false);
        def("cheetah", "<gold>Шкура гепарда", Material.RABBIT_HIDE, "Скорость III на 30–60 секунд", false);
        def("hellfire", "<red>Адское пламя", Material.BLAZE_POWDER, "Огнестойкость на 40 минут (соло-фарм адского камня)", false);
        def("wolf_heart", "<red>Сердце волка", Material.RED_DYE, "Регенерация III на 15–30 секунд", false);
        def("death_gifts", "<dark_gray>Дары смерти", Material.WITHER_ROSE, "Сопротивление II на 10–20 секунд", false);
        def("spider_essence", "<dark_purple>Паучья эссенция", Material.FERMENTED_SPIDER_EYE, "+10% к шансу срабатывания всех навыков тренера на 10–20 секунд", false);
        def("mushrooms", "<red>Грибочки три точки", Material.RED_MUSHROOM, "Случайные эффекты зелий II уровня на 1,5 минуты", false);
        def("snow", "<aqua>Снежный снег", Material.SNOWBALL, "Бросить: попадание даёт Замедление IV на 10 секунд", true);
        def("brick", "<red>Кирпич", Material.BRICK, "Бросить: 7 сердец (14 HP) чистого моментального урона", true);
        def("frozen_blood", "<dark_red>Застывшая кровь", Material.REDSTONE, "Положите на меч: первый удар вызывает кровотечение на 15–25 сек", false);
        def("paradise", "<white>Райское правосудие", Material.FEATHER, "Бросить: 100 урона мобам и боссам в радиусе 5 блоков", true);
        def("jack_pumpkin", "<gold>Тыква Джека", Material.CARVED_PUMPKIN, "Бросить: надевает на голову противника тыкву (нельзя снять) на 30 сек", true);
    }

    public record Def(String id, String name, Material material, String desc, boolean throwable) {
    }
}
