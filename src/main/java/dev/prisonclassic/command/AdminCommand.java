package dev.prisonclassic.command;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.booster.BoosterService;
import dev.prisonclassic.boss.BossManager;
import dev.prisonclassic.build.WorldBuilder;
import dev.prisonclassic.cell.Cell;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.enchant.Ench;
import dev.prisonclassic.faction.Faction;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Region;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AdminCommand implements TabExecutor {
    private final PrisonClassic plugin;

    public AdminCommand(PrisonClassic var1) {
        this.plugin = var1;
    }

    private void ok(CommandSender var1, String var2) {
        var1.sendMessage(Msg.parse("<green>" + var2));
    }

    private void err(CommandSender var1, String var2) {
        var1.sendMessage(Msg.parse("<red>" + var2));
    }

    private void help(CommandSender var1) {
        var1.sendMessage(
            Msg.parse(
                "<gold>/pa <gray>— команды:\n<yellow>build confirm <gray>— построить весь комплекс вокруг вас\n<yellow>wand <gray>— палочка выделения\n<yellow>setspawn <gray>— спавн сервера\n<yellow>mine create <имя> <ур> | delete | setspawn | setblock <имя> <блок> <вес> | removeblock | setreset <имя> <сек> <доля> | reset | list\n<yellow>cell create <имя> <цена> | delete | setspawn | list | free\n<yellow>boss types | add <тип> | remove <id> | list\n<yellow>faction setbase <id>\n<yellow>eco give|take|set <игрок> <сумма>\n<yellow>level <игрок> <n>\n<yellow>give book <игрок> <зачар> <ур> [шанс%] | dust <игрок> <10|100> [кол] | key <игрок> <common|dk|legendary> [кол] | stars <игрок> <кол>\n<yellow>give pbooster <игрок> <coin|block> <+доля> <минуты>  <gray>(-1 минут = навсегда)\n<yellow>give enchants <gray>— список зачарований\n<yellow>give booster <игрок> <coin|block> <local|global> <+доля> <45m|1d|3d|7d|30d|90d|year|wipe> [кол]\n<yellow>give treasure <игрок> <id> | pmine <игрок> [мин] | medicine <игрок> | kit <игрок> | kills <игрок> <моб> <n>\n<yellow>give special <игрок> <basement_pass|dungeon_pass|battle_pass|brain|guts> [кол] | shard <игрок> <id> | premium <игрок> <дни|-1>\n<yellow>booster <coin|block> <+доля> <45m> <gray>— глобальный бустер сразу\n<yellow>point set <id> <gray>— точка захвата на Базе | <yellow>setloc <ключ> <gray>— церковь, сундуки, NPC\n<yellow>reload"
            )
        );
    }

    @Override
    public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
        if (var4.length == 0) {
            this.help(var1);
            return true;
        } else {
            Player var5 = var1 instanceof Player var6 ? var6 : null;

            try {
                String var11 = var4[0].toLowerCase(Locale.ROOT);
                switch (var11) {
                    case "reload":
                        this.plugin.reloadAll();
                        this.ok(var1, "Конфиг перезагружен.");
                        break;
                    case "build":
                        this.build(var1, var5, var4);
                        break;
                    case "wand":
                        if (this.need(var5, var1)) {
                            var5.getInventory().addItem(new ItemStack[]{this.plugin.selection().wand()});
                        }
                        break;
                    case "setspawn":
                        if (this.need(var5, var1)) {
                            this.plugin.locations().set("spawn", var5.getLocation());
                            this.ok(var1, "Спавн установлен.");
                        }
                        break;
                    case "mine":
                        this.mine(var1, var5, var4);
                        break;
                    case "cell":
                        this.cell(var1, var5, var4);
                        break;
                    case "boss":
                        this.boss(var1, var5, var4);
                        break;
                    case "give":
                        this.give(var1, var4);
                        break;
                    case "faction":
                        if (var4.length >= 3 && var4[1].equalsIgnoreCase("setbase") && this.need(var5, var1)) {
                            Faction var13 = this.plugin.factions().get(var4[2]);
                            if (var13 == null) {
                                this.err(var1, "Нет такой фракции.");
                            } else {
                                this.plugin.locations().set("base." + var13.id(), var5.getLocation());
                                this.ok(var1, "База фракции " + var13.id() + " установлена.");
                            }
                        } else if (var4.length < 3) {
                            this.help(var1);
                        }
                        break;
                    case "eco":
                        this.eco(var1, var4);
                        break;
                    case "level":
                        PlayerData var12 = var4.length >= 3 ? this.dataOf(var4[1]) : null;
                        if (var12 == null) {
                            this.err(var1, "/pa level <игрок> <n> (игрок должен быть онлайн)");
                        } else {
                            var12.level = Math.max(1, Math.min(this.plugin.levels().max(), Integer.parseInt(var4[2])));
                            Player var9 = Bukkit.getPlayer(var12.uuid);
                            if (var9 != null) {
                                this.plugin.levels().applyBonuses(var9, var12);
                            }

                            this.ok(var1, "Уровень игрока " + var12.name + " = " + var12.level);
                        }
                        break;
                    case "booster":
                        if (var4.length < 4) {
                            this.err(var1, "/pa booster <coin|block> <+доля, напр. 1 = x2> <длительность: 45m, 2h, 1d>");
                        } else if (!this.plugin
                            .boosters()
                            .startGlobal(
                                var4[1].equalsIgnoreCase("coin"),
                                Double.parseDouble(var4[2]),
                                Math.max(60L, BoosterService.parseDuration(var4[3])),
                                var1.getName()
                            )) {
                            this.err(var1, "Уже идёт глобальный бустер с другим множителем.");
                        }
                        break;
                    case "point":
                        if (var4.length < 3 || !var4[1].equalsIgnoreCase("set") || !this.need(var5, var1)) {
                            this.err(var1, "/pa point set <id>  (точки: " + this.plugin.points().ids() + ")");
                        } else if (!this.plugin.points().ids().contains(var4[2])) {
                            this.err(var1, "Нет такой точки. Точки: " + this.plugin.points().ids());
                        } else {
                            this.plugin.locations().set("point." + var4[2], var5.getLocation());
                            this.plugin.points().refreshHolograms();
                            this.ok(var1, "Точка " + var4[2] + " установлена.");
                        }
                        break;
                    case "setloc":
                        if (var4.length >= 2 && this.need(var5, var1)) {
                            Location var8 = var4[1].startsWith("crate.")
                                ? (var5.getTargetBlockExact(5) == null ? null : var5.getTargetBlockExact(5).getLocation())
                                : var5.getLocation();
                            if (var8 == null) {
                                this.err(var1, "Посмотрите на сундук.");
                            } else {
                                this.plugin.locations().set(var4[1], var8);
                                if (var4[1].startsWith("npc.")) {
                                    this.plugin.npc().refresh();
                                }

                                this.ok(var1, "Точка " + var4[1] + " сохранена.");
                            }
                        } else {
                            this.err(
                                var1,
                                "/pa setloc <ключ>: church, church.a/b (кубоид), crate.*, npc.(shop|seller|priest|smith|awaken|quest|trainer|gypsy|donate|dungeon), npc.merchant.<шахта>, sign.merchant.<шахта>, basement.a/b, dungeon.room1..3, fpoint.<id>, lufonia"
                            );
                        }
                        break;
                    default:
                        this.help(var1);
                }
            } catch (NumberFormatException var10) {
                this.err(var1, "Неверное число.");
            }

            return true;
        }
    }

    private void build(CommandSender var1, Player var2, String[] var3) {
        if (this.need(var2, var1)) {
            if (WorldBuilder.isBusy()) {
                this.err(var1, "Строительство уже идёт.");
            } else {
                WorldBuilder var4 = new WorldBuilder(this.plugin, var2.getLocation());
                if (!var4.heightOk()) {
                    this.err(var1, "Здесь не хватает высоты мира. Встаньте повыше (нужно ~16 блоков вниз и 13 вверх).");
                } else if (var3.length >= 2 && var3[1].equalsIgnoreCase("confirm")) {
                    var4.start(var2);
                } else {
                    var1.sendMessage(
                        Msg.parse(
                            "<yellow>Будут построены отдельные локации-острова вокруг вас: спавн (0,0), камеры (+400 X), База (-400 X), подвал (+400 Z), деревня (-400 Z), личные шахты (-800 X), шахты (от +800 X). Блоки в этих местах заменятся, шахты/камеры/боссы перезапишутся. Лучше всего — в пустом мире. Подтвердите: <white>/pa build confirm"
                        )
                    );
                }
            }
        }
    }

    private boolean need(Player var1, CommandSender var2) {
        if (var1 == null) {
            this.err(var2, "Только для игроков.");
        }

        return var1 != null;
    }

    private PlayerData dataOf(String var1) {
        Player var2 = Bukkit.getPlayerExact(var1);
        return var2 == null ? null : this.plugin.data().get(var2);
    }

    private void mine(CommandSender var1, Player var2, String[] var3) {
        if (var3.length < 2) {
            this.help(var1);
        } else {
            String var4 = var3[1].toLowerCase(Locale.ROOT);
            switch (var4) {
                case "list":
                    for (Mine var13 : this.plugin.mines().all()) {
                        var1.sendMessage(
                            Msg.parse(
                                "<white>"
                                    + var13.name
                                    + " <gray>ур."
                                    + var13.requiredLevel
                                    + ", сброс "
                                    + var13.resetSeconds
                                    + "сек/"
                                    + (int)(var13.resetPercent * 100.0)
                                    + "%, состав "
                                    + var13.composition
                            )
                        );
                    }
                    break;
                case "create":
                    if (!this.need(var2, var1)) {
                        return;
                    }

                    if (var3.length < 4) {
                        this.err(var1, "/pa mine create <имя> <уровень>");
                        return;
                    }

                    Region var10 = this.plugin.selection().region(var2.getUniqueId());
                    if (var10 == null) {
                        this.err(var1, "Сначала выделите область палочкой (/pa wand).");
                        return;
                    }

                    if (this.plugin.mines().get(var3[2]) != null) {
                        this.err(var1, "Шахта уже существует.");
                        return;
                    }

                    Mine var12 = this.plugin.mines().create(var3[2], var10, Integer.parseInt(var3[3]), var2.getLocation());
                    this.plugin.mines().reset(var12);
                    this.ok(var1, "Шахта создана и заполняется. Настройте состав: /pa mine setblock " + var12.name + " <блок> <вес>");
                    break;
                default:
                    if (var3.length < 3) {
                        this.help(var1);
                        return;
                    }

                    Mine var6 = this.plugin.mines().get(var3[2]);
                    if (var6 == null) {
                        this.err(var1, "Шахта не найдена.");
                        return;
                    }

                    String var7 = var3[1].toLowerCase(Locale.ROOT);
                    switch (var7) {
                        case "delete":
                            this.plugin.mines().delete(var6.name);
                            this.ok(var1, "Удалено.");
                            break;
                        case "setspawn":
                            if (this.need(var2, var1)) {
                                var6.spawn = var2.getLocation();
                                this.plugin.mines().save();
                                this.ok(var1, "Точка входа установлена.");
                            }
                            break;
                        case "setblock":
                            if (var3.length < 5) {
                                this.err(var1, "/pa mine setblock <имя> <блок> <вес>");
                                return;
                            }

                            Material var14 = Material.matchMaterial(var3[3]);
                            if (var14 == null || !var14.isBlock()) {
                                this.err(var1, "Неизвестный блок.");
                                return;
                            }

                            var6.composition.put(var14, Double.parseDouble(var3[4]));
                            this.plugin.mines().save();
                            this.ok(var1, "Состав: " + var6.composition);
                            break;
                        case "removeblock":
                            if (var3.length < 4) {
                                this.err(var1, "/pa mine removeblock <имя> <блок>");
                                return;
                            }

                            Material var9 = Material.matchMaterial(var3[3]);
                            if (var9 != null) {
                                var6.composition.remove(var9);
                            }

                            this.plugin.mines().save();
                            this.ok(var1, "Состав: " + var6.composition);
                            break;
                        case "setreset":
                            if (var3.length < 5) {
                                this.err(var1, "/pa mine setreset <имя> <секунды> <доля 0..1>");
                                return;
                            }

                            var6.resetSeconds = Math.max(5, Integer.parseInt(var3[3]));
                            var6.resetPercent = Math.max(0.05, Math.min(1.0, Double.parseDouble(var3[4])));
                            var6.scheduleNext();
                            this.plugin.mines().save();
                            this.ok(var1, "Готово.");
                            break;
                        case "reset":
                            this.plugin.mines().reset(var6);
                            this.ok(var1, "Сброс запущен.");
                            break;
                        default:
                            this.help(var1);
                    }
            }
        }
    }

    private void cell(CommandSender var1, Player var2, String[] var3) {
        if (var3.length < 2) {
            this.help(var1);
        } else {
            String var4 = var3[1].toLowerCase(Locale.ROOT);
            if (var4.equals("list")) {
                for (Cell var8 : this.plugin.cells().all()) {
                    var1.sendMessage(
                        Msg.parse("<white>" + var8.name + " <gray>цена " + Msg.money(var8.price) + (var8.owner == null ? " <green>свободна" : " <red>занята"))
                    );
                }
            } else if (var3.length < 3) {
                this.help(var1);
            } else {
                switch (var4) {
                    case "create":
                        if (!this.need(var2, var1)) {
                            return;
                        }

                        if (var3.length < 4) {
                            this.err(var1, "/pa cell create <имя> <цена>");
                            return;
                        }

                        Region var10 = this.plugin.selection().region(var2.getUniqueId());
                        if (var10 == null) {
                            this.err(var1, "Сначала выделите область палочкой (/pa wand).");
                            return;
                        }

                        this.plugin.cells().create(var3[2], var10, var2.getLocation(), Double.parseDouble(var3[3]));
                        this.ok(var1, "Камера создана. Точка входа — ваша позиция.");
                        break;
                    case "delete":
                        this.ok(var1, this.plugin.cells().delete(var3[2]) ? "Удалено." : "Не найдено.");
                        break;
                    case "setspawn":
                        Cell var9 = this.plugin.cells().get(var3[2]);
                        if (var9 == null) {
                            this.err(var1, "Не найдена.");
                        } else if (this.need(var2, var1)) {
                            var9.spawn = var2.getLocation();
                            this.plugin.cells().save();
                            this.ok(var1, "Точка входа обновлена.");
                        }
                        break;
                    case "free":
                        Cell var7 = this.plugin.cells().get(var3[2]);
                        if (var7 == null) {
                            this.err(var1, "Не найдена.");
                        } else {
                            var7.owner = null;
                            var7.members.clear();
                            this.plugin.cells().save();
                            this.ok(var1, "Камера освобождена.");
                        }
                        break;
                    default:
                        this.help(var1);
                }
            }
        }
    }

    private void boss(CommandSender var1, Player var2, String[] var3) {
        if (var3.length < 2) {
            this.help(var1);
        } else {
            String var4 = var3[1].toLowerCase(Locale.ROOT);
            switch (var4) {
                case "types":
                    for (BossManager.Type var7 : this.plugin.bosses().types()) {
                        var1.sendMessage(
                            Msg.parse(
                                "<white>"
                                    + var7.id()
                                    + " <gray>"
                                    + var7.entity()
                                    + ", HP "
                                    + var7.health()
                                    + ", награда "
                                    + Msg.money(var7.reward())
                                    + ", ур."
                                    + var7.minLevel()
                            )
                        );
                    }
                    break;
                case "list":
                    this.plugin.bosses().describePoints().forEach(var1x -> var1.sendMessage(Msg.parse("<white>" + var1x)));
                    break;
                case "add":
                    if (!this.need(var2, var1)) {
                        return;
                    }

                    if (var3.length < 3 || this.plugin.bosses().type(var3[2]) == null) {
                        this.err(var1, "/pa boss add <тип> (список: /pa boss types)");
                        return;
                    }

                    String var6 = this.plugin.bosses().addPoint(var3[2], var2.getLocation());
                    this.ok(var1, "Точка босса " + var6 + " создана.");
                    break;
                case "remove":
                    if (var3.length < 3) {
                        this.help(var1);
                        return;
                    }

                    this.ok(var1, this.plugin.bosses().removePoint(var3[2]) ? "Удалено." : "Не найдено.");
                    break;
                default:
                    this.help(var1);
            }
        }
    }

    private void eco(CommandSender var1, String[] var2) {
        if (var2.length < 4) {
            this.help(var1);
        } else {
            PlayerData var3 = this.dataOf(var2[2]);
            if (var3 == null) {
                this.err(var1, "Игрок должен быть онлайн.");
            } else {
                double var4 = Double.parseDouble(var2[3]);
                String var6 = var2[1].toLowerCase(Locale.ROOT);
                switch (var6) {
                    case "give":
                        var3.balance += var4;
                        break;
                    case "take":
                        var3.balance = Math.max(0.0, var3.balance - var4);
                        break;
                    case "set":
                        var3.balance = Math.max(0.0, var4);
                        break;
                    default:
                        this.help(var1);
                        return;
                }

                this.ok(var1, var3.name + ": баланс " + Msg.money(var3.balance));
            }
        }
    }

    private void give(CommandSender var1, String[] var2) {
        if (var2.length < 2) {
            this.help(var1);
        } else {
            String var3 = var2[1].toLowerCase(Locale.ROOT);
            if (var3.equals("enchants")) {
                for (Ench var16 : Ench.values()) {
                    var1.sendMessage(Msg.parse("<yellow>" + var16.name() + " <gray>(" + var16.title + ", до " + var16.max + ") — " + var16.desc));
                }
            } else if (var2.length < 3) {
                this.help(var1);
            } else {
                Player var4 = Bukkit.getPlayerExact(var2[2]);
                PlayerData var5 = var4 == null ? null : this.plugin.data().get(var4);
                if (var5 == null) {
                    this.err(var1, "Игрок не найден.");
                } else {
                    switch (var3) {
                        case "book":
                            Ench var20 = var2.length > 3 ? Ench.parse(var2[3]) : null;
                            if (var20 == null || var2.length < 5) {
                                this.err(var1, "/pa give book <игрок> <зачар> <уровень> [шанс%]");
                                return;
                            }

                            int var22 = Math.max(1, Math.min(var20.max, Integer.parseInt(var2[4])));
                            int var23 = var2.length > 5 ? Integer.parseInt(var2[5]) : 10;
                            var4.getInventory().addItem(new ItemStack[]{this.plugin.enchants().book(var20, var22, var23)});
                            break;
                        case "dust":
                            int var19 = var2.length > 3 ? Integer.parseInt(var2[3]) : 10;
                            int var21 = var2.length > 4 ? Integer.parseInt(var2[4]) : 1;
                            var4.getInventory().addItem(new ItemStack[]{this.plugin.enchants().dust(var19, var21)});
                            break;
                        case "key":
                            if (var2.length < 4) {
                                this.err(var1, "/pa give key <игрок> <common|dk|legendary> [кол]");
                                return;
                            }

                            var5.addKey(var2[3].toLowerCase(Locale.ROOT), var2.length > 4 ? Integer.parseInt(var2[4]) : 1);
                            break;
                        case "stars":
                            this.plugin.specials().giveStars(var4, var2.length > 3 ? Long.parseLong(var2[3]) : 1L);
                            break;
                        case "booster":
                            if (var2.length < 7) {
                                this.err(var1, "/pa give booster <игрок> <coin|block> <local|global> <+доля> <45m|1d|3d|7d|30d|90d|year|wipe> [кол]");
                                return;
                            }

                            ItemStack var18 = this.plugin
                                .boosters()
                                .item(
                                    var2[3].equalsIgnoreCase("coin"),
                                    var2[4].equalsIgnoreCase("global"),
                                    Double.parseDouble(var2[5]),
                                    BoosterService.parseDuration(var2[6])
                                );
                            var18.setAmount(var2.length > 7 ? Integer.parseInt(var2[7]) : 1);
                            Items.give(var4, var18);
                            break;
                        case "treasure":
                            ItemStack var17 = var2.length > 3 ? this.plugin.treasures().item(var2[3], var2.length > 4 ? Integer.parseInt(var2[4]) : 1) : null;
                            if (var17 == null) {
                                StringBuilder var9 = new StringBuilder();
                                this.plugin.treasures().all().forEach(var1x -> var9.append(var1x.id()).append(' '));
                                this.err(var1, "/pa give treasure <игрок> <id> [кол]. id: " + var9);
                                return;
                            }

                            Items.give(var4, var17);
                            break;
                        case "pmine":
                            Items.give(var4, this.plugin.privateMines().voucher(var2.length > 3 ? Integer.parseInt(var2[3]) : 20));
                            break;
                        case "medicine":
                            Items.give(var4, this.plugin.church().medicine(var2.length > 3 ? Integer.parseInt(var2[3]) : 1));
                            break;
                        case "special":
                            if (var2.length < 4 || this.plugin.specials().def(var2[3]) == null) {
                                this.err(var1, "/pa give special <игрок> <id> [кол]. id: " + this.plugin.specials().ids());
                                return;
                            }

                            this.plugin.specials().give(var4, var2[3], var2.length > 4 ? Integer.parseInt(var2[4]) : 1);
                            break;
                        case "shard":
                            ItemStack shard = var2.length > 3 ? this.plugin.collections().item(var2[3], 1) : null;
                            if (shard == null) {
                                this.err(var1, "/pa give shard <игрок> <id>. id: " + dev.prisonclassic.collection.CollectionService.SHARDS.keySet());
                                return;
                            }

                            Items.give(var4, shard);
                            break;
                        case "premium":
                            long days = var2.length > 3 ? Long.parseLong(var2[3]) : 30L;
                            var5.autosellPremiumUntil = days < 0L ? -1L : Math.max(System.currentTimeMillis(), var5.autosellPremiumUntil) + days * 86400000L;
                            break;
                        case "kit":
                            var5.starterKit = false;
                            this.plugin.upgrades().giveStarterKit(var4, var5);
                            break;
                        case "kills":
                            if (var2.length < 5) {
                                this.err(var1, "/pa give kills <игрок> <rat|zombie|mini_slime|...> <кол>");
                                return;
                            }

                            var5.mobKills.merge(var2[3], Integer.parseInt(var2[4]), Integer::sum);
                            break;
                        case "pbooster":
                            if (var2.length < 6) {
                                this.err(var1, "/pa give pbooster <игрок> <coin|block> <+доля> <минуты|-1>");
                                return;
                            }

                            double var8 = Double.parseDouble(var2[4]);
                            long var10 = Long.parseLong(var2[5]);
                            long var12 = var10 < 0L ? -1L : System.currentTimeMillis() + var10 * 60000L;
                            if (var2[3].equalsIgnoreCase("coin")) {
                                var5.coinBoost = var8;
                                var5.coinBoostUntil = var12;
                            } else {
                                var5.blockBoost = var8;
                                var5.blockBoostUntil = var12;
                            }
                            break;
                        default:
                            this.help(var1);
                            return;
                    }

                    this.ok(var1, "Выдано.");
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
        ArrayList<String> var5 = new ArrayList<>();
        if (var4.length == 1) {
            var5.addAll(List.of("build", "wand", "setspawn", "mine", "cell", "boss", "faction", "eco", "level", "booster", "give", "point", "setloc", "reload"));
        } else if (var4.length == 2) {
            String var6 = var4[0].toLowerCase(Locale.ROOT);
            switch (var6) {
                case "build":
                    var5.add("confirm");
                    break;
                case "give":
                    var5.addAll(List.of("book", "dust", "key", "stars", "pbooster", "booster", "treasure", "pmine", "medicine", "kit", "kills", "enchants", "special", "shard", "premium"));
                    break;
                case "booster":
                    var5.addAll(List.of("coin", "block"));
                    break;
                case "point":
                    var5.add("set");
                    break;
                case "setloc":
                    var5.addAll(
                        List.of(
                            "church",
                            "crate.common",
                            "crate.dk",
                            "crate.legendary",
                            "npc.shop",
                            "npc.seller",
                            "npc.priest",
                            "npc.smith",
                            "npc.awaken",
                            "npc.quest",
                            "npc.trainer",
                            "npc.gypsy",
                            "npc.donate",
                            "npc.dungeon",
                            "church.a",
                            "church.b",
                            "basement.a",
                            "basement.b",
                            "dungeon.room1",
                            "dungeon.room2",
                            "dungeon.room3",
                            "lufonia"
                        )
                    );
                    break;
                case "mine":
                    var5.addAll(List.of("create", "delete", "setspawn", "setblock", "removeblock", "setreset", "reset", "list"));
                    break;
                case "cell":
                    var5.addAll(List.of("create", "delete", "setspawn", "free", "list"));
                    break;
                case "boss":
                    var5.addAll(List.of("types", "add", "remove", "list"));
                    break;
                case "faction":
                    var5.add("setbase");
                    break;
                case "eco":
                    var5.addAll(List.of("give", "take", "set"));
            }
        } else if (var4.length == 3) {
            String var10 = var4[0].toLowerCase(Locale.ROOT);
            switch (var10) {
                case "mine":
                    this.plugin.mines().all().forEach(var1x -> var5.add(var1x.name));
                    break;
                case "cell":
                    this.plugin.cells().all().forEach(var1x -> var5.add(var1x.name));
                    break;
                case "boss":
                    this.plugin.bosses().types().forEach(var1x -> var5.add(var1x.id()));
                    break;
                case "faction":
                    this.plugin.factions().all().forEach(var1x -> var5.add(var1x.id()));
                    break;
                case "eco":
                    Bukkit.getOnlinePlayers().forEach(var1x -> var5.add(var1x.getName()));
            }
        } else if (var4.length == 4 && var4[0].equalsIgnoreCase("mine") && (var4[1].equalsIgnoreCase("setblock") || var4[1].equalsIgnoreCase("removeblock"))) {
            for (Material var9 : Material.values()) {
                if (var9.isBlock() && !var9.isLegacy()) {
                    var5.add(var9.name().toLowerCase(Locale.ROOT));
                }
            }
        }

        String var12 = var4.length == 0 ? "" : var4[var4.length - 1].toLowerCase(Locale.ROOT);
        var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var12));
        return var5;
    }
}
