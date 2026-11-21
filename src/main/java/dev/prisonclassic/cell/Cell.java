package dev.prisonclassic.cell;

import dev.prisonclassic.util.Region;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

public final class Cell {
    public final String name;
    public Region region;
    public Location spawn;
    public double price;
    public UUID owner;
    /** Уровень камеры 1–4: 2 — сито, 3 — душ, 4 — полка коллекций «Наследие Предков». */
    public int level = 1;
    public final Set<UUID> members = new HashSet<>();

    public Cell(String var1, Region var2, Location var3, double var4) {
        this.name = var1;
        this.region = var2;
        this.spawn = var3;
        this.price = var4;
    }
}
