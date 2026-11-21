package dev.prisonclassic.mine;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Region;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class MineManager {
    private final PrisonClassic plugin;
    private final File file;
    private final Map<String, Mine> mines = new LinkedHashMap<>();

    public MineManager(PrisonClassic var1) {
        this.plugin = var1;
        this.file = new File(var1.getDataFolder(), "mines.yml");
        this.load();
    }

    private void load() {
        this.mines.clear();
        YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection var2 = var1.getConfigurationSection("mines");
        if (var2 != null) {
            for (String var4 : var2.getKeys(false)) {
                ConfigurationSection var5 = var2.getConfigurationSection(var4);
                if (var5 != null) {
                    Region var6 = Region.load(var5.getConfigurationSection("region"));
                    if (var6 != null) {
                        Mine var7 = new Mine(var4, var6, var5.getInt("required-level", 1));
                        var7.title = var5.getString("title", var4);
                        var7.spawn = var5.getLocation("spawn");
                        var7.resetSeconds = var5.getInt("reset-seconds", var5.getInt("reset-minutes", 2) * 60);
                        String var8 = var5.getString("upper");
                        var7.upper = var8 == null ? null : Material.matchMaterial(var8);
                        var7.resetPercent = var5.getDouble("reset-percent", 0.6);
                        var7.base = var5.getBoolean("base", false);
                        var7.hidden = var5.getBoolean("hidden", false);
                        var7.privateSlot = var5.getBoolean("private", false);
                        ConfigurationSection var9 = var5.getConfigurationSection("composition");
                        if (var9 != null) {
                            var7.composition.clear();

                            for (String var11 : var9.getKeys(false)) {
                                Material var12 = Material.matchMaterial(var11);
                                if (var12 != null && var12.isBlock()) {
                                    var7.composition.put(var12, var9.getDouble(var11));
                                }
                            }
                        }

                        var7.scheduleNext();
                        this.mines.put(var4.toLowerCase(), var7);
                    }
                }
            }
        }
    }

    public void save() {
        YamlConfiguration var1 = new YamlConfiguration();

        for (Mine var3 : this.mines.values()) {
            String var4 = "mines." + var3.name + ".";
            var3.region.save(var1.createSection(var4 + "region"));
            var1.set(var4 + "title", var3.title);
            var1.set(var4 + "required-level", var3.requiredLevel);
            var1.set(var4 + "spawn", var3.spawn);
            var1.set(var4 + "reset-seconds", var3.resetSeconds);
            if (var3.upper != null) {
                var1.set(var4 + "upper", var3.upper.name());
            }

            var1.set(var4 + "reset-percent", var3.resetPercent);
            if (var3.base) {
                var1.set(var4 + "base", true);
            }

            if (var3.hidden) {
                var1.set(var4 + "hidden", true);
            }

            if (var3.privateSlot) {
                var1.set(var4 + "private", true);
            }

            for (Entry var6 : var3.composition.entrySet()) {
                var1.set(var4 + "composition." + ((Material)var6.getKey()).name(), var6.getValue());
            }
        }

        try {
            var1.save(this.file);
        } catch (IOException var7) {
            this.plugin.getLogger().warning("Не удалось сохранить mines.yml: " + var7.getMessage());
        }
    }

    public Mine get(String var1) {
        return var1 == null ? null : this.mines.get(var1.toLowerCase());
    }

    public Collection<Mine> all() {
        return this.mines.values();
    }

    public Mine create(String var1, Region var2, int var3, Location var4) {
        Mine var5 = new Mine(var1, var2, var3);
        var5.spawn = var4;
        this.mines.put(var1.toLowerCase(), var5);
        this.save();
        return var5;
    }

    public boolean delete(String var1) {
        boolean var2 = this.mines.remove(var1.toLowerCase()) != null;
        if (var2) {
            this.save();
        }

        return var2;
    }

    public Mine at(Block var1) {
        for (Mine var3 : this.mines.values()) {
            if (var3.region.contains(var1)) {
                return var3;
            }
        }

        return null;
    }

    public void start() {
        this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            long var1 = System.currentTimeMillis();

            for (Mine var4 : this.mines.values()) {
                if ((!var4.privateSlot || var4.owner != null) && !var4.resetting && var1 >= var4.nextReset) {
                    this.reset(var4);
                }
            }
        }, 100L, 20L);
    }

    public void registerBreak(Mine var1) {
        this.registerBreak(var1, 1);
    }

    public void registerBreak(Mine var1, int var2) {
        var1.broken += var2;
        if (!var1.resetting && var1.broken >= var1.region.volume() * var1.resetPercent) {
            this.reset(var1);
        }
    }

    public void reset(final Mine var1) {
        final World var2 = var1.region.bukkitWorld();
        if (var2 != null && !var1.resetting) {
            var1.resetting = true;
            Location var3 = var1.spawn != null ? var1.spawn : this.plugin.spawn();

            for (Player var5 : var2.getPlayers()) {
                if (var1.region.contains(var5.getLocation()) && var3 != null) {
                    var5.teleport(var3);
                    Msg.send(var5, "mine-reset-tp", Msg.p("mine", var1.title));
                }
            }

            final ArrayList<Material> var12 = new ArrayList<>();
            final ArrayList<Double> var13 = new ArrayList<>();
            double var6a = 0.0;

            for (Entry<Material, Double> var9 : var1.composition.entrySet()) {
                if (!((Double)var9.getValue() <= 0.0)) {
                    var6a += var9.getValue();
                    var12.add((Material)var9.getKey());
                    var13.add(var6a);
                }
            }

            if (var12.isEmpty()) {
                var12.add(Material.STONE);
                var13.add(1.0);
                var6a = 1.0;
            }
            final double var6 = var6a;
            {
            }

            final int var10 = this.plugin.getConfig().getInt("mines.blocks-per-tick", 12000);
            final Region var11 = var1.region;
            (new BukkitRunnable() {
                int x = var11.minX();
                int y = var11.minY();
                int z = var11.minZ();

                @Override
                public void run() {
                    int var1x = var10;

                    while (var1x-- > 0) {
                        if (this.x > var11.maxX()) {
                            var1.broken = 0L;
                            var1.resetting = false;
                            var1.scheduleNext();
                            this.cancel();
                            return;
                        }

                        double var2x = ThreadLocalRandom.current().nextDouble() * var6;
                        Material var4 = (Material)var12.get(var12.size() - 1);
                        if (var1.upper != null && this.y == var11.maxY()) {
                            var2.getBlockAt(this.x, this.y, this.z).setType(var1.upper, false);
                            if (++this.z > var11.maxZ()) {
                                this.z = var11.minZ();
                                if (++this.y > var11.maxY()) {
                                    this.y = var11.minY();
                                    this.x++;
                                }
                            }
                        } else {
                            int var5x = 0;

                            while (true) {
                                if (var5x < var13.size()) {
                                    if (!(var2x <= (Double)var13.get(var5x))) {
                                        var5x++;
                                        continue;
                                    }

                                    var4 = (Material)var12.get(var5x);
                                }

                                var2.getBlockAt(this.x, this.y, this.z).setType(var4, false);
                                if (++this.z > var11.maxZ()) {
                                    this.z = var11.minZ();
                                    if (++this.y > var11.maxY()) {
                                        this.y = var11.minY();
                                        this.x++;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }).runTaskTimer(this.plugin, 1L, 1L);
        }
    }
}
