package dev.prisonclassic.level;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class LevelManager {
    private final PrisonClassic plugin;
    private int max;
    private double moneyBase;
    private double moneyExp;
    private double blocksBase;
    private double blocksGrowth;
    private double hpPerLevel;
    private double dmgPerLevel;
    private List<String> names;

    public LevelManager(PrisonClassic var1) {
        this.plugin = var1;
        this.reload();
    }

    public void reload() {
        FileConfiguration var1 = this.plugin.getConfig();
        this.max = var1.getInt("levels.max", 40);
        this.moneyBase = var1.getDouble("levels.money-base", 600.0);
        this.moneyExp = var1.getDouble("levels.money-exp", 2.4);
        this.blocksBase = var1.getDouble("levels.blocks-base", 150.0);
        this.blocksGrowth = var1.getDouble("levels.blocks-growth", 1.45);
        this.hpPerLevel = var1.getDouble("levels.hp-per-level", 2.0);
        this.dmgPerLevel = var1.getDouble("levels.damage-per-level", 0.02);
        this.names = var1.getStringList("levels.names");
    }

    public int max() {
        return this.max;
    }

    public String name(int var1) {
        return String.valueOf(var1);
    }

    private Map<?, ?> row(int var1) {
        List var2 = this.plugin.getConfig().getMapList("levels.table");
        int var3 = var1 - 1;
        return var3 >= 0 && var3 < var2.size() ? (Map)var2.get(var3) : null;
    }

    public double moneyCost(int var1) {
        Map var2 = this.row(var1);
        return var2 != null && var2.get("money") instanceof Number var3 ? var3.doubleValue() : Math.floor(this.moneyBase * Math.pow(var1, this.moneyExp));
    }

    public long blocksNeeded(int var1) {
        Map var2 = this.row(var1);
        if (var2 != null && var2.get("blocks") instanceof Number var6) {
            return var6.longValue();
        } else {
            long var3 = 0L;

            for (int var5 = 1; var5 <= var1; var5++) {
                var3 += (long)Math.floor(this.blocksBase * Math.pow(this.blocksGrowth, var5 - 1));
            }

            return var3;
        }
    }

    public double damageMultiplier(int var1) {
        return 1.0 + (var1 - 1) * this.dmgPerLevel;
    }

    /** Модификатор базового урона: +1 каждые N уровней (по умолчанию 5), финальный бонус на 20 уровне (+4). */
    public double damageBonus(int level) {
        FileConfiguration c = this.plugin.getConfig();
        int every = Math.max(1, c.getInt("levels.damage-every", 5));
        int capLevel = c.getInt("levels.damage-cap-level", 20);
        return Math.min(level, capLevel) / every * c.getDouble("levels.damage-per-step", 1.0);
    }

    public double maxHealth(int level) {
        return 20.0 + (level - 1) * this.hpPerLevel;
    }

    /** Итоговая цена уровня: таблица × (1 − скидка тренера/доната − фракционная скидка). */
    public double levelPrice(PlayerData d) {
        double disc = this.plugin.trainer().discount(d) + this.plugin.factionBalance().discount(d);
        return Math.floor(this.moneyCost(d.level) * (1.0 - Math.min(0.6, disc)));
    }

    public void applyBonuses(Player var1, PlayerData var2) {
        AttributeInstance var3 = var1.getAttribute(Attribute.MAX_HEALTH);
        if (var3 != null) {
            var3.setBaseValue(this.maxHealth(var2.level));
        }

        AttributeInstance dmg = var1.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(1.0 + this.damageBonus(var2.level));
        }

        AttributeInstance speed = var1.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.1 * (1.0 + this.plugin.trainer().agilityBonus(var2)));
        }

        this.givePickaxe(var1, var2);
    }

    public void givePickaxe(Player var1, PlayerData var2) {
        FileConfiguration var3 = this.plugin.getConfig();
        if (var3.getBoolean("pickaxe.enabled", true)) {
            NamespacedKey var4 = new NamespacedKey(this.plugin, "pickaxe");
            Material var5 = Material.matchMaterial(var3.getString("pickaxe.material", "DIAMOND_PICKAXE"));
            if (var5 == null) {
                var5 = Material.DIAMOND_PICKAXE;
            }

            int var6 = Math.max(1, var3.getInt("pickaxe.levels-per-efficiency", 5));
            int var7 = Math.min(var3.getInt("pickaxe.efficiency-max", 6), var3.getInt("pickaxe.efficiency-base", 2) + (var2.level - 1) / var6);
            PlayerInventory var8 = var1.getInventory();
            int var9 = -1;
            ItemStack[] var10 = var8.getStorageContents();

            for (int var11 = 0; var11 < var10.length; var11++) {
                ItemStack var12 = var10[var11];
                if (var12 != null && var12.hasItemMeta() && var12.getItemMeta().getPersistentDataContainer().has(var4, PersistentDataType.BYTE)) {
                    var9 = var11;
                    break;
                }
            }

            ItemStack var13 = var9 >= 0 ? var10[var9] : new ItemStack(var5);
            ItemMeta var14 = var13.getItemMeta();
            var14.displayName(Component.text("Кирка шахтёра", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            var14.setUnbreakable(true);
            var14.addEnchant(Enchantment.EFFICIENCY, var7, true);
            var14.getPersistentDataContainer().set(var4, PersistentDataType.BYTE, (byte)1);
            var13.setItemMeta(var14);
            if (var9 >= 0) {
                var8.setItem(var9, var13);
            } else {
                var8.addItem(new ItemStack[]{var13});
            }
        }
    }

    public boolean rankUp(Player var1, PlayerData var2) {
        if (var2.level >= this.max) {
            Msg.send(var1, "level-max");
            return false;
        } else {
            long var3 = this.blocksNeeded(var2.level);
            double var5 = this.levelPrice(var2);
            if (var2.blocksMined < var3) {
                Msg.send(var1, "level-need-blocks", Msg.p("have", var2.blocksMined), Msg.p("need", var3));
                return false;
            } else if (var2.balance < var5) {
                Msg.send(var1, "level-need-money", Msg.p("cost", Msg.money(var5)));
                return false;
            } else {
                var2.balance -= var5;
                var2.level++;
                this.applyBonuses(var1, var2);
                var1.setHealth(var1.getAttribute(Attribute.MAX_HEALTH).getValue());
                var1.playSound(var1.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                var1.showTitle(Title.title(Msg.parse("<gold><bold>Уровень " + this.name(var2.level)), Msg.parse("<gray>Новый уровень шахт открыт")));
                Msg.send(var1, "level-up", Msg.p("level", this.name(var2.level)));
                if (var2.level == this.plugin.getConfig().getInt("factions.min-level", 5)) {
                    var1.sendMessage(Msg.parse("<gold>Открыто: <white>фракции (/faction), База с точками захвата (/base)"));
                }

                if (var2.level == this.plugin.getConfig().getInt("cells.min-level", 8)) {
                    var1.sendMessage(Msg.parse("<gold>Открыто: <white>личная камера (/cell list) — сито, душ, полка коллекций, тюремная банда"));
                }

                if (var2.level == this.plugin.getConfig().getInt("dungeon.min-level", 17)) {
                    var1.sendMessage(Msg.parse("<gold>Открыто: <white>Вампирские катакомбы (/dungeon) — группа из 3 игроков и пропуск"));
                }

                if (var2.level % this.plugin.getConfig().getInt("levels.damage-every", 5) == 0
                    && var2.level <= this.plugin.getConfig().getInt("levels.damage-cap-level", 20)) {
                    var1.sendMessage(Msg.parse("<red>+1 к базовому урону! <gray>(сейчас +" + (int)this.damageBonus(var2.level) + ")"));
                }

                return true;
            }
        }
    }
}
