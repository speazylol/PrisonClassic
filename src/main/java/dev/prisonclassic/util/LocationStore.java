package dev.prisonclassic.util;

import java.io.File;
import java.io.IOException;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LocationStore {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public LocationStore(JavaPlugin var1) {
        this.plugin = var1;
        var1.getDataFolder().mkdirs();
        this.file = new File(var1.getDataFolder(), "locations.yml");
        this.cfg = YamlConfiguration.loadConfiguration(this.file);
    }

    public Location get(String var1) {
        return this.cfg.getLocation(var1);
    }

    public void set(String var1, Location var2) {
        this.cfg.set(var1, var2);

        try {
            this.cfg.save(this.file);
        } catch (IOException var4) {
            this.plugin.getLogger().warning("Не удалось сохранить locations.yml: " + var4.getMessage());
        }
    }
}
