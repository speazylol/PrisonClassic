package dev.prisonclassic.merchant;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.mine.MineDefs;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Location;
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
import org.bukkit.inventory.ItemStack;

/**
 * Торговцы шахт (Merchant/Trader). На каждой из 24 шахт стоит статический NPC; ПКМ — окно мгновенного выкупа
 * ресурсов именно этой шахты (по позициям или «Продать всё»), рядом — табличка «Продать всё».
 * Цены — прайс-лист SellService с учётом уровня, фракции, бустеров, церкви и т.д.
 * Премиум /sell (флаг AutoSell_Premium) продаёт всё из инвентаря где угодно.
 */
public final class MerchantService implements Listener {
    private final PrisonClassic plugin;

    public MerchantService(PrisonClassic plugin) {
        this.plugin = plugin;
    }

    /** Зона торговца: номер шахты, "6" — подвал (все комнаты), "base" — шахты Базы. */
    public List<Material> goods(String zone) {
        Set<Material> out = new LinkedHashSet<>();
        if (zone.equals("base")) {
            for (Mine m : this.plugin.mines().all()) {
                if (m.base) {
                    for (Material b : m.composition.keySet()) {
                        Material d = MineDefs.dropFor(b);
                        if (d != null) {
                            out.add(d);
                        }
                        Material silk = MineDefs.dropFor(b, true);
                        if (silk != null) {
                            out.add(silk);
                        }
                    }
                }
            }
            return new ArrayList<>(out);
        }
        for (MineDefs.Def d : MineDefs.all()) {
            boolean match = zone.equals("6") ? d.basement() : d.name().equals(zone);
            if (match) {
                out.addAll(MineDefs.sellables(d));
                for (MineDefs.Entry e : d.comp()) {
                    if (MineDefs.isOre(e.block())) {
                        out.add(e.block()); // руда целиком (кирка с «Шёлком»)
                    }
                }
            }
        }
        if (zone.equals("6")) {
            out.add(Material.ROTTEN_FLESH);
        }
        return new ArrayList<>(out);
    }

    public String title(String zone) {
        if (zone.equals("base")) {
            return "Торговец Базы";
        }
        if (zone.equals("6")) {
            return "Торговец подвала";
        }
        MineDefs.Def d = MineDefs.get(zone);
        return "Торговец: " + (d == null ? "шахта " + zone : d.title());
    }

    public void open(Player p, String zone) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        List<Material> goods = this.goods(zone);
        int rows = Math.min(6, Math.max(3, (goods.size() + 9 + 8) / 9 + 1));
        Gui gui = new Gui(rows * 9, Msg.parse(this.title(zone)));
        gui.fill(Items.filler());
        double mult = this.plugin.sell().multiplier(d);
        int slot = 10;
        for (Material m : goods) {
            if (slot >= rows * 9 - 9) {
                break;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            double each = this.plugin.sell().price(m) * mult;
            int have = Items.count(p, it -> it.getType() == m && !it.hasItemMeta());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Цена за шт.: <green>$" + Msg.money(each));
            lore.add("<gray>Цена за стак: <green>$" + Msg.money(each * 64));
            lore.add("<gray>У вас: <white>" + have + (have > 0 ? " <gray>→ <green>$" + Msg.money(each * have) : ""));
            lore.add("<yellow>Нажмите — продать все");
            ItemStack icon = Items.of(m.isItem() ? m : Material.PAPER, "<white><lang:" + m.translationKey() + ">", lore);
            gui.set(slot, icon, (pl, e) -> {
                double sum = this.plugin.sell().sellOnly(pl, this.plugin.data().get(pl), List.of(m));
                this.result(pl, sum);
                this.open(pl, zone);
            });
            slot++;
        }
        gui.set(rows * 9 - 5, Items.of(Material.OAK_SIGN, "<gold><bold>Продать всё",
            "<gray>Продать все ресурсы этой шахты из инвентаря", "<gray>Множитель: <white>x" + String.format(Locale.US, "%.2f", mult)), (pl, e) -> {
                double sum = this.plugin.sell().sellOnly(pl, this.plugin.data().get(pl), this.goods(zone));
                this.result(pl, sum);
                this.open(pl, zone);
            });
        gui.set(rows * 9 - 9, Items.of(Material.BOOK, "<gray>Справка",
            "<gray>Обычные игроки продают ресурсы здесь, у торговца шахты.",
            "<gray>Премиум-команда <white>/sell</white> — только с флагом <gold>AutoSell_Premium</gold>",
            "<gray>(Донат-NPC на спавне или донат-ранг)."));
        gui.open(p);
    }

    private void result(Player p, double sum) {
        PlayerData d = this.plugin.data().get(p);
        if (sum <= 0.0) {
            Msg.send(p, "sell-nothing");
            return;
        }
        Msg.send(p, "sell-done", Msg.p("amount", Msg.money(sum)), Msg.p("mult", String.format(Locale.US, "%.2f", this.plugin.sell().multiplier(d))));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
    }

    /** Табличка «Продать всё» рядом с торговцем (локации sign.merchant.<зона>). */
    @EventHandler(priority = EventPriority.LOW)
    public void onSign(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        if (!b.getType().name().endsWith("_SIGN")) {
            return;
        }
        for (String zone : this.zones()) {
            Location l = this.plugin.locations().get("sign.merchant." + zone);
            if (l != null && l.getWorld() == b.getWorld() && l.getBlockX() == b.getX() && l.getBlockY() == b.getY() && l.getBlockZ() == b.getZ()) {
                e.setCancelled(true);
                PlayerData d = this.plugin.data().get(e.getPlayer());
                if (d != null) {
                    this.result(e.getPlayer(), this.plugin.sell().sellOnly(e.getPlayer(), d, this.goods(zone)));
                }
                return;
            }
        }
    }

    /** Все зоны торговцев: 24 шахты + База. */
    public List<String> zones() {
        List<String> out = new ArrayList<>();
        for (MineDefs.Def d : MineDefs.all()) {
            String z = d.basement() ? "6" : d.name();
            if (!out.contains(z)) {
                out.add(z);
            }
        }
        out.add("base");
        return out;
    }
}
