package dev.prisonclassic.donate;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Донат-привилегии (право prison.donate.1…5 выдаётся на сайте/LuckPerms): VIP, PREMIUM, ELITE, LEGEND, EVO.
 * Перки: телепорт быстрее, бустеры, /sell в любой точке, автопродажа, без нужд, личная шахта, ежедневные ключи,
 * префикс в чате и табе. Плюс магазин за звёзды (ДК, AutoSell_Premium).
 */
public final class DonateService implements org.bukkit.event.Listener {
    private final PrisonClassic plugin;
    private final Map<UUID, Integer> pending = new HashMap<>();

    public DonateService(PrisonClassic var1) {
        this.plugin = var1;
        Bukkit.getScheduler().runTaskTimer(var1, () -> {
            for (Player var3 : Bukkit.getOnlinePlayers()) {
                PlayerData var4 = var1.data().get(var3);
                if (var4 != null) {
                    var4.donate = this.rank(var3);
                    var3.playerListName(var4.donate > 0 ? this.prefix(var4.donate).append(net.kyori.adventure.text.Component.text(var3.getName())) : null);
                }
            }
        }, 20L, 600L);
    }

    public int rank(Player var1) {
        for (int var2 = 5; var2 >= 1; var2--) {
            if (var1.hasPermission("prison.donate." + var2)) {
                return var2;
            }
        }

        return 0;
    }

    private double cfg(int var1, String var2, double var3) {
        return this.plugin.getConfig().getDouble("donate." + var1 + "." + var2, var3);
    }

    public double tpSeconds(int var1) {
        return var1 <= 0 ? this.plugin.getConfig().getDouble("donate.base-tp-seconds", 3.0) : this.cfg(var1, "tp-seconds", 3.0);
    }

    public double coinBoost(int var1) {
        return var1 <= 0 ? 0.0 : this.cfg(var1, "coin-boost", 0.0);
    }

    public double blockBoost(int var1) {
        return var1 <= 0 ? 0.0 : this.cfg(var1, "block-boost", 0.0);
    }

    public boolean autosell(int var1) {
        return var1 > 0 && this.plugin.getConfig().getBoolean("donate." + var1 + ".autosell", false);
    }

    public boolean noNeeds(int var1) {
        return var1 > 0 && this.plugin.getConfig().getBoolean("donate." + var1 + ".no-needs", false);
    }

    public boolean sell(int r) {
        return r > 0 && this.plugin.getConfig().getBoolean("donate." + r + ".sell", false);
    }

    public String name(int r) {
        return this.plugin.getConfig().getString("donate." + r + ".name", "Ранг " + r);
    }

    public String color(int r) {
        return this.plugin.getConfig().getString("donate." + r + ".color", "gold");
    }

    public net.kyori.adventure.text.Component prefix(int r) {
        return Msg.parse("<dark_gray>[<" + this.color(r) + "><bold>" + this.name(r) + "</bold></" + this.color(r) + "><dark_gray>] ");
    }

    /** Префикс ранга в чате. */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent e) {
        PlayerData d = this.plugin.data().get(e.getPlayer());
        int r = d == null ? 0 : d.donate;
        net.kyori.adventure.text.Component pre = r > 0 ? this.prefix(r) : net.kyori.adventure.text.Component.empty();
        e.renderer((src, name, msg, viewer) -> pre.append(name).append(Msg.parse("<dark_gray> » <white>")).append(msg));
    }

    /** Список перков ранга для меню. */
    private java.util.List<String> perks(int r) {
        var c = this.plugin.getConfig();
        String b = "donate." + r + ".";
        java.util.List<String> l = new java.util.ArrayList<>();
        l.add("<gray>Телепорт: <white>" + this.tpSeconds(r) + " сек.");
        if (this.coinBoost(r) > 0) l.add("<gray>Монеты: <green>+" + Math.round(this.coinBoost(r) * 100) + "%");
        if (this.blockBoost(r) > 0) l.add("<gray>Блоки: <aqua>+" + Math.round(this.blockBoost(r) * 100) + "%");
        if (this.sell(r)) l.add("<gold>/sell <gray>— продажа всего в любой точке");
        if (this.autosell(r)) l.add("<gold>/autosell <gray>— автопродажа при добыче");
        if (this.noNeeds(r)) l.add("<gray>Без нужд (туалет, душ, сон)");
        long pm = c.getLong("private-mine.donate-minutes-per-day." + r, 0);
        if (pm > 0) l.add("<gray>Личная шахта: <white>" + pm + " мин. в день");
        if (this.perkDiscount(r) > 0) l.add("<gray>Скидка у тренера: <white>" + Math.round(this.perkDiscount(r) * 100) + "%");
        int keys = c.getInt(b + "daily-keys", 0), dk = c.getInt(b + "daily-dk", 0);
        if (keys > 0 || dk > 0) l.add("<gray>Ежедневно: <white>" + (keys > 0 ? keys + " обычн. ключ(а)" : "") + (dk > 0 ? (keys > 0 ? " + " : "") + dk + " ДК" : ""));
        l.add("<gray>Префикс в чате и табе");
        return l;
    }

    /** /donate — привилегии, ежедневная награда, магазин за звёзды. */
    public void open(Player p) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) return;
        dev.prisonclassic.ui.Gui gui = new dev.prisonclassic.ui.Gui(45, Msg.parse("Донат-привилегии"));
        gui.fill(dev.prisonclassic.util.Items.filler());
        org.bukkit.Material[] icons = {org.bukkit.Material.IRON_INGOT, org.bukkit.Material.GOLD_INGOT, org.bukkit.Material.DIAMOND,
                org.bukkit.Material.EMERALD, org.bukkit.Material.NETHER_STAR};
        for (int r = 1; r <= 5; r++) {
            java.util.List<String> lore = new java.util.ArrayList<>(this.perks(r));
            lore.add(" ");
            lore.add(d.donate == r ? "<green><bold>✔ Ваша привилегия" : d.donate > r ? "<dark_green>Включено в вашу" : "<yellow>Купить: " + this.plugin.getConfig().getString("donate.site", "сайт сервера"));
            gui.set(10 + r, dev.prisonclassic.util.Items.of(icons[r - 1], "<" + this.color(r) + "><bold>" + this.name(r), lore));
        }
        boolean claimed = d.donateDailyDay == java.time.LocalDate.now().toEpochDay();
        gui.set(30, dev.prisonclassic.util.Items.of(claimed ? org.bukkit.Material.MINECART : org.bukkit.Material.CHEST_MINECART,
                "<gold><bold>Ежедневная награда", d.donate <= 0 ? "<gray>Только для привилегий" : claimed ? "<red>Уже получена сегодня" : "<yellow>Нажмите — забрать"),
                (pl, e) -> this.claimDaily(pl));
        gui.set(32, dev.prisonclassic.util.Items.of(org.bukkit.Material.NETHER_STAR, "<yellow><bold>Магазин за звёзды",
                "<gray>Древний ключ, AutoSell_Premium", "<yellow>Нажмите — открыть"), (pl, e) -> this.openShop(pl));
        gui.set(4, dev.prisonclassic.util.Items.of(org.bukkit.Material.GOLD_BLOCK, "<gold><bold>Ваш статус: "
                + (d.donate > 0 ? "<" + this.color(d.donate) + ">" + this.name(d.donate) : "<gray>игрок"),
                "<gray>Покупка привилегий: <white>" + this.plugin.getConfig().getString("donate.site", "сайт сервера")));
        gui.open(p);
    }

    private void claimDaily(Player p) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null || d.donate <= 0) {
            p.sendMessage(Msg.parse("<red>Ежедневная награда — только для привилегий. /donate"));
            return;
        }
        long today = java.time.LocalDate.now().toEpochDay();
        if (d.donateDailyDay == today) {
            p.sendMessage(Msg.parse("<red>Вы уже забрали награду сегодня."));
            return;
        }
        d.donateDailyDay = today;
        int keys = this.plugin.getConfig().getInt("donate." + d.donate + ".daily-keys", 0);
        int dk = this.plugin.getConfig().getInt("donate." + d.donate + ".daily-dk", 0);
        if (keys > 0) d.addKey("common", keys);
        if (dk > 0) d.addKey("dk", dk);
        p.sendMessage(Msg.parse("<gold>Награда " + this.name(d.donate) + ": <white>" + keys + " обычн. ключ(а)" + (dk > 0 ? " + " + dk + " ДК" : "") + " <gray>(/keys)"));
        this.open(p);
    }

    public double perkDiscount(int var1) {
        return var1 <= 0 ? 0.0 : this.cfg(var1, "trainer-discount", 0.0);
    }

    /**
     * Донат-NPC на спавне: покупки за Звёзды (донат-валюта).
     *  - Древний ключ (ДК) — 14 Звёзд (ТЗ);
     *  - флаг AutoSell_Premium (премиум /sell и автопродажа) на N дней.
     */
    public void openShop(Player p) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        int dkPrice = this.plugin.getConfig().getInt("donate-npc.dk-stars", 14);
        int premStars = this.plugin.getConfig().getInt("donate-npc.autosell-premium.stars", 120);
        int premDays = this.plugin.getConfig().getInt("donate-npc.autosell-premium.days", 30);
        dev.prisonclassic.ui.Gui gui = new dev.prisonclassic.ui.Gui(27, Msg.parse("Донат-магазин"));
        gui.fill(dev.prisonclassic.util.Items.filler());
        gui.set(4, dev.prisonclassic.util.Items.of(org.bukkit.Material.NETHER_STAR, "<yellow><bold>Ваши Звёзды: " + this.plugin.specials().stars(p, d),
            "<gray>Звёзды — донат-валюта: боссы, сито, кейсы, трофеи."));
        gui.set(11, dev.prisonclassic.util.Items.of(org.bukkit.Material.TRIPWIRE_HOOK, "<aqua><bold>Древний ключ (ДК)",
            "<gray>Цена: <yellow>" + dkPrice + " ★", "<gray>Единственный источник книги «Тотем бессмертия».", "<yellow>Нажмите — купить 1"), (pl, e) -> {
                PlayerData x = this.plugin.data().get(pl);
                if (!this.plugin.specials().takeStars(pl, x, dkPrice)) {
                    pl.sendMessage(Msg.parse("<red>Нужно " + dkPrice + " звёзд."));
                    return;
                }
                x.addKey("dk", 1);
                pl.sendMessage(Msg.parse("<green>Куплен Древний ключ. <gray>(/keys)"));
                this.openShop(pl);
            });
        boolean active = d.autosellPremiumUntil < 0L || System.currentTimeMillis() < d.autosellPremiumUntil;
        gui.set(15, dev.prisonclassic.util.Items.of(org.bukkit.Material.HOPPER, "<gold><bold>AutoSell_Premium · " + premDays + " дн.",
            "<gray>Премиум-команда <white>/sell</white> — мгновенная продажа всего",
            "<gray>инвентаря в любой точке + <white>/autosell</white>.",
            "<gray>Цена: <yellow>" + premStars + " ★",
            active ? "<green>Активно: " + (d.autosellPremiumUntil < 0L ? "навсегда" : dev.prisonclassic.util.Items.fmtTime((d.autosellPremiumUntil - System.currentTimeMillis()) / 1000L)) : "<red>Не активно",
            "<yellow>Нажмите — купить/продлить"), (pl, e) -> {
                PlayerData x = this.plugin.data().get(pl);
                if (x.autosellPremiumUntil < 0L) {
                    pl.sendMessage(Msg.parse("<green>У вас уже бессрочный AutoSell_Premium."));
                    return;
                }
                if (!this.plugin.specials().takeStars(pl, x, premStars)) {
                    pl.sendMessage(Msg.parse("<red>Нужно " + premStars + " звёзд."));
                    return;
                }
                long base = Math.max(System.currentTimeMillis(), x.autosellPremiumUntil);
                x.autosellPremiumUntil = base + premDays * 86400000L;
                pl.sendMessage(Msg.parse("<gold>AutoSell_Premium активирован на " + premDays + " дн. <gray>/sell и /autosell доступны."));
                this.openShop(pl);
            });
        java.util.List<String> ranks = new java.util.ArrayList<>();
        ranks.add("<gray>Покупаются за реальные деньги на сайте сервера:");
        for (int i = 1; i <= 5; i++) {
            ranks.add("<white>Ранг " + i + "<gray>: телепорт " + this.tpSeconds(i) + "с, монеты +" + Math.round(this.coinBoost(i) * 100) + "%"
                + (this.autosell(i) ? ", <gold>AutoSell_Premium</gold>" : "") + (this.noNeeds(i) ? ", без нужд" : ""));
        }
        gui.set(22, dev.prisonclassic.util.Items.of(org.bukkit.Material.GOLD_BLOCK, "<gold>Донат-ранги", ranks));
        gui.open(p);
    }

    public void go(Player var1, Location var2) {
        PlayerData var3 = this.plugin.data().get(var1);
        double var4 = this.tpSeconds(var3 == null ? 0 : var3.donate);
        if (!(var4 <= 0.05) && !var1.hasPermission("prison.admin")) {
            Location var6 = var1.getLocation().clone();
            int var7 = this.pending.merge(var1.getUniqueId(), 1, Integer::sum);
            var1.sendActionBar(Msg.parse("<yellow>Телепорт через " + String.format("%.2f", var4) + " сек. Не двигайтесь."));
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (var1.isOnline() && this.pending.getOrDefault(var1.getUniqueId(), 0) == var7) {
                    if (var1.getLocation().distanceSquared(var6) > 1.5) {
                        var1.sendActionBar(Msg.parse("<red>Телепорт отменён: вы двигались."));
                    } else {
                        var1.teleport(var2);
                    }
                }
            }, Math.max(1L, Math.round(var4 * 20.0)));
        } else {
            var1.teleport(var2);
        }
    }
}
