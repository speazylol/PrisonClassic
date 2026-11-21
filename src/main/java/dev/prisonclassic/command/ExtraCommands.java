package dev.prisonclassic.command;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class ExtraCommands implements TabExecutor, Listener {
    private final PrisonClassic plugin;
    private long topCacheAt = 0L;
    private final Map<String, List<ExtraCommands.Row>> topCache = new HashMap<>();

    public ExtraCommands(PrisonClassic var1) {
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
                    case "trainer":
                        this.plugin.trainer().open(var5);
                        break;
                    case "ec":
                        this.openEc(var5, var6);
                        break;
                    case "top":
                        this.top(var5, var4.length == 0 ? "level" : var4[0].toLowerCase(Locale.ROOT));
                        break;
                    case "keys":
                        this.keys(var5, var6, var4);
                        break;
                    case "exchange":
                        this.exchange(var5, var6, var4);
                        break;
                    case "active":
                        this.plugin.trainer().activate(var5, var6);
                }

                return true;
            }
        } else {
            var1.sendMessage("Только для игроков.");
            return true;
        }
    }

    private void openEc(Player var1, PlayerData var2) {
        Inventory var3 = Bukkit.createInventory(new ExtraCommands.EcHolder(), 36, Component.text("Эндер-сундук"));

        for (int var4 = 0; var4 < Math.min(36, var2.endChest.size()); var4++) {
            String var5 = var2.endChest.get(var4);
            if (var5 != null && !var5.isEmpty()) {
                try {
                    var3.setItem(var4, ItemStack.deserializeBytes(Base64.getDecoder().decode(var5)));
                } catch (Exception var7) {
                    this.plugin.getLogger().warning("Эндер-сундук: не удалось прочитать предмет у " + var1.getName());
                }
            }
        }

        var1.openInventory(var3);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent var1) {
        if (var1.getInventory().getHolder() instanceof ExtraCommands.EcHolder && var1.getPlayer() instanceof Player var2) {
            PlayerData var6 = this.plugin.data().get(var2);
            if (var6 != null) {
                var6.endChest.clear();

                for (int var4 = 0; var4 < 36; var4++) {
                    ItemStack var5 = var1.getInventory().getItem(var4);
                    var6.endChest.add(var5 != null && !var5.getType().isAir() ? Base64.getEncoder().encodeToString(var5.serializeAsBytes()) : "");
                }
            }
        }
    }

    private void top(Player var1, String var2) {
        if (!List.of("level", "blocks", "day", "kills").contains(var2)) {
            var1.sendMessage(Msg.parse("<red>/top <level|blocks|day|kills>"));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                HashMap<UUID, PlayerData> var3 = new HashMap<>();
                for (PlayerData stored : this.plugin.data().storage().loadAll()) {
                    var3.put(stored.uuid, stored);
                }

                ArrayList<PlayerData> var12 = new ArrayList<>(this.plugin.data().all());
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    for (PlayerData var5x : var12) {
                        var3.put(var5x.uuid, var5x);
                    }

                    long var13 = LocalDate.now().toEpochDay();
                    ArrayList<ExtraCommands.Row> var6 = new ArrayList<>();

                    for (PlayerData var8 : var3.values()) {
                        long var9x = switch (var2) {
                            case "level" -> var8.level * 10000000000L + var8.blocksMined;
                            case "blocks" -> var8.blocksMined;
                            case "day" -> var8.blocksDay == var13 ? var8.blocksToday : 0L;
                            default -> var8.kills;
                        };
                        if (var9x > 0L) {
                            var6.add(new ExtraCommands.Row(var8.name, var9x));
                        }
                    }

                    var6.sort((var0x, var1xxx) -> Long.compare(var1xxx.value(), var0x.value()));

                    var1.sendMessage(Msg.parse("<gold>Топ — " + switch (var2) {
                        case "level" -> "уровень";
                        case "blocks" -> "блоки (всего)";
                        case "day" -> "блоки (за день)";
                        default -> "убийства";
                    }));

                    for (int var14 = 0; var14 < Math.min(10, var6.size()); var14++) {
                        ExtraCommands.Row var16 = (ExtraCommands.Row)var6.get(var14);
                        long var17 = var2.equals("level") ? var16.value() / 10000000000L : var16.value();
                        var1.sendMessage(Msg.parse("<yellow>" + (var14 + 1) + ". <white>" + var16.name() + " <gray>— <green>" + var17));
                    }
                });
            });
        }
    }

    private void keys(Player var1, PlayerData var2, String[] var3) {
        if (var3.length >= 2 && var3[0].equalsIgnoreCase("open")) {
            this.plugin.crates().openCrate(var1, var3[1].toLowerCase(Locale.ROOT));
        } else if (var3.length == 0) {
            this.plugin.crates().open(var1);
        } else {
            var1.sendMessage(
                Msg.parse(
                    "<gold>Ключи: <white>обычные "
                        + var2.key("common")
                        + "<gray>, <white>ДК "
                        + var2.key("dk")
                        + "<gray>, <white>легендарные "
                        + var2.key("legendary")
                        + " <gray>| Звёзды: <yellow>"
                        + this.plugin.specials().stars(var1, var2)
                )
            );
            var1.sendMessage(Msg.parse("<gray>/keys — меню сундуков, /keys open <common|dk|legendary> — открыть сундук"));
        }
    }

    private void exchange(Player var1, PlayerData var2, String[] var3) {
        FileConfiguration var4 = this.plugin.getConfig();
        String var5 = var3.length == 0 ? "" : var3[0].toLowerCase(Locale.ROOT);
        switch (var5) {
            case "stars":
                int var18 = var4.getInt("exchange.stars-per-dk", 8);
                long var21 = this.plugin.specials().stars(var1, var2) / var18;
                if (var21 <= 0L) {
                    var1.sendMessage(Msg.parse("<red>Нужно " + var18 + " звёзд на 1 ДК-ключ."));
                    return;
                }

                this.plugin.specials().takeStars(var1, var2, var21 * var18);
                var2.addKey("dk", (int)var21);
                var1.sendMessage(Msg.parse("<green>Обменяно на ДК-ключи: " + var21));
                break;
            case "common":
                int var17 = var4.getInt("exchange.common-per-dk", 64);
                int var20 = var2.key("common") / var17;
                if (var20 <= 0) {
                    var1.sendMessage(Msg.parse("<red>Нужно " + var17 + " обычных ключей на 1 ДК-ключ."));
                    return;
                }

                var2.addKey("common", -var20 * var17);
                var2.addKey("dk", var20);
                var1.sendMessage(Msg.parse("<green>Обменяно на ДК-ключи: " + var20));
                break;
            case "keys":
                int var16 = var4.getInt("exchange.dk-per-legendary", 16);
                int var19 = var2.key("dk") / var16;
                if (var19 <= 0) {
                    var1.sendMessage(Msg.parse("<red>Нужно " + var16 + " ДК-ключей на 1 легендарный."));
                    return;
                }

                var2.addKey("dk", -var19 * var16);
                var2.addKey("legendary", var19);
                var1.sendMessage(Msg.parse("<green>Обменяно на легендарные ключи: " + var19));
                break;
            case "dust":
                int var15 = var4.getInt("exchange.dust-per-100", 15);
                int var9 = 0;

                for (ItemStack var13 : var1.getInventory().getStorageContents()) {
                    if (this.plugin.enchants().dustPct(var13) == 10) {
                        var9 += var13.getAmount();
                    }
                }

                int var23 = var9 / var15;
                if (var23 <= 0) {
                    var1.sendMessage(Msg.parse("<red>Нужно " + var15 + " пыли 10% на 1 пыль 100%."));
                    return;
                }

                int var25 = var23 * var15;
                ItemStack[] var27 = var1.getInventory().getStorageContents();

                for (int var28 = 0; var28 < var27.length && var25 > 0; var28++) {
                    if (this.plugin.enchants().dustPct(var27[var28]) == 10) {
                        int var14 = Math.min(var25, var27[var28].getAmount());
                        var25 -= var14;
                        var27[var28].setAmount(var27[var28].getAmount() - var14);
                        if (var27[var28].getAmount() <= 0) {
                            var27[var28] = null;
                        }
                    }
                }

                var1.getInventory().setStorageContents(var27);

                for (ItemStack var30 : var1.getInventory().addItem(new ItemStack[]{this.plugin.enchants().dust(100, var23)}).values()) {
                    var1.getWorld().dropItemNaturally(var1.getLocation(), var30);
                }

                var1.sendMessage(Msg.parse("<green>Получено пыли 100%: " + var23));
                break;
            case "unique":
                long var8 = 0L;
                ItemStack[] var10 = var1.getInventory().getStorageContents();

                for (int var11 = 0; var11 < var10.length; var11++) {
                    int var12 = this.plugin.enchants().uniqueStars(var10[var11]);
                    if (var12 > 0) {
                        var8 += var12;
                        var10[var11] = null;
                    }
                }

                if (var8 <= 0L) {
                    var1.sendMessage(Msg.parse("<red>Нет уникальных трофеев в инвентаре."));
                    return;
                }

                var1.getInventory().setStorageContents(var10);
                this.plugin.specials().giveStars(var1, var8);
                var1.sendMessage(Msg.parse("<yellow>Получено звёзд: " + var8));
                break;
            default:
                var1.sendMessage(
                    Msg.parse(
                        "<gold>/exchange <stars|common|keys|dust|unique> <gray>— звёзды→ДК ("
                            + var4.getInt("exchange.stars-per-dk", 8)
                            + ":1), обычные→ДК ("
                            + var4.getInt("exchange.common-per-dk", 64)
                            + ":1), ДК→лег. ("
                            + var4.getInt("exchange.dk-per-legendary", 16)
                            + ":1), пыль 10%→100% ("
                            + var4.getInt("exchange.dust-per-100", 15)
                            + ":1), трофеи→звёзды"
                    )
                );
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
        String var5 = var2.getName().toLowerCase(Locale.ROOT);
        if (var4.length == 1) {
            if (var5.equals("top")) {
                return List.of("level", "blocks", "day", "kills");
            }

            if (var5.equals("exchange")) {
                return List.of("stars", "common", "keys", "dust", "unique");
            }

            if (var5.equals("keys")) {
                return List.of("open");
            }
        } else if (var4.length == 2 && var5.equals("keys")) {
            return List.of("common", "dk", "legendary");
        }

        return List.of();
    }

    private static final class EcHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record Row(String name, long value) {
    }
}
