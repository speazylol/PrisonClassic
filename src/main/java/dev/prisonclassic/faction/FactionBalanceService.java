package dev.prisonclassic.faction;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Динамическая фракционная скидка (/lvl, тренер, /up).
 *
 * Раз в минуту снимается онлайн каждой фракции и обновляется экспоненциальное скользящее среднее
 * (EMA, окно factions.balance.window-hours, по умолчанию 168 ч = 7 суток):
 *   avg_f = avg_f + alpha * (online_f - avg_f),  alpha = 2 / (N + 1),  N = окно в минутах.
 * Скидка фракции:
 *   mean = среднее avg по всем фракциям;
 *   discount_f = clamp( (mean - avg_f) / mean * factor, 0, max ).
 * Чем меньше исторический онлайн фракции относительно других, тем больше её скидка.
 */
public final class FactionBalanceService {
    private final PrisonClassic plugin;
    private final File file;
    private final Map<String, Double> avg = new HashMap<>();
    private long samples = 0L;

    public FactionBalanceService(PrisonClassic plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "faction-online.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        if (y.isConfigurationSection("avg")) {
            for (String k : y.getConfigurationSection("avg").getKeys(false)) {
                this.avg.put(k, y.getDouble("avg." + k));
            }
        }
        this.samples = y.getLong("samples", 0L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);
    }

    private FileConfiguration c() {
        return this.plugin.getConfig();
    }

    private void sample() {
        Map<String, Integer> online = new HashMap<>();
        for (Faction f : this.plugin.factions().all()) {
            online.put(f.id(), 0);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = this.plugin.data().get(p);
            if (d != null && d.faction != null && online.containsKey(d.faction)) {
                online.merge(d.faction, 1, Integer::sum);
            }
        }
        double window = Math.max(1.0, this.c().getDouble("factions.balance.window-hours", 168.0) * 60.0);
        double alpha = 2.0 / (window + 1.0);
        // На первых сэмплах окно короче, чтобы среднее быстро «разогрелось».
        alpha = Math.max(alpha, 1.0 / (this.samples + 1.0));
        for (Map.Entry<String, Integer> e : online.entrySet()) {
            double prev = this.avg.getOrDefault(e.getKey(), (double)e.getValue());
            this.avg.put(e.getKey(), prev + alpha * (e.getValue() - prev));
        }
        this.samples++;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Double> e : this.avg.entrySet()) {
            y.set("avg." + e.getKey(), e.getValue());
        }
        y.set("samples", this.samples);
        try {
            y.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("faction-online.yml: " + e.getMessage());
        }
    }

    public double averageOnline(String faction) {
        return faction == null ? 0.0 : this.avg.getOrDefault(faction, 0.0);
    }

    public double discount(String faction) {
        if (faction == null || !this.c().getBoolean("factions.balance.enabled", true) || this.avg.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int n = 0;
        for (Faction f : this.plugin.factions().all()) {
            sum += this.avg.getOrDefault(f.id(), 0.0);
            n++;
        }
        if (n == 0 || sum <= 0.0) {
            return 0.0;
        }
        double mean = sum / n;
        double mine = this.avg.getOrDefault(faction, 0.0);
        double raw = (mean - mine) / mean * this.c().getDouble("factions.balance.factor", 0.5);
        return Math.max(0.0, Math.min(this.c().getDouble("factions.balance.max-discount", 0.25), raw));
    }

    public double discount(PlayerData d) {
        return d == null ? 0.0 : this.discount(d.faction);
    }
}
