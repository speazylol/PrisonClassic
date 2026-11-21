package dev.prisonclassic.faction;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class FactionManager {
    private final PrisonClassic plugin;
    private final Map<String, Faction> factions = new LinkedHashMap<>();

    public FactionManager(PrisonClassic var1) {
        this.plugin = var1;
        this.reload();
    }

    public void reload() {
        this.factions.clear();
        ConfigurationSection var1 = this.plugin.getConfig().getConfigurationSection("factions.list");
        if (var1 != null) {
            for (String var3 : var1.getKeys(false)) {
                ConfigurationSection var4 = var1.getConfigurationSection(var3);
                if (var4 != null) {
                    this.factions
                        .put(
                            var3.toLowerCase(),
                            new Faction(
                                var3.toLowerCase(),
                                var4.getString("name", var3),
                                var4.getString("color", "white"),
                                var4.getDouble("sell-bonus", 1.0),
                                var4.getDouble("boss-bonus", 1.0),
                                var4.getDouble("damage-bonus", 1.0)
                            )
                        );
                }
            }
        }
    }

    public Faction get(String var1) {
        return var1 == null ? null : this.factions.get(var1.toLowerCase());
    }

    public Collection<Faction> all() {
        return this.factions.values();
    }

    public Location base(Faction var1) {
        return this.plugin.locations().get("base." + var1.id());
    }

    public void join(Player var1, PlayerData var2, Faction var3) {
        if (var2.faction != null) {
            Msg.send(var1, "faction-already");
        } else {
            int var4 = this.plugin.getConfig().getInt("factions.min-level", 5);
            if (var2.level < var4) {
                var1.sendMessage(Msg.parse("<red>Вступить во фракцию можно с " + var4 + " уровня."));
            } else {
                var2.faction = var3.id();
                Msg.send(var1, "faction-joined", Msg.pm("faction", var3.tag()));
            }
        }
    }

    public void leave(Player var1, PlayerData var2) {
        if (var2.faction == null) {
            Msg.send(var1, "faction-none");
        } else {
            double var3 = this.plugin.getConfig().getDouble("factions.leave-cost", 5000.0);
            if (var2.balance < var3) {
                Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var3)));
            } else {
                var2.balance -= var3;
                var2.faction = null;
                Msg.send(var1, "faction-left", Msg.p("cost", Msg.money(var3)));
            }
        }
    }
}
