package dev.prisonclassic.pmine;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class PrivateMineService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey voucherKey;
    private final Map<UUID, Mine> sessions = new HashMap<>();

    public PrivateMineService(PrisonClassic var1) {
        this.plugin = var1;
        this.voucherKey = new NamespacedKey(var1, "pmine_voucher");
        Bukkit.getScheduler().runTaskTimer(var1, this::tick, 20L, 20L);
    }

    private long dailyAllowance(PlayerData var1) {
        return var1.donate <= 0 ? 0L : this.plugin.getConfig().getLong("private-mine.donate-minutes-per-day." + var1.donate, 0L) * 60L;
    }

    private void rollDay(PlayerData var1) {
        long var2 = LocalDate.now().toEpochDay();
        if (var1.privateMineDay != var2) {
            var1.privateMineDay = var2;
            var1.privateMineUsedToday = 0L;
        }
    }

    public long available(PlayerData var1) {
        this.rollDay(var1);
        return Math.max(0L, this.dailyAllowance(var1) - var1.privateMineUsedToday) + var1.privateMineSeconds;
    }

    public ItemStack voucher(int var1) {
        ItemStack var2 = Items.of(
            Material.MAP, "<aqua><bold>Личная шахта · " + var1 + " мин.", "<gray>ПКМ — добавить время личной шахты", "<gray>Запуск: <white>/pmine"
        );
        ItemMeta var3 = var2.getItemMeta();
        var3.getPersistentDataContainer().set(this.voucherKey, PersistentDataType.INTEGER, var1);
        var2.setItemMeta(var3);
        return var2;
    }

    @EventHandler
    public void onVoucher(PlayerInteractEvent var1) {
        if (var1.getHand() == EquipmentSlot.HAND) {
            if (var1.getAction() == Action.RIGHT_CLICK_AIR || var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
                ItemStack var2 = var1.getItem();
                if (var2 != null && var2.hasItemMeta()) {
                    Integer var3 = var2.getItemMeta().getPersistentDataContainer().get(this.voucherKey, PersistentDataType.INTEGER);
                    if (var3 != null) {
                        var1.setCancelled(true);
                        PlayerData var4 = this.plugin.data().get(var1.getPlayer());
                        if (var4 != null) {
                            var2.setAmount(var2.getAmount() - 1);
                            var4.privateMineSeconds = var4.privateMineSeconds + var3.intValue() * 60L;
                            var1.getPlayer()
                                .sendMessage(
                                    Msg.parse(
                                        "<green>+" + var3 + " мин. личной шахты. Доступно: <white>" + Items.fmtTime(this.available(var4)) + " <gray>(/pmine)"
                                    )
                                );
                        }
                    }
                }
            }
        }
    }

    public Mine sessionOf(UUID var1) {
        return this.sessions.get(var1);
    }

    public void open(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            Mine var3 = this.sessions.get(var1.getUniqueId());
            Gui var4 = new Gui(54, Msg.parse("Личная шахта"));
            var4.set(
                49,
                Items.of(
                    Material.CLOCK,
                    "<yellow>Доступно времени: <white>" + Items.fmtTime(this.available(var2)),
                    "<gray>Донат 5: " + this.plugin.getConfig().getLong("private-mine.donate-minutes-per-day.5", 60L) + " мин. в день",
                    "<gray>Купленное время: <white>" + Items.fmtTime(var2.privateMineSeconds),
                    var3 != null ? "<green>Сейчас активна. ЛКМ — завершить" : "<gray>Выберите шахту ниже"
                ),
                (var1x, var2x) -> {
                    if (this.sessions.containsKey(var1x.getUniqueId())) {
                        this.stop(var1x.getUniqueId(), "<yellow>Личная шахта закрыта.");
                        var1x.closeInventory();
                    }
                }
            );
            int var5 = 0;

            for (Mine var7 : this.plugin.mines().all()) {
                if (!var7.hidden && !var7.privateSlot && var5 < 45) {
                    boolean var8 = var2.level >= var7.requiredLevel;
                    Material var9 = var8 ? this.iconOf(var7) : Material.BARRIER;
                    var4.set(
                        var5++,
                        Items.of(
                            var9,
                            "<gold>" + var7.name + ". " + var7.title,
                            var8 ? "<yellow>Нажмите — личная шахта с этим составом" : "<red>Нужен уровень " + var7.requiredLevel
                        ),
                        (var3x, var4x) -> {
                            if (var8) {
                                this.start(var3x, var7);
                            }
                        }
                    );
                }
            }

            var4.open(var1);
        }
    }

    private Material iconOf(Mine var1) {
        Material var2 = Material.STONE;
        double var3 = -1.0;

        for (Entry var6 : var1.composition.entrySet()) {
            if ((Double)var6.getValue() > var3 && ((Material)var6.getKey()).isItem()) {
                var3 = (Double)var6.getValue();
                var2 = (Material)var6.getKey();
            }
        }

        return var2;
    }

    public void start(Player var1, Mine var2) {
        PlayerData var3 = this.plugin.data().get(var1);
        if (var3 != null) {
            if (this.sessions.containsKey(var1.getUniqueId())) {
                var1.sendMessage(Msg.parse("<red>Личная шахта уже открыта. /pmine — завершить."));
            } else if (this.available(var3) <= 0L) {
                var1.sendMessage(Msg.parse("<red>Нет времени личной шахты. Донат 5 даёт час в день, время можно купить."));
            } else {
                Mine var4 = null;

                for (Mine var6 : this.plugin.mines().all()) {
                    if (var6.privateSlot && var6.owner == null && !var6.resetting) {
                        var4 = var6;
                        break;
                    }
                }

                if (var4 == null) {
                    var1.sendMessage(Msg.parse("<red>Все личные шахты заняты, попробуйте позже."));
                } else {
                    var4.owner = var1.getUniqueId();
                    var4.title = "Личная шахта " + var1.getName() + " (" + var2.title + ")";
                    var4.composition.clear();
                    var4.composition.putAll(var2.composition);
                    var4.composition.remove(Material.LAVA);
                    var4.upper = var2.upper;
                    var4.requiredLevel = 1;
                    this.sessions.put(var1.getUniqueId(), var4);
                    this.plugin.mines().reset(var4);
                    var1.closeInventory();
                    Mine var7 = var4;
                    var1.sendMessage(Msg.parse("<green>Личная шахта готовится... <gray>Доступно: " + Items.fmtTime(this.available(var3))));
                    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                        Location var3x = var7.spawn;
                        if (var1.isOnline() && var3x != null && this.sessions.get(var1.getUniqueId()) == var7) {
                            var1.teleport(var3x);
                            var1.playSound(var1.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                        }
                    }, 40L);
                }
            }
        }
    }

    private void tick() {
        for (UUID var2 : new ArrayList<>(this.sessions.keySet())) {
            Player var3 = Bukkit.getPlayer(var2);
            PlayerData var4 = var3 == null ? null : this.plugin.data().get(var3);
            if (var4 == null) {
                this.stop(var2, null);
            } else {
                this.rollDay(var4);
                if (var4.privateMineUsedToday < this.dailyAllowance(var4)) {
                    var4.privateMineUsedToday++;
                } else if (var4.privateMineSeconds > 0L) {
                    var4.privateMineSeconds--;
                }

                long var5 = this.available(var4);
                if (var5 <= 0L) {
                    this.stop(var2, "<red>Время личной шахты закончилось.");
                } else if (var5 == 60L || var5 == 10L) {
                    var3.sendMessage(Msg.parse("<yellow>Личная шахта закроется через " + var5 + " сек."));
                }
            }
        }
    }

    public void stop(UUID var1, String var2) {
        Mine var3 = this.sessions.remove(var1);
        if (var3 != null) {
            Player var4 = Bukkit.getPlayer(var1);
            if (var4 != null) {
                if (var3.region.contains(var4.getLocation())
                    || var3.spawn != null && var3.spawn.getWorld() == var4.getWorld() && var3.spawn.distanceSquared(var4.getLocation()) < 400.0) {
                    Location var5 = this.plugin.spawn();
                    if (var5 != null) {
                        var4.teleport(var5);
                    }
                }

                if (var2 != null) {
                    var4.sendMessage(Msg.parse(var2));
                }
            }

            var3.owner = null;
            var3.title = "Личная шахта (свободна)";
            var3.composition.clear();
            var3.composition.put(Material.STONE, 1.0);
            var3.upper = null;
            if (this.plugin.isEnabled()) {
                this.plugin.mines().reset(var3);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent var1) {
        this.stop(var1.getPlayer().getUniqueId(), null);
    }

    public void shutdown() {
        for (UUID var2 : new ArrayList<>(this.sessions.keySet())) {
            this.stop(var2, null);
        }
    }
}
