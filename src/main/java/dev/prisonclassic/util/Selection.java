package dev.prisonclassic.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class Selection {
    private final NamespacedKey wandKey;
    private final Map<UUID, Location[]> points = new HashMap<>();

    public Selection(JavaPlugin var1) {
        this.wandKey = new NamespacedKey(var1, "wand");
    }

    public ItemStack wand() {
        ItemStack var1 = new ItemStack(Material.BLAZE_ROD);
        ItemMeta var2 = var1.getItemMeta();
        var2.displayName(Component.text("Палочка выделения", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        var2.lore(List.of(Component.text("ЛКМ — точка 1, ПКМ — точка 2", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        var2.getPersistentDataContainer().set(this.wandKey, PersistentDataType.BYTE, (byte)1);
        var1.setItemMeta(var2);
        return var1;
    }

    public boolean isWand(ItemStack var1) {
        return var1 != null && var1.hasItemMeta() && var1.getItemMeta().getPersistentDataContainer().has(this.wandKey, PersistentDataType.BYTE);
    }

    public void set(UUID var1, int var2, Location var3) {
        this.points.computeIfAbsent(var1, var0 -> new Location[2])[var2] = var3;
    }

    public Region region(UUID var1) {
        Location[] var2 = this.points.get(var1);
        if (var2 != null && var2[0] != null && var2[1] != null) {
            return var2[0].getWorld() != null && var2[0].getWorld().equals(var2[1].getWorld()) ? Region.of(var2[0], var2[1]) : null;
        } else {
            return null;
        }
    }
}
