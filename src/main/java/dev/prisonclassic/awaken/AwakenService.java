package dev.prisonclassic.awaken;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.upgrade.UpgradeService;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Players;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class AwakenService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey shardKey;
    private final NamespacedKey forgeKey;
    private final NamespacedKey awakenKey;

    public AwakenService(PrisonClassic var1) {
        this.plugin = var1;
        this.shardKey = new NamespacedKey(var1, "metal_shard");
        this.forgeKey = new NamespacedKey(var1, "forge");
        this.awakenKey = new NamespacedKey(var1, "awaken");
    }

    private AwakenService.Cfg cfg() {
        return new AwakenService.Cfg();
    }

    public ItemStack shard(int var1) {
        ItemStack var2 = Items.of(
            Material.IRON_NUGGET, "<gray><bold>Металлический осколок", "<gray>Выпадает с мобов деревни Люфония", "<yellow>Заточка и пробуждение: /awaken"
        );
        var2.setAmount(var1);
        ItemMeta var3 = var2.getItemMeta();
        var3.getPersistentDataContainer().set(this.shardKey, PersistentDataType.BYTE, (byte)1);
        var2.setItemMeta(var3);
        return var2;
    }

    private boolean isShard(ItemStack var1) {
        return var1 != null && var1.hasItemMeta() && var1.getItemMeta().getPersistentDataContainer().has(this.shardKey, PersistentDataType.BYTE);
    }

    private int get(ItemStack var1, NamespacedKey var2) {
        if (var1 != null && var1.hasItemMeta()) {
            Integer var3 = var1.getItemMeta().getPersistentDataContainer().get(var2, PersistentDataType.INTEGER);
            return var3 == null ? 0 : var3;
        } else {
            return 0;
        }
    }

    public int forge(ItemStack var1) {
        return this.get(var1, this.forgeKey);
    }

    public int awaken(ItemStack var1) {
        return this.get(var1, this.awakenKey);
    }

    private static boolean weapon(Material var0) {
        UpgradeService.Chain var1 = UpgradeService.Chain.of(var0);
        return var1 == UpgradeService.Chain.SWORD || var1 == UpgradeService.Chain.AXE || var1 == UpgradeService.Chain.BOW;
    }

    private String effect(Material var1, int var2) {
        UpgradeService.Chain var3 = UpgradeService.Chain.of(var1);
        if (var3 == null) {
            return "";
        } else {
            FileConfiguration var4 = this.plugin.getConfig();

            return switch (var3) {
                case SWORD, AXE, BOW -> "+" + Math.round(var2 * var4.getDouble("awaken.weapon-damage-per-stage", 0.04) * 100.0) + "% урона";
                case ARMOR -> "-"
                    + String.format(Locale.US, "%.1f", var2 * var4.getDouble("awaken.armor-reduction-per-stage", 0.005) * 100.0)
                    + "% входящего урона (эта часть)";
                default -> "+" + Math.round(var2 * var4.getDouble("awaken.tool-extra-per-stage", 0.03) * 100.0) + "% шанс доп. добычи";
            };
        }
    }

    public List<String> loreLines(ItemStack var1) {
        ArrayList var2 = new ArrayList();
        int var3 = this.forge(var1);
        int var4 = this.awaken(var1);
        if (var3 > 0) {
            var2.add("<gray>Заточка: <white>+" + var3 + " урона");
        }

        if (var4 > 0) {
            var2.add("<light_purple>Пробуждение " + var4 + ": <white>" + this.effect(var1.getType(), var4));
        }

        return var2;
    }

    public double toolExtra(ItemStack var1) {
        return this.awaken(var1) * this.plugin.getConfig().getDouble("awaken.tool-extra-per-stage", 0.03);
    }

    private double forgeCost(int var1) {
        return this.plugin.getConfig().getDouble("awaken.forge.coins", 300.0) * Math.pow(var1, 1.5);
    }

    private int forgeShards(int var1) {
        return this.plugin.getConfig().getInt("awaken.forge.shards", 5) * var1;
    }

    private int forgeChance(int var1) {
        return Math.max(30, 100 - (var1 - 1) * this.plugin.getConfig().getInt("awaken.forge.chance-drop", 6));
    }

    private double awakenCost(int var1) {
        return this.plugin.getConfig().getDouble("awaken.coins", 3000.0) * Math.pow(var1, 1.6);
    }

    private int awakenShards(int var1) {
        return this.plugin.getConfig().getInt("awaken.shards", 12) * var1;
    }

    private int awakenChance(int var1) {
        return Math.max(25, 92 - (var1 - 1) * this.plugin.getConfig().getInt("awaken.chance-drop", 5));
    }

    public void open(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            AwakenService.Cfg var3 = this.cfg();
            if (var2.level < var3.minLevel()) {
                var1.sendMessage(Msg.parse("<red>Пробуждение вещей доступно с " + var3.minLevel() + " уровня (деревня Люфония)."));
            } else {
                ItemStack var4 = var1.getInventory().getItemInMainHand();
                if (!var4.getType().isAir() && UpgradeService.Chain.of(var4.getType()) != null) {
                    int var5 = Items.count(var1, this::isShard);
                    Gui var6 = new Gui(27, Msg.parse("Пробуждение вещей"));
                    var6.fill(Items.filler());
                    var6.set(4, var4.clone());
                    var6.set(22, Items.of(Material.IRON_NUGGET, "<gray>Осколков у вас: <white>" + var5));
                    if (weapon(var4.getType())) {
                        int var7 = this.forge(var4);
                        if (var7 >= var3.maxForge()) {
                            var6.set(11, Items.of(Material.ANVIL, "<gold>Заточка: максимум (+" + var7 + ")"));
                        } else {
                            int var8 = var7 + 1;
                            var6.set(
                                11,
                                Items.of(
                                    Material.GRINDSTONE,
                                    "<gold>Заточка +" + var8,
                                    "<gray>+1 к урону оружия (сейчас +" + var7 + ")",
                                    req(var5 >= this.forgeShards(var8)) + "Осколки: " + this.forgeShards(var8),
                                    req(var2.balance >= this.forgeCost(var8)) + "Монеты: $" + Msg.money(this.forgeCost(var8)),
                                    "<gray>Шанс: <white>" + this.forgeChance(var8) + "%",
                                    "<gray>При неудаче теряются осколки и монеты"
                                ),
                                (var1x, var2x) -> this.doForge(var1x)
                            );
                        }
                    } else {
                        var6.set(11, Items.of(Material.GRINDSTONE, "<gray>Заточка", "<gray>Только для мечей, топоров и луков"));
                    }

                    int var10 = this.awaken(var4);
                    if (var10 >= var3.maxAwaken()) {
                        var6.set(
                            15,
                            Items.of(
                                Material.NETHER_STAR, "<light_purple>Пробуждение: максимум (" + var10 + ")", "<white>" + this.effect(var4.getType(), var10)
                            )
                        );
                    } else {
                        int var11 = var10 + 1;
                        int var9 = var3.minLevel() - 1 + var11;
                        var6.set(
                            15,
                            Items.of(
                                Material.END_CRYSTAL,
                                "<light_purple>Пробуждение " + var11,
                                "<gray>Станет: <white>" + this.effect(var4.getType(), var11),
                                req(var2.level >= var9) + "Уровень игрока: " + var9,
                                req(var5 >= this.awakenShards(var11)) + "Осколки: " + this.awakenShards(var11),
                                req(var2.balance >= this.awakenCost(var11)) + "Монеты: $" + Msg.money(this.awakenCost(var11)),
                                "<gray>Шанс: <white>" + this.awakenChance(var11) + "%",
                                "<gray>При неудаче теряются осколки и монеты"
                            ),
                            (var1x, var2x) -> this.doAwaken(var1x)
                        );
                    }

                    var6.open(var1);
                } else {
                    var1.sendMessage(Msg.parse("<red>Возьмите в руку оружие, инструмент или броню."));
                }
            }
        }
    }

    private static String req(boolean var0) {
        return var0 ? "<green>✔ <gray>" : "<red>✘ <gray>";
    }

    private boolean pay(Player var1, PlayerData var2, int var3, double var4) {
        if (Items.count(var1, this::isShard) < var3) {
            var1.sendMessage(Msg.parse("<red>Не хватает осколков (" + var3 + "). Их роняют мобы деревни Люфония: /lufonia"));
            return false;
        } else if (var2.balance < var4) {
            Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var4)));
            return false;
        } else {
            Items.take(var1, this::isShard, var3);
            var2.balance -= var4;
            return true;
        }
    }

    private void set(ItemStack var1, NamespacedKey var2, int var3) {
        ItemMeta var4 = var1.getItemMeta();
        PersistentDataContainer var5 = var4.getPersistentDataContainer();
        var5.set(var2, PersistentDataType.INTEGER, var3);
        var1.setItemMeta(var4);
        this.plugin.enchants().refreshLore(var1);
    }

    private void doForge(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        ItemStack var3 = var1.getInventory().getItemInMainHand();
        if (var2 != null && weapon(var3.getType())) {
            int var4 = this.forge(var3) + 1;
            if (var4 <= this.cfg().maxForge() && this.pay(var1, var2, this.forgeShards(var4), this.forgeCost(var4))) {
                if (ThreadLocalRandom.current().nextInt(100) < this.forgeChance(var4)) {
                    this.set(var3, this.forgeKey, var4);
                    var1.playSound(var1.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0F, 1.0F);
                    var1.sendMessage(Msg.parse("<green>Заточка успешна: +" + var4 + " урона."));
                } else {
                    var1.playSound(var1.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7F, 1.0F);
                    var1.sendMessage(Msg.parse("<yellow>Заточка не удалась."));
                }

                this.open(var1);
            }
        }
    }

    private void doAwaken(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        ItemStack var3 = var1.getInventory().getItemInMainHand();
        if (var2 != null && UpgradeService.Chain.of(var3.getType()) != null) {
            int var4 = this.awaken(var3) + 1;
            if (var4 <= this.cfg().maxAwaken()) {
                int var5 = this.cfg().minLevel() - 1 + var4;
                if (var2.level < var5) {
                    var1.sendMessage(Msg.parse("<red>Нужен уровень " + var5));
                } else if (this.pay(var1, var2, this.awakenShards(var4), this.awakenCost(var4))) {
                    if (ThreadLocalRandom.current().nextInt(100) < this.awakenChance(var4)) {
                        this.set(var3, this.awakenKey, var4);
                        var1.playSound(var1.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0F, 1.0F);
                        var1.sendMessage(Msg.parse("<light_purple>Вещь пробуждена! Ступень " + var4 + ": <white>" + this.effect(var3.getType(), var4)));
                    } else {
                        var1.playSound(var1.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7F, 1.0F);
                        var1.sendMessage(Msg.parse("<yellow>Пробуждение не удалось."));
                    }

                    this.open(var1);
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onDamage(EntityDamageByEntityEvent var1) {
        Player var2 = Players.attacker(var1.getDamager());
        if (var2 != null) {
            ItemStack var3 = var2.getInventory().getItemInMainHand();
            boolean var4 = var1.getDamager() instanceof Player;
            boolean var5 = var1.getDamager() instanceof Arrow && var3.getType() == Material.BOW;
            if (var4 && weapon(var3.getType()) && var3.getType() != Material.BOW || var5) {
                double var6 = var1.getDamage() + this.forge(var3);
                var6 *= 1.0 + this.awaken(var3) * this.plugin.getConfig().getDouble("awaken.weapon-damage-per-stage", 0.04);
                var1.setDamage(var6);
            }
        }

        if (var1.getEntity() instanceof Player var9) {
            int var11 = 0;

            for (ItemStack var8 : var9.getInventory().getArmorContents()) {
                var11 += this.awaken(var8);
            }

            if (var11 > 0) {
                double var13 = Math.min(0.5, var11 * this.plugin.getConfig().getDouble("awaken.armor-reduction-per-stage", 0.005));
                var1.setDamage(var1.getDamage() * (1.0 - var13));
            }
        }
    }

    private final class Cfg {
        int minLevel() {
            return AwakenService.this.plugin.getConfig().getInt("awaken.min-level", 20);
        }

        int maxForge() {
            return AwakenService.this.plugin.getConfig().getInt("awaken.forge.max", 10);
        }

        int maxAwaken() {
            return AwakenService.this.plugin.getConfig().getInt("awaken.stages", 12);
        }
    }
}
