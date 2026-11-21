package dev.prisonclassic.command;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.cell.Cell;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.faction.Faction;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class PlayerCommands implements TabExecutor {
    private final PrisonClassic plugin;

    public PlayerCommands(PrisonClassic var1) {
        this.plugin = var1;
    }

    @Override
    public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
        if (var1 instanceof Player var5) {
            PlayerData var6 = this.plugin.data().get(var5);
            if (var6 == null) {
                return true;
            } else {
                String var7 = var2.getName().toLowerCase(Locale.ROOT);
                switch (var7) {
                    case "spawn":
                        this.spawn(var5);
                        break;
                    case "mine":
                        this.mine(var5, var6, var4);
                        break;
                    case "level":
                        this.level(var5, var6, var4);
                        break;
                    case "rankup":
                        this.plugin.levels().rankUp(var5, var6);
                        break;
                    case "sell":
                        this.sell(var5, var6);
                        break;
                    case "autosell":
                        this.autosell(var5, var6);
                        break;
                    case "balance":
                        Msg.send(var5, "balance", Msg.p("balance", Msg.money(var6.balance)));
                        break;
                    case "pay":
                        this.pay(var5, var6, var4);
                        break;
                    case "cell":
                        this.cell(var5, var6, var4);
                        break;
                    case "faction":
                        this.faction(var5, var6, var4);
                        break;
                    case "fleave":
                        this.plugin.factions().leave(var5, var6);
                        break;
                    case "base":
                        this.base(var5, var6);
                        break;
                    case "quest":
                        this.quest(var5, var6);
                }

                return true;
            }
        } else {
            var1.sendMessage("Только для игроков.");
            return true;
        }
    }

    private void spawn(Player var1) {
        Location var2 = this.plugin.spawn();
        if (var2 == null) {
            Msg.send(var1, "no-spawn");
        } else {
            this.plugin.donate().go(var1, var2);
        }
    }

    private void sell(Player var1, PlayerData var2) {
        if (!this.plugin.sell().canSell(var1, var2)) {
            // ТЗ: /sell только с флагом AutoSell_Premium; остальные продают вручную у торговца шахты
            Msg.send(var1, "sell-premium-only");
            return;
        }

        double var3 = this.plugin.sell().sellAll(var1, var2);
        if (var3 <= 0.0) {
            Msg.send(var1, "sell-nothing");
        } else {
            Msg.send(var1, "sell-done", Msg.p("amount", Msg.money(var3)), Msg.p("mult", String.format(Locale.US, "%.2f", this.plugin.sell().multiplier(var2))));
        }
    }

    private void autosell(Player var1, PlayerData var2) {
        if (!this.plugin.sell().premium(var1, var2)) {
            Msg.send(var1, "sell-premium-only");
        } else {
            var2.autosell = !var2.autosell;
            Msg.send(var1, var2.autosell ? "autosell-on" : "autosell-off");
        }
    }

    private void pay(Player var1, PlayerData var2, String[] var3) {
        if (var3.length < 2) {
            var1.sendMessage(Msg.parse("<red>/pay <игрок> <сумма>"));
        } else {
            Player var4 = Bukkit.getPlayerExact(var3[0]);
            PlayerData var5 = var4 == null ? null : this.plugin.data().get(var4);
            if (var5 == null) {
                Msg.send(var1, "player-offline");
            } else {
                double var6;
                try {
                    var6 = Double.parseDouble(var3[1]);
                } catch (NumberFormatException var9) {
                    Msg.send(var1, "bad-number");
                    return;
                }

                if (var6 <= 0.0 || var4.equals(var1)) {
                    Msg.send(var1, "bad-number");
                } else if (var2.balance < var6) {
                    Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var6)));
                } else {
                    var2.balance -= var6;
                    var5.balance += var6;
                    Msg.send(var1, "pay-sent", Msg.p("player", var4.getName()), Msg.p("amount", Msg.money(var6)));
                    Msg.send(var4, "pay-got", Msg.p("player", var1.getName()), Msg.p("amount", Msg.money(var6)));
                }
            }
        }
    }

    private void mine(Player var1, PlayerData var2, String[] var3) {
        if (var3.length == 0) {
            this.plugin.menus().mines(var1);
        } else {
            Mine var4 = this.plugin.mines().get(var3[0]);
            if (var4 == null) {
                Msg.send(var1, "mine-unknown");
            } else if (var2.level < var4.requiredLevel) {
                Msg.send(var1, "mine-locked", Msg.p("level", this.plugin.levels().name(var4.requiredLevel)), Msg.p("mine", var4.title));
            } else if (var4.name.startsWith("6")) {
                this.plugin.basement().enter(var1);
            } else {
                Location var5 = var4.spawn != null ? var4.spawn : this.plugin.spawn();
                if (var5 == null) {
                    Msg.send(var1, "no-spawn");
                } else {
                    this.plugin.donate().go(var1, var5);
                }
            }
        }
    }

    private void level(Player var1, PlayerData var2, String[] var3) {
        if (var3.length > 0 && var3[0].equalsIgnoreCase("up")) {
            this.plugin.levels().rankUp(var1, var2);
        } else if (var3.length == 0) {
            this.plugin.menus().level(var1);
        } else if (var2.level >= this.plugin.levels().max()) {
            Msg.send(var1, "level-max");
        } else {
            Msg.send(
                var1,
                "level-info",
                Msg.p("level", this.plugin.levels().name(var2.level)),
                Msg.p("next", this.plugin.levels().name(var2.level + 1)),
                Msg.p("blocks", var2.blocksMined),
                Msg.p("need_blocks", this.plugin.levels().blocksNeeded(var2.level)),
                Msg.p("cost", Msg.money(this.plugin.levels().levelPrice(var2)))
            );
        }
    }

    private void cell(Player var1, PlayerData var2, String[] var3) {
        String var4 = var3.length == 0 ? "tp" : var3[0].toLowerCase(Locale.ROOT);
        switch (var4) {
            case "tp":
                Cell var16 = this.plugin.cells().ownedBy(var1.getUniqueId());
                if (var16 == null || var16.spawn == null) {
                    Msg.send(var1, "cell-none");
                    return;
                }

                var1.teleport(var16.spawn);
                break;
            case "list":
                var1.sendMessage(Msg.parse("<gold>Камеры:"));

                for (Cell var20 : this.plugin.cells().all()) {
                    String var21 = "<green>свободна <gray>(" + Msg.money(var20.price) + ")";
                    if (var20.owner != null) {
                        OfflinePlayer var22 = Bukkit.getOfflinePlayer(var20.owner);
                        var21 = "<red>занята <gray>" + (var22.getName() == null ? "?" : var22.getName());
                    }

                    var1.sendMessage(Msg.parse("<white>" + var20.name + " " + var21));
                }
                break;
            case "buy":
                if (var3.length < 2) {
                    var1.sendMessage(Msg.parse("<red>/cell buy <название>"));
                    return;
                }

                Cell var14 = this.plugin.cells().get(var3[1]);
                if (var14 == null) {
                    Msg.send(var1, "cell-unknown");
                    return;
                }

                if (this.plugin.cells().ownedBy(var1.getUniqueId()) != null) {
                    Msg.send(var1, "cell-have");
                    return;
                }

                int var19 = this.plugin.getConfig().getInt("cells.min-level", 8);
                if (var2.level < var19) {
                    var1.sendMessage(Msg.parse("<red>Личная камера доступна с " + var19 + " уровня."));
                    return;
                }

                if (var14.owner != null) {
                    Msg.send(var1, "cell-taken");
                    return;
                }

                if (var2.balance < var14.price) {
                    Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var14.price)));
                    return;
                }

                var2.balance = var2.balance - var14.price;
                var14.owner = var1.getUniqueId();
                var14.level = 1;
                var14.members.clear();
                this.plugin.cells().save();
                Msg.send(var1, "cell-bought", Msg.p("cell", var14.name));
                if (var14.spawn != null) {
                    var1.teleport(var14.spawn);
                }
                break;
            case "add":
                Cell var13 = this.plugin.cells().ownedBy(var1.getUniqueId());
                if (var13 == null) {
                    Msg.send(var1, "cell-none");
                    return;
                }

                Player var18 = var3.length < 2 ? null : Bukkit.getPlayerExact(var3[1]);
                if (var18 == null) {
                    Msg.send(var1, "player-offline");
                    return;
                }

                var13.members.add(var18.getUniqueId());
                this.plugin.cells().save();
                Msg.send(var1, "cell-member-added", Msg.p("player", var18.getName()));
                break;
            case "remove":
                Cell var12 = this.plugin.cells().ownedBy(var1.getUniqueId());
                if (var12 == null) {
                    Msg.send(var1, "cell-none");
                    return;
                }

                if (var3.length < 2) {
                    var1.sendMessage(Msg.parse("<red>/cell remove <игрок>"));
                    return;
                }

                UUID var17 = null;

                for (UUID var10 : var12.members) {
                    String var11 = Bukkit.getOfflinePlayer(var10).getName();
                    if (var11 != null && var11.equalsIgnoreCase(var3[1])) {
                        var17 = var10;
                    }
                }

                if (var17 == null) {
                    Msg.send(var1, "player-offline");
                    return;
                }

                var12.members.remove(var17);
                this.plugin.cells().save();
                Msg.send(var1, "cell-member-removed", Msg.p("player", var3[1]));
                break;
            case "upgrade":
            case "menu":
                this.plugin.cellService().open(var1);
                break;
            case "leave":
                Cell var7 = this.plugin.cells().ownedBy(var1.getUniqueId());
                if (var7 == null) {
                    Msg.send(var1, "cell-none");
                    return;
                }

                double var8 = var7.price * this.plugin.getConfig().getDouble("cells.refund", 0.5);
                var2.balance += var8;
                var7.owner = null;
                var7.level = 1;
                var7.members.clear();
                this.plugin.cells().save();
                Msg.send(var1, "cell-left", Msg.p("amount", Msg.money(var8)));
                break;
            default:
                var1.sendMessage(Msg.parse("<red>/cell [menu|upgrade|buy|list|add|remove|leave]"));
        }
    }

    private void faction(Player var1, PlayerData var2, String[] var3) {
        if (var3.length >= 2 && var3[0].equalsIgnoreCase("join")) {
            Faction var6 = this.plugin.factions().get(var3[1]);
            if (var6 == null) {
                Msg.send(var1, "faction-unknown");
            } else {
                this.plugin.factions().join(var1, var2, var6);
            }
        } else if (var3.length == 0) {
            this.plugin.menus().factions(var1);
        } else {
            var1.sendMessage(Msg.parse("<gold>Фракции <gray>(/faction join <id>):"));

            for (Faction var5 : this.plugin.factions().all()) {
                var1.sendMessage(
                    Msg.parse(
                        "<white>"
                            + var5.id()
                            + " — "
                            + var5.tag()
                            + " <gray>| продажа x"
                            + var5.sellBonus()
                            + ", боссы x"
                            + var5.bossBonus()
                            + ", урон x"
                            + var5.damageBonus()
                    )
                );
            }
        }
    }

    private void base(Player var1, PlayerData var2) {
        if (var2.level < this.plugin.getConfig().getInt("factions.min-level", 5)) {
            var1.sendMessage(Msg.parse("<red>База доступна с " + this.plugin.getConfig().getInt("factions.min-level", 5) + " уровня."));
        } else {
            Faction var3 = this.plugin.factions().get(var2.faction);
            if (var3 == null) {
                Msg.send(var1, "faction-none");
            } else {
                Location var4 = this.plugin.factions().base(var3);
                if (var4 == null) {
                    Msg.send(var1, "no-base");
                } else {
                    this.plugin.donate().go(var1, var4);
                }
            }
        }
    }

    private void quest(Player var1, PlayerData var2) {
        var1.sendMessage(Msg.parse("<gold>Ежедневные квесты:"));

        for (Component var4 : this.plugin.quests().describe(var2)) {
            var1.sendMessage(var4);
        }

        String var5 = this.plugin.mining().nextMilestone(var2);
        var1.sendMessage(Msg.parse(var5 == null ? "<green>Все награды за блоки получены!" : "<yellow>Следующая награда за блоки: <white>" + var5));
    }

    @Override
    public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
        ArrayList<String> var5 = new ArrayList<>();
        String var6 = var2.getName().toLowerCase(Locale.ROOT);
        if (var6.equals("mine") && var4.length == 1) {
            this.plugin.mines().all().forEach(var1x -> var5.add(var1x.name));
        } else if (var6.equals("cell")) {
            if (var4.length == 1) {
                var5.addAll(List.of("menu", "upgrade", "list", "buy", "add", "remove", "leave"));
            } else if (var4.length == 2 && var4[0].equalsIgnoreCase("buy")) {
                this.plugin.cells().all().forEach(var1x -> var5.add(var1x.name));
            } else if (var4.length == 2 && var4[0].equalsIgnoreCase("add")) {
                Bukkit.getOnlinePlayers().forEach(var1x -> var5.add(var1x.getName()));
            }
        } else if (var6.equals("faction")) {
            if (var4.length == 1) {
                var5.add("join");
            } else if (var4.length == 2) {
                this.plugin.factions().all().forEach(var1x -> var5.add(var1x.id()));
            }
        } else if (var6.equals("level") && var4.length == 1) {
            var5.add("up");
        } else if (var6.equals("pay") && var4.length == 1) {
            Bukkit.getOnlinePlayers().forEach(var1x -> var5.add(var1x.getName()));
        }

        String var7 = var4.length == 0 ? "" : var4[var4.length - 1].toLowerCase(Locale.ROOT);
        var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var7));
        return var5;
    }
}
