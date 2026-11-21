package dev.prisonclassic.cell;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.util.Region;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class CellManager {
    private final PrisonClassic plugin;
    private final File file;
    private final Map<String, Cell> cells = new LinkedHashMap<>();

    public CellManager(PrisonClassic var1) {
        this.plugin = var1;
        this.file = new File(var1.getDataFolder(), "cells.yml");
        this.load();
    }

    private void load() {
        this.cells.clear();
        YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection var2 = var1.getConfigurationSection("cells");
        if (var2 != null) {
            for (String var4 : var2.getKeys(false)) {
                ConfigurationSection var5 = var2.getConfigurationSection(var4);
                if (var5 != null) {
                    Region var6 = Region.load(var5.getConfigurationSection("region"));
                    if (var6 != null) {
                        Cell var7 = new Cell(var4, var6, var5.getLocation("spawn"), var5.getDouble("price", 1000.0));
                        String var8 = var5.getString("owner");
                        if (var8 != null) {
                            try {
                                var7.owner = UUID.fromString(var8);
                            } catch (IllegalArgumentException var12) {
                            }
                        }

                        var7.level = Math.max(1, Math.min(4, var5.getInt("level", 1)));

                        for (String var10 : var5.getStringList("members")) {
                            try {
                                var7.members.add(UUID.fromString(var10));
                            } catch (IllegalArgumentException var13) {
                            }
                        }

                        this.cells.put(var4.toLowerCase(), var7);
                    }
                }
            }
        }
    }

    public void save() {
        YamlConfiguration var1 = new YamlConfiguration();

        for (Cell var3 : this.cells.values()) {
            String var4 = "cells." + var3.name + ".";
            var3.region.save(var1.createSection(var4 + "region"));
            var1.set(var4 + "spawn", var3.spawn);
            var1.set(var4 + "price", var3.price);
            var1.set(var4 + "owner", var3.owner == null ? null : var3.owner.toString());
            var1.set(var4 + "level", var3.level);
            ArrayList var5 = new ArrayList();

            for (UUID var7 : var3.members) {
                var5.add(var7.toString());
            }

            var1.set(var4 + "members", var5);
        }

        try {
            var1.save(this.file);
        } catch (IOException var8) {
            this.plugin.getLogger().warning("Не удалось сохранить cells.yml: " + var8.getMessage());
        }
    }

    public Cell get(String var1) {
        return var1 == null ? null : this.cells.get(var1.toLowerCase());
    }

    public Collection<Cell> all() {
        return this.cells.values();
    }

    public Cell create(String var1, Region var2, Location var3, double var4) {
        Cell var6 = new Cell(var1, var2, var3, var4);
        this.cells.put(var1.toLowerCase(), var6);
        this.save();
        return var6;
    }

    public boolean delete(String var1) {
        boolean var2 = this.cells.remove(var1.toLowerCase()) != null;
        if (var2) {
            this.save();
        }

        return var2;
    }

    public Cell at(Block var1) {
        for (Cell var3 : this.cells.values()) {
            if (var3.region.contains(var1)) {
                return var3;
            }
        }

        return null;
    }

    public Cell ownedBy(UUID var1) {
        for (Cell var3 : this.cells.values()) {
            if (var1.equals(var3.owner)) {
                return var3;
            }
        }

        return null;
    }

    public boolean canUse(Player var1, Cell var2) {
        return var1.hasPermission("prison.admin") && var1.getGameMode() == GameMode.CREATIVE
            ? true
            : var1.getUniqueId().equals(var2.owner) || var2.members.contains(var1.getUniqueId()) || this.plugin.gangs().cellAccess(var1.getUniqueId(), var2);
    }
}
