package dev.prisonclassic.listener;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.cell.Cell;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class WorldListener implements Listener {
    private final PrisonClassic plugin;

    public WorldListener(PrisonClassic var1) {
        this.plugin = var1;
    }

    private boolean bypass(Player var1) {
        return var1.hasPermission("prison.admin") && var1.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onBreak(BlockBreakEvent var1) {
        Player var2 = var1.getPlayer();
        Block var3 = var1.getBlock();
        Mine var4 = this.plugin.mines().at(var3);
        if (var4 != null) {
            this.mineBlock(var1, var2, var3, var4);
        } else {
            Cell var5 = this.plugin.cells().at(var3);
            if (var5 != null) {
                if (!this.plugin.cells().canUse(var2, var5)) {
                    var1.setCancelled(true);
                    Msg.send(var2, "cell-foreign");
                }
            } else {
                if (!this.bypass(var2)) {
                    var1.setCancelled(true);
                }
            }
        }
    }

    private void mineBlock(BlockBreakEvent var1, Player var2, Block var3, Mine var4) {
        PlayerData var5 = this.plugin.data().get(var2);
        if (var5 == null) {
            var1.setCancelled(true);
        } else if (var4.privateSlot && !var2.getUniqueId().equals(var4.owner)) {
            var1.setCancelled(true);
            var2.sendMessage(Msg.parse("<red>Это чужая личная шахта. Своя — /pmine"));
        } else if (var5.level < var4.requiredLevel) {
            var1.setCancelled(true);
            Msg.send(var2, "mine-locked", Msg.p("level", this.plugin.levels().name(var4.requiredLevel)), Msg.p("mine", var4.title));
        } else {
            this.plugin.mining().onMine(var1, var2, var5, var4);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onPlace(BlockPlaceEvent var1) {
        Player var2 = var1.getPlayer();
        Block var3 = var1.getBlockPlaced();
        if (this.plugin.mines().at(var3) != null) {
            if (!this.bypass(var2)) {
                var1.setCancelled(true);
            }
        } else {
            Cell var4 = this.plugin.cells().at(var3);
            if (var4 != null) {
                if (!this.plugin.cells().canUse(var2, var4)) {
                    var1.setCancelled(true);
                    Msg.send(var2, "cell-foreign");
                }
            } else {
                if (!this.bypass(var2)) {
                    var1.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent var1) {
        if (var1.getHand() == EquipmentSlot.HAND) {
            Player var2 = var1.getPlayer();
            Block var3 = var1.getClickedBlock();
            if (var3 != null) {
                if (this.plugin.selection().isWand(var1.getItem()) && var2.hasPermission("prison.admin")) {
                    Location var5 = var3.getLocation();
                    if (var1.getAction() == Action.LEFT_CLICK_BLOCK) {
                        this.plugin.selection().set(var2.getUniqueId(), 0, var5);
                        Msg.send(var2, "wand-pos", Msg.p("n", 1), Msg.p("pos", this.fmt(var5)));
                        var1.setCancelled(true);
                    } else if (var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
                        this.plugin.selection().set(var2.getUniqueId(), 1, var5);
                        Msg.send(var2, "wand-pos", Msg.p("n", 2), Msg.p("pos", this.fmt(var5)));
                        var1.setCancelled(true);
                    }
                } else {
                    if (var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
                        Cell var4 = this.plugin.cells().at(var3);
                        if (var4 != null && !this.plugin.cells().canUse(var2, var4)) {
                            var1.setCancelled(true);
                            Msg.send(var2, "cell-foreign");
                        }
                    }
                }
            }
        }
    }

    private String fmt(Location var1) {
        return var1.getBlockX() + " " + var1.getBlockY() + " " + var1.getBlockZ();
    }
}
