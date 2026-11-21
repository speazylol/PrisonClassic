package dev.prisonclassic.basement;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Region;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Тюремный подвал (шахта 6): ПВП-зона из 4 комнат (земляная, каменная, сверхдорогая лазуритовая, Логово со Сточным слизнем).
 * Вход с 6 уровня по «Пропуску в подвал» (выпадает из кейсов): пропуск даёт сессию на basement.session-minutes минут.
 * По окончании сессии игрок выводится на спавн. Зомби подвала роняют кишки, монеты, золото и порошок улучшения книг (пыль).
 */
public final class BasementService {
    private final PrisonClassic plugin;

    public BasementService(PrisonClassic plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    private Region region() {
        Location a = this.plugin.locations().get("basement.a");
        Location b = this.plugin.locations().get("basement.b");
        return a == null || b == null || a.getWorld() != b.getWorld() ? null : Region.of(a, b);
    }

    public boolean inside(Player p) {
        Region r = this.region();
        return r != null && r.contains(p.getLocation());
    }

    public void enter(Player p) {
        PlayerData d = this.plugin.data().get(p);
        Location to = this.plugin.locations().get("warp.basement");
        if (d == null) {
            return;
        }
        if (to == null) {
            Msg.send(p, "no-spawn");
            return;
        }
        int min = this.plugin.getConfig().getInt("basement.min-level", 6);
        if (d.level < min) {
            p.sendMessage(Msg.parse("<red>Подвал доступен с " + min + " уровня."));
            return;
        }
        long now = System.currentTimeMillis();
        if (d.basementUntil <= now && !p.hasPermission("prison.admin")) {
            if (!this.plugin.specials().take(p, "basement_pass", 1)) {
                p.sendMessage(Msg.parse("<red>Нужен «Пропуск в подвал» <gray>— выпадает из сундуков с ключами (/keys)."));
                return;
            }
            d.basementUntil = now + this.plugin.getConfig().getLong("basement.session-minutes", 30L) * 60000L;
            p.sendMessage(Msg.parse("<dark_gray>Пропуск принят. <gray>Доступ в подвал на " + Items.fmtTime((d.basementUntil - now) / 1000L) + ". <red>Здесь PvP!"));
        }
        this.plugin.donate().go(p, to);
    }

    private void tick() {
        Region r = this.region();
        if (r == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.hasPermission("prison.admin") || !r.contains(p.getLocation())) {
                continue;
            }
            PlayerData d = this.plugin.data().get(p);
            if (d == null) {
                continue;
            }
            long left = d.basementUntil - now;
            if (left <= 0L) {
                Location spawn = this.plugin.spawn();
                if (spawn != null) {
                    p.teleport(spawn);
                }
                p.sendMessage(Msg.parse("<gray>Время пропуска в подвал истекло — охрана вывела вас наверх."));
            } else if (left <= 60000L && left > 55000L) {
                p.sendMessage(Msg.parse("<yellow>Пропуск в подвал истечёт через минуту."));
            }
        }
    }
}
