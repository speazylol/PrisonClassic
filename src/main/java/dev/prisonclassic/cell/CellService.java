package dev.prisonclassic.cell;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Личная камера (CellManager хранит регионы/владельцев, CellService — меню и 4 тира улучшений).
 * Открывается с 8 уровня. Тиры: 1 — камера и Тюремная банда; 2 — Сито; 3 — Душ (снимает грязь/голод, бафф «Удача»);
 * 4 — Полка коллекций «Наследие Предков». Блоки в камере: компостер = сито, котёл с водой = душ, кафедра = полка.
 */
public final class CellService implements Listener {
    private final PrisonClassic plugin;

    public CellService(PrisonClassic plugin) {
        this.plugin = plugin;
    }

    private List<Double> costs() {
        List<Double> c = this.plugin.getConfig().getDoubleList("cells.upgrade-costs");
        return c.isEmpty() ? List.of(5000.0, 15000.0, 40000.0) : c;
    }

    public int tier(Player p) {
        Cell c = this.plugin.cells().ownedBy(p.getUniqueId());
        return c == null ? 0 : c.level;
    }

    public void open(Player p) {
        PlayerData d = this.plugin.data().get(p);
        Cell cell = this.plugin.cells().ownedBy(p.getUniqueId());
        if (d == null) {
            return;
        }
        if (cell == null) {
            Msg.send(p, "cell-none");
            return;
        }
        int sieve = this.plugin.getConfig().getInt("cells.tiers.sieve", 2);
        int shower = this.plugin.getConfig().getInt("cells.tiers.shower", 3);
        int shelf = this.plugin.getConfig().getInt("cells.tiers.shelf", 4);
        Gui gui = new Gui(27, Msg.parse("Камера " + cell.name + " · уровень " + cell.level));
        gui.fill(Items.filler());
        if (cell.level < 4) {
            double cost = this.costs().get(Math.min(this.costs().size() - 1, cell.level - 1));
            gui.set(4, Items.of(Material.ANVIL, "<gold>Улучшить камеру до " + (cell.level + 1) + " уровня",
                "<gray>Цена: <green>$" + Msg.money(cost), "<gray>Откроется: <white>" + switch (cell.level + 1) {
                    case 2 -> "Сито";
                    case 3 -> "Душ";
                    default -> "Полка коллекций «Наследие Предков»";
                }, "<yellow>Нажмите для улучшения"), (pl, e) -> this.upgrade(pl));
        } else {
            gui.set(4, Items.of(Material.NETHER_STAR, "<gold>Камера улучшена до максимума (4)"));
        }
        gui.set(10, Items.of(Material.VILLAGER_SPAWN_EGG, "<gold>Тюремная банда", "<gray>Наём NPC-помощников, кормление арбузами,", "<gray>монеты и щебень в офлайне", "<yellow>Открыть"),
            (pl, e) -> this.plugin.sixes().open(pl));
        gui.set(12, Items.of(cell.level >= sieve ? Material.COMPOSTER : Material.BARRIER, "<gold>Сито",
            "<gray>Гравий/песок/щебень → драгоценные камни и реагенты", cell.level >= sieve ? "<yellow>Открыть" : "<red>С " + sieve + " уровня камеры"),
            (pl, e) -> this.plugin.sixes().sieve(pl));
        gui.set(14, Items.of(cell.level >= shower ? Material.WATER_BUCKET : Material.BARRIER, "<aqua>Душ",
            "<gray>Смывает грязь и голод, даёт бафф «Удача»", cell.level >= shower ? "<yellow>Помыться" : "<red>С " + shower + " уровня камеры"),
            (pl, e) -> {
                if (this.tier(pl) >= shower) {
                    this.plugin.needs().shower(pl, true);
                    pl.closeInventory();
                }
            });
        gui.set(16, Items.of(cell.level >= shelf ? Material.LECTERN : Material.BARRIER, "<gold>Полка коллекций",
            "<gray>Наследие Предков: 6 веток × 5 осколков", cell.level >= shelf ? "<yellow>Открыть" : "<red>С " + shelf + " уровня камеры (просмотр — /collection)"),
            (pl, e) -> this.plugin.collections().open(pl));
        gui.set(22, Items.of(Material.ENDER_PEARL, "<gray>Телепорт в камеру"), (pl, e) -> {
            if (cell.spawn != null) {
                pl.closeInventory();
                this.plugin.donate().go(pl, cell.spawn);
            }
        });
        gui.open(p);
    }

    public void upgrade(Player p) {
        PlayerData d = this.plugin.data().get(p);
        Cell cell = this.plugin.cells().ownedBy(p.getUniqueId());
        if (d == null || cell == null || cell.level >= 4) {
            return;
        }
        double cost = this.costs().get(Math.min(this.costs().size() - 1, cell.level - 1));
        if (d.balance < cost) {
            Msg.send(p, "not-enough", Msg.p("cost", Msg.money(cost)));
            return;
        }
        d.balance -= cost;
        cell.level++;
        this.plugin.cells().save();
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F, 1.2F);
        p.sendMessage(Msg.parse("<green>Камера улучшена до " + cell.level + " уровня!"));
        this.open(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        Cell cell = this.plugin.cells().at(b);
        if (cell == null || !e.getPlayer().getUniqueId().equals(cell.owner) && !cell.members.contains(e.getPlayer().getUniqueId())) {
            return;
        }
        Player p = e.getPlayer();
        switch (b.getType()) {
            case COMPOSTER -> {
                e.setCancelled(true);
                this.plugin.sixes().sieve(p);
            }
            case LECTERN -> {
                e.setCancelled(true);
                this.plugin.collections().open(p);
            }
            case WATER_CAULDRON -> {
                e.setCancelled(true);
                if (cell.level >= this.plugin.getConfig().getInt("cells.tiers.shower", 3)) {
                    this.plugin.needs().shower(p, true);
                } else {
                    p.sendMessage(Msg.parse("<red>Душ в камере открывается на " + this.plugin.getConfig().getInt("cells.tiers.shower", 3) + "-м уровне. /cell upgrade"));
                }
            }
            case BARREL -> {
                e.setCancelled(true);
                this.open(p);
            }
            default -> {
            }
        }
    }
}
