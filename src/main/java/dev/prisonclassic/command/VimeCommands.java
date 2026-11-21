package dev.prisonclassic.command;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class VimeCommands implements TabExecutor {
    private final PrisonClassic plugin;

    public VimeCommands(PrisonClassic var1) {
        this.plugin = var1;
    }

    @Override
    public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
        if (!(var1 instanceof Player var5)) {
            var1.sendMessage("Только для игроков.");
            return true;
        } else {
            PlayerData var6 = this.plugin.data().get(var5);
            if (var6 == null) {
                return true;
            } else {
                String var7 = var2.getName().toLowerCase(Locale.ROOT);
                switch (var7) {
                    case "up":
                        this.plugin.upgrades().open(var5);
                        break;
                    case "shop":
                        this.plugin.shop().open(var5);
                        break;
                    case "auc":
                        if (var4.length >= 2 && var4[0].equalsIgnoreCase("sell")) {
                            this.plugin.auction().sell(var5, var4[1]);
                        } else {
                            this.plugin.auction().open(var5, 0);
                        }
                        break;
                    case "pmine":
                        this.plugin.privateMines().open(var5);
                        break;
                    case "church":
                        Location var12 = this.plugin.church().church();
                        if (var12 == null) {
                            var5.sendMessage(Msg.parse("<red>Церковь ещё не построена."));
                        } else {
                            this.plugin.donate().go(var5, var12);
                        }
                        break;
                    case "needs":
                        var5.sendMessage(Msg.parse("<gold>Нужды <gray>(0 — всё хорошо, 70+ — штрафы):"));
                        var5.sendMessage(
                            Msg.parse(
                                "<gray>Туалет: <white>"
                                    + (int)var6.needToilet
                                    + " <gray>| Душ: <white>"
                                    + (int)var6.needShower
                                    + " <gray>| Сон: <white>"
                                    + (int)var6.needSleep
                            )
                        );
                        var5.sendMessage(Msg.parse("<gray>Болезнь: " + (var6.sick ? "<red>да — лекарство в церкви (/church)" : "<green>нет")));
                        break;
                    case "gang":
                        this.plugin.gangs().command(var5, var4);
                        break;
                    case "g":
                        if (var4.length == 0) {
                            var5.sendMessage(Msg.parse("<red>/g <сообщение банде>"));
                        } else {
                            this.plugin.gangs().chat(var5, String.join(" ", var4));
                        }
                        break;
                    case "family":
                        this.plugin.families().command(var5, var4);
                        break;
                    case "six":
                        this.plugin.sixes().open(var5);
                        break;
                    case "sieve":
                        if (this.plugin.cells().ownedBy(var5.getUniqueId()) == null) {
                            var5.sendMessage(Msg.parse("<red>Сито стоит в личной камере. /cell list"));
                        } else {
                            this.plugin.sixes().sieve(var5);
                        }
                        break;
                    case "awaken":
                        this.plugin.awaken().open(var5);
                        break;
                    case "lufonia":
                        int var11 = this.plugin.getConfig().getInt("awaken.min-level", 20);
                        Location var13 = this.plugin.locations().get("lufonia");
                        if (var6.level < var11) {
                            var5.sendMessage(Msg.parse("<red>Деревня Люфония доступна с " + var11 + " уровня."));
                        } else if (var13 == null) {
                            var5.sendMessage(Msg.parse("<red>Деревня ещё не построена."));
                        } else {
                            this.plugin.donate().go(var5, var13);
                        }
                        break;
                    case "warp":
                        this.plugin.menus().warps(var5);
                        break;
                    case "points":
                        var5.sendMessage(Msg.parse("<gold>Точки захвата на Базе:"));

                        for (String var10 : this.plugin.points().describe()) {
                            var5.sendMessage(Msg.parse(var10));
                        }
                }

                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
        String var5 = var2.getName().toLowerCase(Locale.ROOT);
        if (var5.equals("auc") && var4.length == 1) {
            return List.of("sell");
        } else if (var5.equals("gang") && var4.length == 1) {
            return List.of(
                "create", "invite", "accept", "deny", "leave", "kick", "transfer", "disband", "deposit", "withdraw", "upgrade", "home", "top", "info"
            );
        } else if (var5.equals("family") && var4.length == 1) {
            return List.of("propose", "accept", "deny", "tp", "msg", "divorce");
        } else if ((var5.equals("gang") || var5.equals("family")) && var4.length == 2) {
            ArrayList var6 = new ArrayList();
            Bukkit.getOnlinePlayers().forEach(var1x -> var6.add(var1x.getName()));
            return var6;
        } else {
            return List.of();
        }
    }
}
