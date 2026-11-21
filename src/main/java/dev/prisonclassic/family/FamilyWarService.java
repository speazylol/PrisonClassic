package dev.prisonclassic.family;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.gang.GangService;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.persistence.PersistentDataType;

/**
 * Войны Семей за контрольные точки (FamilyService — клановая часть). «Семья» = клан игроков (/gang).
 *
 * Условие штурма: в сети минимум 1 член защищающейся Семьи (для занятой точки).
 * Штурм длится family-war.assault-seconds; прогресс растёт, когда у точки только атакующие, падает — когда только защитники.
 * Защита от спам-атак: после штурма точка уходит на КД. Исключение — если атаковала нейтральная Семья
 * (без своей точки) и проиграла: КД для других Семей НЕ выставляется, точку можно штурмовать повторно без задержек.
 * Владелец точки получает доход в казну Семьи каждый час.
 */
public final class FamilyWarService {
    private final PrisonClassic plugin;
    private final File file;
    private final NamespacedKey holoKey;
    private final Map<String, FPoint> points = new LinkedHashMap<>();

    private static final class FPoint {
        final String id;
        final String name;
        String owner;
        long cooldownUntil;
        Assault assault;

        FPoint(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class Assault {
        String attacker;
        boolean attackerNeutral;
        long until;
        double progress;
        BossBar bar;
        final Set<UUID> viewers = new HashSet<>();
    }

    public FamilyWarService(PrisonClassic plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "family-points.yml");
        this.holoKey = new NamespacedKey(plugin, "fpoint_holo");
        this.reload();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::income, 72000L, 72000L);
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshHolograms, 120L);
    }

    private ConfigurationSection c() {
        ConfigurationSection s = this.plugin.getConfig().getConfigurationSection("family-war");
        return s == null ? this.plugin.getConfig().createSection("family-war") : s;
    }

    public void reload() {
        this.points.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection list = this.c().getConfigurationSection("points");
        if (list == null) {
            return;
        }
        for (String id : list.getKeys(false)) {
            FPoint p = new FPoint(id, list.getString(id, id));
            p.owner = y.getString(id + ".owner");
            p.cooldownUntil = y.getLong(id + ".cooldown", 0L);
            this.points.put(id, p);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (FPoint p : this.points.values()) {
            y.set(p.id + ".owner", p.owner);
            y.set(p.id + ".cooldown", p.cooldownUntil);
        }
        try {
            y.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("family-points.yml: " + e.getMessage());
        }
    }

    public Set<String> ids() {
        return this.points.keySet();
    }

    private Location loc(String id) {
        return this.plugin.locations().get("fpoint." + id);
    }

    private String gangName(String id) {
        GangService.Gang g = this.plugin.gangs().byId(id);
        return g == null ? (id == null ? "ничья" : id) : g.name;
    }

    private int owned(String gang) {
        int n = 0;
        for (FPoint p : this.points.values()) {
            if (gang != null && gang.equals(p.owner)) {
                n++;
            }
        }
        return n;
    }

    private boolean online(String gang) {
        GangService.Gang g = this.plugin.gangs().byId(gang);
        if (g == null) {
            return false;
        }
        for (UUID u : g.members.keySet()) {
            if (Bukkit.getPlayer(u) != null) {
                return true;
            }
        }
        return false;
    }

    public void command(Player p, String[] a) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("attack")) {
            this.attack(p, d, a.length > 1 ? a[1] : null);
            return;
        }
        p.sendMessage(Msg.parse("<light_purple><bold>Войны Семей</bold> <gray>— контрольные точки (Семья = клан /gang):"));
        long now = System.currentTimeMillis();
        for (FPoint fp : this.points.values()) {
            String state = fp.assault != null ? "<red>идёт штурм (" + this.gangName(fp.assault.attacker) + ")"
                : (fp.cooldownUntil > now ? "<yellow>КД " + Items.fmtTime((fp.cooldownUntil - now) / 1000L) : "<green>можно штурмовать");
            p.sendMessage(Msg.parse("<white>" + fp.id + " — " + fp.name + " <gray>| владелец: <white>" + this.gangName(fp.owner) + " <gray>| " + state));
        }
        p.sendMessage(Msg.parse("<yellow>/fw attack <точка> <gray>— начать штурм (стойте у точки). Доход владельцу: $" + Msg.money(this.c().getDouble("income-per-hour", 2000.0)) + "/час в казну."));
    }

    private void attack(Player p, PlayerData d, String id) {
        GangService.Gang g = this.plugin.gangs().of(d);
        if (g == null) {
            p.sendMessage(Msg.parse("<red>Штурмовать точки может только Семья (клан): /gang create"));
            return;
        }
        FPoint fp = id == null ? null : this.points.get(id.toLowerCase(Locale.ROOT));
        Location l = fp == null ? null : this.loc(fp.id);
        if (fp == null || l == null) {
            p.sendMessage(Msg.parse("<red>Нет такой точки. /fw"));
            return;
        }
        if (g.id.equals(fp.owner)) {
            p.sendMessage(Msg.parse("<red>Эта точка уже ваша."));
            return;
        }
        if (fp.assault != null) {
            p.sendMessage(Msg.parse("<red>Точку уже штурмуют."));
            return;
        }
        long now = System.currentTimeMillis();
        if (fp.cooldownUntil > now) {
            p.sendMessage(Msg.parse("<red>Точка на КД ещё " + Items.fmtTime((fp.cooldownUntil - now) / 1000L) + "."));
            return;
        }
        if (fp.owner != null && !this.online(fp.owner)) {
            p.sendMessage(Msg.parse("<red>Штурм невозможен: в сети нет ни одного члена Семьи-защитника."));
            return;
        }
        double r = this.c().getDouble("start-radius", 10.0);
        if (p.getWorld() != l.getWorld() || p.getLocation().distanceSquared(l) > r * r) {
            p.sendMessage(Msg.parse("<red>Подойдите к точке ближе (" + (int)r + " блоков)."));
            return;
        }
        Assault as = new Assault();
        as.attacker = g.id;
        as.attackerNeutral = this.owned(g.id) == 0;
        as.until = now + this.c().getLong("assault-seconds", 300L) * 1000L;
        as.bar = BossBar.bossBar(Msg.parse("Штурм"), 0.0F, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        fp.assault = as;
        Bukkit.broadcast(Msg.parse("<light_purple>⚔ Семья <white>" + g.name + "</white> начала штурм точки <white>" + fp.name + "</white>"
            + (fp.owner == null ? "" : " (владелец — " + this.gangName(fp.owner) + ")") + "!"));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        double radius = this.c().getDouble("capture-radius", 6.0);
        double rate = 100.0 / Math.max(10.0, this.c().getDouble("capture-seconds", 90.0));
        boolean changed = false;
        for (FPoint fp : this.points.values()) {
            Assault as = fp.assault;
            Location l = this.loc(fp.id);
            if (as == null || l == null || l.getWorld() == null) {
                continue;
            }
            int att = 0;
            int def = 0;
            for (Player pl : l.getWorld().getPlayers()) {
                boolean near = pl.getLocation().distanceSquared(l) <= 900.0;
                if (near && as.viewers.add(pl.getUniqueId())) {
                    pl.showBossBar(as.bar);
                } else if (!near && as.viewers.remove(pl.getUniqueId())) {
                    pl.hideBossBar(as.bar);
                }
                if (pl.isDead() || pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE || pl.getLocation().distanceSquared(l) > radius * radius) {
                    continue;
                }
                GangService.Gang g = this.plugin.gangs().of(this.plugin.data().get(pl));
                if (g == null) {
                    continue;
                }
                if (g.id.equals(as.attacker)) {
                    att++;
                } else if (g.id.equals(fp.owner)) {
                    def++;
                }
            }
            if (att > 0 && def == 0) {
                as.progress = Math.min(100.0, as.progress + rate * Math.min(3, att));
            } else if (def > 0 && att == 0) {
                as.progress = Math.max(0.0, as.progress - rate * Math.min(3, def));
            }
            l.getWorld().spawnParticle(Particle.WITCH, l.clone().add(0.0, 1.0, 0.0), 8, radius / 2.0, 0.3, radius / 2.0, 0.0);
            as.bar.progress((float)(as.progress / 100.0));
            as.bar.name(Msg.parse("<light_purple>Штурм «" + fp.name + "»: <white>" + this.gangName(as.attacker) + " <gray>" + (int)as.progress + "% | "
                + Items.fmtTime(Math.max(0L, (as.until - now) / 1000L)) + (att > 0 && def > 0 ? " <yellow>(точка оспаривается)" : "")));
            if (as.progress >= 100.0) {
                String old = fp.owner;
                fp.owner = as.attacker;
                fp.cooldownUntil = now + this.c().getLong("cooldown-seconds", 1800L) * 1000L;
                Bukkit.broadcast(Msg.parse("<light_purple>⚔ Семья <white>" + this.gangName(as.attacker) + "</white> захватила точку <white>" + fp.name + "</white>"
                    + (old == null ? "" : " у " + this.gangName(old)) + "!"));
                this.endAssault(fp);
                changed = true;
            } else if (now >= as.until) {
                if (as.attackerNeutral) {
                    // Нейтральная Семья проиграла: КД не выставляется — точку можно штурмовать повторно без задержек
                    fp.cooldownUntil = 0L;
                } else {
                    fp.cooldownUntil = now + this.c().getLong("cooldown-seconds", 1800L) * 1000L;
                }
                Bukkit.broadcast(Msg.parse("<light_purple>⚔ Штурм точки <white>" + fp.name + "</white> отбит. " + (as.attackerNeutral ? "<gray>(атаковала нейтральная Семья — КД не выставлен)" : "")));
                this.endAssault(fp);
                changed = true;
            }
        }
        if (changed) {
            this.save();
            this.refreshHolograms();
        }
    }

    private void endAssault(FPoint fp) {
        Assault as = fp.assault;
        fp.assault = null;
        if (as == null) {
            return;
        }
        for (UUID u : as.viewers) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null) {
                pl.hideBossBar(as.bar);
            }
        }
    }

    private void income() {
        double per = this.c().getDouble("income-per-hour", 2000.0);
        for (FPoint fp : this.points.values()) {
            GangService.Gang g = this.plugin.gangs().byId(fp.owner);
            if (g != null) {
                g.bank += per;
            }
        }
        this.plugin.gangs().save();
    }

    public void refreshHolograms() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(TextDisplay.class)) {
                if (e.getPersistentDataContainer().has(this.holoKey, PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }
        for (FPoint fp : this.points.values()) {
            Location l = this.loc(fp.id);
            if (l == null || l.getWorld() == null) {
                continue;
            }
            TextDisplay t = l.getWorld().spawn(l.clone().add(0.0, 2.5, 0.0), TextDisplay.class);
            t.text(Msg.parse("<light_purple><bold>" + fp.name + "</bold>\n<gray>Семья: <white>" + this.gangName(fp.owner) + "\n<gray>/fw attack " + fp.id));
            t.setBillboard(Billboard.CENTER);
            t.setPersistent(false);
            t.getPersistentDataContainer().set(this.holoKey, PersistentDataType.STRING, fp.id);
        }
    }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (FPoint fp : this.points.values()) {
            out.add(fp.name + ": " + this.gangName(fp.owner));
        }
        return out;
    }

    public void shutdown() {
        for (FPoint fp : this.points.values()) {
            this.endAssault(fp);
        }
        this.save();
    }
}
