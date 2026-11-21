package dev.prisonclassic.data;

import dev.prisonclassic.PrisonClassic;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlStorage implements Storage {
    private final PrisonClassic plugin;
    private final File dir;
    private final File dungeonFile;

    public YamlStorage(PrisonClassic plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
        this.dir.mkdirs();
        this.dungeonFile = new File(plugin.getDataFolder(), "dungeon-records.yml");
    }

    private File file(UUID uuid) {
        return new File(this.dir, uuid + ".yml");
    }

    @Override
    public YamlConfiguration load(UUID uuid) {
        File f = this.file(uuid);
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
    }

    @Override
    public void save(UUID uuid, YamlConfiguration snapshot, PlayerData indexed) {
        try {
            snapshot.save(this.file(uuid));
        } catch (IOException e) {
            this.plugin.getLogger().warning("Не удалось сохранить " + uuid + ": " + e.getMessage());
        }
    }

    @Override
    public List<PlayerData> loadAll() {
        List<PlayerData> out = new ArrayList<>();
        File[] files = this.dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                try {
                    UUID id = UUID.fromString(f.getName().replace(".yml", ""));
                    out.add(PlayerData.read(id, YamlConfiguration.loadConfiguration(f)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    @Override
    public synchronized void saveDungeonRecord(String party, long millis, long when) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.dungeonFile);
        List<Map<?, ?>> list = new ArrayList<>(y.getMapList("records"));
        list.add(Map.of("party", party, "ms", millis, "at", when));
        list.sort((a, b) -> Long.compare(((Number)a.get("ms")).longValue(), ((Number)b.get("ms")).longValue()));
        if (list.size() > 100) {
            list = new ArrayList<>(list.subList(0, 100));
        }
        y.set("records", list);
        try {
            y.save(this.dungeonFile);
        } catch (IOException e) {
            this.plugin.getLogger().warning("dungeon-records.yml: " + e.getMessage());
        }
    }

    @Override
    public synchronized List<String> topDungeon(int limit) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.dungeonFile);
        List<String> out = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("records")) {
            if (out.size() >= limit) {
                break;
            }
            out.add(m.get("party") + "|" + m.get("ms") + "|" + m.get("at"));
        }
        return out;
    }

    @Override
    public String name() {
        return "YAML";
    }

    @Override
    public void close() {
    }
}
