package dev.prisonclassic.trainer;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.enchant.EnchantService;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * TrainerService — тренер пассивных навыков (NPC «Тренер прокачки», /trainer).
 *
 * Навыки (trainer.skills, по тирам, с требованиями): Ловкость, Удача, Проклятие I–VI, Урон по мобам I–IX, Критический удар I–V.
 * Требования тира: монеты + блоки по типам (block:MATERIAL), убийства мобов (mob:id), убийства игроков (player_kills), уровень.
 * Экономические перки (trainer.perks): скидки, бустеры монет/блоков, дроп ключей, шанс звёзд, урон крита, активка.
 * Все цены умножаются на (1 − скидка тренера − динамическая фракционная скидка).
 */
public final class TrainerService implements Listener {
    public static final List<String> SKILLS = List.of("agility", "luck", "curse", "mobdmg", "crit");
    private final PrisonClassic plugin;

    public TrainerService(PrisonClassic plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection perks() {
        return this.plugin.getConfig().getConfigurationSection("trainer.perks");
    }

    private ConfigurationSection skill(String id) {
        return this.plugin.getConfig().getConfigurationSection("trainer.skills." + id);
    }

    public List<Map<?, ?>> tiers(String id) {
        return this.plugin.getConfig().getMapList("trainer.skills." + id + ".tiers");
    }

    // ===================== Скидки =====================

    public double discount(PlayerData d) {
        double v = d.perk("discount") * this.plugin.getConfig().getDouble("trainer.perks.discount.step", 0.01) + this.plugin.donate().perkDiscount(d.donate);
        return Math.min(this.plugin.getConfig().getDouble("trainer.max-discount", 0.1), v);
    }

    private double totalDiscount(PlayerData d) {
        return Math.min(0.6, this.discount(d) + this.plugin.factionBalance().discount(d));
    }

    public double cost(String perk, int level, PlayerData d) {
        ConfigurationSection c = this.perks().getConfigurationSection(perk);
        double base = c.getDouble("base-cost", 10000.0);
        double growth = c.getDouble("growth", 1.4);
        return Math.floor(base * Math.pow(growth, level) * (1.0 - this.totalDiscount(d)));
    }

    public double skillCost(String skill, int nextTierIndex, PlayerData d) {
        List<Map<?, ?>> t = this.tiers(skill);
        if (nextTierIndex < 0 || nextTierIndex >= t.size()) {
            return 0.0;
        }
        Object c = t.get(nextTierIndex).get("cost");
        double base = c instanceof Number n ? n.doubleValue() : 0.0;
        return Math.floor(base * (1.0 - this.totalDiscount(d)));
    }

    // ===================== Эффекты навыков =====================

    /** Суммарное значение навыка на текущем тире (поле value тира — накопительное). */
    public double skillValue(PlayerData d, String skill) {
        int tier = d.perk(skill);
        if (tier <= 0) {
            return 0.0;
        }
        List<Map<?, ?>> t = this.tiers(skill);
        if (t.isEmpty()) {
            return 0.0;
        }
        Object v = t.get(Math.min(tier, t.size()) - 1).get("value");
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** +10% к шансу срабатывания всех навыков тренера (сокровище «Паучья эссенция»). */
    public double procBonus(PlayerData d) {
        return System.currentTimeMillis() < d.critBonusUntil ? this.plugin.getConfig().getDouble("treasures.spider-essence-bonus", 0.10) : 0.0;
    }

    public double agilityBonus(PlayerData d) {
        return this.skillValue(d, "agility");
    }

    public double luckChance(PlayerData d) {
        double v = this.skillValue(d, "luck");
        if (System.currentTimeMillis() < d.luckUntil) {
            v += this.plugin.getConfig().getDouble("cells.shower-luck-bonus", 0.05);
        }
        return v + (v > 0.0 ? this.procBonus(d) : 0.0);
    }

    public double curseChance(PlayerData d) {
        double v = this.skillValue(d, "curse");
        return v > 0.0 ? v + this.procBonus(d) : 0.0;
    }

    public double critChance(PlayerData d) {
        double v = this.skillValue(d, "crit");
        return v > 0.0 ? v + this.procBonus(d) : 0.0;
    }

    public double mobDamage(PlayerData d) {
        return this.skillValue(d, "mobdmg");
    }

    // ===================== Требования =====================

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private String reqLabel(String key) {
        if (key.equals("player_kills")) {
            return "Убийств игроков";
        } else if (key.equals("level")) {
            return "Уровень игрока";
        } else if (key.startsWith("mob:")) {
            String id = key.substring(4);
            var t = this.plugin.bosses().type(id);
            return "Убить: " + (t == null ? id : t.name()) + "<gray>";
        } else if (key.startsWith("block:")) {
            Material m = Material.matchMaterial(key.substring(6));
            return "Вскопать: " + (m == null ? key.substring(6) : "<lang:" + m.translationKey() + ">");
        }
        return key;
    }

    private long have(PlayerData d, String key) {
        if (key.equals("player_kills")) {
            return d.kills;
        } else if (key.equals("level")) {
            return d.level;
        } else if (key.startsWith("mob:")) {
            return d.mob(key.substring(4));
        } else if (key.startsWith("block:")) {
            return d.mined(key.substring(6));
        }
        return 0L;
    }

    /** Список требований тира: строка → выполнено. */
    public List<Map.Entry<String, Boolean>> requirements(String skill, int tierIndex, PlayerData d) {
        List<Map.Entry<String, Boolean>> out = new ArrayList<>();
        List<Map<?, ?>> t = this.tiers(skill);
        if (tierIndex < 0 || tierIndex >= t.size()) {
            return out;
        }
        double cost = this.skillCost(skill, tierIndex, d);
        out.add(Map.entry("Монеты: $" + Msg.money(cost) + " <dark_gray>(у вас $" + Msg.money(d.balance) + ")", d.balance >= cost));
        if (t.get(tierIndex).get("req") instanceof Map<?, ?> req) {
            for (Map.Entry<?, ?> e : req.entrySet()) {
                String key = String.valueOf(e.getKey());
                long need = num(e.getValue());
                long have = this.have(d, key);
                out.add(Map.entry(this.reqLabel(key) + " " + Math.min(have, need) + "/" + need, have >= need));
            }
        }
        return out;
    }

    // ===================== GUI =====================

    public void open(Player p) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Тренер прокачки"));
        ItemStack filler = Items.filler();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(4, Items.of(Material.BOOK, "<gold><bold>Навыки персонажа",
            "<gray>Тиры открываются по порядку.",
            "<gray>Требования: монеты, блоки по типам, убийства.",
            "<gray>Фракционная скидка: <green>" + Math.round(this.plugin.factionBalance().discount(d) * 100) + "%",
            "<gray>Скидка тренера/доната: <green>" + Math.round(this.discount(d) * 100) + "%"));
        int[] skillSlots = {20, 21, 22, 23, 24};
        for (int i = 0; i < SKILLS.size(); i++) {
            String id = SKILLS.get(i);
            ConfigurationSection c = this.skill(id);
            if (c == null) {
                continue;
            }
            inv.setItem(skillSlots[i], this.skillIcon(id, c, d));
            h.map.put(skillSlots[i], "skill:" + id);
        }
        int slot = 37;
        if (this.perks() != null) {
            for (String id : this.perks().getKeys(false)) {
                if (slot > 43) {
                    break;
                }
                ConfigurationSection c = this.perks().getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                inv.setItem(slot, this.perkIcon(id, c, d));
                h.map.put(slot, "perk:" + id);
                slot++;
            }
        }
        inv.setItem(31, Items.of(Material.EMERALD, "<green>Экономические перки <gray>(ряд ниже)", "<gray>Скидки, бустеры, ключи, звёзды, активка"));
        p.openInventory(inv);
    }

    private ItemStack skillIcon(String id, ConfigurationSection c, PlayerData d) {
        int tier = d.perk(id);
        List<Map<?, ?>> t = this.tiers(id);
        Material mat = Material.matchMaterial(c.getString("material", "PAPER"));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + c.getString("desc", ""));
        lore.add("<yellow>Тир: <white>" + EnchantService.roman(tier) + " / " + EnchantService.roman(t.size()));
        lore.add("<gray>Сейчас: <white>" + this.format(id, this.skillValue(d, id)));
        if (tier >= t.size()) {
            lore.add("<green>Максимальный тир!");
        } else {
            Object v = t.get(tier).get("value");
            lore.add("<gray>Следующий тир: <aqua>" + this.format(id, v instanceof Number n ? n.doubleValue() : 0.0));
            lore.add(" ");
            boolean all = true;
            for (Map.Entry<String, Boolean> e : this.requirements(id, tier, d)) {
                lore.add((e.getValue() ? "<green>✔ <gray>" : "<red>✘ <gray>") + e.getKey());
                all &= e.getValue();
            }
            lore.add(" ");
            lore.add(all ? "<green>Нажмите, чтобы изучить" : "<red>Не все условия выполнены");
        }
        return Items.of(mat == null ? Material.PAPER : mat, "<gold>" + c.getString("name", id), lore);
    }

    private String format(String id, double v) {
        return switch (id) {
            case "agility" -> "+" + Math.round(v * 100) + "% к скорости бега";
            case "luck" -> Math.round(v * 1000) / 10.0 + "% шанс x2 дропа редких руд";
            case "curse" -> Math.round(v * 100) + "% шанс Проклятия при PVP-ударе";
            case "mobdmg" -> "+" + (int)v + " чистого урона по мобам и боссам";
            case "crit" -> Math.round(v * 100) + "% шанс крита по игроку";
            default -> String.valueOf(v);
        };
    }

    private ItemStack perkIcon(String id, ConfigurationSection c, PlayerData d) {
        int lvl = d.perk(id);
        int max = c.getInt("max", 10);
        Material mat = Material.matchMaterial(c.getString("material", "PAPER"));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + c.getString("desc", ""));
        lore.add("<yellow>Уровень: " + lvl + "/" + max);
        int minLevel = c.getInt("min-level", 1);
        if (d.level < minLevel) {
            lore.add("<red>Нужен уровень игрока: " + minLevel);
        } else if (lvl >= max) {
            lore.add("<green>Максимум!");
        } else {
            lore.add("<green>Улучшить: $" + Msg.money(this.cost(id, lvl, d)));
        }
        return Items.of(mat == null ? Material.PAPER : mat, "<aqua>" + c.getString("name", id), lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        PlayerData d = this.plugin.data().get(p);
        String key = h.map.get(e.getRawSlot());
        if (d == null || key == null) {
            return;
        }
        if (key.startsWith("skill:")) {
            this.learn(p, d, key.substring(6));
        } else {
            this.buyPerk(p, d, key.substring(5));
        }
    }

    public void learn(Player p, PlayerData d, String skill) {
        int tier = d.perk(skill);
        List<Map<?, ?>> t = this.tiers(skill);
        if (tier >= t.size()) {
            p.sendMessage(Msg.parse("<green>Навык уже на максимальном тире."));
            return;
        }
        for (Map.Entry<String, Boolean> r : this.requirements(skill, tier, d)) {
            if (!r.getValue()) {
                p.sendMessage(Msg.parse("<red>Не выполнено: <gray>" + r.getKey()));
                return;
            }
        }
        double cost = this.skillCost(skill, tier, d);
        d.balance -= cost;
        d.perks.put(skill, tier + 1);
        if (skill.equals("agility")) {
            this.plugin.levels().applyBonuses(p, d);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
        p.sendMessage(Msg.parse("<green>Изучено: <gold>" + this.skill(skill).getString("name", skill) + " " + EnchantService.roman(tier + 1)));
        this.open(p);
    }

    private void buyPerk(Player p, PlayerData d, String perk) {
        ConfigurationSection c = this.perks().getConfigurationSection(perk);
        int lvl = d.perk(perk);
        if (c == null || lvl >= c.getInt("max", 10)) {
            return;
        }
        if (d.level < c.getInt("min-level", 1)) {
            p.sendMessage(Msg.parse("<red>Нужен уровень " + c.getInt("min-level", 1)));
            return;
        }
        double cost = this.cost(perk, lvl, d);
        if (d.balance < cost) {
            Msg.send(p, "not-enough", Msg.p("cost", Msg.money(cost)));
            return;
        }
        d.balance -= cost;
        d.perks.put(perk, lvl + 1);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.4F);
        this.open(p);
    }

    // ===================== Активка (перк) =====================

    public void activate(Player p, PlayerData d) {
        int lvl = d.perk("active");
        if (lvl <= 0) {
            p.sendMessage(Msg.parse("<red>Активка не куплена у тренера."));
            return;
        }
        long now = System.currentTimeMillis();
        if (now < d.activeCooldown) {
            p.sendMessage(Msg.parse("<red>Перезарядка: " + (d.activeCooldown - now) / 1000L + " сек."));
            return;
        }
        double radius = this.plugin.getConfig().getDouble("trainer.active.radius", 6.0);
        double cd = this.plugin.getConfig().getDouble("trainer.active.cooldown", 60.0) - (lvl - 1) * 10;
        d.activeCooldown = now + (long)(Math.max(10.0, cd) * 1000.0);
        double stolen = 0.0;
        for (Player o : p.getWorld().getPlayers()) {
            if (o.equals(p) || o.getLocation().distanceSquared(p.getLocation()) > radius * radius || !this.plugin.canPvp(p, o)) {
                continue;
            }
            double take = Math.min(2.0, Math.max(0.0, o.getHealth() - 1.0));
            o.setHealth(o.getHealth() - take);
            stolen += take;
            o.sendMessage(Msg.parse("<red>" + p.getName() + " украл у вас сердце!"));
        }
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + stolen));
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4F, 1.8F);
        p.sendMessage(Msg.parse("<dark_red>Активка! <gray>Украдено: " + (int)(stolen / 2.0) + " сердец."));
    }

    private static final class Holder implements InventoryHolder {
        final Map<Integer, String> map = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
