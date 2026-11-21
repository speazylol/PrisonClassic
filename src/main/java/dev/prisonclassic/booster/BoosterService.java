package dev.prisonclassic.booster;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class BoosterService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey key;
    private final File file;
    private double gCoin = 0.0;
    private double gBlock = 0.0;
    private long gCoinUntil = 0L;
    private long gBlockUntil = 0L;
    private String gCoinBy = "";
    private String gBlockBy = "";
    private final BossBar coinBar = BossBar.bossBar(Msg.parse(""), 1.0F, Color.YELLOW, Overlay.PROGRESS);
    private final BossBar blockBar = BossBar.bossBar(Msg.parse(""), 1.0F, Color.BLUE, Overlay.PROGRESS);
    private long gCoinTotal = 1L;
    private long gBlockTotal = 1L;

    public BoosterService(PrisonClassic var1) {
        this.plugin = var1;
        this.key = new NamespacedKey(var1, "booster");
        this.file = new File(var1.getDataFolder(), "boosters.yml");
        this.load();
        Bukkit.getScheduler().runTaskTimer(var1, this::tickBars, 40L, 20L);
    }

    private void load() {
        YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
        this.gCoin = var1.getDouble("coin.value");
        this.gCoinUntil = var1.getLong("coin.until");
        this.gCoinTotal = Math.max(1L, var1.getLong("coin.total", 1L));
        this.gCoinBy = var1.getString("coin.by", "");
        this.gBlock = var1.getDouble("block.value");
        this.gBlockUntil = var1.getLong("block.until");
        this.gBlockTotal = Math.max(1L, var1.getLong("block.total", 1L));
        this.gBlockBy = var1.getString("block.by", "");
    }

    private void save() {
        YamlConfiguration var1 = new YamlConfiguration();
        var1.set("coin.value", this.gCoin);
        var1.set("coin.until", this.gCoinUntil);
        var1.set("coin.total", this.gCoinTotal);
        var1.set("coin.by", this.gCoinBy);
        var1.set("block.value", this.gBlock);
        var1.set("block.until", this.gBlockUntil);
        var1.set("block.total", this.gBlockTotal);
        var1.set("block.by", this.gBlockBy);

        try {
            var1.save(this.file);
        } catch (IOException var3) {
            this.plugin.getLogger().warning("boosters.yml: " + var3.getMessage());
        }
    }

    public double globalCoin() {
        return System.currentTimeMillis() < this.gCoinUntil ? 1.0 + this.gCoin : 1.0;
    }

    public double globalBlock() {
        return System.currentTimeMillis() < this.gBlockUntil ? 1.0 + this.gBlock : 1.0;
    }

    public String describe() {
        StringBuilder var1 = new StringBuilder();
        if (this.globalCoin() > 1.0) {
            var1.append("монеты x").append(fmt(this.globalCoin()));
        }

        if (this.globalBlock() > 1.0) {
            var1.append(var1.length() > 0 ? ", " : "").append("блоки x").append(fmt(this.globalBlock()));
        }

        return var1.length() == 0 ? "—" : var1.toString();
    }

    private static String fmt(double var0) {
        return String.format(Locale.US, "%.2f", var0).replaceAll("\\.?0+$", "");
    }

    public boolean startGlobal(boolean var1, double var2, long var4, String var6) {
        long var7 = System.currentTimeMillis();
        if (var1) {
            if (var7 < this.gCoinUntil && Math.abs(this.gCoin - var2) > 1.0E-9) {
                return false;
            }

            if (var7 < this.gCoinUntil) {
                this.gCoinUntil += var4 * 1000L;
                this.gCoinTotal += var4 * 1000L;
            } else {
                this.gCoinUntil = var7 + var4 * 1000L;
                this.gCoinTotal = var4 * 1000L;
            }

            this.gCoin = var2;
            this.gCoinBy = var6;
        } else {
            if (var7 < this.gBlockUntil && Math.abs(this.gBlock - var2) > 1.0E-9) {
                return false;
            }

            if (var7 < this.gBlockUntil) {
                this.gBlockUntil += var4 * 1000L;
                this.gBlockTotal += var4 * 1000L;
            } else {
                this.gBlockUntil = var7 + var4 * 1000L;
                this.gBlockTotal = var4 * 1000L;
            }

            this.gBlock = var2;
            this.gBlockBy = var6;
        }

        this.save();
        Bukkit.broadcast(
            Msg.parse(
                "<light_purple>★ <white>"
                    + var6
                    + " <light_purple>запустил глобальный бустер "
                    + (var1 ? "монет" : "блоков")
                    + " <bold>x"
                    + fmt(1.0 + var2)
                    + "</bold> на "
                    + Items.fmtTime(var4)
                    + "!"
            )
        );
        return true;
    }

    private void tickBars() {
        long var1 = System.currentTimeMillis();
        this.bar(
            this.coinBar,
            var1 < this.gCoinUntil,
            "<yellow>Глобальный бустер монет x"
                + fmt(1.0 + this.gCoin)
                + " <gray>("
                + this.gCoinBy
                + ") <white>"
                + Items.fmtTime((this.gCoinUntil - var1) / 1000L),
            (double)(this.gCoinUntil - var1) / this.gCoinTotal
        );
        this.bar(
            this.blockBar,
            var1 < this.gBlockUntil,
            "<aqua>Глобальный бустер блоков x"
                + fmt(1.0 + this.gBlock)
                + " <gray>("
                + this.gBlockBy
                + ") <white>"
                + Items.fmtTime((this.gBlockUntil - var1) / 1000L),
            (double)(this.gBlockUntil - var1) / this.gBlockTotal
        );
    }

    private void bar(BossBar var1, boolean var2, String var3, double var4) {
        if (var2) {
            var1.name(Msg.parse(var3));
            var1.progress((float)Math.max(0.0, Math.min(1.0, var4)));

            for (Player var7 : Bukkit.getOnlinePlayers()) {
                var7.showBossBar(var1);
            }
        } else {
            for (Player var9 : Bukkit.getOnlinePlayers()) {
                var9.hideBossBar(var1);
            }
        }
    }

    public ItemStack item(boolean var1, boolean var2, double var3, long var5) {
        String var7 = var1 ? "монет" : "блоков";
        String var8 = var5 < 0L ? "до вайпа" : Items.fmtTime(var5);
        ItemStack var9 = Items.of(
            var1 ? Material.GOLD_NUGGET : Material.PRISMARINE_CRYSTALS,
            (var2 ? "<light_purple><bold>Глобальный" : "<aqua><bold>Личный") + " бустер " + var7 + " x" + fmt(1.0 + var3),
            "<gray>Длительность: <white>" + var8,
            var2 ? "<gray>Действует на <white>всех игроков" : "<gray>Действует только на вас",
            "<yellow>ПКМ — активировать"
        );
        ItemMeta var10 = var9.getItemMeta();
        var10.getPersistentDataContainer()
            .set(this.key, PersistentDataType.STRING, (var1 ? "coin" : "block") + ":" + (var2 ? "global" : "local") + ":" + var3 + ":" + var5);
        var9.setItemMeta(var10);
        return var9;
    }

    public static long parseDuration(String var0) {
        var0 = var0.toLowerCase(Locale.ROOT);
        if (var0.equals("wipe") || var0.equals("вайп") || var0.equals("-1")) {
            return -1L;
        } else if (!var0.equals("year") && !var0.equals("год")) {
            long var1 = 60L;
            if (var0.endsWith("d")) {
                var1 = 86400L;
            } else if (var0.endsWith("h")) {
                var1 = 3600L;
            } else if (var0.endsWith("s")) {
                var1 = 1L;
            }

            String var3 = var0.replaceAll("[^0-9]", "");
            return Long.parseLong(var3.isEmpty() ? "0" : var3) * var1;
        } else {
            return 31536000L;
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent var1) {
        if (var1.getHand() == EquipmentSlot.HAND) {
            if (var1.getAction() == Action.RIGHT_CLICK_AIR || var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
                ItemStack var2 = var1.getItem();
                if (var2 != null && var2.hasItemMeta()) {
                    String var3 = var2.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
                    if (var3 != null) {
                        var1.setCancelled(true);
                        Player var4 = var1.getPlayer();
                        PlayerData var5 = this.plugin.data().get(var4);
                        if (var5 != null) {
                            String[] var6 = var3.split(":");
                            boolean var7 = var6[0].equals("coin");
                            boolean var8 = var6[1].equals("global");
                            double var9 = Double.parseDouble(var6[2]);
                            long var11 = Long.parseLong(var6[3]);
                            boolean var13 = var8
                                ? this.startGlobal(var7, var9, Math.max(60L, var11), var4.getName())
                                : this.startLocal(var4, var5, var7, var9, var11);
                            if (var13) {
                                var2.setAmount(var2.getAmount() - 1);
                                var4.playSound(var4.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.6F);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean startLocal(Player var1, PlayerData var2, boolean var3, double var4, long var6) {
        long var8 = System.currentTimeMillis();
        double var10 = var3 ? var2.coinBoost : var2.blockBoost;
        long var12 = var3 ? var2.coinBoostUntil : var2.blockBoostUntil;
        boolean var14 = var10 > 0.0 && (var12 < 0L || var8 < var12);
        long var15;
        if (var14 && Math.abs(var10 - var4) < 1.0E-9) {
            if (var12 < 0L) {
                var1.sendMessage(Msg.parse("<red>Такой бустер у вас уже действует до вайпа."));
                return false;
            }

            var15 = var6 < 0L ? -1L : var12 + var6 * 1000L;
        } else {
            if (var14 && var4 < var10) {
                var1.sendMessage(Msg.parse("<red>У вас уже действует более сильный бустер (x" + fmt(1.0 + var10) + ")."));
                return false;
            }

            var15 = var6 < 0L ? -1L : var8 + var6 * 1000L;
        }

        if (var3) {
            var2.coinBoost = var4;
            var2.coinBoostUntil = var15;
        } else {
            var2.blockBoost = var4;
            var2.blockBoostUntil = var15;
        }

        var1.sendMessage(
            Msg.parse(
                "<green>Личный бустер "
                    + (var3 ? "монет" : "блоков")
                    + " x"
                    + fmt(1.0 + var4)
                    + " активирован: <white>"
                    + (var15 < 0L ? "до вайпа" : Items.fmtTime((var15 - var8) / 1000L))
            )
        );
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent var1) {
        this.tickBars();
    }
}
