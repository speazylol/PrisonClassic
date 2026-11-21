package dev.prisonclassic.command;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** Команды модулей ТЗ: /dungeon, /fw (войны Семей), /ttt (крестики-нолики), /collection, /donate. */
public final class SpecCommands implements TabExecutor {
    private final PrisonClassic plugin;

    public SpecCommands(PrisonClassic plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return true;
        }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "dungeon" -> this.plugin.dungeon().command(p, args);
            case "fw" -> this.plugin.familyWar().command(p, args);
            case "ttt" -> this.plugin.ttt().command(p, args);
            case "collection" -> this.plugin.collections().open(p);
            case "donate" -> this.plugin.donate().open(p);
            default -> {
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            switch (name) {
                case "dungeon" -> out.addAll(List.of("invite", "accept", "leave", "start", "top", "info"));
                case "fw" -> out.addAll(List.of("list", "attack"));
                case "ttt" -> {
                    out.addAll(List.of("accept", "deny"));
                    Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
                }
                default -> {
                }
            }
        } else if (args.length == 2) {
            if (name.equals("dungeon") && args[0].equalsIgnoreCase("invite")) {
                Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            } else if (name.equals("fw") && args[0].equalsIgnoreCase("attack")) {
                out.addAll(this.plugin.familyWar().ids());
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
