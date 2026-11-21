package dev.prisonclassic.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlayerData {
    public final UUID uuid;
    public String name = "?";
    public int level = 1;
    public double balance = 0.0;
    public String faction;
    public long blocksMined = 0L;
    public long blocksToday = 0L;
    public long blocksDay = -1L;
    public int bossKills = 0;
    public int kills = 0;
    public int deaths = 0;
    public boolean autosell = false;
    public long stars = 0L;
    public final Map<String, Integer> keys = new HashMap<>();
    public final Map<String, Integer> perks = new HashMap<>();
    public double coinBoost = 0.0;
    public double blockBoost = 0.0;
    public long coinBoostUntil = 0L;
    public long blockBoostUntil = 0L;
    public final List<String> endChest = new ArrayList<>();
    public final Set<String> milestones = new HashSet<>();
    public long activeCooldown = 0L;
    public int donate = 0;
    public final Map<String, Integer> mobKills = new HashMap<>();
    public final Map<String, Long> stats = new HashMap<>();
    public double needToilet = 0.0;
    public double needShower = 0.0;
    public double needSleep = 0.0;
    public boolean sick = false;
    public boolean starterKit = false;
    public long roosterUntil = 0L;
    public long critBonusUntil = 0L;
    public long privateMineSeconds = 0L;
    public long privateMineDay = -1L;
    public long privateMineUsedToday = 0L;
    public String gang = null;
    public UUID partner = null;
    public String partnerName = null;
    public final List<Integer> sixes = new ArrayList<>();
    public double sixFood = 0.0;
    public double sixGravel = 0.0;
    public long sixLast = 0L;
    public long questDay = -1L;
    public final List<String> activeQuests = new ArrayList<>();
    public final Map<String, Double> questProgress = new HashMap<>();
    public final Set<String> questDone = new HashSet<>();
    // ===== ТЗ: потиповой пул блоков, сытость, коллекции, баффы, подземелье =====
    public final Map<String, Long> blockTypes = new HashMap<>();
    public int food = 20;
    public float saturation = 5.0F;
    public final Set<String> collection = new HashSet<>();
    public final Set<String> branchesDone = new HashSet<>();
    public long luckUntil = 0L;
    public long churchBoostMs = 0L;
    public long autosellPremiumUntil = 0L;
    public long donateDailyDay = -1L;
    public long basementUntil = 0L;
    public boolean dirty = false;
    public long totemCooldown = 0L;
    public int dungeonClears = 0;
    public long dungeonBestMs = 0L;
    public long dungeonFirstClear = 0L;
    public long lastSeen = 0L;

    public PlayerData(UUID var1) {
        this.uuid = var1;
    }

    public int key(String var1) {
        return this.keys.getOrDefault(var1, 0);
    }

    public void addKey(String var1, int var2) {
        this.keys.merge(var1, var2, Integer::sum);
    }

    public int perk(String var1) {
        return this.perks.getOrDefault(var1, 0);
    }

    public int mob(String var1) {
        return this.mobKills.getOrDefault(var1, 0);
    }

    public long stat(String var1) {
        return this.stats.getOrDefault(var1, 0L);
    }

    public long mined(String material) {
        return this.blockTypes.getOrDefault(material.toUpperCase(java.util.Locale.ROOT), 0L);
    }

    public void write(YamlConfiguration var1) {
        var1.set("name", this.name);
        var1.set("level", this.level);
        var1.set("balance", this.balance);
        var1.set("faction", this.faction);
        var1.set("blocks", this.blocksMined);
        var1.set("blocks-today", this.blocksToday);
        var1.set("blocks-day", this.blocksDay);
        var1.set("boss-kills", this.bossKills);
        var1.set("kills", this.kills);
        var1.set("deaths", this.deaths);
        var1.set("autosell", this.autosell);
        var1.set("stars", this.stars);

        for (Entry var3 : this.keys.entrySet()) {
            var1.set("keys." + (String)var3.getKey(), var3.getValue());
        }

        for (Entry var8 : this.perks.entrySet()) {
            var1.set("perks." + (String)var8.getKey(), var8.getValue());
        }

        var1.set("boost.coin", this.coinBoost);
        var1.set("boost.coin-until", this.coinBoostUntil);
        var1.set("boost.block", this.blockBoost);
        var1.set("boost.block-until", this.blockBoostUntil);
        var1.set("endchest", this.endChest);
        var1.set("milestones", new ArrayList<>(this.milestones));
        var1.set("quests.day", this.questDay);
        var1.set("quests.active", this.activeQuests);
        var1.set("quests.done", new ArrayList<>(this.questDone));

        for (Entry var9 : this.questProgress.entrySet()) {
            var1.set("quests.progress." + (String)var9.getKey(), var9.getValue());
        }

        for (Entry var10 : this.mobKills.entrySet()) {
            var1.set("mob-kills." + (String)var10.getKey(), var10.getValue());
        }

        for (Entry var11 : this.stats.entrySet()) {
            var1.set("stats." + (String)var11.getKey(), var11.getValue());
        }

        var1.set("needs.toilet", this.needToilet);
        var1.set("needs.shower", this.needShower);
        var1.set("needs.sleep", this.needSleep);
        var1.set("sick", this.sick);
        var1.set("starter-kit", this.starterKit);
        var1.set("private-mine.seconds", this.privateMineSeconds);
        var1.set("private-mine.day", this.privateMineDay);
        var1.set("private-mine.used", this.privateMineUsedToday);
        var1.set("gang", this.gang);
        var1.set("partner", this.partner == null ? null : this.partner.toString());
        var1.set("partner-name", this.partnerName);
        var1.set("sixes.levels", this.sixes);
        var1.set("sixes.food", this.sixFood);
        var1.set("sixes.gravel", this.sixGravel);
        var1.set("sixes.last", this.sixLast);
        var1.set("uuid", this.uuid.toString());
        for (Entry<String, Long> e : this.blockTypes.entrySet()) {
            var1.set("block-types." + e.getKey(), e.getValue());
        }
        var1.set("food", this.food);
        var1.set("saturation", (double)this.saturation);
        var1.set("collection.shards", new ArrayList<>(this.collection));
        var1.set("collection.branches", new ArrayList<>(this.branchesDone));
        var1.set("buffs.luck-until", this.luckUntil);
        var1.set("buffs.church-ms", this.churchBoostMs);
        var1.set("buffs.autosell-premium-until", this.autosellPremiumUntil);
        var1.set("donate.daily-day", this.donateDailyDay);
        var1.set("buffs.basement-until", this.basementUntil);
        var1.set("needs.dirty", this.dirty);
        var1.set("totem-cooldown", this.totemCooldown);
        var1.set("dungeon.clears", this.dungeonClears);
        var1.set("dungeon.best-ms", this.dungeonBestMs);
        var1.set("dungeon.first-clear", this.dungeonFirstClear);
        var1.set("last-seen", this.lastSeen);
    }

    public static PlayerData read(UUID var0, YamlConfiguration var1) {
        PlayerData var2 = new PlayerData(var0);
        var2.name = var1.getString("name", "?");
        var2.level = Math.max(1, var1.getInt("level", 1));
        var2.balance = var1.getDouble("balance", 0.0);
        var2.faction = var1.getString("faction");
        var2.blocksMined = var1.getLong("blocks", 0L);
        var2.blocksToday = var1.getLong("blocks-today", 0L);
        var2.blocksDay = var1.getLong("blocks-day", -1L);
        var2.bossKills = var1.getInt("boss-kills", 0);
        var2.kills = var1.getInt("kills", 0);
        var2.deaths = var1.getInt("deaths", 0);
        var2.autosell = var1.getBoolean("autosell", false);
        var2.stars = var1.getLong("stars", 0L);
        ConfigurationSection var3 = var1.getConfigurationSection("keys");
        if (var3 != null) {
            for (String var5 : var3.getKeys(false)) {
                var2.keys.put(var5, var3.getInt(var5));
            }
        }

        ConfigurationSection var11 = var1.getConfigurationSection("perks");
        if (var11 != null) {
            for (String var6 : var11.getKeys(false)) {
                var2.perks.put(var6, var11.getInt(var6));
            }
        }

        var2.coinBoost = var1.getDouble("boost.coin", 0.0);
        var2.coinBoostUntil = var1.getLong("boost.coin-until", 0L);
        var2.blockBoost = var1.getDouble("boost.block", 0.0);
        var2.blockBoostUntil = var1.getLong("boost.block-until", 0L);
        var2.endChest.addAll(var1.getStringList("endchest"));
        var2.milestones.addAll(var1.getStringList("milestones"));
        var2.questDay = var1.getLong("quests.day", -1L);
        var2.activeQuests.addAll(var1.getStringList("quests.active"));
        var2.questDone.addAll(var1.getStringList("quests.done"));
        ConfigurationSection var13 = var1.getConfigurationSection("quests.progress");
        if (var13 != null) {
            for (String var7 : var13.getKeys(false)) {
                var2.questProgress.put(var7, var13.getDouble(var7));
            }
        }

        ConfigurationSection var15 = var1.getConfigurationSection("mob-kills");
        if (var15 != null) {
            for (String var8 : var15.getKeys(false)) {
                var2.mobKills.put(var8, var15.getInt(var8));
            }
        }

        ConfigurationSection var17 = var1.getConfigurationSection("stats");
        if (var17 != null) {
            for (String var9 : var17.getKeys(false)) {
                var2.stats.put(var9, var17.getLong(var9));
            }
        }

        var2.needToilet = var1.getDouble("needs.toilet", 0.0);
        var2.needShower = var1.getDouble("needs.shower", 0.0);
        var2.needSleep = var1.getDouble("needs.sleep", 0.0);
        var2.sick = var1.getBoolean("sick", false);
        var2.starterKit = var1.getBoolean("starter-kit", false);
        var2.privateMineSeconds = var1.getLong("private-mine.seconds", 0L);
        var2.privateMineDay = var1.getLong("private-mine.day", -1L);
        var2.privateMineUsedToday = var1.getLong("private-mine.used", 0L);
        var2.gang = var1.getString("gang");
        String var19 = var1.getString("partner");

        try {
            var2.partner = var19 == null ? null : UUID.fromString(var19);
        } catch (IllegalArgumentException var10) {
        }

        var2.partnerName = var1.getString("partner-name");
        var2.sixes.addAll(var1.getIntegerList("sixes.levels"));
        var2.sixFood = var1.getDouble("sixes.food", 0.0);
        var2.sixGravel = var1.getDouble("sixes.gravel", 0.0);
        var2.sixLast = var1.getLong("sixes.last", 0L);
        ConfigurationSection bt = var1.getConfigurationSection("block-types");
        if (bt != null) {
            for (String k : bt.getKeys(false)) {
                var2.blockTypes.put(k, bt.getLong(k));
            }
        }
        var2.food = var1.getInt("food", 20);
        var2.saturation = (float)var1.getDouble("saturation", 5.0);
        var2.collection.addAll(var1.getStringList("collection.shards"));
        var2.branchesDone.addAll(var1.getStringList("collection.branches"));
        var2.luckUntil = var1.getLong("buffs.luck-until", 0L);
        var2.churchBoostMs = var1.getLong("buffs.church-ms", 0L);
        var2.autosellPremiumUntil = var1.getLong("buffs.autosell-premium-until", 0L);
        var2.donateDailyDay = var1.getLong("donate.daily-day", -1L);
        var2.basementUntil = var1.getLong("buffs.basement-until", 0L);
        var2.dirty = var1.getBoolean("needs.dirty", false);
        var2.totemCooldown = var1.getLong("totem-cooldown", 0L);
        var2.dungeonClears = var1.getInt("dungeon.clears", 0);
        var2.dungeonBestMs = var1.getLong("dungeon.best-ms", 0L);
        var2.dungeonFirstClear = var1.getLong("dungeon.first-clear", 0L);
        var2.lastSeen = var1.getLong("last-seen", 0L);
        return var2;
    }
}
