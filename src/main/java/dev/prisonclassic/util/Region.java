package dev.prisonclassic.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static Region of(Location var0, Location var1) {
        return new Region(
            var0.getWorld().getName(),
            Math.min(var0.getBlockX(), var1.getBlockX()),
            Math.min(var0.getBlockY(), var1.getBlockY()),
            Math.min(var0.getBlockZ(), var1.getBlockZ()),
            Math.max(var0.getBlockX(), var1.getBlockX()),
            Math.max(var0.getBlockY(), var1.getBlockY()),
            Math.max(var0.getBlockZ(), var1.getBlockZ())
        );
    }

    public boolean contains(Block var1) {
        return var1.getWorld().getName().equals(this.world) && this.contains(var1.getX(), var1.getY(), var1.getZ());
    }

    public boolean contains(Location var1) {
        return var1.getWorld() != null && var1.getWorld().getName().equals(this.world) && this.contains(var1.getBlockX(), var1.getBlockY(), var1.getBlockZ());
    }

    public boolean contains(int var1, int var2, int var3) {
        return var1 >= this.minX && var1 <= this.maxX && var2 >= this.minY && var2 <= this.maxY && var3 >= this.minZ && var3 <= this.maxZ;
    }

    public long volume() {
        return (long)(this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
    }

    public World bukkitWorld() {
        return Bukkit.getWorld(this.world);
    }

    public void save(ConfigurationSection var1) {
        var1.set("world", this.world);
        var1.set("min", this.minX + "," + this.minY + "," + this.minZ);
        var1.set("max", this.maxX + "," + this.maxY + "," + this.maxZ);
    }

    public static Region load(ConfigurationSection var0) {
        if (var0 == null) {
            return null;
        } else {
            try {
                String[] var1 = var0.getString("min", "0,0,0").split(",");
                String[] var2 = var0.getString("max", "0,0,0").split(",");
                return new Region(
                    var0.getString("world", "world"),
                    Integer.parseInt(var1[0].trim()),
                    Integer.parseInt(var1[1].trim()),
                    Integer.parseInt(var1[2].trim()),
                    Integer.parseInt(var2[0].trim()),
                    Integer.parseInt(var2[1].trim()),
                    Integer.parseInt(var2[2].trim())
                );
            } catch (Exception var3) {
                return null;
            }
        }
    }
}
