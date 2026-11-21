package dev.prisonclassic.gypsy;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * NPC «Цыган» (спавн): скрещивание и огненные мечи.
 * Рецепт: Алмазный меч «Острота I» + 20 Звёзд = Огненный меч «Острота III, Заговор огня I».
 * Огненный меч тир II (Заговор огня II): 3000 монет + 3500 крыс, 800 зомби, 300 пыльных слизней.
 * Огненный меч не улучшается через /up.
 */
public final class GypsyService {
    private final PrisonClassic plugin;
    private final NamespacedKey key;

    public GypsyService(PrisonClassic plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "fire_sword");
    }

    private ConfigurationSection c() {
        ConfigurationSection s = this.plugin.getConfig().getConfigurationSection("gypsy");
        return s == null ? this.plugin.getConfig().createSection("gypsy") : s;
    }

    public boolean isFireSword(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(this.key, PersistentDataType.INTEGER);
    }

    public int fireTier(ItemStack it) {
        if (!this.isFireSword(it)) {
            return 0;
        }
        Integer v = it.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }

    private Map<String, Integer> kills() {
        Map<String, Integer> out = new LinkedHashMap<>();
        ConfigurationSection k = this.c().getConfigurationSection("fire-sword-2.kills");
        if (k == null) {
            out.put("rat", 3500);
            out.put("zombie", 800);
            out.put("mini_slime", 300);
        } else {
            for (String id : k.getKeys(false)) {
                out.put(id, k.getInt(id));
            }
        }
        return out;
    }

    public void open(Player p) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        int stars = this.c().getInt("fire-sword.stars", 20);
        int sharpIn = this.c().getInt("fire-sword.need-sharpness", 1);
        double cost2 = this.c().getDouble("fire-sword-2.coins", 3000.0);
        ItemStack hand = p.getInventory().getItemInMainHand();
        Gui gui = new Gui(27, Msg.parse("Цыган"));
        gui.fill(Items.filler());
        gui.set(4, Items.of(Material.EMERALD, "<gold><bold>Цыган", "<gray>«Подходи, дорогой, скрестим что угодно!»", "<gray>Возьмите предмет в руку."));

        boolean okSword = hand.getType() == Material.DIAMOND_SWORD && !this.isFireSword(hand) && hand.getEnchantmentLevel(Enchantment.SHARPNESS) >= sharpIn;
        List<String> l1 = new ArrayList<>();
        l1.add("<gray>Алмазный меч «Острота " + sharpIn + "» + <yellow>" + stars + " ★");
        l1.add("<gray>→ <gold>Огненный меч «Острота III, Заговор огня I»");
        l1.add(" ");
        l1.add((okSword ? "<green>✔" : "<red>✘") + " <gray>Алмазный меч с Остротой " + sharpIn + "+ в руке");
        long starsHave = this.plugin.specials().stars(p, d);
        l1.add((starsHave >= stars ? "<green>✔" : "<red>✘") + " <gray>Звёзды: " + starsHave + "/" + stars);
        gui.set(11, Items.of(Material.BLAZE_POWDER, "<gold>Скрещивание: Огненный меч", l1), (pl, e) -> this.craft(pl));

        int tier = this.fireTier(hand);
        List<String> l2 = new ArrayList<>();
        l2.add("<gray>Огненный меч I → <gold>Огненный меч II (Заговор огня II)");
        l2.add(" ");
        l2.add((tier == 1 ? "<green>✔" : "<red>✘") + " <gray>Огненный меч I в руке");
        l2.add((d.balance >= cost2 ? "<green>✔" : "<red>✘") + " <gray>Монеты: $" + Msg.money(cost2));
        for (Map.Entry<String, Integer> k : this.kills().entrySet()) {
            var t = this.plugin.bosses().type(k.getKey());
            int have = d.mob(k.getKey());
            l2.add((have >= k.getValue() ? "<green>✔" : "<red>✘") + " <gray>Убить: <reset>" + (t == null ? k.getKey() : t.name()) + "<gray> " + Math.min(have, k.getValue()) + "/" + k.getValue());
        }
        gui.set(15, Items.of(Material.FIRE_CHARGE, "<gold>Огненный меч: тир II", l2), (pl, e) -> this.upgrade(pl));
        gui.open(p);
    }

    private void craft(Player p) {
        PlayerData d = this.plugin.data().get(p);
        ItemStack hand = p.getInventory().getItemInMainHand();
        int stars = this.c().getInt("fire-sword.stars", 20);
        int sharpIn = this.c().getInt("fire-sword.need-sharpness", 1);
        if (hand.getType() != Material.DIAMOND_SWORD || this.isFireSword(hand) || hand.getEnchantmentLevel(Enchantment.SHARPNESS) < sharpIn) {
            p.sendMessage(Msg.parse("<red>Нужен алмазный меч с «Остротой " + sharpIn + "» в руке."));
            return;
        }
        if (!this.plugin.specials().takeStars(p, d, stars)) {
            p.sendMessage(Msg.parse("<red>Нужно " + stars + " звёзд (у вас " + this.plugin.specials().stars(p, d) + ")."));
            return;
        }
        ItemStack out = hand.clone();
        ItemMeta m = out.getItemMeta();
        m.removeEnchant(Enchantment.SHARPNESS);
        m.addEnchant(Enchantment.SHARPNESS, Math.max(3, hand.getEnchantmentLevel(Enchantment.SHARPNESS)), true);
        m.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
        m.displayName(Items.line("<gold><bold>Огненный меч"));
        m.getPersistentDataContainer().set(this.key, PersistentDataType.INTEGER, 1);
        m.setUnbreakable(true);
        out.setItemMeta(m);
        this.plugin.enchants().refreshLore(out);
        p.getInventory().setItemInMainHand(out);
        p.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.8F);
        p.sendMessage(Msg.parse("<gold>Цыган: «Держи, дорогой — Огненный меч!» <gray>(Острота III, Заговор огня I)"));
        this.open(p);
    }

    private void upgrade(Player p) {
        PlayerData d = this.plugin.data().get(p);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (this.fireTier(hand) != 1) {
            p.sendMessage(Msg.parse("<red>Нужен Огненный меч I в руке."));
            return;
        }
        double cost = this.c().getDouble("fire-sword-2.coins", 3000.0);
        if (d.balance < cost) {
            Msg.send(p, "not-enough", Msg.p("cost", Msg.money(cost)));
            return;
        }
        for (Map.Entry<String, Integer> k : this.kills().entrySet()) {
            if (d.mob(k.getKey()) < k.getValue()) {
                p.sendMessage(Msg.parse("<red>Не выполнено: убить " + k.getKey() + " " + d.mob(k.getKey()) + "/" + k.getValue()));
                return;
            }
        }
        d.balance -= cost;
        ItemStack out = hand.clone();
        ItemMeta m = out.getItemMeta();
        m.removeEnchant(Enchantment.FIRE_ASPECT);
        m.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        m.displayName(Items.line("<gold><bold>Огненный меч II"));
        m.getPersistentDataContainer().set(this.key, PersistentDataType.INTEGER, 2);
        out.setItemMeta(m);
        this.plugin.enchants().refreshLore(out);
        p.getInventory().setItemInMainHand(out);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F, 0.9F);
        p.sendMessage(Msg.parse("<gold>Огненный меч улучшен: Заговор огня II!"));
        this.open(p);
    }
}
