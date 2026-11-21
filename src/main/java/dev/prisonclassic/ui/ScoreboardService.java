package dev.prisonclassic.ui;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.cell.Cell;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.faction.Faction;
import dev.prisonclassic.util.Msg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class ScoreboardService {
    private final PrisonClassic plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private List<String> lines;
    private String title;

    public ScoreboardService(PrisonClassic var1) {
        this.plugin = var1;
        this.reload();
    }

    public void reload() {
        this.lines = new java.util.ArrayList<>(this.plugin.getConfig().getStringList("scoreboard.lines"));
        // Звёзды — предмет в инвентаре, на скорборде не показываются
        this.lines.removeIf(l -> l.contains("<stars>"));
        if (this.lines.size() > 15) {
            this.lines = this.lines.subList(0, 15);
        }

        this.title = this.plugin.getConfig().getString("scoreboard.title", "<gold>PRISON");
    }

    public void start() {
        long var1 = this.plugin.getConfig().getLong("scoreboard.update-ticks", 20L);
        Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            for (Player var2 : Bukkit.getOnlinePlayers()) {
                this.update(var2);
            }
        }, 20L, var1);
    }

    public void add(Player var1) {
        Scoreboard var2 = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective var3 = var2.registerNewObjective("prison", Criteria.DUMMY, Msg.parse(this.title));
        var3.setDisplaySlot(DisplaySlot.SIDEBAR);
        var3.numberFormat(NumberFormat.blank());

        for (int var4 = 0; var4 < this.lines.size(); var4++) {
            String var5 = "§" + Integer.toHexString(var4) + "§r";
            Team var6 = var2.registerNewTeam("l" + var4);
            var6.addEntry(var5);
            var3.getScore(var5).setScore(this.lines.size() - var4);
        }

        this.boards.put(var1.getUniqueId(), var2);
        var1.setScoreboard(var2);
        this.update(var1);
    }

    public void remove(Player var1) {
        this.boards.remove(var1.getUniqueId());
        var1.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void shutdown() {
        for (Player var2 : Bukkit.getOnlinePlayers()) {
            this.remove(var2);
        }
    }

    public void update(Player var1) {
        Scoreboard var2 = this.boards.get(var1.getUniqueId());
        PlayerData var3 = this.plugin.data().get(var1);
        if (var2 != null && var3 != null) {
            Faction var4 = this.plugin.factions().get(var3.faction);
            Cell var5 = this.plugin.cells().ownedBy(var1.getUniqueId());
            String var6 = var3.level >= this.plugin.levels().max() ? "MAX" : var3.blocksMined + "/" + this.plugin.levels().blocksNeeded(var3.level);
            String var7 = this.plugin.boosters().describe();
            TagResolver var8 = TagResolver.resolver(
                new TagResolver[]{
                    Msg.p("player", var1.getName()),
                    Msg.p("level", this.plugin.levels().name(var3.level)),
                    Msg.p("balance", Msg.money(var3.balance)),
                    Msg.p("blocks", var3.blocksMined),
                    Msg.p("progress", var6),
                    Msg.p("boss_kills", var3.bossKills),
                    Msg.p("stars", var3.stars),
                    Msg.p("kills", var3.kills),
                    Msg.p("cell", var5 == null ? "нет" : var5.name),
                    Msg.p("booster", var7),
                    Msg.pm("needs", this.plugin.needs().line(var3)),
                    Msg.pm("sick", var3.sick ? "<red>болен" : "<green>здоров"),
                    Msg.p("gang", this.plugin.gangs().tag(var3)),
                    Msg.p("partner", this.plugin.families().partnerName(var3)),
                    Msg.pm("faction", var4 == null ? "<gray>нет" : var4.tag()),
                    Msg.pm("church", this.plugin.church().buffLine(var3)),
                    Msg.p("collection", var3.collection.size() + "/30"),
                    Msg.p("hp", (int)this.plugin.levels().maxHealth(var3.level)),
                    Msg.pm("premium", this.plugin.sell().premium(var1, var3) ? "<gold>AutoSell" : "<gray>нет")
                }
            );

            for (int var9 = 0; var9 < this.lines.size(); var9++) {
                Team var10 = var2.getTeam("l" + var9);
                if (var10 != null) {
                    Component var11 = Msg.parse(this.lines.get(var9), var8);
                    var10.prefix(var11);
                }
            }
        }
    }
}
