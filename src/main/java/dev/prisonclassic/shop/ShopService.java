package dev.prisonclassic.shop;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.upgrade.UpgradeService;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ShopService {
    private final PrisonClassic plugin;

    public ShopService(PrisonClassic var1) {
        this.plugin = var1;
    }

    private List<ShopService.Entry> entries() {
        ArrayList var1 = new ArrayList();

        for (Map var3 : this.plugin.getConfig().getMapList("shop.items")) {
            String var4 = var3.get("special") == null ? "" : String.valueOf(var3.get("special"));
            Material var5 = Material.matchMaterial(String.valueOf(var3.get("material")));
            if (var5 != null || !var4.isEmpty()) {
                var1.add(
                    new ShopService.Entry(
                        var4,
                        var5 == null ? Material.PAPER : var5,
                        var3.get("name") == null ? "" : String.valueOf(var3.get("name")),
                        var3.get("amount") instanceof Number var6 ? var6.intValue() : 1,
                        var3.get("price") instanceof Number var8 ? var8.doubleValue() : 1.0,
                        var3.get("level") instanceof Number var9 ? var9.intValue() : 1
                    )
                );
            }
        }

        return var1;
    }

    private ItemStack create(ShopService.Entry var1) {
        String var3 = var1.special();

        ItemStack var2 = switch (var3) {
            case "toilet" -> this.plugin.needs().toiletPaper(var1.amount());
            case "soap" -> this.plugin.needs().soap(var1.amount());
            case "charm" -> this.plugin.church().charm(var1.amount());
            default -> new ItemStack(var1.material(), var1.amount());
        };
        if (var1.special().isEmpty() && UpgradeService.Chain.of(var1.material()) != null) {
            this.plugin.upgrades().mark(var2);
            this.plugin.enchants().refreshLore(var2);
        }

        return var2;
    }

    public void open(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            Gui var3 = new Gui(54, Msg.parse("Магазин"));
            int var4 = 0;

            for (ShopService.Entry var6 : this.entries()) {
                if (var4 >= 54) {
                    break;
                }

                ItemStack var7 = this.create(var6).clone();
                String var8 = var6.name().isEmpty() ? null : var6.name();
                ArrayList<String> var9 = new ArrayList<>();
                var9.add("<gray>Количество: <white>" + var6.amount());
                var9.add("<gray>Цена: <green>$" + Msg.money(var6.price()));
                if (var6.level() > 1) {
                    var9.add((var2.level >= var6.level() ? "<green>" : "<red>") + "Нужен уровень " + var6.level());
                }

                var9.add("<yellow>Нажмите, чтобы купить");
                ItemMeta var10 = var7.getItemMeta();
                if (var8 != null) {
                    var10.displayName(Items.line(var8));
                }

                ArrayList var11 = new ArrayList();
                if (var10.lore() != null) {
                    var11.addAll(var10.lore());
                }

                for (String var13 : var9) {
                    var11.add(Items.line(var13));
                }

                var10.lore(var11);
                var7.setItemMeta(var10);
                var3.set(var4++, var7, (var2x, var3x) -> this.buy(var2x, var6));
            }

            var3.open(var1);
        }
    }

    private void buy(Player var1, ShopService.Entry var2) {
        PlayerData var3 = this.plugin.data().get(var1);
        if (var3 != null) {
            if (var3.level < var2.level()) {
                var1.sendMessage(Msg.parse("<red>Нужен уровень " + var2.level()));
            } else if (var3.balance < var2.price()) {
                Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var2.price())));
            } else {
                var3.balance = var3.balance - var2.price();
                Items.give(var1, this.create(var2));
                var1.playSound(var1.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
            }
        }
    }

    private record Entry(String special, Material material, String name, int amount, double price, int level) {
    }
}
