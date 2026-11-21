package dev.prisonclassic.faction;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.util.Msg;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
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

public final class PointService {
    private final PrisonClassic plugin;
    private final File file;
    private final NamespacedKey holoKey;
    private final Map<String, PointService.Point> points = new LinkedHashMap<>();

    public PointService(PrisonClassic var1) {
        this.plugin = var1;
        this.file = new File(var1.getDataFolder(), "points.yml");
        this.holoKey = new NamespacedKey(var1, "point_holo");
        this.reload();
        Bukkit.getScheduler().runTaskTimer(var1, this::tick, 40L, 20L);
        Bukkit.getScheduler().runTaskLater(var1, this::refreshHolograms, 100L);
    }

    public void reload() {
        YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
        this.points.clear();
        ConfigurationSection var2 = this.plugin.getConfig().getConfigurationSection("factions.points.list");
        if (var2 != null) {
            for (String var4 : var2.getKeys(false)) {
                PointService.Point var5 = new PointService.Point(var4, var2.getString(var4, var4));
                var5.owner = var1.getString(var4);
                var5.progress = var5.owner == null ? 0.0 : 100.0;
                var5.bar = BossBar.bossBar(Msg.parse(var5.name), 0.0F, Color.WHITE, Overlay.NOTCHED_10);
                this.points.put(var4, var5);
            }
        }
    }

    private void save() {
        YamlConfiguration var1 = new YamlConfiguration();

        for (PointService.Point var3 : this.points.values()) {
            var1.set(var3.id, var3.owner);
        }

        try {
            var1.save(this.file);
        } catch (IOException var4) {
            this.plugin.getLogger().warning("points.yml: " + var4.getMessage());
        }
    }

    public Set<String> ids() {
        return this.points.keySet();
    }

    public Location loc(String var1) {
        return this.plugin.locations().get("point." + var1);
    }

    public int owned(String var1) {
        if (var1 == null) {
            return 0;
        } else {
            int var2 = 0;

            for (PointService.Point var4 : this.points.values()) {
                if (var1.equals(var4.owner)) {
                    var2++;
                }
            }

            return var2;
        }
    }

    public double blockMultiplier(PlayerData var1, Mine var2) {
        if (var2 != null && var2.base) {
            int var3 = this.owned(var1.faction);
            return var3 <= 0 ? 1.0 : 1.0 + var3;
        } else {
            return 1.0;
        }
    }

    public double upgradeDiscount(PlayerData var1) {
        return this.owned(var1.faction) * this.plugin.getConfig().getDouble("factions.points.upgrade-discount-per-point", 0.03);
    }

    private void tick() {
        double var1 = this.plugin.getConfig().getDouble("factions.points.radius", 4.0);
        double var3 = 100.0 / Math.max(5, this.plugin.getConfig().getInt("factions.points.capture-seconds", 60));
        boolean var5 = false;

        for (PointService.Point var7 : this.points.values()) {
            Location var8 = this.loc(var7.id);
            if (var8 != null && var8.getWorld() != null) {
                HashMap<String, Integer> var9 = new HashMap<>();

                for (Player var11 : var8.getWorld().getPlayers()) {
                    boolean var12 = var11.getLocation().distanceSquared(var8) <= var1 * var1;
                    boolean var13 = var11.getLocation().distanceSquared(var8) <= 900.0;
                    if (var13 && var7.viewers.add(var11.getUniqueId())) {
                        var11.showBossBar(var7.bar);
                    } else if (!var13 && var7.viewers.remove(var11.getUniqueId())) {
                        var11.hideBossBar(var7.bar);
                    }

                    if (var12 && !var11.isDead() && var11.getGameMode() != GameMode.SPECTATOR && var11.getGameMode() != GameMode.CREATIVE) {
                        PlayerData var14 = this.plugin.data().get(var11);
                        if (var14 != null && var14.faction != null) {
                            var9.merge(var14.faction, 1, Integer::sum);
                        }
                    }
                }

                if (var9.size() == 1) {
                    String var15 = (String)var9.keySet().iterator().next();
                    int var17 = Math.min(3, (Integer)var9.get(var15));
                    if (var15.equals(var7.owner)) {
                        var7.progress = Math.min(100.0, var7.progress + var3 * var17);
                        var7.capturer = null;
                    } else if (var7.owner != null && var7.progress > 0.0) {
                        var7.progress = Math.max(0.0, var7.progress - var3 * var17);
                        if (var7.progress <= 0.0) {
                            this.broadcast("<gray>Точка <white>" + var7.name + "</white> потеряна фракцией " + this.tag(var7.owner));
                            var7.owner = null;
                            var5 = true;
                        }
                    } else {
                        var7.capturer = var15;
                        var7.progress = Math.min(100.0, var7.progress + var3 * var17);
                        if (var7.progress >= 100.0) {
                            var7.owner = var15;
                            var7.capturer = null;
                            var5 = true;
                            this.broadcast(
                                "<gold>Фракция "
                                    + this.tag(var15)
                                    + " <gold>захватила точку <white>"
                                    + var7.name
                                    + "<gold>! Точек у фракции: <white>"
                                    + this.owned(var15)
                            );
                        }
                    }

                    var8.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, var8.clone().add(0.0, 1.0, 0.0), 6, var1 / 2.0, 0.3, var1 / 2.0, 0.0);
                }

                String var16 = var7.owner == null ? "<gray>ничья" : this.tag(var7.owner);
                String var18 = var7.capturer != null ? " <yellow>захватывает " + this.tag(var7.capturer) : "";
                var7.bar.name(Msg.parse("<white>Точка " + var7.name + ": " + var16 + var18));
                var7.bar.progress((float)Math.max(0.0, Math.min(1.0, var7.progress / 100.0)));
                var7.bar.color(var7.owner == null ? Color.WHITE : this.color(var7.owner));
            }
        }

        if (var5) {
            this.save();
            this.refreshHolograms();
        }
    }

    private Color color(String var1) {
        Faction var2 = this.plugin.factions().get(var1);
        if (var2 == null) {
            return Color.WHITE;
        } else {
            String var3 = var2.color();

            return switch (var3) {
                case "blue", "aqua", "dark_aqua", "dark_blue" -> Color.BLUE;
                case "red", "dark_red" -> Color.RED;
                case "gold", "yellow" -> Color.YELLOW;
                case "green", "dark_green" -> Color.GREEN;
                case "light_purple", "dark_purple" -> Color.PURPLE;
                default -> Color.WHITE;
            };
        }
    }

    private String tag(String var1) {
        Faction var2 = this.plugin.factions().get(var1);
        return var2 == null ? var1 : var2.tag();
    }

    private void broadcast(String var1) {
        Bukkit.broadcast(Msg.parse(var1));
    }

    public void refreshHolograms() {
        for (World var2 : Bukkit.getWorlds()) {
            for (Entity var4 : var2.getEntitiesByClass(TextDisplay.class)) {
                if (var4.getPersistentDataContainer().has(this.holoKey, PersistentDataType.STRING)) {
                    var4.remove();
                }
            }
        }

        for (PointService.Point var6 : this.points.values()) {
            Location var7 = this.loc(var6.id);
            if (var7 != null && var7.getWorld() != null) {
                TextDisplay var8 = var7.getWorld().spawn(var7.clone().add(0.0, 2.5, 0.0), TextDisplay.class);
                var8.text(
                    Msg.parse(
                        "<gold><bold>Точка "
                            + var6.name
                            + "</bold>\n"
                            + (var6.owner == null ? "<gray>ничья" : this.tag(var6.owner))
                            + "\n<gray>Бустер блоков на Базе и скидка на /up"
                    )
                );
                var8.setBillboard(Billboard.CENTER);
                var8.setPersistent(false);
                var8.getPersistentDataContainer().set(this.holoKey, PersistentDataType.STRING, var6.id);
            }
        }
    }

    public List<String> describe() {
        ArrayList var1 = new ArrayList();

        for (PointService.Point var3 : this.points.values()) {
            var1.add("<white>" + var3.name + "<gray>: " + (var3.owner == null ? "ничья" : this.tag(var3.owner)));
        }

        return var1;
    }

    public void shutdown() {
        for (PointService.Point var2 : this.points.values()) {
            for (UUID var4 : var2.viewers) {
                Player var5 = Bukkit.getPlayer(var4);
                if (var5 != null) {
                    var5.hideBossBar(var2.bar);
                }
            }
        }
    }

    private static final class Point {
        final String id;
        final String name;
        String owner;
        String capturer;
        double progress;
        BossBar bar;
        final Set<UUID> viewers = new HashSet<>();

        Point(String var1, String var2) {
            this.id = var1;
            this.name = var2;
        }
    }
}
