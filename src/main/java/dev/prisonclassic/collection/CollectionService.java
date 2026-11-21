package dev.prisonclassic.collection;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.cell.Cell;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Bukkit;

/**
 * Коллекции «Наследие Предков»: 6 веток знаний × 5 уникальных осколков = 30 осколков.
 * Осколки выпадают в строго заданных зонах (шахта / локация + блок или моб). Сдаются на Полку коллекций
 * (4-й уровень личной камеры). Полная ветка даёт перманентный бафф персонажу.
 */
public final class CollectionService implements Listener {
    public record Branch(String id, String name, String color, String buff) {
    }

    /** zone: номер шахты, "spawn", "base", "6" (подвал), "village"; block — конкретный блок или null; mob — id миньона или null. */
    public record Shard(String id, String name, String branch, Material material, String zone, Material block, String mob) {
    }

    public static final Map<String, Branch> BRANCHES = new LinkedHashMap<>();
    public static final Map<String, Shard> SHARDS = new LinkedHashMap<>();

    static {
        branch("fire", "Огонь", "<red>", "Урон от огня и лавы снижен на 50%");
        branch("sky", "Небо", "<aqua>", "Урон от падения снижен на 50%");
        branch("witcher", "Ведьмак", "<dark_green>", "+10% урона по мобам и боссам");
        branch("oldbeliever", "Старовер", "<gold>", "Удвоение времени баффа церковной службы");
        branch("knight", "Рыцарство", "<gray>", "-5% входящего урона от игроков");
        branch("poison", "Яды", "<dark_purple>", "Иммунитет к ядам");

        // Огонь
        shard("phoenix_feather", "Перо феникса", "fire", Material.FEATHER, "5", null, null);
        shard("basalt", "Базальт", "fire", Material.BASALT, "7", Material.BASALT, null);
        shard("cream", "Крем", "fire", Material.MAGMA_CREAM, "7", null, "magma_mini");
        shard("nugget", "Самородок", "fire", Material.GOLD_NUGGET, "7", Material.NETHER_GOLD_ORE, null);
        shard("amaterasu", "Пламя Аматерасу", "fire", Material.BLAZE_POWDER, "8", null, null);
        // Небо
        shard("rabbit_foot", "Лапка кролика", "sky", Material.RABBIT_FOOT, "spawn", null, "rabbit");
        shard("griffin_feather", "Перо грифона", "sky", Material.PHANTOM_MEMBRANE, "10", null, null);
        shard("golden_bee", "Золотая пчела", "sky", Material.HONEYCOMB, "6", Material.GOLD_BLOCK, null);
        shard("horseshoe", "Подкова", "sky", Material.IRON_NUGGET, "1", Material.GRAVEL, null);
        shard("coin", "Монета", "sky", Material.SUNFLOWER, "11", Material.EMERALD_BLOCK, null);
        // Ведьмак
        shard("rat_tail", "Хвост крысы", "witcher", Material.STRING, "base", null, "rat");
        shard("herbs", "Травы", "witcher", Material.FERN, "3", null, "rabbit");
        shard("contract", "Контракт", "witcher", Material.PAPER, "village", null, "pigzombie");
        shard("cerebellum", "Мозжечок", "witcher", Material.PINK_DYE, "6", null, "basement_zombie");
        shard("minted_coin", "Чеканная монета", "witcher", Material.GOLD_INGOT, "11", null, "mini_slime");
        // Старовер
        shard("cross", "Крест", "oldbeliever", Material.END_ROD, "15", null, null);
        shard("censer", "Кадило", "oldbeliever", Material.LANTERN, "25", null, null);
        shard("rosary", "Чётки", "oldbeliever", Material.CHAIN, "21", null, null);
        shard("relics", "Мощи", "oldbeliever", Material.BONE, "10", Material.BONE_BLOCK, null);
        shard("fresco", "Фреска", "oldbeliever", Material.PAINTING, "9", null, null);
        // Рыцарство
        shard("dragon_scale", "Чешуйка дракона", "knight", Material.TURTLE_SCUTE, "13", null, null);
        shard("plate", "Пластина", "knight", Material.IRON_INGOT, "17", null, null);
        shard("harness", "Сбруя", "knight", Material.SADDLE, "19", null, null);
        shard("spurs", "Шпоры", "knight", Material.GOLDEN_HORSE_ARMOR, "23", null, null);
        shard("link", "Звено", "knight", Material.IRON_BARS, "21", Material.OBSIDIAN, null);
        // Яды
        shard("cobra_venom", "Яд кобры", "poison", Material.SPIDER_EYE, "2", Material.MELON, null);
        shard("miasma", "Миазмы", "poison", Material.GREEN_DYE, "8", null, null);
        shard("amber", "Янтарь", "poison", Material.ORANGE_DYE, "1", Material.GRAVEL, null);
        shard("karakurt", "Каракурт", "poison", Material.FERMENTED_SPIDER_EYE, "10", Material.COBWEB, null);
        shard("thallium", "Крупица таллия", "poison", Material.GLOWSTONE_DUST, "3", null, null);
    }

    private static void branch(String id, String name, String color, String buff) {
        BRANCHES.put(id, new Branch(id, name, color, buff));
    }

    private static void shard(String id, String name, String branch, Material m, String zone, Material block, String mob) {
        SHARDS.put(id, new Shard(id, name, branch, m, zone, block, mob));
    }

    private final PrisonClassic plugin;
    private final NamespacedKey key;

    public CollectionService(PrisonClassic plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "shard");
    }

    public ItemStack item(String id, int amount) {
        Shard s = SHARDS.get(id);
        if (s == null) {
            return null;
        }
        Branch b = BRANCHES.get(s.branch());
        ItemStack it = Items.of(s.material(), b.color() + "<bold>" + s.name(),
            "<gray>Осколок коллекции «" + b.name() + "»",
            "<gray>Наследие Предков: сдайте на Полку коллекций",
            "<dark_gray>(4-й уровень личной камеры, /collection)");
        it.setAmount(Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, id);
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    public String idOf(ItemStack it) {
        return it != null && it.hasItemMeta() ? it.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING) : null;
    }

    public boolean has(PlayerData d, String branch) {
        return d != null && d.branchesDone.contains(branch);
    }

    // ===================== Выпадение =====================

    private static String zoneOfMine(Mine mine) {
        if (mine.base) {
            return "base";
        }
        return mine.name.startsWith("6") ? "6" : mine.name;
    }

    /** Зона по координатам: спавн, база, подвал, деревня или ближайшая шахта. */
    public String zoneOf(Location l) {
        Location spawn = this.plugin.spawn();
        if (spawn != null && spawn.getWorld() == l.getWorld() && spawn.distanceSquared(l) < 70 * 70) {
            return "spawn";
        }
        for (String k : List.of("lufonia", "npc.awaken")) {
            Location v = this.plugin.locations().get(k);
            if (v != null && v.getWorld() == l.getWorld() && v.distanceSquared(l) < 75 * 75) {
                return "village";
            }
        }
        Mine best = null;
        double bd = 100.0 * 100.0;
        for (Mine m : this.plugin.mines().all()) {
            if (m.privateSlot || m.spawn == null || m.spawn.getWorld() != l.getWorld()) {
                continue;
            }
            double dd = m.spawn.distanceSquared(l);
            if (dd < bd) {
                bd = dd;
                best = m;
            }
        }
        return best == null ? "" : zoneOfMine(best);
    }

    public void onMine(Player p, PlayerData d, Mine mine, Material block) {
        String zone = zoneOfMine(mine);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double any = this.plugin.getConfig().getDouble("collections.chance-any-block", 1.0 / 6000.0);
        double specific = this.plugin.getConfig().getDouble("collections.chance-block", 1.0 / 1500.0);
        for (Shard s : SHARDS.values()) {
            if (s.mob() != null || !s.zone().equals(zone)) {
                continue;
            }
            if (s.block() != null && s.block() != block) {
                continue;
            }
            if (r.nextDouble() < (s.block() == null ? any : specific)) {
                this.drop(p, s);
            }
        }
    }

    public void onMobKill(Player p, PlayerData d, String mobId, Location where) {
        String zone = this.zoneOf(where);
        double chance = this.plugin.getConfig().getDouble("collections.chance-mob", 1.0 / 150.0);
        for (Shard s : SHARDS.values()) {
            if (mobId.equals(s.mob()) && s.zone().equals(zone) && ThreadLocalRandom.current().nextDouble() < chance) {
                this.drop(p, s);
            }
        }
    }

    private void drop(Player p, Shard s) {
        Items.give(p, this.item(s.id(), 1));
        Branch b = BRANCHES.get(s.branch());
        p.sendMessage(Msg.parse("<gold>✦ Осколок коллекции: " + b.color() + s.name() + " <gray>(ветка «" + b.name() + "»)"));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
    }

    // ===================== Полка коллекций =====================

    public void open(Player p) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        Gui gui = new Gui(54, Msg.parse("Наследие Предков"));
        gui.fill(Items.filler());
        int row = 0;
        for (Branch b : BRANCHES.values()) {
            int base = row * 9;
            boolean done = d.branchesDone.contains(b.id());
            gui.set(base, Items.of(done ? Material.ENCHANTED_BOOK : Material.BOOK, b.color() + "<bold>" + b.name(),
                "<gray>Бафф: <white>" + b.buff(), done ? "<green>Ветка собрана — бафф активен" : "<yellow>Соберите 5 осколков"));
            int col = 2;
            for (Shard s : SHARDS.values()) {
                if (!s.branch().equals(b.id())) {
                    continue;
                }
                boolean have = d.collection.contains(s.id());
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Где искать: <white>" + this.where(s));
                lore.add(have ? "<green>На полке" : "<red>Не сдан");
                gui.set(base + col, Items.of(have ? s.material() : Material.GRAY_DYE, (have ? b.color() : "<dark_gray>") + s.name(), lore));
                col++;
            }
            row++;
        }
        Cell cell = this.plugin.cells().ownedBy(p.getUniqueId());
        int need = this.plugin.getConfig().getInt("cells.tiers.shelf", 4);
        boolean can = cell != null && cell.level >= need;
        gui.set(53, Items.of(can ? Material.LECTERN : Material.BARRIER, can ? "<green><bold>Сдать осколки" : "<red>Полка недоступна",
            can ? "<gray>Положить на полку все осколки из инвентаря" : "<gray>Нужен " + need + "-й уровень личной камеры (/cell upgrade)"),
            (pl, ev) -> {
                if (can) {
                    this.deposit(pl);
                }
            });
        gui.open(p);
    }

    private String where(Shard s) {
        String zone = switch (s.zone()) {
            case "spawn" -> "спавн";
            case "base" -> "База (/base)";
            case "village" -> "Деревня Люфония";
            case "6" -> "Тюремный подвал (шахта 6)";
            default -> "шахта " + s.zone();
        };
        if (s.mob() != null) {
            var t = this.plugin.bosses().type(s.mob());
            return zone + ", убийство: " + (t == null ? s.mob() : t.name());
        }
        if (s.block() != null) {
            return zone + ", блок: <lang:" + s.block().translationKey() + ">";
        }
        return zone + ", любой блок";
    }

    public void deposit(Player p) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        int added = 0;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length; i++) {
            String id = this.idOf(inv[i]);
            if (id == null || d.collection.contains(id) || !SHARDS.containsKey(id)) {
                continue;
            }
            d.collection.add(id);
            added++;
            inv[i].setAmount(inv[i].getAmount() - 1);
            if (inv[i].getAmount() <= 0) {
                inv[i] = null;
            }
        }
        p.getInventory().setStorageContents(inv);
        if (added == 0) {
            p.sendMessage(Msg.parse("<red>Нет новых осколков для полки."));
            return;
        }
        p.sendMessage(Msg.parse("<green>На полку положено осколков: " + added));
        p.playSound(p.getLocation(), Sound.BLOCK_CHISELED_BOOKSHELF_INSERT, 1.0F, 1.0F);
        for (Branch b : BRANCHES.values()) {
            if (d.branchesDone.contains(b.id())) {
                continue;
            }
            boolean full = SHARDS.values().stream().filter(s -> s.branch().equals(b.id())).allMatch(s -> d.collection.contains(s.id()));
            if (full) {
                d.branchesDone.add(b.id());
                Bukkit.broadcast(Msg.parse("<gold>✦ " + p.getName() + " собрал(а) ветку коллекции " + b.color() + "«" + b.name() + "»<gold>! <gray>(" + b.buff() + ")"));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }
        }
        this.open(p);
    }

    // ===================== Перманентные баффы веток =====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        switch (e.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                if (this.has(d, "fire")) {
                    e.setDamage(e.getDamage() * 0.5);
                }
            }
            case FALL -> {
                if (this.has(d, "sky")) {
                    e.setDamage(e.getDamage() * 0.5);
                }
            }
            case POISON -> {
                if (this.has(d, "poison")) {
                    e.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent e) {
        if (e.getEntity() instanceof Player p && e.getNewEffect() != null && e.getNewEffect().getType().equals(PotionEffectType.POISON)) {
            if (this.has(this.plugin.data().get(p), "poison")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player attacker = dev.prisonclassic.util.Players.attacker(e.getDamager());
        if (attacker != null && e.getEntity() instanceof LivingEntity t && !(t instanceof Player)) {
            if (this.has(this.plugin.data().get(attacker), "witcher")) {
                e.setDamage(e.getDamage() * 1.10);
            }
        }
        if (attacker != null && e.getEntity() instanceof Player v && !v.equals(attacker)) {
            if (this.has(this.plugin.data().get(v), "knight")) {
                e.setDamage(e.getDamage() * 0.95);
            }
        }
    }
}
