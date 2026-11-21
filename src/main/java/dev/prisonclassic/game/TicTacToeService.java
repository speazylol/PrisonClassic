package dev.prisonclassic.game;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Мини-игра «Крестики-нолики» на монеты: /ttt <ник> <сумма>, /ttt accept, /ttt deny.
 * Ставки обоих игроков удерживаются в банке игры (снимаются с баланса при старте).
 * Победитель моментально получает банк (минус комиссия ttt.fee), ничья — возврат ставок.
 * Ход — ttt.turn-seconds секунд, иначе техническое поражение; выход с сервера — поражение.
 */
public final class TicTacToeService implements Listener {
    private static final int[] SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
    private final PrisonClassic plugin;
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, Game> games = new HashMap<>();

    private record Invite(UUID from, double bet, long until) {
    }

    private final class Game implements InventoryHolder {
        final UUID x;
        final UUID o;
        final double bet;
        final int[] cells = new int[9];
        UUID turn;
        long turnUntil;
        boolean over;
        final Inventory inv;

        Game(UUID x, UUID o, double bet) {
            this.x = x;
            this.o = o;
            this.bet = bet;
            this.turn = x;
            this.inv = Bukkit.createInventory(this, 27, Component.text("Крестики-нолики · $" + Msg.money(bet)));
        }

        @Override
        public Inventory getInventory() {
            return this.inv;
        }
    }

    public TicTacToeService(PrisonClassic plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void command(Player p, String[] a) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        if (a.length == 0) {
            p.sendMessage(Msg.parse("<gold>/ttt <ник> <сумма> <gray>— вызвать на крестики-нолики на монеты, <gold>/ttt accept | deny"));
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("accept")) {
            this.accept(p, d);
            return;
        }
        if (sub.equals("deny")) {
            Invite inv = this.invites.remove(p.getUniqueId());
            Player from = inv == null ? null : Bukkit.getPlayer(inv.from());
            if (from != null) {
                from.sendMessage(Msg.parse("<gray>" + p.getName() + " отказался от партии."));
            }
            p.sendMessage(Msg.parse("<gray>Вызов отклонён."));
            return;
        }
        if (a.length < 2) {
            p.sendMessage(Msg.parse("<red>/ttt <ник> <сумма>"));
            return;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p)) {
            Msg.send(p, "player-offline");
            return;
        }
        double bet;
        try {
            bet = Double.parseDouble(a[1]);
        } catch (NumberFormatException e) {
            Msg.send(p, "bad-number");
            return;
        }
        double min = this.plugin.getConfig().getDouble("ttt.min-bet", 10.0);
        if (bet < min) {
            p.sendMessage(Msg.parse("<red>Минимальная ставка: $" + Msg.money(min)));
            return;
        }
        if (d.balance < bet) {
            Msg.send(p, "not-enough", Msg.p("cost", Msg.money(bet)));
            return;
        }
        if (this.games.containsKey(p.getUniqueId()) || this.games.containsKey(t.getUniqueId())) {
            p.sendMessage(Msg.parse("<red>Кто-то из вас уже играет."));
            return;
        }
        this.invites.put(t.getUniqueId(), new Invite(p.getUniqueId(), bet, System.currentTimeMillis() + 60000L));
        p.sendMessage(Msg.parse("<gold>Вызов на крестики-нолики отправлен " + t.getName() + " (ставка $" + Msg.money(bet) + ")."));
        t.sendMessage(Msg.parse("<gold>" + p.getName() + " вызывает вас на крестики-нолики! Ставка: <green>$" + Msg.money(bet)
            + "</green>. <green>/ttt accept <gray>или <red>/ttt deny <gray>(60 сек)"));
    }

    private void accept(Player p, PlayerData d) {
        Invite inv = this.invites.remove(p.getUniqueId());
        if (inv == null || System.currentTimeMillis() > inv.until()) {
            p.sendMessage(Msg.parse("<red>Нет активных вызовов."));
            return;
        }
        Player from = Bukkit.getPlayer(inv.from());
        PlayerData fd = from == null ? null : this.plugin.data().get(from);
        if (fd == null) {
            Msg.send(p, "player-offline");
            return;
        }
        if (fd.balance < inv.bet()) {
            p.sendMessage(Msg.parse("<red>У " + from.getName() + " уже не хватает монет на ставку."));
            return;
        }
        if (d.balance < inv.bet()) {
            Msg.send(p, "not-enough", Msg.p("cost", Msg.money(inv.bet())));
            return;
        }
        // Удержание ставок в банке игры
        fd.balance -= inv.bet();
        d.balance -= inv.bet();
        Game g = new Game(from.getUniqueId(), p.getUniqueId(), inv.bet());
        g.turnUntil = System.currentTimeMillis() + this.turnMs();
        this.games.put(g.x, g);
        this.games.put(g.o, g);
        this.render(g);
        from.openInventory(g.inv);
        p.openInventory(g.inv);
    }

    private long turnMs() {
        return this.plugin.getConfig().getLong("ttt.turn-seconds", 30L) * 1000L;
    }

    private String name(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "?" : n;
    }

    private void render(Game g) {
        ItemStack filler = Items.filler();
        for (int i = 0; i < 27; i++) {
            g.inv.setItem(i, filler);
        }
        for (int i = 0; i < 9; i++) {
            ItemStack it = switch (g.cells[i]) {
                case 1 -> Items.of(Material.RED_CONCRETE, "<red><bold>X <gray>(" + this.name(g.x) + ")");
                case 2 -> Items.of(Material.BLUE_CONCRETE, "<blue><bold>O <gray>(" + this.name(g.o) + ")");
                default -> Items.of(Material.WHITE_STAINED_GLASS_PANE, "<gray>Пусто", "<yellow>Нажмите, чтобы сходить");
            };
            g.inv.setItem(SLOTS[i], it);
        }
        long left = Math.max(0L, (g.turnUntil - System.currentTimeMillis()) / 1000L);
        g.inv.setItem(9, Items.of(Material.GOLD_INGOT, "<gold>Банк: $" + Msg.money(g.bet * 2), "<gray>Комиссия: " + Math.round(this.plugin.getConfig().getDouble("ttt.fee", 0.0) * 100) + "%"));
        g.inv.setItem(17, Items.of(g.turn.equals(g.x) ? Material.RED_DYE : Material.BLUE_DYE, "<white>Ходит: " + this.name(g.turn), "<gray>Осталось: " + left + " сек."));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Game g)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || g.over || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        int idx = -1;
        for (int i = 0; i < 9; i++) {
            if (SLOTS[i] == e.getRawSlot()) {
                idx = i;
            }
        }
        if (idx < 0) {
            return;
        }
        if (!g.turn.equals(p.getUniqueId())) {
            p.sendActionBar(Msg.parse("<red>Сейчас ход соперника."));
            return;
        }
        if (g.cells[idx] != 0) {
            return;
        }
        g.cells[idx] = p.getUniqueId().equals(g.x) ? 1 : 2;
        p.playSound(p.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0F, 1.0F);
        int w = this.winner(g);
        if (w > 0) {
            this.end(g, w == 1 ? g.x : g.o, "собрал(а) линию");
        } else if (this.full(g)) {
            this.draw(g);
        } else {
            g.turn = g.turn.equals(g.x) ? g.o : g.x;
            g.turnUntil = System.currentTimeMillis() + this.turnMs();
            this.render(g);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Game) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Game g && !g.over && e.getPlayer() instanceof Player p) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (!g.over && p.isOnline() && !(p.getOpenInventory().getTopInventory().getHolder() instanceof Game)) {
                    p.openInventory(g.inv);
                }
            }, 2L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game g = this.games.get(e.getPlayer().getUniqueId());
        if (g != null && !g.over) {
            this.end(g, e.getPlayer().getUniqueId().equals(g.x) ? g.o : g.x, "соперник покинул игру");
        }
        this.invites.remove(e.getPlayer().getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Game g : new java.util.HashSet<>(this.games.values())) {
            if (g.over) {
                continue;
            }
            if (now > g.turnUntil) {
                this.end(g, g.turn.equals(g.x) ? g.o : g.x, "соперник не сходил вовремя");
            } else {
                this.render(g);
            }
        }
        this.invites.values().removeIf(i -> now > i.until());
    }

    private int winner(Game g) {
        for (int[] l : LINES) {
            if (g.cells[l[0]] != 0 && g.cells[l[0]] == g.cells[l[1]] && g.cells[l[1]] == g.cells[l[2]]) {
                return g.cells[l[0]];
            }
        }
        return 0;
    }

    private boolean full(Game g) {
        for (int c : g.cells) {
            if (c == 0) {
                return false;
            }
        }
        return true;
    }

    private void end(Game g, UUID winner, String why) {
        g.over = true;
        double prize = g.bet * 2 * (1.0 - this.plugin.getConfig().getDouble("ttt.fee", 0.0));
        PlayerData wd = this.plugin.data().get(winner);
        if (wd != null) {
            wd.balance += prize;
        }
        for (UUID u : new UUID[]{g.x, g.o}) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.closeInventory();
                p.sendMessage(Msg.parse(u.equals(winner)
                    ? "<green><bold>Победа!</bold> <gray>(" + why + ") Выигрыш: <green>$" + Msg.money(prize)
                    : "<red>Поражение. <gray>" + this.name(winner) + " " + why + "."));
                p.playSound(p.getLocation(), u.equals(winner) ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            }
        }
        this.games.remove(g.x);
        this.games.remove(g.o);
    }

    private void draw(Game g) {
        g.over = true;
        for (UUID u : new UUID[]{g.x, g.o}) {
            PlayerData d = this.plugin.data().get(u);
            if (d != null) {
                d.balance += g.bet;
            }
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.closeInventory();
                p.sendMessage(Msg.parse("<yellow>Ничья! <gray>Ставки возвращены."));
            }
        }
        this.games.remove(g.x);
        this.games.remove(g.o);
    }

    /** При выключении сервера — вернуть удержанные ставки. */
    public void shutdown() {
        for (Game g : new java.util.HashSet<>(this.games.values())) {
            if (!g.over) {
                this.draw(g);
            }
        }
    }
}
