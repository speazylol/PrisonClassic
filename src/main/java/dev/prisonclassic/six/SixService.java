package dev.prisonclassic.six;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class SixService {
    private final PrisonClassic plugin;
    private final NamespacedKey gravelKey;
    private final java.util.Map<java.util.UUID, Double> lastCoins = new java.util.HashMap<>();

    public SixService(PrisonClassic var1) {
        this.plugin = var1;
        this.gravelKey = new NamespacedKey(var1, "six_gravel");
        org.bukkit.Bukkit.getScheduler().runTaskTimer(var1, () -> {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                PlayerData d = var1.data().get(p);
                if (d != null) {
                    this.update(d);
                }
            }
        }, 1200L, 1200L);
    }

    /** Сообщить игроку, сколько банда принесла, пока его не было. */
    public void report(Player p, PlayerData d) {
        this.update(d);
        Double c = this.lastCoins.remove(d.uuid);
        if (c != null && c >= 1.0) {
            p.sendMessage(Msg.parse("<gold>Тюремная банда <gray>принесла вам <green>$" + Msg.money(c) + "</green> и щебень, пока вас не было."));
        }
    }

    private int cellTier(Player p) {
        dev.prisonclassic.cell.Cell cell = this.plugin.cells().ownedBy(p.getUniqueId());
        return cell == null ? 0 : cell.level;
    }

    private ConfigurationSection c() {
        ConfigurationSection var1 = this.plugin.getConfig().getConfigurationSection("sixes");
        return var1 == null ? this.plugin.getConfig().createSection("sixes") : var1;
    }

    private int totalLevels(PlayerData var1) {
        int var2 = 0;

        for (int var4 : var1.sixes) {
            var2 += var4;
        }

        return var2;
    }

    public void update(PlayerData var1) {
        long var2 = System.currentTimeMillis();
        if (var1.sixLast > 0L && !var1.sixes.isEmpty() && this.plugin.cells().ownedBy(var1.uuid) != null) {
            double var4 = (var2 - var1.sixLast) / 3600000.0;
            var1.sixLast = var2;
            int var6 = this.totalLevels(var1);
            double var7 = var6 * this.c().getDouble("food-per-hour-per-level", 1.0);
            double var9 = var7 <= 0.0 ? var4 : Math.min(var4, var1.sixFood / var7);
            var1.sixFood = Math.max(0.0, var1.sixFood - var7 * var9);
            double var11 = var6 * this.c().getDouble("gravel-per-hour-per-level", 8.0) * var9;
            var1.sixGravel = Math.min(this.c().getDouble("max-storage", 2048.0), var1.sixGravel + var11);
            // Тюремная банда пассивно приносит монеты (в том числе в офлайне), пока сыта
            double coins = var6 * this.c().getDouble("coins-per-hour-per-level", 15.0) * var9;
            if (coins > 0.0) {
                var1.balance += coins;
                var1.stats.merge("six-coins", Math.round(coins), Long::sum);
                this.lastCoins.merge(var1.uuid, coins, Double::sum);
            }
        } else {
            var1.sixLast = var2;
        }
    }

    public ItemStack gravel(int var1) {
        ItemStack var2 = this.plugin
            .sell()
            .special(
                Material.GRAVEL,
                "<gray>Щебень",
                this.c().getDouble("gravel-price", 0.5),
                var1,
                "<gray>Принесли шестёрки",
                "<yellow>Просеять: /sieve <gray>или продать: /sell"
            );
        ItemMeta var3 = var2.getItemMeta();
        var3.getPersistentDataContainer().set(this.gravelKey, PersistentDataType.BYTE, (byte)1);
        var2.setItemMeta(var3);
        return var2;
    }

    private boolean isGravel(ItemStack var1) {
        if (var1 == null) {
            return false;
        }
        if (var1.hasItemMeta()) {
            return var1.getItemMeta().getPersistentDataContainer().has(this.gravelKey, PersistentDataType.BYTE);
        }
        // ТЗ: в сито идёт и накопанный гравий/песок
        return var1.getType() == Material.GRAVEL || var1.getType() == Material.SAND || var1.getType() == Material.RED_SAND;
    }

    private double price(String var1, int var2, double var3) {
        List var5 = this.c().getDoubleList(var1);
        return var2 < var5.size() ? (Double)var5.get(var2) : var3;
    }

    public void open(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            if (this.plugin.cells().ownedBy(var1.getUniqueId()) == null) {
                var1.sendMessage(Msg.parse("<red>Тюремная банда живёт в личной камере. Сначала купите камеру: /cell list"));
            } else {
                this.update(var2);
                int var3 = this.c().getInt("max", 4);
                int var4 = this.c().getInt("max-level", 3);
                Gui var5 = new Gui(27, Msg.parse("Тюремная банда"));
                var5.fill(Items.filler());

                for (int var6 = 0; var6 < var3; var6++) {
                    int var7 = var6;
                    if (var6 < var2.sixes.size()) {
                        int var8 = var2.sixes.get(var6);
                        double var9 = this.price("upgrade-cost", var8, 100000.0);
                        var5.set(
                            10 + var6,
                            Items.of(
                                Material.VILLAGER_SPAWN_EGG,
                                "<gold>Член банды #" + (var6 + 1) + " <gray>ур. " + var8,
                                "<gray>Приносит <white>" + (int)(var8 * this.c().getDouble("gravel-per-hour-per-level", 8.0)) + "</white> щебня в час",
                                "<gray>Приносит <white>$" + Msg.money(var8 * this.c().getDouble("coins-per-hour-per-level", 15.0)) + "</white> в час (и офлайн)",
                                "<gray>Ест <white>" + this.c().getDouble("food-per-hour-per-level", 1.0) * var8 + "</white> еды в час",
                                var8 >= var4 ? "<green>Максимальный уровень" : "<yellow>Нажмите — улучшить за $" + Msg.money(var9)
                            ),
                            (var2x, var3x) -> this.upgrade(var2x, var7)
                        );
                    } else {
                        double var13 = this.price("buy-cost", var6, 100000.0);
                        var5.set(
                            10 + var6,
                            Items.of(Material.BARRIER, "<gray>Свободное место", "<yellow>Нажмите — нанять шестёрку за $" + Msg.money(var13)),
                            (var1x, var2x) -> this.buy(var1x)
                        );
                    }
                }

                int var11 = this.totalLevels(var2);
                double var12 = var11 * this.c().getDouble("food-per-hour-per-level", 1.0);
                var5.set(
                    15,
                    Items.of(
                        Material.BREAD,
                        "<green>Покормить",
                        "<gray>Запас еды: <white>" + String.format(Locale.US, "%.1f", var2.sixFood),
                        var12 > 0.0 ? "<gray>Хватит на <white>" + Items.fmtTime((long)(var2.sixFood / var12 * 3600.0)) : "<gray>Шестёрок нет",
                        "<yellow>Нажмите — отдать всю еду из инвентаря",
                        "<dark_gray>Арбуз 4, долька 1, хлеб/яблоко 1, мясо 2"
                    ),
                    (var1x, var2x) -> this.feed(var1x)
                );
                var5.set(
                    16,
                    Items.of(
                        Material.GRAVEL,
                        "<white>Склад щебня: " + (int)var2.sixGravel,
                        "<gray>Вместимость: " + (int)this.c().getDouble("max-storage", 2048.0),
                        "<yellow>Нажмите — забрать"
                    ),
                    (var1x, var2x) -> this.collect(var1x)
                );
                var5.set(
                    22,
                    Items.of(Material.SCAFFOLDING, "<gold>Сито", "<gray>Просеять щебень, гравий, песок:", "<gray>драгоценные камни, пыль, субстанция, ключи, звёзды", "<dark_gray>Нужен 2-й уровень камеры"),
                    (var1x, var2x) -> this.sieve(var1x)
                );
                var5.open(var1);
            }
        }
    }

    private void buy(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        int var3 = this.c().getInt("max", 4);
        if (var2.sixes.size() < var3) {
            double var4 = this.price("buy-cost", var2.sixes.size(), 100000.0);
            if (var2.balance < var4) {
                Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var4)));
            } else {
                this.update(var2);
                var2.balance -= var4;
                var2.sixes.add(1);
                var1.playSound(var1.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0F, 1.0F);
                var1.sendMessage(Msg.parse("<green>Вы наняли члена тюремной банды. Кормите его арбузами с фермы!"));
                this.open(var1);
            }
        }
    }

    private void upgrade(Player var1, int var2) {
        PlayerData var3 = this.plugin.data().get(var1);
        int var4 = var3.sixes.get(var2);
        if (var4 < this.c().getInt("max-level", 3)) {
            double var5 = this.price("upgrade-cost", var4, 100000.0);
            if (var3.balance < var5) {
                Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var5)));
            } else {
                this.update(var3);
                var3.balance -= var5;
                var3.sixes.set(var2, var4 + 1);
                var1.playSound(var1.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1.0F, 1.0F);
                this.open(var1);
            }
        }
    }

    private void feed(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        this.update(var2);
        ConfigurationSection var3 = this.c().getConfigurationSection("food");
        if (var3 != null) {
            double var4 = 0.0;
            ItemStack[] var6 = var1.getInventory().getStorageContents();

            for (int var7 = 0; var7 < var6.length; var7++) {
                ItemStack var8 = var6[var7];
                if (var8 != null && !var8.hasItemMeta() && var3.isSet(var8.getType().name())) {
                    var4 += var3.getDouble(var8.getType().name()) * var8.getAmount();
                    var6[var7] = null;
                }
            }

            if (var4 <= 0.0) {
                var1.sendMessage(Msg.parse("<red>В инвентаре нет еды для банды (арбузы с фермы, хлеб, мясо, яблоки)."));
            } else {
                var1.getInventory().setStorageContents(var6);
                var2.sixFood += var4;
                var1.playSound(var1.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
                var1.sendMessage(Msg.parse("<green>Шестёрки накормлены: +" + String.format(Locale.US, "%.1f", var4) + " еды."));
                this.open(var1);
            }
        }
    }

    private void collect(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        this.update(var2);
        int var3 = (int)var2.sixGravel;
        if (var3 <= 0) {
            var1.sendMessage(Msg.parse("<gray>Склад пуст."));
        } else {
            var2.sixGravel -= var3;

            while (var3 > 0) {
                int var4 = Math.min(64, var3);
                Items.give(var1, this.gravel(var4));
                var3 -= var4;
            }

            var1.playSound(var1.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.0F, 1.0F);
            this.open(var1);
        }
    }

    public void sieve(Player var1) {
        int tierNeed = this.plugin.getConfig().getInt("cells.tiers.sieve", 2);
        if (this.cellTier(var1) < tierNeed) {
            var1.sendMessage(Msg.parse("<red>Сито открывается на " + tierNeed + "-м уровне камеры. <gray>/cell upgrade"));
            return;
        }

        int var2 = Items.count(var1, this::isGravel);
        Gui var3 = new Gui(27, Msg.parse("Сито"));
        var3.fill(Items.filler());
        var3.set(4, Items.of(Material.GRAVEL, "<white>Щебня/гравия/песка в инвентаре: " + var2));
        int[] var4 = new int[]{1, 16, 64, 0};
        int[] var5 = new int[]{10, 12, 14, 16};

        for (int var6 = 0; var6 < 4; var6++) {
            int var7 = var4[var6];
            var3.set(
                var5[var6],
                Items.of(
                    Material.SCAFFOLDING,
                    "<gold>Просеять " + (var7 == 0 ? "всё" : var7),
                    "<gray>Пыль 10% — чаще всего, драгоценные камни,",
                    "<gray>белая субстанция, ключи, звёзды"
                ),
                (var2x, var3x) -> this.sift(var2x, var7 == 0 ? Items.count(var2x, this::isGravel) : var7)
            );
        }

        var3.set(22, Items.of(Material.ARROW, "<yellow>← К шестёркам"), (var1x, var2x) -> this.open(var1x));
        var3.open(var1);
    }

    private void sift(Player var1, int var2) {
        PlayerData var3 = this.plugin.data().get(var1);
        if (var3 != null && var2 > 0 && Items.take(var1, this::isGravel, var2)) {
            List<Map<?, ?>> var4 = this.plugin.getConfig().getMapList("sieve.loot");
            double var5 = 0.0;

            for (Map<?, ?> var8 : var4) {
                var5 += var8.get("weight") instanceof Number var9 ? var9.doubleValue() : 1.0;
            }

            int var20 = 0;
            int var21 = 0;
            int var22 = 0;
            int var23 = 0;
            java.util.EnumMap<Material, Integer> gemsFound = new java.util.EnumMap<>(Material.class);
            ThreadLocalRandom var11 = ThreadLocalRandom.current();

            for (int var12 = 0; var12 < var2; var12++) {
                double var13 = var11.nextDouble() * var5;
                String var15 = "nothing";

                for (Map<?, ?> var17 : var4) {
                    var13 -= var17.get("weight") instanceof Number var18 ? var18.doubleValue() : 1.0;
                    if (var13 <= 0.0) {
                        var15 = String.valueOf(var17.get("type"));
                        break;
                    }
                }

                switch (var15) {
                    case "dust":
                        var20++;
                        break;
                    case "substance":
                        var21++;
                        break;
                    case "key":
                        var22++;
                        break;
                    case "stars":
                        var23++;
                        break;
                    case "gem":
                        Material[] gems = {Material.DIAMOND, Material.EMERALD, Material.LAPIS_LAZULI, Material.AMETHYST_SHARD, Material.GOLD_NUGGET};
                        gemsFound.merge(gems[var11.nextInt(gems.length)], 1, Integer::sum);
                }
            }

            if (var20 > 0) {
                Items.give(var1, this.plugin.enchants().dust(10, var20));
            }

            if (var21 > 0) {
                Items.give(
                    var1,
                    this.plugin
                        .sell()
                        .special(
                            Material.SUGAR,
                            "<white>Белая субстанция",
                            this.plugin.getConfig().getDouble("sieve.substance-price", 15.0),
                            var21,
                            "<gray>Дорого продаётся: /sell"
                        )
                );
            }

            if (var22 > 0) {
                var3.addKey("common", var22);
            }

            this.plugin.specials().giveStars(var1, var23);
            int gemTotal = 0;
            for (java.util.Map.Entry<Material, Integer> g : gemsFound.entrySet()) {
                Items.give(var1, new ItemStack(g.getKey(), g.getValue()));
                gemTotal += g.getValue();
            }
            var1.playSound(var1.getLocation(), Sound.BLOCK_SAND_BREAK, 1.0F, 0.8F);
            var1.sendMessage(
                Msg.parse(
                    "<gold>Сито (" + var2 + " шт.): <gray>камни x" + gemTotal + ", пыль 10% x" + var20 + ", субстанция x" + var21 + ", обычные ключи x" + var22 + ", звёзды x" + var23
                )
            );
            this.sieve(var1);
        } else {
            var1.sendMessage(Msg.parse("<red>Не хватает щебня/гравия/песка. Щебень приносит банда (/six), гравий и песок — Землянка."));
        }
    }
}
