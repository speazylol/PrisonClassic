package dev.prisonclassic.mine;

import dev.prisonclassic.util.Region;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;

public final class Mine {
    public final String name;
    public String title;
    public Region region;
    public int requiredLevel;
    public Location spawn;
    public int resetSeconds = 120;
    public Material upper = null;
    public double resetPercent = 0.6;
    public boolean base = false;
    public boolean hidden = false;
    public boolean privateSlot = false;
    public UUID owner = null;
    public final Map<Material, Double> composition = new LinkedHashMap<>();
    public long broken = 0L;
    public long nextReset;
    public boolean resetting = false;

    public Mine(String var1, Region var2, int var3) {
        this.name = var1;
        this.title = var1;
        this.region = var2;
        this.requiredLevel = var3;
        this.composition.put(Material.STONE, 100.0);
        this.scheduleNext();
    }

    public void scheduleNext() {
        this.nextReset = System.currentTimeMillis() + this.resetSeconds * 1000L;
    }
}
