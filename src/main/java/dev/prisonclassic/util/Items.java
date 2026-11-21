package dev.prisonclassic.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class Items {
    private Items() {
    }

    public static Component line(String var0) {
        return Msg.parse(var0).decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack of(Material var0, String var1, List<String> var2) {
        ItemStack var3 = new ItemStack(var0);
        ItemMeta var4 = var3.getItemMeta();
        if (var4 == null) {
            return var3;
        } else {
            var4.displayName(line(var1));
            ArrayList var5 = new ArrayList();

            for (String var7 : var2) {
                var5.add(line(var7));
            }

            var4.lore(var5);
            var4.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            var3.setItemMeta(var4);
            return var3;
        }
    }

    public static ItemStack of(Material var0, String var1, String... var2) {
        return of(var0, var1, List.of(var2));
    }

    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static void give(Player var0, ItemStack var1) {
        for (ItemStack var3 : var0.getInventory().addItem(new ItemStack[]{var1}).values()) {
            var0.getWorld().dropItemNaturally(var0.getLocation(), var3);
        }
    }

    public static int count(Player var0, Predicate<ItemStack> var1) {
        int var2 = 0;

        for (ItemStack var6 : var0.getInventory().getStorageContents()) {
            if (var6 != null && var1.test(var6)) {
                var2 += var6.getAmount();
            }
        }

        return var2;
    }

    public static boolean take(Player var0, Predicate<ItemStack> var1, int var2) {
        if (count(var0, var1) < var2) {
            return false;
        } else {
            ItemStack[] var3 = var0.getInventory().getStorageContents();

            for (int var4 = 0; var4 < var3.length && var2 > 0; var4++) {
                if (var3[var4] != null && var1.test(var3[var4])) {
                    int var5 = Math.min(var2, var3[var4].getAmount());
                    var2 -= var5;
                    var3[var4].setAmount(var3[var4].getAmount() - var5);
                    if (var3[var4].getAmount() <= 0) {
                        var3[var4] = null;
                    }
                }
            }

            var0.getInventory().setStorageContents(var3);
            return true;
        }
    }

    public static String fmtTime(long var0) {
        if (var0 < 0L) {
            return "навсегда";
        } else {
            long var2 = var0 / 86400L;
            long var4 = var0 / 3600L % 24L;
            long var6 = var0 / 60L % 60L;
            long var8 = var0 % 60L;
            if (var2 > 0L) {
                return var2 + "д " + var4 + "ч";
            } else if (var4 > 0L) {
                return var4 + "ч " + var6 + "м";
            } else {
                return var6 > 0L ? var6 + "м " + var8 + "с" : var8 + "с";
            }
        }
    }
}
