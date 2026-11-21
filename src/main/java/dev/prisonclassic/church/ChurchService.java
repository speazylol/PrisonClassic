package dev.prisonclassic.church;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ChurchService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey key;
    private String currentService = null;
    private final Set<UUID> blessed = new HashSet<>();
    private final java.util.Map<UUID, Long> lastCredit = new java.util.HashMap<>();

    public ChurchService(PrisonClassic var1) {
        this.plugin = var1;
        this.key = new NamespacedKey(var1, "church_item");
        Bukkit.getScheduler().runTaskTimer(var1, this::diseaseTick, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(var1, this::symptoms, 600L, 600L);
        Bukkit.getScheduler().runTaskTimer(var1, this::serviceTick, 200L, 100L);
        Bukkit.getScheduler().runTaskTimer(var1, this::buffTick, 20L, 20L);
    }

    /** Кубоид церкви (Region из church.a / church.b); если не задан — радиус вокруг точки church. */
    public boolean inside(Player p) {
        Location a = this.plugin.locations().get("church.a");
        Location b = this.plugin.locations().get("church.b");
        if (a != null && b != null && a.getWorld() == p.getWorld()) {
            return dev.prisonclassic.util.Region.of(a, b).contains(p.getLocation());
        }
        Location c = this.church();
        double r = this.c().getDouble("church.radius", 12.0);
        return c != null && c.getWorld() == p.getWorld() && c.distanceSquared(p.getLocation()) <= r * r;
    }

    /** Таймер баффа «активной игры»: идёт только пока игрок в сети. */
    private void buffTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = this.plugin.data().get(p);
            if (d == null || d.churchBoostMs <= 0L) {
                continue;
            }
            d.churchBoostMs = Math.max(0L, d.churchBoostMs - 1000L);
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, this.c().getInt("church.haste-level", 2) - 1, true, false, true));
            if (d.churchBoostMs == 0L) {
                p.sendMessage(Msg.parse("<gray>Благословение церкви закончилось."));
            }
        }
    }

    public String buffLine(PlayerData d) {
        return d.churchBoostMs <= 0L ? "<gray>нет" : "<aqua>Спешка + x" + (1.0 + this.c().getDouble("church.sell-bonus", 0.25)) + " продажа <white>" + dev.prisonclassic.util.Items.fmtTime(d.churchBoostMs / 1000L);
    }

    private FileConfiguration c() {
        return this.plugin.getConfig();
    }

    public Location church() {
        return this.plugin.locations().get("church");
    }

    private void diseaseTick() {
        double var1 = this.c().getDouble("church.disease-chance-per-minute", 0.004);

        for (Player var4 : Bukkit.getOnlinePlayers()) {
            PlayerData var5 = this.plugin.data().get(var4);
            if (var5 != null
                && !var5.sick
                && var4.getGameMode() != GameMode.CREATIVE
                && var5.level >= this.c().getInt("church.disease-min-level", 3)
                && ThreadLocalRandom.current().nextDouble() < var1) {
                var5.sick = true;
                var4.sendMessage(
                    Msg.parse(
                        "<dark_green><bold>Вы заболели!</bold> <gray>Идите в церковь: купите оберег (пожертвование), обменяйте его на лекарство и выпейте."
                    )
                );
                var4.playSound(var4.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.6F, 0.6F);
            }
        }
    }

    private void symptoms() {
        for (Player var2 : Bukkit.getOnlinePlayers()) {
            PlayerData var3 = this.plugin.data().get(var2);
            if (var3 != null && var3.sick) {
                var2.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 700, 0, true, false, true));
                var2.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0, true, false, true));
                if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                    var2.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 140, 0, true, false, true));
                    var2.sendActionBar(Msg.parse("<dark_green>Вам плохо... Вы больны. <gray>(лекарство — в церкви)"));
                }
            }
        }
    }

    public ItemStack charm(int var1) {
        return this.special(Material.TOTEM_OF_UNDYING, "<gold>Оберег", "charm", var1, "<gray>Обменяйте у священника на лекарство");
    }

    public ItemStack medicine(int var1) {
        return this.special(Material.HONEY_BOTTLE, "<green>Лекарство", "medicine", var1, "<gray>ПКМ — выпить и вылечиться");
    }

    private ItemStack special(Material var1, String var2, String var3, int var4, String var5) {
        ItemStack var6 = Items.of(var1, var2, var5);
        var6.setAmount(var4);
        ItemMeta var7 = var6.getItemMeta();
        var7.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, var3);
        var6.setItemMeta(var7);
        return var6;
    }

    private String idOf(ItemStack var1) {
        return var1 != null && var1.hasItemMeta() ? var1.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING) : null;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent var1) {
        if (var1.getHand() == EquipmentSlot.HAND) {
            if (var1.getAction() == Action.RIGHT_CLICK_AIR || var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
                String var2 = this.idOf(var1.getItem());
                if (var2 != null) {
                    var1.setCancelled(true);
                    if (var2.equals("medicine")) {
                        Player var3 = var1.getPlayer();
                        PlayerData var4 = this.plugin.data().get(var3);
                        if (var4 != null) {
                            if (!var4.sick) {
                                var3.sendMessage(Msg.parse("<gray>Вы здоровы, лекарство не нужно."));
                            } else {
                                var1.getItem().setAmount(var1.getItem().getAmount() - 1);
                                var4.sick = false;
                                var3.removePotionEffect(PotionEffectType.WEAKNESS);
                                var3.removePotionEffect(PotionEffectType.NAUSEA);
                                var3.removePotionEffect(PotionEffectType.HUNGER);
                                var3.playSound(var3.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0F, 1.0F);
                                var3.sendMessage(Msg.parse("<green>Вы выздоровели!"));
                            }
                        }
                    }
                }
            }
        }
    }

    public void open(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            double var3 = this.c().getDouble("church.charm-price", 15.0);
            Gui var5 = new Gui(27, Msg.parse("Церковь"));
            var5.fill(Items.filler());
            var5.set(
                11,
                Items.of(
                    Material.TOTEM_OF_UNDYING,
                    "<gold>Пожертвование",
                    "<gray>Купить оберег за <green>$" + Msg.money(var3),
                    var2.sick ? "<red>Вы больны" : "<green>Вы здоровы"
                ),
                (var3x, var4) -> {
                    PlayerData var5x = this.plugin.data().get(var3x);
                    if (var5x.balance < var3) {
                        Msg.send(var3x, "not-enough", Msg.p("cost", Msg.money(var3)));
                    } else {
                        var5x.balance -= var3;
                        Items.give(var3x, this.charm(1));
                        var3x.sendMessage(Msg.parse("<gold>Спасибо за пожертвование! Вы получили оберег."));
                    }
                }
            );
            var5.set(13, Items.of(Material.HONEY_BOTTLE, "<green>Обменять оберег на лекарство", "<gray>Нужен 1 оберег"), (var1x, var2x) -> {
                if (!Items.take(var1x, var1xx -> "charm".equals(this.idOf(var1xx)), 1)) {
                    var1x.sendMessage(Msg.parse("<red>У вас нет оберега. Сначала сделайте пожертвование."));
                } else {
                    Items.give(var1x, this.medicine(1));
                    var1x.sendMessage(Msg.parse("<green>Священник дал вам лекарство. ПКМ — выпить."));
                }
            });
            var5.set(
                15,
                Items.of(
                    Material.CLOCK,
                    "<yellow>Служение",
                    "<gray>Проходит 4 раза в сутки (МСК):",
                    "<white>" + String.join(", ", this.times()),
                    "<gray>Каждая минута в церкви — <aqua>Спешка</aqua> и <green>множитель продажи</green>",
                    "<gray>на 20–40 минут активной игры",
                    this.currentService != null ? "<green>Служение идёт прямо сейчас!" : "<gray>Ближайшее: <white>" + this.nextService()
                )
            );
            var5.open(var1);
        }
    }

    private List<String> times() {
        List var1 = this.c().getStringList("church.service-times");
        return var1.isEmpty() ? List.of("00:55", "06:55", "12:55", "18:55") : var1;
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(ZoneId.of(this.c().getString("church.timezone", "Europe/Moscow")));
    }

    private String nextService() {
        LocalTime var1 = this.now().toLocalTime();
        String var2 = null;

        for (String var4 : this.times()) {
            LocalTime var5 = LocalTime.parse(var4);
            if (var5.isAfter(var1) && (var2 == null || var5.isBefore(LocalTime.parse(var2)))) {
                var2 = var4;
            }
        }

        return var2 == null ? this.times().get(0) : var2;
    }

    private void serviceTick() {
        ZonedDateTime var1 = this.now();
        int var2 = this.c().getInt("church.service-minutes", 5);
        String var3 = null;

        for (String var5 : this.times()) {
            LocalTime var6 = LocalTime.parse(var5);
            ZonedDateTime var7 = var1.with(var6);
            if (var7.isAfter(var1)) {
                var7 = var7.minusDays(1L);
            }

            if (!var1.isBefore(var7) && var1.isBefore(var7.plusMinutes(var2))) {
                var3 = var7.toLocalDate() + " " + var5;
            }
        }

        if (var3 == null) {
            this.currentService = null;
        } else {
            if (!var3.equals(this.currentService)) {
                this.currentService = var3;
                this.blessed.clear();
                Bukkit.broadcast(
                    Msg.parse(
                        "<gold>⛪ В церкви началась служба! <gray>Стойте внутри церкви: каждая минута даёт Спешку и множитель продажи на 20–40 минут активной игры. Служба идёт "
                            + var2
                            + " мин."
                    )
                );
            }

            long now = System.currentTimeMillis();
            long first = this.c().getLong("church.first-minute-buff-minutes", 20L) * 60000L;
            long step = this.c().getLong("church.per-minute-buff-minutes", 5L) * 60000L;
            long cap = this.c().getLong("church.max-buff-minutes", 40L) * 60000L;
            for (Player var8 : Bukkit.getOnlinePlayers()) {
                if (!this.inside(var8)) {
                    continue;
                }
                PlayerData d = this.plugin.data().get(var8);
                if (d == null) {
                    continue;
                }
                long last = this.lastCredit.getOrDefault(var8.getUniqueId(), 0L);
                if (now - last < 60000L) {
                    continue;
                }
                this.lastCredit.put(var8.getUniqueId(), now);
                // Старовер (коллекция): удвоение времени службы
                long mult = this.plugin.collections().has(d, "oldbeliever") ? 2L : 1L;
                boolean firstMinute = this.blessed.add(var8.getUniqueId());
                long add = (firstMinute ? first : step) * mult;
                d.churchBoostMs = Math.min(cap * mult, Math.max(d.churchBoostMs, 0L) + add);
                var8.sendMessage(Msg.parse("<gold>⛪ Служба: <aqua>Спешка " + this.c().getInt("church.haste-level", 2) + "</aqua> и <green>+" + Math.round(this.c().getDouble("church.sell-bonus", 0.25) * 100)
                    + "% к продаже</green> <gray>— осталось " + dev.prisonclassic.util.Items.fmtTime(d.churchBoostMs / 1000L) + " активной игры"));
                var8.playSound(var8.getLocation(), Sound.BLOCK_BELL_USE, 1.0F, 1.0F);
            }
        }
    }
}
