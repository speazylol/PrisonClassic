package dev.prisonclassic.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class Gui implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, BiConsumer<Player, InventoryClickEvent>> actions = new HashMap<>();

    public Gui(int var1, Component var2) {
        this.inv = Bukkit.createInventory(this, var1, var2);
    }

    public Gui set(int var1, ItemStack var2) {
        this.inv.setItem(var1, var2);
        return this;
    }

    public Gui set(int var1, ItemStack var2, BiConsumer<Player, InventoryClickEvent> var3) {
        this.inv.setItem(var1, var2);
        this.actions.put(var1, var3);
        return this;
    }

    public Gui fill(ItemStack var1) {
        for (int var2 = 0; var2 < this.inv.getSize(); var2++) {
            if (this.inv.getItem(var2) == null) {
                this.inv.setItem(var2, var1);
            }
        }

        return this;
    }

    public void open(Player var1) {
        var1.openInventory(this.inv);
    }

    @Override
    public Inventory getInventory() {
        return this.inv;
    }

    public static final class GuiListener implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent var1) {
            if (var1.getInventory().getHolder() instanceof Gui var2) {
                var1.setCancelled(true);
                if (var1.getWhoClicked() instanceof Player var5) {
                    if (var1.getClickedInventory() == var1.getInventory()) {
                        BiConsumer var6 = var2.actions.get(var1.getRawSlot());
                        if (var6 != null) {
                            var6.accept(var5, var1);
                        }
                    }
                }
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent var1) {
            if (var1.getInventory().getHolder() instanceof Gui) {
                var1.setCancelled(true);
            }
        }
    }
}
