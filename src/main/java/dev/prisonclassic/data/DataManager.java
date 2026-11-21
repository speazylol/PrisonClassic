package dev.prisonclassic.data;

import dev.prisonclassic.PrisonClassic;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * DataManager: кэш PlayerData онлайн-игроков + хранилище (YAML или MySQL).
 * Снимок (YamlConfiguration) делается в главном потоке, запись — асинхронно.
 */
public final class DataManager {
    private final PrisonClassic plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private Storage storage;

    public DataManager(PrisonClassic plugin) {
        this.plugin = plugin;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("storage");
        String type = c == null ? "yaml" : c.getString("type", "yaml");
        if ("mysql".equalsIgnoreCase(type)) {
            try {
                this.storage = new MySqlStorage(plugin, c.getConfigurationSection("mysql") == null ? c.createSection("mysql") : c.getConfigurationSection("mysql"));
            } catch (Exception e) {
                plugin.getLogger().severe("MySQL недоступен (" + e.getMessage() + "), используется YAML.");
                this.storage = new YamlStorage(plugin);
            }
        } else {
            this.storage = new YamlStorage(plugin);
        }
        plugin.getLogger().info("Хранилище данных игроков: " + this.storage.name());
    }

    public Storage storage() {
        return this.storage;
    }

    public PlayerData get(UUID uuid) {
        return this.cache.get(uuid);
    }

    public PlayerData get(Player player) {
        return this.cache.get(player.getUniqueId());
    }

    public Collection<PlayerData> all() {
        return this.cache.values();
    }

    /** Можно вызывать из AsyncPlayerPreLoginEvent. */
    public PlayerData load(UUID uuid, String name) {
        PlayerData cached = this.cache.get(uuid);
        if (cached != null) {
            cached.name = name;
            return cached;
        }
        YamlConfiguration y = this.storage.load(uuid);
        PlayerData d = y != null ? PlayerData.read(uuid, y) : new PlayerData(uuid);
        d.name = name;
        this.cache.put(uuid, d);
        return d;
    }

    public void unload(UUID uuid) {
        PlayerData d = this.cache.remove(uuid);
        if (d != null) {
            d.lastSeen = System.currentTimeMillis();
            this.save(d, true);
        }
    }

    private YamlConfiguration snapshot(PlayerData d) {
        Player p = Bukkit.getPlayer(d.uuid);
        if (p != null) {
            d.food = p.getFoodLevel();
            d.saturation = p.getSaturation();
        }
        YamlConfiguration y = new YamlConfiguration();
        d.write(y);
        return y;
    }

    public void save(PlayerData d, boolean async) {
        YamlConfiguration y = this.snapshot(d);
        if (async && this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.storage.save(d.uuid, y, d));
        } else {
            this.storage.save(d.uuid, y, d);
        }
    }

    public void saveAll(boolean async) {
        for (PlayerData d : this.cache.values()) {
            this.save(d, async);
        }
    }

    public void startAutosave() {
        long period = this.plugin.getConfig().getLong("autosave-minutes", 5L) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this.plugin, () -> this.saveAll(true), period, period);
    }

    public void close() {
        this.storage.close();
    }
}
