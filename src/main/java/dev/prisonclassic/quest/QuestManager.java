package dev.prisonclassic.quest;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class QuestManager {
    private final PrisonClassic plugin;
    private final Map<String, QuestManager.Def> pool = new LinkedHashMap<>();

    public QuestManager(PrisonClassic var1) {
        this.plugin = var1;
        this.reload();
    }

    public void reload() {
        this.pool.clear();
        ConfigurationSection var1 = this.plugin.getConfig().getConfigurationSection("quests.pool");
        if (var1 != null) {
            for (String var3 : var1.getKeys(false)) {
                ConfigurationSection var4 = var1.getConfigurationSection(var3);
                if (var4 != null) {
                    try {
                        this.pool
                            .put(
                                var3,
                                new QuestManager.Def(
                                    var3,
                                    QuestType.valueOf(var4.getString("type", "MINE").toUpperCase()),
                                    var4.getDouble("amount", 100.0),
                                    var4.getDouble("reward", 100.0),
                                    var4.getString("desc", var3)
                                )
                            );
                    } catch (IllegalArgumentException var6) {
                        this.plugin.getLogger().warning("quests.pool." + var3 + ": неизвестный тип");
                    }
                }
            }
        }
    }

    public void ensureDaily(PlayerData var1) {
        long var2 = LocalDate.now().toEpochDay();
        if (var1.questDay != var2 || var1.activeQuests.isEmpty()) {
            var1.questDay = var2;
            var1.activeQuests.clear();
            var1.questProgress.clear();
            var1.questDone.clear();
            ArrayList var4 = new ArrayList<>(this.pool.keySet());
            Collections.shuffle(var4);
            int var5 = Math.min(this.plugin.getConfig().getInt("quests.daily-count", 3), var4.size());
            var1.activeQuests.addAll(var4.subList(0, var5));
        }
    }

    public void progress(Player var1, PlayerData var2, QuestType var3, double var4) {
        this.ensureDaily(var2);

        for (String var7 : var2.activeQuests) {
            QuestManager.Def var8 = this.pool.get(var7);
            if (var8 != null && var8.type() == var3 && !var2.questDone.contains(var7)) {
                double var9 = var2.questProgress.merge(var7, var4, Double::sum);
                if (var9 >= var8.amount()) {
                    var2.questDone.add(var7);
                    var2.balance = var2.balance + var8.reward();
                    Msg.send(var1, "quest-done", Msg.p("quest", var8.desc()), Msg.p("reward", Msg.money(var8.reward())));
                }
            }
        }
    }

    public List<Component> describe(PlayerData var1) {
        this.ensureDaily(var1);
        ArrayList var2 = new ArrayList();

        for (String var4 : var1.activeQuests) {
            QuestManager.Def var5 = this.pool.get(var4);
            if (var5 != null) {
                if (var1.questDone.contains(var4)) {
                    var2.add(Msg.parse("<green>✔ <gray><strikethrough>" + var5.desc()));
                } else {
                    double var6 = var1.questProgress.getOrDefault(var4, 0.0);
                    var2.add(
                        Msg.parse(
                            "<yellow>➤ <white>" + var5.desc() + " <gray>(" + (long)var6 + "/" + (long)var5.amount() + ") <green>+" + Msg.money(var5.reward())
                        )
                    );
                }
            }
        }

        return var2;
    }

    public record Def(String id, QuestType type, double amount, double reward, String desc) {
    }
}
