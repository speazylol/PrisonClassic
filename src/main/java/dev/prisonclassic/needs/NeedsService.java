package dev.prisonclassic.needs;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class NeedsService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private Set<Material> toilet = EnumSet.noneOf(Material.class);
    private Set<Material> shower = EnumSet.noneOf(Material.class);

    public NeedsService(PrisonClassic var1) {
        this.plugin = var1;
        this.itemKey = new NamespacedKey(var1, "need_item");
        this.reload();
        Bukkit.getScheduler().runTaskTimer(var1, this::tick, 200L, 200L);
    }

    public void reload() {
        this.toilet = this.mats("needs.toilet-blocks", List.of("HOPPER"));
        this.shower = this.mats("needs.shower-blocks", List.of("WATER_CAULDRON"));
    }

    private Set<Material> mats(String var1, List<String> var2) {
        EnumSet var3 = EnumSet.noneOf(Material.class);

        for (String var6 : this.plugin.getConfig().isList(var1) ? this.plugin.getConfig().getStringList(var1) : var2) {
            Material var7 = Material.matchMaterial(var6);
            if (var7 != null) {
                var3.add(var7);
            }
        }

        return var3;
    }

    private ConfigurationSection cfg() {
        ConfigurationSection var1 = this.plugin.getConfig().getConfigurationSection("needs");
        return var1 == null ? this.plugin.getConfig().createSection("needs") : var1;
    }

    public boolean exempt(Player var1, PlayerData var2) {
        return !this.cfg().getBoolean("enabled", true)
            || var1.getGameMode() == GameMode.CREATIVE
            || var1.getGameMode() == GameMode.SPECTATOR
            || var1.hasPermission("prison.noneeds")
            || this.plugin.donate().noNeeds(var2.donate);
    }

    private void tick() {
        double var1 = 0.16666666666666666;

        for (Player var4 : Bukkit.getOnlinePlayers()) {
            PlayerData var5 = this.plugin.data().get(var4);
            if (var5 != null) {
                if (this.exempt(var4, var5)) {
                    var5.needToilet = var5.needShower = var5.needSleep = 0.0;
                    var5.dirty = false;
                } else {
                    var5.needToilet = Math.min(100.0, var5.needToilet + this.cfg().getDouble("toilet-per-minute", 1.4) * var1);
                    var5.needShower = Math.min(100.0, var5.needShower + this.cfg().getDouble("shower-per-minute", 0.9) * var1);
                    var5.needSleep = Math.min(100.0, var5.needSleep + this.cfg().getDouble("sleep-per-minute", 0.7) * var1);
                    double var6 = this.cfg().getDouble("threshold", 70.0);
                    ArrayList var8 = new ArrayList();
                    if (var5.needToilet >= var6) {
                        var4.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 260, var5.needToilet >= 100.0 ? 1 : 0, true, false, true));
                        var8.add("в туалет");
                    }

                    if (var5.dirty) {
                        // Сходил в туалет без бумаги: Медлительность не снимется, пока не помоется в душе
                        var4.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 260, 0, true, false, true));
                        if (Math.random() < 0.3) {
                            var4.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, true, false, true));
                        }
                        var8.add("<bold>срочно в душ</bold>");
                    }

                    if (var5.needShower >= var6) {
                        if (var5.needShower >= 100.0 || Math.random() < 0.3) {
                            var4.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, true, false, true));
                        }

                        var8.add("в душ");
                    }

                    if (var5.needSleep >= var6) {
                        var4.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 260, var5.needSleep >= 100.0 ? 1 : 0, true, false, true));
                        var8.add("поспать");
                    }

                    if (var5.needToilet >= 100.0 || var5.needSleep >= 100.0 || var5.needShower >= 100.0) {
                        // Критический порог: жёстко режутся боевые параметры
                        var4.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 260, 0, true, false, true));
                    }

                    if (!var8.isEmpty()) {
                        var4.sendActionBar(Msg.parse("<red>Вам нужно " + String.join(", ", var8) + "! <gray>(санузел и спальня на спавне)"));
                    }
                }
            }
        }
    }

    public String line(PlayerData var1) {
        return this.col(var1.needToilet)
            + "Т"
            + (int)var1.needToilet
            + " "
            + this.col(var1.needShower)
            + "Д"
            + (int)var1.needShower
            + " "
            + this.col(var1.needSleep)
            + "С"
            + (int)var1.needSleep
            + (var1.dirty ? " <red>грязь" : "");
    }

    private String col(double var1) {
        return var1 >= this.cfg().getDouble("threshold", 70.0) ? "<red>" : (var1 >= 40.0 ? "<yellow>" : "<green>");
    }

    public ItemStack toiletPaper(int var1) {
        return this.special(Material.PAPER, "<white>Туалетная бумага", "toilet", var1, "<gray>Сходить в туалет полностью");
    }

    public ItemStack soap(int var1) {
        return this.special(Material.WHITE_DYE, "<aqua>Мыло", "soap", var1, "<gray>Помыться в душе полностью");
    }

    private ItemStack special(Material var1, String var2, String var3, int var4, String var5) {
        ItemStack var6 = Items.of(var1, var2, var5);
        var6.setAmount(var4);
        ItemMeta var7 = var6.getItemMeta();
        var7.getPersistentDataContainer().set(this.itemKey, PersistentDataType.STRING, var3);
        var6.setItemMeta(var7);
        return var6;
    }

    private boolean isItem(ItemStack var1, String var2) {
        return var1 != null && var1.hasItemMeta() && var2.equals(var1.getItemMeta().getPersistentDataContainer().get(this.itemKey, PersistentDataType.STRING));
    }

    @EventHandler(
        priority = EventPriority.LOW
    )
    public void onInteract(PlayerInteractEvent var1) {
        if (var1.getHand() == EquipmentSlot.HAND && var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block var2 = var1.getClickedBlock();
            if (var2 != null) {
                Material var3 = var2.getType();
                boolean var4 = var3.name().endsWith("_BED");
                if (this.toilet.contains(var3) || this.shower.contains(var3) || var4) {
                    Player var5 = var1.getPlayer();
                    if (!var5.isSneaking() || var5.getGameMode() != GameMode.CREATIVE) {
                        if (this.plugin.cells().at(var2) == null || var4) {
                            var1.setCancelled(true);
                            PlayerData var6 = this.plugin.data().get(var5);
                            if (var6 != null) {
                                long var7 = System.currentTimeMillis();
                                if (var7 >= this.cooldown.getOrDefault(var5.getUniqueId(), 0L)) {
                                    this.cooldown.put(var5.getUniqueId(), var7 + 3000L);
                                    if (this.toilet.contains(var3)) {
                                        boolean var9 = Items.take(var5, var1x -> this.isItem(var1x, "toilet"), 1);
                                        var6.needToilet = 0.0;
                                        var5.playSound(var5.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.0F, 0.7F);
                                        if (var9) {
                                            var5.removePotionEffect(PotionEffectType.SLOWNESS);
                                            var5.sendMessage(Msg.parse("<green>Вы сходили в туалет."));
                                        } else {
                                            // ТЗ: бумага кончилась — бежать в душ, иначе дебафф не снимется
                                            var6.dirty = true;
                                            var6.needShower = Math.min(100.0, var6.needShower + this.cfg().getDouble("no-paper-dirt", 40.0));
                                            var5.sendMessage(Msg.parse("<red>Туалетная бумага кончилась! <gray>Медлительность не пройдёт, пока вы не помоетесь в душе. <dark_gray>(бумага — /shop)"));
                                        }
                                    } else if (this.shower.contains(var3)) {
                                        this.shower(var5, false);
                                    } else {
                                        var6.needSleep = 0.0;
                                        var5.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                                        var5.playSound(var5.getLocation(), Sound.BLOCK_WOOL_PLACE, 1.0F, 0.6F);
                                        var5.sendMessage(Msg.parse("<green>Вы вздремнули и отдохнули."));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Душ (санузел на спавне или в камере 3+ уровня): с мылом — полностью смывает грязь, голод и «туалетную» Медлительность,
     * даёт бафф «Удача» (+шанс x2 редких руд); без мыла — частично. Грязь после туалета без бумаги смывается в любом случае.
     */
    public void shower(Player p, boolean inCell) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        boolean soap = Items.take(p, it -> this.isItem(it, "soap"), 1);
        boolean wasDirty = d.dirty;
        d.dirty = false;
        d.needShower = soap ? 0.0 : Math.max(0.0, d.needShower - this.cfg().getDouble("without-item", 50.0));
        p.removePotionEffect(PotionEffectType.NAUSEA);
        p.removePotionEffect(PotionEffectType.HUNGER);
        if (wasDirty || d.needToilet < this.cfg().getDouble("threshold", 70.0)) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.0F, 1.0F);
        if (soap) {
            long min = this.plugin.getConfig().getLong("cells.shower-luck-minutes", 10L);
            d.luckUntil = System.currentTimeMillis() + min * 60000L;
            p.sendMessage(Msg.parse("<green>Вы помылись с мылом. <gold>Бафф «Удача» <gray>на " + min + " мин. (шанс x2 редких руд)"));
        } else {
            p.sendMessage(Msg.parse("<yellow>Вы ополоснулись водой. <gray>(с мылом — полностью и бафф «Удача»)"));
        }
    }

    @EventHandler
    public void onBed(PlayerBedEnterEvent var1) {
        var1.setCancelled(true);
    }
}
