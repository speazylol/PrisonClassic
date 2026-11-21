package dev.prisonclassic.auction;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AuctionService implements Listener {
    private final PrisonClassic plugin;
    private final File file;
    private final Map<String, AuctionService.Lot> lots = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> returns = new HashMap<>();
    private final Map<UUID, Double> payouts = new HashMap<>();
    private long counter = 0L;

    public AuctionService(PrisonClassic var1) {
        this.plugin = var1;
        this.file = new File(var1.getDataFolder(), "auction.yml");
        this.load();
        Bukkit.getScheduler().runTaskTimer(var1, this::expire, 1200L, 1200L);
    }

    private static String enc(ItemStack var0) {
        return Base64.getEncoder().encodeToString(var0.serializeAsBytes());
    }

    private static ItemStack dec(String var0) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(var0));
    }

    private void load() {
        YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
        this.counter = var1.getLong("counter", 0L);
        ConfigurationSection var2 = var1.getConfigurationSection("lots");
        if (var2 != null) {
            for (String var4 : var2.getKeys(false)) {
                ConfigurationSection var5 = var2.getConfigurationSection(var4);

                try {
                    this.lots
                        .put(
                            var4,
                            new AuctionService.Lot(
                                var4,
                                UUID.fromString(var5.getString("seller")),
                                var5.getString("name", "?"),
                                var5.getDouble("price"),
                                var5.getLong("created"),
                                dec(var5.getString("item"))
                            )
                        );
                } catch (Exception var11) {
                    this.plugin.getLogger().warning("auction.yml: лот " + var4 + " повреждён");
                }
            }
        }

        ConfigurationSection var12 = var1.getConfigurationSection("returns");
        if (var12 != null) {
            for (String var15 : var12.getKeys(false)) {
                ArrayList var6 = new ArrayList();

                for (String var8 : var12.getStringList(var15)) {
                    try {
                        var6.add(dec(var8));
                    } catch (Exception var10) {
                    }
                }

                this.returns.put(UUID.fromString(var15), var6);
            }
        }

        ConfigurationSection var14 = var1.getConfigurationSection("payouts");
        if (var14 != null) {
            for (String var17 : var14.getKeys(false)) {
                this.payouts.put(UUID.fromString(var17), var14.getDouble(var17));
            }
        }
    }

    public void save() {
        YamlConfiguration var1 = new YamlConfiguration();
        var1.set("counter", this.counter);

        for (AuctionService.Lot var3 : this.lots.values()) {
            String var4 = "lots." + var3.id + ".";
            var1.set(var4 + "seller", var3.seller.toString());
            var1.set(var4 + "name", var3.sellerName);
            var1.set(var4 + "price", var3.price);
            var1.set(var4 + "created", var3.created);
            var1.set(var4 + "item", enc(var3.item));
        }

        for (Entry<UUID, List<ItemStack>> var10 : this.returns.entrySet()) {
            if (!((List)var10.getValue()).isEmpty()) {
                ArrayList var12 = new ArrayList();

                for (ItemStack var6 : var10.getValue()) {
                    var12.add(enc(var6));
                }

                var1.set("returns." + var10.getKey(), var12);
            }
        }

        for (Entry var11 : this.payouts.entrySet()) {
            var1.set("payouts." + var11.getKey(), var11.getValue());
        }

        try {
            var1.save(this.file);
        } catch (IOException var7) {
            this.plugin.getLogger().warning("auction.yml: " + var7.getMessage());
        }
    }

    private long lifetimeMs() {
        return this.plugin.getConfig().getLong("auction.lot-hours", 48L) * 3600000L;
    }

    private void expire() {
        long var1 = System.currentTimeMillis();
        boolean var3 = false;
        Iterator var4 = this.lots.values().iterator();

        while (var4.hasNext()) {
            AuctionService.Lot var5 = (AuctionService.Lot)var4.next();
            if (var1 - var5.created > this.lifetimeMs()) {
                this.returns.computeIfAbsent(var5.seller, var0 -> new ArrayList<>()).add(var5.item);
                var4.remove();
                var3 = true;
            }
        }

        if (var3) {
            this.save();
        }
    }

    public void sell(Player var1, String var2) {
        PlayerData var3 = this.plugin.data().get(var1);
        if (var3 != null) {
            double var4;
            try {
                var4 = Double.parseDouble(var2);
            } catch (NumberFormatException var11) {
                Msg.send(var1, "bad-number");
                return;
            }

            if (!(var4 < 1.0) && !(var4 > 1.0E15)) {
                ItemStack var6 = var1.getInventory().getItemInMainHand();
                if (var6.getType().isAir()) {
                    var1.sendMessage(Msg.parse("<red>Возьмите предмет в руку."));
                } else {
                    int var7 = this.plugin.getConfig().getInt("auction.max-lots", 12);
                    long var8 = this.lots.values().stream().filter(var1x -> var1x.seller.equals(var1.getUniqueId())).count();
                    if (var8 >= var7) {
                        var1.sendMessage(Msg.parse("<red>У вас уже " + var7 + " лотов."));
                    } else {
                        String var10 = String.valueOf(++this.counter);
                        this.lots.put(var10, new AuctionService.Lot(var10, var1.getUniqueId(), var1.getName(), var4, System.currentTimeMillis(), var6.clone()));
                        var1.getInventory().setItemInMainHand(null);
                        this.save();
                        var1.sendMessage(Msg.parse("<green>Лот выставлен за <white>$" + Msg.money(var4) + "<green>. /auc"));
                    }
                }
            } else {
                Msg.send(var1, "bad-number");
            }
        }
    }

    private ItemStack icon(AuctionService.Lot var1, String... var2) {
        ItemStack var3 = var1.item.clone();
        ItemMeta var4 = var3.getItemMeta();
        ArrayList var5 = var4.lore() == null ? new ArrayList() : new ArrayList<>(var4.lore());
        var5.add(Items.line(" "));
        var5.add(Items.line("<gray>Продавец: <white>" + var1.sellerName));
        var5.add(Items.line("<gray>Цена: <green>$" + Msg.money(var1.price)));
        long var6 = (this.lifetimeMs() - (System.currentTimeMillis() - var1.created)) / 1000L;
        var5.add(Items.line("<gray>Осталось: <white>" + Items.fmtTime(Math.max(0L, var6))));

        for (String var11 : var2) {
            var5.add(Items.line(var11));
        }

        var4.lore(var5);
        var3.setItemMeta(var4);
        return var3;
    }

    public void open(Player var1, int var2) {
        ArrayList var3 = new ArrayList<>(this.lots.values());
        Collections.reverse(var3);
        int var4 = Math.max(1, (var3.size() + 44) / 45);
        int var5 = Math.max(0, Math.min(var2, var4 - 1));
        Gui var6 = new Gui(54, Msg.parse("Аукцион · стр. " + (var5 + 1) + "/" + var4));

        for (int var7 = 0; var7 < 45 && var5 * 45 + var7 < var3.size(); var7++) {
            AuctionService.Lot var8 = (AuctionService.Lot)var3.get(var5 * 45 + var7);
            var6.set(
                var7,
                this.icon(var8, var8.seller.equals(var1.getUniqueId()) ? "<yellow>Это ваш лот" : "<yellow>Нажмите — купить"),
                (var3x, var4x) -> this.buy(var3x, var8.id, var5)
            );
        }

        for (int var9 = 45; var9 < 54; var9++) {
            var6.set(var9, Items.filler());
        }

        if (var5 > 0) {
            var6.set(45, Items.of(Material.ARROW, "<yellow>← Назад"), (var2x, var3x) -> this.open(var2x, var5 - 1));
        }

        if (var5 < var4 - 1) {
            var6.set(53, Items.of(Material.ARROW, "<yellow>Вперёд →"), (var2x, var3x) -> this.open(var2x, var5 + 1));
        }

        var6.set(48, Items.of(Material.CHEST, "<gold>Мои лоты", "<gray>Снять лот — 10% от цены"), (var1x, var2x) -> this.mine(var1x));
        int var10 = this.returns.getOrDefault(var1.getUniqueId(), List.of()).size();
        var6.set(
            50,
            Items.of(Material.HOPPER, "<aqua>Возвраты: " + var10, "<gray>Непроданные лоты", "<yellow>Нажмите — забрать"),
            (var1x, var2x) -> this.collect(var1x)
        );
        var6.set(49, Items.of(Material.PAPER, "<white>Как продать", "<gray>Возьмите предмет в руку", "<white>/auc sell <цена>"));
        var6.open(var1);
    }

    private void mine(Player var1) {
        Gui var2 = new Gui(27, Msg.parse("Мои лоты"));
        int var3 = 0;
        double var4 = this.plugin.getConfig().getDouble("auction.remove-fee", 0.1);

        for (AuctionService.Lot var7 : new ArrayList<>(this.lots.values())) {
            if (var7.seller.equals(var1.getUniqueId()) && var3 < 26) {
                var2.set(var3++, this.icon(var7, "<yellow>Нажмите — снять за $" + Msg.money(var7.price * var4)), (var2x, var3x) -> this.remove(var2x, var7.id));
            }
        }

        var2.set(26, Items.of(Material.ARROW, "<yellow>← К аукциону"), (var1x, var2x) -> this.open(var1x, 0));
        var2.open(var1);
    }

    private void remove(Player var1, String var2) {
        AuctionService.Lot var3 = this.lots.get(var2);
        PlayerData var4 = this.plugin.data().get(var1);
        if (var3 != null && var4 != null && var3.seller.equals(var1.getUniqueId())) {
            double var5 = Math.floor(var3.price * this.plugin.getConfig().getDouble("auction.remove-fee", 0.1));
            if (var4.balance < var5) {
                Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var5)));
            } else {
                var4.balance -= var5;
                this.lots.remove(var2);
                Items.give(var1, var3.item);
                this.save();
                var1.sendMessage(Msg.parse("<yellow>Лот снят (комиссия $" + Msg.money(var5) + ")."));
                this.mine(var1);
            }
        }
    }

    private void collect(Player var1) {
        List<ItemStack> var2 = this.returns.remove(var1.getUniqueId());
        if (var2 != null && !var2.isEmpty()) {
            for (ItemStack var4 : var2) {
                Items.give(var1, var4);
            }

            this.save();
            var1.sendMessage(Msg.parse("<green>Вы забрали " + var2.size() + " предм."));
            var1.closeInventory();
        } else {
            var1.sendMessage(Msg.parse("<gray>Возвратов нет."));
        }
    }

    private void buy(Player var1, String var2, int var3) {
        AuctionService.Lot var4 = this.lots.get(var2);
        PlayerData var5 = this.plugin.data().get(var1);
        if (var4 != null && var5 != null) {
            if (var4.seller.equals(var1.getUniqueId())) {
                var1.sendMessage(Msg.parse("<red>Это ваш лот. Снять его можно в «Мои лоты»."));
            } else if (var5.balance < var4.price) {
                Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var4.price)));
            } else if (var1.getInventory().firstEmpty() < 0) {
                var1.sendMessage(Msg.parse("<red>Освободите место в инвентаре."));
            } else {
                this.lots.remove(var2);
                var5.balance = var5.balance - var4.price;
                Items.give(var1, var4.item);
                double var6 = var4.price * (1.0 - this.plugin.getConfig().getDouble("auction.tax", 0.05));
                Player var8 = Bukkit.getPlayer(var4.seller);
                PlayerData var9 = var8 == null ? null : this.plugin.data().get(var8);
                if (var9 != null) {
                    var9.balance += var6;
                    var8.sendMessage(Msg.parse("<green>Ваш лот купил " + var1.getName() + ": +$" + Msg.money(var6)));
                } else {
                    this.payouts.merge(var4.seller, var6, Double::sum);
                }

                this.save();
                var1.playSound(var1.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                var1.sendMessage(Msg.parse("<green>Покупка: $" + Msg.money(var4.price)));
                this.open(var1, var3);
            }
        } else {
            this.open(var1, var3);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent var1) {
        Double var2 = this.payouts.remove(var1.getPlayer().getUniqueId());
        if (var2 != null) {
            PlayerData var3 = this.plugin.data().get(var1.getPlayer());
            if (var3 == null) {
                this.payouts.put(var1.getPlayer().getUniqueId(), var2);
            } else {
                var3.balance = var3.balance + var2;
                this.save();
                var1.getPlayer().sendMessage(Msg.parse("<green>Пока вас не было, на аукционе продано на $" + Msg.money(var2)));
            }
        }
    }

    private static final class Lot {
        final String id;
        final UUID seller;
        final String sellerName;
        final double price;
        final long created;
        final ItemStack item;

        Lot(String var1, UUID var2, String var3, double var4, long var6, ItemStack var8) {
            this.id = var1;
            this.seller = var2;
            this.sellerName = var3;
            this.price = var4;
            this.created = var6;
            this.item = var8;
        }
    }
}
