package dev.prisonclassic.boss;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.enchant.Ench;
import dev.prisonclassic.faction.Faction;
import dev.prisonclassic.quest.QuestType;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * BossManager — циклический спавн мировых боссов и мобов-миньонов по точкам (boss-points.yml).
 *
 * Реестр боссов — config.yml → bosses.types (HP, урон, награда, КД респавна [min..max], звёзды [min..max], спец-лут).
 * HP выше 1024 (лимит атрибута) реализовано масштабированием: у сущности 1024 HP, входящий урон × 1024/HP.
 *
 * Распределение награды (BossManager$Drop / distribute):
 *  - пул монет = награда × бонус фракции; топ-3 по урону делят top-pool (70%) пропорционально урону, остальные — 30%;
 *  - топ-1: Легендарный ключ, топ-2/3: Древний ключ (ДК); топ-3 бросают шанс на спец-трофей и получают звёзды по диапазону;
 *  - остальные участники, преодолевшие порог урона (min-share, 5%), получают утешительный дроп руд и монет;
 *  - ниже порога — ничего.
 */
public final class BossManager {
    private final PrisonClassic plugin;
    private final NamespacedKey bossKey;
    private final File file;
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Map<String, Point> points = new LinkedHashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();

    public BossManager(PrisonClassic plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "boss");
        this.file = new File(plugin.getDataFolder(), "boss-points.yml");
        this.reloadTypes();
        this.loadPoints();
    }

    private static int num(Map<?, ?> m, String k, int def) {
        return m.get(k) instanceof Number n ? n.intValue() : def;
    }

    public void reloadTypes() {
        this.types.clear();
        ConfigurationSection all = this.plugin.getConfig().getConfigurationSection("bosses.types");
        if (all == null) {
            return;
        }
        for (String id : all.getKeys(false)) {
            ConfigurationSection c = all.getConfigurationSection(id);
            if (c == null) {
                continue;
            }
            try {
                List<Drop> drops = new ArrayList<>();
                for (Map<?, ?> m : c.getMapList("drops")) {
                    drops.add(new Drop(String.valueOf(m.get("item")), m.get("chance") instanceof Number n ? n.doubleValue() : 1.0, num(m, "min", 1), num(m, "max", 1)));
                }
                Material special = Material.matchMaterial(c.getString("special.material", "NETHER_STAR"));
                int respawn = c.getInt("respawn-seconds", 300);
                this.types.put(id.toLowerCase(), new Type(
                    id.toLowerCase(),
                    c.getString("name", id),
                    EntityType.valueOf(c.getString("entity", "ZOMBIE").toUpperCase()),
                    Math.max(1.0, c.getDouble("health", 200.0)),
                    c.getDouble("damage", 6.0),
                    c.getDouble("reward", 1000.0),
                    c.getInt("min-level", 1),
                    respawn,
                    Math.max(respawn, c.getInt("respawn-max-seconds", respawn)),
                    c.getBoolean("minion", false),
                    c.getInt("stars.min", 0),
                    c.getInt("stars.max", c.getInt("stars.min", 0)),
                    c.getString("special.name", ""),
                    special == null ? Material.NETHER_STAR : special,
                    c.getDouble("special.chance", 0.0),
                    c.getInt("special.min", 1),
                    c.getInt("special.max", c.getInt("special.min", 1)),
                    c.getInt("special.stars", 0),
                    c.getString("special.item", ""),
                    drops,
                    c.getString("hit-effect", ""),
                    c.getInt("size", 0),
                    c.getDouble("scale", 0.0),
                    c.getString("helmet", "IRON_HELMET"),
                    c.getString("weapon", "")
                ));
            } catch (IllegalArgumentException e) {
                this.plugin.getLogger().warning("bosses.types." + id + ": неизвестный entity");
            }
        }
    }

    private void loadPoints() {
        this.points.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection s = y.getConfigurationSection("points");
        if (s == null) {
            return;
        }
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            Location l = c == null ? null : c.getLocation("location");
            if (l != null) {
                this.points.put(id, new Point(id, c.getString("type", ""), l));
            }
        }
    }

    private void savePoints() {
        YamlConfiguration y = new YamlConfiguration();
        for (Point p : this.points.values()) {
            y.set("points." + p.id + ".type", p.type);
            y.set("points." + p.id + ".location", p.loc);
        }
        try {
            y.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Не удалось сохранить boss-points.yml: " + e.getMessage());
        }
    }

    public Collection<Type> types() {
        return this.types.values();
    }

    public Type type(String id) {
        return id == null ? null : this.types.get(id.toLowerCase());
    }

    public String addPoint(String type, Location loc) {
        int i = 1;
        while (this.points.containsKey("b" + i)) {
            i++;
        }
        String id = "b" + i;
        this.points.put(id, new Point(id, type.toLowerCase(), loc.clone()));
        this.savePoints();
        return id;
    }

    public boolean removePoint(String id) {
        Point p = this.points.remove(id);
        if (p == null) {
            return false;
        }
        if (p.entity != null) {
            Entity e = Bukkit.getEntity(p.entity);
            Active a = this.active.get(p.entity);
            if (a != null) {
                this.dispose(p.entity, a);
            }
            if (e != null) {
                e.remove();
            }
        }
        this.savePoints();
        return true;
    }

    public void clearPoints() {
        for (String id : new ArrayList<>(this.points.keySet())) {
            this.removePoint(id);
        }
    }

    public List<String> describePoints() {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Point p : this.points.values()) {
            String state = p.entity != null ? "жив" : (p.nextSpawn > now ? "респавн через " + Items.fmtTime((p.nextSpawn - now) / 1000L) : "ожидает");
            out.add(p.id + " — " + p.type + " @ " + p.loc.getWorld().getName() + " " + p.loc.getBlockX() + " " + p.loc.getBlockY() + " " + p.loc.getBlockZ() + " (" + state + ")");
        }
        return out;
    }

    /** Точки спавна указанного типа (для арены Злого Тыкова). */
    public List<Location> pointsOf(String typeId) {
        List<Location> out = new ArrayList<>();
        for (Point p : this.points.values()) {
            if (p.type.equalsIgnoreCase(typeId)) {
                out.add(p.loc);
            }
        }
        return out;
    }

    /** Живой босс указанного типа или null. */
    public LivingEntity alive(String typeId) {
        for (Entry<UUID, Active> e : this.active.entrySet()) {
            if (e.getValue().type.id().equalsIgnoreCase(typeId) && Bukkit.getEntity(e.getKey()) instanceof LivingEntity le && le.isValid()) {
                return le;
            }
        }
        return null;
    }

    /** Ближайшее время респавна (мс) босса типа, 0 — жив/ожидает. */
    public long nextSpawn(String typeId) {
        long best = Long.MAX_VALUE;
        for (Point p : this.points.values()) {
            if (p.type.equalsIgnoreCase(typeId)) {
                if (p.entity != null) {
                    return 0L;
                }
                best = Math.min(best, p.nextSpawn);
            }
        }
        return best == Long.MAX_VALUE ? -1L : best;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 100L, 10L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Point p : this.points.values()) {
            if (p.entity == null) {
                Type t = this.types.get(p.type);
                World w = p.loc.getWorld();
                if (t != null && w != null && now >= p.nextSpawn && w.isChunkLoaded(p.loc.getBlockX() >> 4, p.loc.getBlockZ() >> 4)) {
                    this.spawn(p, t);
                }
            } else {
                Entity e = Bukkit.getEntity(p.entity);
                Active a = this.active.get(p.entity);
                if (e != null && e.isValid() && e instanceof LivingEntity le) {
                    if (a != null && a.bar != null) {
                        this.updateBar(a, le);
                    }
                    // Босс не уходит далеко от арены
                    if (a != null && !a.type.minion() && le.getLocation().distanceSquared(p.loc) > 30 * 30) {
                        le.teleport(p.loc);
                    }
                } else {
                    if (a != null) {
                        this.dispose(p.entity, a);
                    }
                    p.entity = null;
                    p.nextSpawn = now + 30000L;
                }
            }
        }
    }

    private void spawn(Point p, Type t) {
        Entity raw = p.loc.getWorld().spawnEntity(p.loc, t.entity(), SpawnReason.CUSTOM);
        if (!(raw instanceof LivingEntity le)) {
            raw.remove();
            return;
        }
        if (le instanceof Slime s && t.size() > 0) {
            s.setSize(t.size());
        }
        le.customName(Msg.parse(t.name()));
        le.setCustomNameVisible(true);
        le.setRemoveWhenFarAway(false);
        le.setPersistent(false);
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.min(1024.0, t.health()));
            le.setHealth(Math.min(1024.0, t.health()));
        }
        AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(t.damage());
        }
        if (t.scale() > 0.0) {
            AttributeInstance sc = le.getAttribute(Attribute.SCALE);
            if (sc != null) {
                sc.setBaseValue(t.scale());
            }
        }
        if (!t.minion()) {
            AttributeInstance kb = le.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.setBaseValue(0.8);
            }
        }
        EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            Material helmet = t.helmet().isEmpty() ? null : Material.matchMaterial(t.helmet());
            if (helmet != null) {
                eq.setHelmet(new ItemStack(helmet));
                eq.setHelmetDropChance(0.0F);
            }
            Material weapon = t.weapon().isEmpty() ? null : Material.matchMaterial(t.weapon());
            if (weapon != null) {
                eq.setItemInMainHand(new ItemStack(weapon));
                eq.setItemInMainHandDropChance(0.0F);
            }
        }
        le.getPersistentDataContainer().set(this.bossKey, PersistentDataType.STRING, t.id());
        BossBar bar = t.minion() ? null : BossBar.bossBar(Msg.parse(t.name()), 1.0F, Color.RED, Overlay.PROGRESS);
        this.active.put(le.getUniqueId(), new Active(t, p, bar));
        p.entity = le.getUniqueId();
        if (!t.minion()) {
            Bukkit.broadcast(Msg.parse("<red>⚔ Появился босс " + t.name() + "<red>! <gray>(" + t.health() + " HP)"));
            this.plugin.bossFights().onSpawn(t, le, p.loc);
        }
    }

    /** Множитель входящего урона для боссов с HP > 1024. */
    public double damageScale(Entity e) {
        Active a = this.active.get(e.getUniqueId());
        return a == null || a.type.health() <= 1024.0 ? 1.0 : 1024.0 / a.type.health();
    }

    private void updateBar(Active a, LivingEntity le) {
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        double max = hp == null ? 20.0 : hp.getValue();
        double frac = Math.max(0.0, Math.min(1.0, le.getHealth() / max));
        a.bar.progress((float)frac);
        a.bar.name(Msg.parse(a.type.name() + " <white>" + Math.round(frac * a.type.health()) + "/" + Math.round(a.type.health()) + " HP"));
        for (Player pl : le.getWorld().getPlayers()) {
            boolean near = pl.getLocation().distanceSquared(le.getLocation()) <= 1600.0;
            boolean shown = a.viewers.contains(pl.getUniqueId());
            if (near && !shown) {
                pl.showBossBar(a.bar);
                a.viewers.add(pl.getUniqueId());
            } else if (!near && shown) {
                pl.hideBossBar(a.bar);
                a.viewers.remove(pl.getUniqueId());
            }
        }
    }

    private void dispose(UUID id, Active a) {
        for (UUID v : a.viewers) {
            Player pl = Bukkit.getPlayer(v);
            if (pl != null && a.bar != null) {
                pl.hideBossBar(a.bar);
            }
        }
        a.viewers.clear();
        this.active.remove(id);
        if (!a.type.minion()) {
            this.plugin.bossFights().onGone(a.type, id);
        }
    }

    public void removeAll() {
        for (Entry<UUID, Active> e : new ArrayList<>(this.active.entrySet())) {
            Entity en = Bukkit.getEntity(e.getKey());
            if (en != null) {
                en.remove();
            }
            this.dispose(e.getKey(), e.getValue());
        }
        for (Point p : this.points.values()) {
            p.entity = null;
        }
    }

    public Type typeOf(Entity e) {
        Active a = this.active.get(e.getUniqueId());
        return a == null ? null : a.type;
    }

    public void addDamage(Entity e, Player p, double dmg) {
        Active a = this.active.get(e.getUniqueId());
        if (a != null) {
            a.damage.merge(p.getUniqueId(), dmg, Double::sum);
        }
    }

    public void onDeath(LivingEntity e) {
        Active a = this.active.get(e.getUniqueId());
        if (a == null) {
            return;
        }
        if (a.type.minion()) {
            this.minionDeath(e, a);
            return;
        }
        this.distribute(a);
        long spread = (long)(a.type.respawnMax() - a.type.respawnSeconds());
        long respawn = a.type.respawnSeconds() + (spread > 0 ? ThreadLocalRandom.current().nextLong(spread + 1) : 0L);
        a.point.entity = null;
        a.point.nextSpawn = System.currentTimeMillis() + respawn * 1000L;
        Bukkit.broadcast(Msg.parse("<gray>Следующее появление " + a.type.name() + "<gray> — через " + Items.fmtTime(respawn) + "."));
        this.dispose(e.getUniqueId(), a);
    }

    /** BossManager$Drop: распределение награды пропорционально урону (см. javadoc класса). */
    private void distribute(Active a) {
        ConfigurationSection c = this.plugin.getConfig().getConfigurationSection("bosses.distribution");
        double topShare = c == null ? 0.7 : c.getDouble("top-pool", 0.7);
        double minShare = c == null ? 0.05 : c.getDouble("min-share", 0.05);
        List<Entry<UUID, Double>> list = new ArrayList<>(a.damage.entrySet());
        list.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        double total = 0.0;
        for (Entry<UUID, Double> e : list) {
            total += e.getValue();
        }
        if (total <= 0.0) {
            return;
        }
        double topTotal = 0.0;
        double restTotal = 0.0;
        for (int i = 0; i < list.size(); i++) {
            double share = list.get(i).getValue() / total;
            if (i < 3) {
                topTotal += list.get(i).getValue();
            } else if (share >= minShare) {
                restTotal += list.get(i).getValue();
            }
        }
        if (restTotal <= 0.0) {
            topShare = 1.0;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < list.size(); i++) {
            UUID id = list.get(i).getKey();
            double dmg = list.get(i).getValue();
            double share = dmg / total;
            Player p = Bukkit.getPlayer(id);
            PlayerData d = p == null ? null : this.plugin.data().get(p);
            if (d == null) {
                continue;
            }
            Faction f = this.plugin.factions().get(d.faction);
            double fb = f == null ? 1.0 : f.bossBonus();
            boolean top = i < 3;
            if (!top && share < minShare) {
                p.sendMessage(Msg.parse("<gray>Босс " + a.type.name() + "<gray> повержен. Ваш вклад " + Math.round(share * 100) + "% — ниже порога награды (" + Math.round(minShare * 100) + "%)."));
                continue;
            }
            double coins = top ? a.type.reward() * topShare * dmg / topTotal * fb : a.type.reward() * (1.0 - topShare) * dmg / restTotal * fb;
            d.balance += coins;
            d.bossKills++;
            d.mobKills.merge(a.type.id(), 1, Integer::sum);
            List<String> got = new ArrayList<>();
            got.add("<green>$" + Msg.money(coins));
            if (top) {
                // Ключи
                String key = i == 0 ? "legendary" : "dk";
                d.addKey(key, 1);
                got.add("<gold>" + (i == 0 ? "Легендарный ключ" : "Древний ключ"));
                // Звёзды по диапазону, с перком тренера «Шанс звёзд»
                if (a.type.starsMax() > 0) {
                    double perk = 1.0 + Math.min(0.1, d.perk("stars") * this.plugin.getConfig().getDouble("trainer.perks.stars.step", 0.01));
                    double weight = i == 0 ? 1.0 : Math.min(1.0, share * 2.0 + 0.2);
                    long stars = Math.round((a.type.starsMin() + r.nextDouble() * (a.type.starsMax() - a.type.starsMin()) * weight) * perk);
                    this.plugin.specials().giveStars(p, Math.max(a.type.starsMin(), stars));
                    got.add("<yellow>★" + Math.max(a.type.starsMin(), stars));
                }
                // Спец-лут / трофей
                if (!a.type.specialName().isEmpty() && r.nextDouble() < a.type.specialChance()) {
                    int n = a.type.specialMin() + (a.type.specialMax() > a.type.specialMin() ? r.nextInt(a.type.specialMax() - a.type.specialMin() + 1) : 0);
                    ItemStack it;
                    if (!a.type.specialItem().isEmpty()) {
                        it = this.plugin.specials().create(a.type.specialItem(), n);
                    } else {
                        it = this.plugin.enchants().unique(a.type.specialMat(), a.type.specialName(), Math.max(1, a.type.specialStars()));
                        it.setAmount(n);
                    }
                    if (it != null) {
                        Items.give(p, it);
                        got.add("<light_purple>" + a.type.specialName() + " x" + n);
                    }
                }
            } else {
                // Утешительный дроп руд
                Material[] ores = {Material.DIAMOND, Material.GOLD_INGOT, Material.EMERALD, Material.IRON_INGOT, Material.LAPIS_LAZULI};
                Material m = ores[r.nextInt(ores.length)];
                int n = 1 + r.nextInt(m == Material.DIAMOND || m == Material.EMERALD ? 4 : 10);
                Items.give(p, new ItemStack(m, n));
                got.add("<aqua><lang:" + m.translationKey() + "> x" + n);
            }
            p.sendMessage(Msg.parse("<gold>Босс " + a.type.name() + "<gold> повержен! <gray>Место: <white>" + (i + 1) + "<gray>, вклад <white>" + Math.round(share * 100) + "%<gray>. Награда: " + String.join("<gray>, ", got)));
            this.plugin.quests().progress(p, d, QuestType.BOSS, 1.0);
        }
        Player best = Bukkit.getPlayer(list.get(0).getKey());
        if (best != null) {
            Bukkit.broadcast(Msg.msg("boss-killed", Msg.pm("boss", a.type.name()), Msg.p("player", best.getName())));
        }
    }

    private void minionDeath(LivingEntity e, Active a) {
        Player killer = e.getKiller();
        if (killer == null) {
            UUID best = null;
            double bd = 0.0;
            for (Entry<UUID, Double> en : a.damage.entrySet()) {
                if (en.getValue() > bd) {
                    bd = en.getValue();
                    best = en.getKey();
                }
            }
            if (best != null) {
                killer = Bukkit.getPlayer(best);
            }
        }
        if (killer != null) {
            PlayerData d = this.plugin.data().get(killer);
            if (d != null) {
                d.balance += a.type.reward();
                d.mobKills.merge(a.type.id(), 1, Integer::sum);
                this.plugin.quests().progress(killer, d, QuestType.MOB, 1.0);
                this.plugin.collections().onMobKill(killer, d, a.type.id(), e.getLocation());
                int hunter = this.plugin.enchants().handLevel(killer, Ench.HUNTER);
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (Drop drop : a.type.drops()) {
                    if (r.nextDouble() >= drop.chance()) {
                        continue;
                    }
                    int n = drop.min() + (drop.max() > drop.min() ? r.nextInt(drop.max() - drop.min() + 1) : 0);
                    if (hunter > 0 && r.nextDouble() < 0.25 + 0.15 * hunter) {
                        n += hunter;
                    }
                    String item = drop.item().toUpperCase();
                    ItemStack it = switch (item) {
                        case "DUST" -> this.plugin.enchants().dust(10, n);
                        case "SHARD" -> this.plugin.awaken().shard(n);
                        case "COINS" -> {
                            d.balance += n;
                            yield null;
                        }
                        default -> {
                            if (this.plugin.specials().def(drop.item().toLowerCase()) != null) {
                                yield this.plugin.specials().create(drop.item().toLowerCase(), n);
                            }
                            Material m = Material.matchMaterial(drop.item());
                            yield m == null ? null : new ItemStack(m, n);
                        }
                    };
                    if (it != null) {
                        Items.give(killer, it);
                        String sid = this.plugin.specials().id(it);
                        if ("battle_pass".equals(sid)) {
                            killer.sendMessage(Msg.parse("<gold><bold>Редкая находка!</bold> <gray>Боевой пропуск на арену Злого Тыкова."));
                        }
                    }
                }
            }
        }
        a.point.entity = null;
        a.point.nextSpawn = System.currentTimeMillis() + a.type.respawnSeconds() * 1000L;
        this.dispose(e.getUniqueId(), a);
    }

    /** Для AI: заставить босса атаковать ближайшего игрока (у некоторых сущностей нет агрессии по умолчанию). */
    public void aggro(LivingEntity boss, Player target) {
        if (boss instanceof Mob m && (m.getTarget() == null || !m.getTarget().isValid())) {
            m.setTarget(target);
        }
    }

    private static final class Active {
        final Type type;
        final Point point;
        final BossBar bar;
        final Map<UUID, Double> damage = new HashMap<>();
        final Set<UUID> viewers = new HashSet<>();

        Active(Type type, Point point, BossBar bar) {
            this.type = type;
            this.point = point;
            this.bar = bar;
        }
    }

    /** Строка таблицы дропа миньона: предмет (материал, DUST, SHARD, COINS или id особого предмета), шанс, мин–макс. */
    public record Drop(String item, double chance, int min, int max) {
    }

    private static final class Point {
        final String id;
        final String type;
        final Location loc;
        UUID entity;
        long nextSpawn = 0L;

        Point(String id, String type, Location loc) {
            this.id = id;
            this.type = type;
            this.loc = loc;
        }
    }

    public record Type(
        String id,
        String name,
        EntityType entity,
        double health,
        double damage,
        double reward,
        int minLevel,
        int respawnSeconds,
        int respawnMax,
        boolean minion,
        int starsMin,
        int starsMax,
        String specialName,
        Material specialMat,
        double specialChance,
        int specialMin,
        int specialMax,
        int specialStars,
        String specialItem,
        List<Drop> drops,
        String hitEffect,
        int size,
        double scale,
        String helmet,
        String weapon
    ) {
    }
}
