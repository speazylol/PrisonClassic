package dev.prisonclassic.ui;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.faction.Faction;
import dev.prisonclassic.level.LevelManager;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.mine.MineDefs;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MenuService implements Listener {
    private final PrisonClassic plugin;

    public MenuService(PrisonClassic var1) {
        this.plugin = var1;
    }

    private static Component t(String var0, NamedTextColor var1) {
        return Component.text(var0, var1).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material var0, String var1, NamedTextColor var2, List<Component> var3) {
        ItemStack var4 = new ItemStack(var0);
        ItemMeta var5 = var4.getItemMeta();
        var5.displayName(t(var1, var2));
        var5.lore(var3);
        var4.setItemMeta(var5);
        return var4;
    }

    public void mines(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            MenuService.Holder var3 = new MenuService.Holder();
            Inventory var4 = Bukkit.createInventory(var3, 54, Component.text("Шахты"));
            int var5 = 0;

            for (Mine var7 : this.plugin.mines().all()) {
                if (var5 >= 54) {
                    break;
                }

                if (!var7.hidden && !var7.privateSlot) {
                    boolean var8 = var2.level >= var7.requiredLevel;
                    Material var9 = Material.STONE;
                    double var10 = -1.0;

                    for (Entry var13 : var7.composition.entrySet()) {
                        Material var14 = MineDefs.dropFor((Material)var13.getKey());
                        if (var14 != null && (Double)var13.getValue() > var10) {
                            var10 = (Double)var13.getValue();
                            var9 = ((Material)var13.getKey()).isItem() ? (Material)var13.getKey() : var14;
                        }
                    }

                    ArrayList var15 = new ArrayList();
                    var15.add(t("Уровень: " + var7.requiredLevel, var8 ? NamedTextColor.GREEN : NamedTextColor.RED));
                    var15.add(t(var8 ? "Нажмите для телепорта" : "Недоступна", var8 ? NamedTextColor.YELLOW : NamedTextColor.DARK_RED));
                    var4.setItem(var5, item(var8 ? var9 : Material.BARRIER, var7.name + ". " + var7.title, NamedTextColor.GOLD, var15));
                    var3.actions.put(var5, var3x -> {
                        if (var3x.getOpenInventory() != null) {
                            var3x.closeInventory();
                        }

                        if (var7.name.startsWith("6")) {
                            this.plugin.basement().enter(var3x);
                            return;
                        }

                        Location var4x = var7.spawn != null ? var7.spawn : this.plugin.spawn();
                        if (var4x == null) {
                            Msg.send(var3x, "no-spawn");
                        } else if (var8) {
                            this.plugin.donate().go(var3x, var4x);
                        }
                    });
                    var5++;
                }
            }

            var1.openInventory(var4);
        }
    }

    public void warps(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            MenuService.Holder var3 = new MenuService.Holder();
            Inventory var4 = Bukkit.createInventory(var3, 27, Component.text("Локации"));
            int var5 = this.plugin.getConfig().getInt("factions.min-level", 5);
            int var6 = this.plugin.getConfig().getInt("awaken.min-level", 20);
            Object[][] var7 = new Object[][]{
                {10, Material.BEACON, "Спавн", "spawn", 1},
                {11, Material.DIAMOND_PICKAXE, "Шахты", null, 1},
                {12, Material.IRON_BARS, "Тюремный блок (камеры)", "warp.cells", 1},
                {13, Material.RED_BANNER, "База фракций", "warp.base", var5},
                {14, Material.MOSSY_STONE_BRICKS, "Тюремный подвал (пропуск, PvP)", "warp.basement", 6},
                {15, Material.HAY_BLOCK, "Деревня Люфония", "lufonia", var6},
                {16, Material.MAP, "Личная шахта", "pmine", 1},
                {21, Material.BELL, "Церковь (спавн)", "church", 1},
                {23, Material.IRON_SWORD, "Стройка — вход в катакомбы", "npc.dungeon", 17}
            };

            for (Object[] var11 : var7) {
                int var12 = (Integer)var11[0];
                int var13 = (Integer)var11[4];
                String var14 = (String)var11[3];
                boolean var15 = var2.level >= var13;
                ArrayList var16 = new ArrayList();
                var16.add(t(var13 > 1 ? "С " + var13 + " уровня" : "Доступно всем", var15 ? NamedTextColor.GREEN : NamedTextColor.RED));
                var4.setItem(var12, item(var15 ? (Material)var11[1] : Material.BARRIER, (String)var11[2], NamedTextColor.GOLD, var16));
                var3.actions.put(var12, var4x -> {
                    var4x.closeInventory();
                    if (!var15) {
                        var4x.sendMessage(Msg.parse("<red>Нужен уровень " + var13));
                    } else if (var14 == null) {
                        this.mines(var4x);
                    } else if (var14.equals("pmine")) {
                        this.plugin.privateMines().open(var4x);
                    } else if (var14.equals("warp.basement")) {
                        this.plugin.basement().enter(var4x);
                    } else {
                        Location var5x = var14.equals("spawn") ? this.plugin.spawn() : this.plugin.locations().get(var14);
                        if (var5x == null) {
                            Msg.send(var4x, "no-spawn");
                        } else {
                            this.plugin.donate().go(var4x, var5x);
                        }
                    }
                });
            }

            var1.openInventory(var4);
        }
    }

    public void level(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            MenuService.Holder var3 = new MenuService.Holder();
            Inventory var4 = Bukkit.createInventory(var3, 27, Component.text("Повышение уровня"));
            LevelManager var5 = this.plugin.levels();
            ArrayList var6 = new ArrayList();
            var6.add(t("Текущий уровень: " + var2.level, NamedTextColor.WHITE));
            if (var2.level >= var5.max()) {
                var6.add(t("Максимальный уровень!", NamedTextColor.GOLD));
            } else {
                long var7 = var5.blocksNeeded(var2.level);
                double var9 = var5.levelPrice(var2);
                boolean var11 = var2.blocksMined >= var7 && var2.balance >= var9;
                var6.add(t("Следующий уровень: " + (var2.level + 1) + "  +" + (int)(var5.maxHealth(var2.level + 1) - var5.maxHealth(var2.level)) + " HP (+1 сердце)", NamedTextColor.GREEN));
                var6.add(t("Здоровье: " + (int)var5.maxHealth(var2.level) + " HP, бонус урона: +" + (int)var5.damageBonus(var2.level), NamedTextColor.GRAY));
                var6.add(t("Фракционная скидка: " + Math.round(this.plugin.factionBalance().discount(var2) * 100.0) + "% (по историческому онлайну)", NamedTextColor.AQUA));
                var6.add(t("Стоимость: $" + Msg.money(var9), var2.balance >= var9 ? NamedTextColor.GREEN : NamedTextColor.RED));
                var6.add(t("Блоки: " + var2.blocksMined + "/" + var7, var2.blocksMined >= var7 ? NamedTextColor.GREEN : NamedTextColor.RED));
                var6.add(t(var11 ? "Нажмите для повышения" : "Не все условия выполнены", var11 ? NamedTextColor.YELLOW : NamedTextColor.RED));
            }

            var4.setItem(13, item(Material.EXPERIENCE_BOTTLE, "Повышение уровня", NamedTextColor.GOLD, var6));
            var3.actions.put(13, var1x -> {
                PlayerData var2x = this.plugin.data().get(var1x);
                if (var2x != null) {
                    this.plugin.levels().rankUp(var1x, var2x);
                }

                this.level(var1x);
            });
            var1.openInventory(var4);
        }
    }

    public void factions(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            MenuService.Holder var3 = new MenuService.Holder();
            Inventory var4 = Bukkit.createInventory(var3, 27, Component.text("Выбор фракции"));
            Material[] var5 = new Material[]{Material.IRON_INGOT, Material.COAL, Material.GOLD_INGOT};
            int[] var6 = new int[]{11, 13, 15};
            int var7 = 0;

            for (Faction var9 : this.plugin.factions().all()) {
                if (var7 >= 3) {
                    break;
                }

                List var10 = List.of(
                    t("Средний онлайн: " + String.format(java.util.Locale.US, "%.1f", this.plugin.factionBalance().averageOnline(var9.id())) + " · скидка " + Math.round(this.plugin.factionBalance().discount(var9.id()) * 100.0) + "%", NamedTextColor.AQUA),
                    t("Продажа x" + var9.sellBonus(), NamedTextColor.GREEN),
                    t("Награда с боссов x" + var9.bossBonus(), NamedTextColor.GOLD),
                    t("Урон x" + var9.damageBonus(), NamedTextColor.RED),
                    t(var2.faction == null ? "Нажмите для вступления" : "Вы уже во фракции", NamedTextColor.YELLOW)
                );
                var4.setItem(var6[var7], item(var5[var7], var9.id(), NamedTextColor.WHITE, var10));
                var3.actions.put(var6[var7], var2x -> {
                    PlayerData var3x = this.plugin.data().get(var2x);
                    if (var3x != null) {
                        var2x.closeInventory();
                        this.plugin.factions().join(var2x, var3x, var9);
                    }
                });
                var7++;
            }

            var1.openInventory(var4);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent var1) {
        if (var1.getInventory().getHolder() instanceof MenuService.Holder var2) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player var5) {
                if (var1.getClickedInventory() == var1.getInventory()) {
                    Consumer var6 = var2.actions.get(var1.getRawSlot());
                    if (var6 != null) {
                        var6.accept(var5);
                    }
                }
            }
        }
    }

    private static final class Holder implements InventoryHolder {
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
