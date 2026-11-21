package dev.prisonclassic.sell;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.faction.Faction;
import dev.prisonclassic.mine.MineDefs;
import dev.prisonclassic.quest.QuestType;
import dev.prisonclassic.util.Items;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class SellService {
    private final PrisonClassic plugin;
    private final NamespacedKey contrabandKey;
    private final NamespacedKey priceKey;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public SellService(PrisonClassic var1) {
        this.plugin = var1;
        this.contrabandKey = new NamespacedKey(var1, "contraband");
        this.priceKey = new NamespacedKey(var1, "sell_price");
        this.reload();
    }

    public void reload() {
        this.prices.clear();
        this.prices.putAll(MineDefs.defaultPrices());
        ConfigurationSection var1 = this.plugin.getConfig().getConfigurationSection("sell.prices");

        for (String var3 : var1 == null ? Set.<String>of() : var1.getKeys(false)) {
            Material var4 = Material.matchMaterial(var3);
            if (var4 == null) {
                this.plugin.getLogger().warning("sell.prices: неизвестный материал " + var3);
            } else {
                this.prices.put(var4, var1.getDouble(var3));
            }
        }

        ConfigurationSection var6 = this.plugin.getConfig().getConfigurationSection("sell.stack-prices");
        if (var6 != null) {
            for (String var8 : var6.getKeys(false)) {
                Material var5 = Material.matchMaterial(var8);
                if (var5 == null) {
                    this.plugin.getLogger().warning("sell.stack-prices: неизвестный материал " + var8);
                } else {
                    this.prices.put(var5, var6.getDouble(var8) / 64.0);
                }
            }
        }
    }

    public ItemStack contraband() {
        ItemStack var1 = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta var2 = var1.getItemMeta();
        var2.displayName(Component.text("Контрабанда", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        var2.lore(List.of(Component.text("Нашёл в шахте. Продай — но не попадись.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        var2.getPersistentDataContainer().set(this.contrabandKey, PersistentDataType.BYTE, (byte)1);
        var1.setItemMeta(var2);
        return var1;
    }

    public boolean isContraband(ItemStack var1) {
        return var1 != null && var1.hasItemMeta() && var1.getItemMeta().getPersistentDataContainer().has(this.contrabandKey, PersistentDataType.BYTE);
    }

    public ItemStack special(Material var1, String var2, double var3, int var5, String... var6) {
        ItemStack var7 = Items.of(var1, var2, var6);
        var7.setAmount(var5);
        ItemMeta var8 = var7.getItemMeta();
        var8.getPersistentDataContainer().set(this.priceKey, PersistentDataType.DOUBLE, var3);
        var7.setItemMeta(var8);
        return var7;
    }

    /**
     * Флаг AutoSell_Premium (ТЗ): премиум-команда /sell и автопродажа.
     * Источники: право AutoSell_Premium / prison.autosell.premium (донат), донат-ранг с autosell, или покупка за звёзды у Донат-NPC.
     */
    public boolean premium(Player p, PlayerData d) {
        return p.hasPermission("AutoSell_Premium")
            || p.hasPermission("prison.autosell.premium")
            || p.hasPermission("prison.autosell")
            || this.plugin.donate().autosell(d.donate)
            || d.autosellPremiumUntil < 0L
            || System.currentTimeMillis() < d.autosellPremiumUntil;
    }

    /** Можно ли продавать всё командой /sell (в любой точке): премиум или донат-ранг с флагом sell. */
    public boolean canSell(Player p, PlayerData d) {
        return this.premium(p, d) || this.plugin.donate().sell(d.donate);
    }

    /** Продать всё из инвентаря, но только материалы из списка (торговец шахты). */
    public double sellOnly(Player p, PlayerData d, java.util.Collection<Material> allowed) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        double sum = 0.0;
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (it == null || it.getType().isAir()) {
                continue;
            }
            boolean special = this.isContraband(it) || it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(this.priceKey, PersistentDataType.DOUBLE);
            if (!special && it.hasItemMeta()) {
                continue; // предметы с метой (инструменты, книги) торговец не берёт
            }
            if (!special && !allowed.contains(it.getType())) {
                continue;
            }
            double v = this.valueOf(it, d);
            if (v > 0.0) {
                sum += v;
                inv[i] = null;
            }
        }
        if (sum > 0.0) {
            p.getInventory().setStorageContents(inv);
            d.balance += sum;
            this.plugin.quests().progress(p, d, QuestType.SELL, sum);
        }
        return sum;
    }

    public double price(Material m) {
        return this.prices.getOrDefault(m, 0.0);
    }

    public double multiplier(PlayerData var1) {
        double var2 = 1.0 + (var1.level - 1) * this.plugin.getConfig().getDouble("sell.level-bonus", 0.01);
        Faction var4 = this.plugin.factions().get(var1.faction);
        if (var4 != null) {
            var2 *= var4.sellBonus();
        }

        var2 *= 1.0 + this.plugin.gangs().sellBonus(var1) + this.plugin.families().sellBonus(var1);
        long var5 = System.currentTimeMillis();
        double var7 = var1.perk("coin") * this.plugin.getConfig().getDouble("trainer.perks.coin.step", 0.01) + this.plugin.donate().coinBoost(var1.donate);
        if (var1.coinBoost > 0.0 && (var1.coinBoostUntil < 0L || var5 < var1.coinBoostUntil)) {
            var7 += var1.coinBoost;
        }

        var2 *= 1.0 + var7;
        if (var5 < var1.roosterUntil) {
            var2 *= 2.0;
        }

        if (var1.churchBoostMs > 0L) {
            // Церковная служба: множитель продаваемых ресурсов на 20–40 минут активной игры
            var2 *= 1.0 + this.plugin.getConfig().getDouble("church.sell-bonus", 0.25);
        }

        return var2 * this.plugin.boosters().globalCoin();
    }

    public double blockMultiplier(PlayerData var1) {
        long var2 = System.currentTimeMillis();
        double var4 = var1.perk("blocks") * this.plugin.getConfig().getDouble("trainer.perks.blocks.step", 0.01) + this.plugin.donate().blockBoost(var1.donate);
        if (var1.blockBoost > 0.0 && (var1.blockBoostUntil < 0L || var2 < var1.blockBoostUntil)) {
            var4 += var1.blockBoost;
        }

        double var6 = 1.0 + var4 + this.plugin.gangs().blockBonus(var1);
        return var6 * this.plugin.boosters().globalBlock();
    }

    public double valueOf(ItemStack var1, PlayerData var2) {
        if (var1 == null || var1.getType().isAir()) {
            return 0.0;
        } else if (this.isContraband(var1)) {
            return var1.getAmount() * this.plugin.getConfig().getDouble("contraband.value", 20.0) * this.multiplier(var2);
        } else if (var1.hasItemMeta()) {
            Double var4 = var1.getItemMeta().getPersistentDataContainer().get(this.priceKey, PersistentDataType.DOUBLE);
            return var4 == null ? 0.0 : var4 * var1.getAmount() * this.multiplier(var2);
        } else {
            Double var3 = this.prices.get(var1.getType());
            return var3 == null ? 0.0 : var3 * var1.getAmount() * this.multiplier(var2);
        }
    }

    public double sellAll(Player var1, PlayerData var2) {
        ItemStack[] var3 = var1.getInventory().getStorageContents();
        double var4 = 0.0;

        for (int var6 = 0; var6 < var3.length; var6++) {
            double var7 = this.valueOf(var3[var6], var2);
            if (var7 > 0.0) {
                var4 += var7;
                var3[var6] = null;
            }
        }

        if (var4 > 0.0) {
            var1.getInventory().setStorageContents(var3);
            var2.balance += var4;
            this.plugin.quests().progress(var1, var2, QuestType.SELL, var4);
        }

        return var4;
    }
}
