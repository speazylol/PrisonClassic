package dev.prisonclassic.enchant;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.upgrade.UpgradeService;
import dev.prisonclassic.util.Items;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class EnchantService {
    private final PrisonClassic plugin;
    private final NamespacedKey enchKey;
    private final NamespacedKey bookKey;
    private final NamespacedKey dustKey;
    private final NamespacedKey uniqueKey;

    public EnchantService(PrisonClassic var1) {
        this.plugin = var1;
        this.enchKey = new NamespacedKey(var1, "ench");
        this.bookKey = new NamespacedKey(var1, "book");
        this.dustKey = new NamespacedKey(var1, "dust");
        this.uniqueKey = new NamespacedKey(var1, "unique");
    }

    public Map<Ench, Integer> read(ItemStack var1) {
        EnumMap var2 = new EnumMap<>(Ench.class);
        if (var1 != null && var1.hasItemMeta()) {
            String var3 = var1.getItemMeta().getPersistentDataContainer().get(this.enchKey, PersistentDataType.STRING);
            if (var3 != null && !var3.isEmpty()) {
                for (String var7 : var3.split(",")) {
                    String[] var8 = var7.split(":");
                    Ench var9 = var8.length == 2 ? Ench.parse(var8[0]) : null;
                    if (var9 != null) {
                        try {
                            var2.put(var9, Math.min(var9.max, Integer.parseInt(var8[1])));
                        } catch (NumberFormatException var11) {
                        }
                    }
                }

                return var2;
            } else {
                return var2;
            }
        } else {
            return var2;
        }
    }

    public int level(ItemStack var1, Ench var2) {
        return this.read(var1).getOrDefault(var2, 0);
    }

    public void write(ItemStack var1, Map<Ench, Integer> var2) {
        ItemMeta var3 = var1.getItemMeta();
        StringBuilder var4 = new StringBuilder();

        for (Entry var6 : var2.entrySet()) {
            if (var4.length() > 0) {
                var4.append(',');
            }

            var4.append(((Ench)var6.getKey()).name()).append(':').append(var6.getValue());
        }

        var3.getPersistentDataContainer().set(this.enchKey, PersistentDataType.STRING, var4.toString());
        var1.setItemMeta(var3);
        this.refreshLore(var1);
    }

    public void refreshLore(ItemStack var1) {
        if (var1 != null && !var1.getType().isAir() && this.readBook(var1) == null) {
            ItemMeta var2 = var1.getItemMeta();
            if (var2 != null) {
                ArrayList var3 = new ArrayList();

                for (Entry var5 : this.read(var1).entrySet()) {
                    var3.add(
                        Component.text(((Ench)var5.getKey()).title + " " + roman((Integer)var5.getValue()), NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)
                    );
                }

                String rar = this.plugin.upgrades() == null ? null : this.plugin.upgrades().rarityLine(var1);
                if (rar != null) {
                    var3.add(Items.line(rar));
                }

                String var7 = this.plugin.upgrades() == null ? null : this.plugin.upgrades().blocksLine(var1);
                if (var7 != null) {
                    var3.add(Items.line(var7));
                }

                if (this.plugin.awaken() != null) {
                    for (String var6 : this.plugin.awaken().loreLines(var1)) {
                        var3.add(Items.line(var6));
                    }
                }

                String var9 = this.plugin.treasures() == null ? null : this.plugin.treasures().loreLine(var1);
                if (var9 != null) {
                    var3.add(Items.line(var9));
                }

                if (UpgradeService.Chain.of(var1.getType()) != null) {
                    var3.add(Items.line("<dark_gray>Улучшение: /up"));
                }

                var2.lore(var3);
                var1.setItemMeta(var2);
            }
        }
    }

    public int slots(Material var1) {
        FileConfiguration var2 = this.plugin.getConfig();
        if (Ench.Kind.ARMOR.matches(var1)) {
            return var2.getInt("enchants.slots.armor", 3);
        } else {
            return !Ench.Kind.SWORD.matches(var1) && !Ench.Kind.BOW.matches(var1)
                ? var2.getInt("enchants.slots.tool", 4)
                : var2.getInt("enchants.slots.weapon", 2);
        }
    }

    public String apply(ItemStack var1, Ench var2, int var3) {
        if (var1 != null && var2.fits(var1.getType())) {
            Map<Ench, Integer> var4 = this.read(var1);
            int var5 = var4.getOrDefault(var2, 0);
            if (var5 >= var3) {
                return "На предмете уже есть такой или лучший уровень.";
            } else if (var5 == 0 && var4.size() >= this.slots(var1.getType())) {
                return "Нет свободных слотов зачарований (" + this.slots(var1.getType()) + ").";
            } else {
                var4.put(var2, Math.min(var2.max, var3));
                this.write(var1, var4);
                return null;
            }
        } else {
            return "Это зачарование не подходит к предмету.";
        }
    }

    public int armorLevel(Player var1, Ench var2) {
        int var3 = 0;

        for (ItemStack var7 : var1.getInventory().getArmorContents()) {
            var3 += this.level(var7, var2);
        }

        return Math.min(var2.max, var3);
    }

    public int armorSum(Player var1, Ench var2) {
        int var3 = 0;

        for (ItemStack var7 : var1.getInventory().getArmorContents()) {
            var3 += this.level(var7, var2);
        }

        return var3;
    }

    /** Максимальный уровень чара среди надетых частей (для «1 книга на сет»). */
    public int armorMax(Player var1, Ench var2) {
        int best = 0;
        for (ItemStack it : var1.getInventory().getArmorContents()) {
            best = Math.max(best, this.level(it, var2));
        }
        return best;
    }

    public int handLevel(Player var1, Ench var2) {
        return this.level(var1.getInventory().getItemInMainHand(), var2);
    }

    public ItemStack book(Ench var1, int var2, int var3) {
        ItemStack var4 = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta var5 = var4.getItemMeta();
        var5.displayName(Component.text("Книга: " + var1.title + " " + roman(var2), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        var5.lore(
            List.of(
                Component.text("Шанс успеха: " + var3 + "%", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(var1.desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Перетащите книгу на предмет. Пыль — на книгу.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            )
        );
        var5.getPersistentDataContainer().set(this.bookKey, PersistentDataType.STRING, var1.name() + ":" + var2 + ":" + var3);
        var4.setItemMeta(var5);
        return var4;
    }

    public Object[] readBook(ItemStack var1) {
        if (var1 != null && var1.hasItemMeta()) {
            String var2 = var1.getItemMeta().getPersistentDataContainer().get(this.bookKey, PersistentDataType.STRING);
            if (var2 == null) {
                return null;
            } else {
                String[] var3 = var2.split(":");
                Ench var4 = Ench.parse(var3[0]);
                return var4 != null && var3.length >= 3 ? new Object[]{var4, Integer.parseInt(var3[1]), Integer.parseInt(var3[2])} : null;
            }
        } else {
            return null;
        }
    }

    public ItemStack bookWithChance(ItemStack var1, int var2) {
        Object[] var3 = this.readBook(var1);
        return var3 == null ? var1 : this.book((Ench)var3[0], (Integer)var3[1], var2);
    }

    public int maxBookChance() {
        return this.plugin.getConfig().getInt("enchants.max-book-chance", 80);
    }

    public ItemStack randomBook(String var1) {
        ThreadLocalRandom var2 = ThreadLocalRandom.current();
        ArrayList<Ench> var3 = new ArrayList<>(List.of(Ench.values()));
        var3.remove(Ench.TOTEM); // «Тотем бессмертия» — строго из Древнего ключа (отдельная запись в луте ДК-сундука)
        Ench var4 = (Ench)var3.get(var2.nextInt(var3.size()));

        int var5 = switch (var1) {
            case "legendary" -> var4.max;
            case "dk" -> Math.max(1, var4.max - 1 - (var2.nextDouble() < 0.4 ? 1 : 0));
            default -> Math.max(1, Math.min(var4.max - 1, 1 + var2.nextInt(2)));
        };
        if (!var1.equals("legendary") && var5 >= var4.max && var4.max > 1) {
            var5 = var4.max - 1;
        }

        int var6 = var1.equals("legendary") ? 30 : (var1.equals("dk") ? 20 : 10);
        return this.book(var4, var5, var6);
    }

    public ItemStack dust(int var1, int var2) {
        ItemStack var3 = new ItemStack(Material.GLOWSTONE_DUST, var2);
        ItemMeta var4 = var3.getItemMeta();
        var4.displayName(
            Component.text("Пыль " + var1 + "%", var1 >= 100 ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        );
        var4.lore(
            List.of(
                Component.text("Перетащите на книгу: +" + var1 + "% к шансу (макс. " + this.maxBookChance() + "%)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
        );
        var4.getPersistentDataContainer().set(this.dustKey, PersistentDataType.INTEGER, var1);
        var3.setItemMeta(var4);
        return var3;
    }

    public int dustPct(ItemStack var1) {
        if (var1 != null && var1.hasItemMeta()) {
            Integer var2 = var1.getItemMeta().getPersistentDataContainer().get(this.dustKey, PersistentDataType.INTEGER);
            return var2 == null ? 0 : var2;
        } else {
            return 0;
        }
    }

    public ItemStack unique(Material var1, String var2, int var3) {
        ItemStack var4 = new ItemStack(var1);
        ItemMeta var5 = var4.getItemMeta();
        var5.displayName(Component.text(var2, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        var5.lore(
            List.of(
                Component.text("Уникальный трофей босса. Обмен: /exchange unique (" + var3 + " зв.)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
        );
        var5.getPersistentDataContainer().set(this.uniqueKey, PersistentDataType.INTEGER, var3);
        var4.setItemMeta(var5);
        return var4;
    }

    public int uniqueStars(ItemStack var1) {
        if (var1 != null && var1.hasItemMeta()) {
            Integer var2 = var1.getItemMeta().getPersistentDataContainer().get(this.uniqueKey, PersistentDataType.INTEGER);
            return var2 == null ? 0 : var2 * var1.getAmount();
        } else {
            return 0;
        }
    }

    public static String roman(int var0) {
        return switch (var0) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(var0);
        };
    }
}
