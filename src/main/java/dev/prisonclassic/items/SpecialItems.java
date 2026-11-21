package dev.prisonclassic.items;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.util.Items;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Реестр особых предметов режима (PDC "special"):
 * пропуска (подвал, подземелье, боевой пропуск Злого Тыкова), квестовый «Мозг», «Кишки» из зомби подвала.
 * Трофеи боссов (Сточный порошок, Паучий глаз, Снежок, Пламенный эфес, Мокрая губка, Слеза гаста, Тыква)
 * выдаются через EnchantService.unique(...) и меняются на звёзды (/exchange unique).
 */
public final class SpecialItems {
    public record Def(String id, Material material, String name, List<String> lore, double sellPrice) {
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private final NamespacedKey key;
    private final NamespacedKey priceKey;

    static {
        def("basement_pass", Material.PAPER, "<dark_gray><bold>Пропуск в подвал", 0.0,
            "<gray>Вход в Тюремный подвал (с 6 уровня).", "<gray>Используется при входе: /warp → Подвал.");
        def("dungeon_pass", Material.NAME_TAG, "<dark_red><bold>Пропуск в подземелье", 0.0,
            "<gray>Вампирские катакомбы (с 17 уровня).", "<gray>Группа строго из 3 игроков. /dungeon");
        def("battle_pass", Material.MOJANG_BANNER_PATTERN, "<gold><bold>Боевой пропуск", 0.0,
            "<gray>Вход на арену Злого Тыкова (шахта 20).", "<gray>Тратится при входе на арену.");
        def("brain", Material.BRAIN_CORAL, "<light_purple>Мозг", 0.0,
            "<gray>Квестовый предмет с Королевского зомби.", "<gray>Сдайте Квестовику: 5 шт. → ключ и монеты.");
        def("star", Material.NETHER_STAR, "<yellow><bold>★ Звезда", 0.0,
            "<gray>Донат-валюта режима. Выпадает с боссов,", "<gray>из сита и сундуков.",
            "<gray>Тратится у Цыгана, Донат-NPC и в /exchange.");
        def("guts", Material.ROTTEN_FLESH, "<red>Кишки", 4.0,
            "<gray>Выпадают с зомби в подвале.", "<gray>Продаются торговцу подвала или /sell.");
    }

    private static void def(String id, Material m, String name, double price, String... lore) {
        DEFS.put(id, new Def(id, m, name, List.of(lore), price));
    }

    public SpecialItems(PrisonClassic plugin) {
        this.key = new NamespacedKey(plugin, "special");
        this.priceKey = new NamespacedKey(plugin, "sell_price");
    }

    public Def def(String id) {
        return DEFS.get(id);
    }

    public ItemStack create(String id, int amount) {
        Def d = DEFS.get(id);
        if (d == null) {
            return null;
        }
        ItemStack it = Items.of(d.material(), d.name(), d.lore());
        it.setAmount(Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, id);
        if (d.sellPrice() > 0.0) {
            m.getPersistentDataContainer().set(this.priceKey, PersistentDataType.DOUBLE, d.sellPrice());
        }
        it.setItemMeta(m);
        return it;
    }

    public String id(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
    }

    public boolean is(ItemStack it, String id) {
        return id.equals(this.id(it));
    }

    public int count(Player p, String id) {
        return Items.count(p, it -> this.is(it, id));
    }

    public boolean take(Player p, String id, int n) {
        return Items.take(p, it -> this.is(it, id), n);
    }

    public void give(Player p, String id, int n) {
        ItemStack it = this.create(id, n);
        if (it != null) {
            Items.give(p, it);
        }
    }

    // ===================== Звёзды как предмет =====================

    /** Выдать звёзды предметом в инвентарь (лишнее падает на землю). */
    public void giveStars(Player p, long n) {
        while (n > 0) {
            int part = (int)Math.min(64L, n);
            this.give(p, "star", part);
            n -= part;
        }
    }

    /** Звёзды игрока: предметы в инвентаре + старый баланс (данные до версии 4.0.1). */
    public long stars(Player p, dev.prisonclassic.data.PlayerData d) {
        return this.count(p, "star") + (d == null ? 0L : Math.max(0L, d.stars));
    }

    /** Списать звёзды: сначала старый баланс, затем предметы из инвентаря. */
    public boolean takeStars(Player p, dev.prisonclassic.data.PlayerData d, long n) {
        if (this.stars(p, d) < n) {
            return false;
        }
        long fromBalance = Math.min(n, Math.max(0L, d.stars));
        d.stars -= fromBalance;
        long rest = n - fromBalance;
        return rest <= 0 || this.take(p, "star", (int)rest);
    }

    public java.util.Set<String> ids() {
        return DEFS.keySet();
    }
}
