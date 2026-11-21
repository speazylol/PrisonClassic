package dev.prisonclassic.dungeon;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Подземелье «Вампирские катакомбы» (доступ с 17 уровня, вход у NPC на Стройке).
 * Группа строго из 3 игроков, лидер тратит «Пропуск в подземелье» (редкий дроп при копании на Стройке).
 * 3 зала: Вампиры → Стражи и кровососы → Граф-вампир. Тайм-аттак: время прохождения группы пишется в глобальную БД
 * (MySQL-таблица prison_dungeon_records или dungeon-records.yml), /dungeon top.
 * Награда за первое закрытие: 20 000 опыта (очков прогресса уровня) каждому участнику.
 */
public final class DungeonService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey mobKey;
    private final Map<UUID, Set<UUID>> parties = new HashMap<>();
    private final Map<UUID, UUID> memberOf = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();
    private Run run;

    private static final class Run {
        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> alive = new HashSet<>();
        final Set<UUID> mobs = new HashSet<>();
        long started;
        int room = 0;
        long deadline;
        UUID boss;
        long lastAdds;
    }

    public DungeonService(PrisonClassic plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "dungeon_mob");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private FileConfiguration c() {
        return this.plugin.getConfig();
    }

    private int minLevel() {
        return this.c().getInt("dungeon.min-level", 17);
    }

    private int partySize() {
        return this.c().getInt("dungeon.party-size", 3);
    }

    // ===================== Команда /dungeon =====================

    public void command(Player p, String[] a) {
        PlayerData d = this.plugin.data().get(p);
        if (d == null) {
            return;
        }
        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite" -> {
                Player t = a.length < 2 ? null : Bukkit.getPlayerExact(a[1]);
                if (t == null || t.equals(p)) {
                    Msg.send(p, "player-offline");
                    return;
                }
                UUID leader = this.memberOf.getOrDefault(p.getUniqueId(), p.getUniqueId());
                if (!leader.equals(p.getUniqueId())) {
                    p.sendMessage(Msg.parse("<red>Приглашать может только лидер группы."));
                    return;
                }
                Set<UUID> party = this.parties.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashSet<>(List.of(p.getUniqueId())));
                this.memberOf.put(p.getUniqueId(), p.getUniqueId());
                if (party.size() >= this.partySize()) {
                    p.sendMessage(Msg.parse("<red>В группе уже " + this.partySize() + " игрока."));
                    return;
                }
                this.invites.put(t.getUniqueId(), p.getUniqueId());
                p.sendMessage(Msg.parse("<gray>Приглашение отправлено: <white>" + t.getName()));
                t.sendMessage(Msg.parse("<dark_red>" + p.getName() + " зовёт вас в Вампирские катакомбы. <green>/dungeon accept"));
            }
            case "accept" -> {
                UUID leader = this.invites.remove(p.getUniqueId());
                Set<UUID> party = leader == null ? null : this.parties.get(leader);
                if (party == null) {
                    p.sendMessage(Msg.parse("<red>Нет приглашений."));
                    return;
                }
                this.leave(p.getUniqueId());
                if (party.size() >= this.partySize()) {
                    p.sendMessage(Msg.parse("<red>Группа уже заполнена."));
                    return;
                }
                party.add(p.getUniqueId());
                this.memberOf.put(p.getUniqueId(), leader);
                this.tellParty(party, "<green>" + p.getName() + " вступил в группу (" + party.size() + "/" + this.partySize() + ").");
            }
            case "leave" -> {
                this.leave(p.getUniqueId());
                p.sendMessage(Msg.parse("<gray>Вы покинули группу."));
            }
            case "start" -> this.start(p);
            case "top" -> this.top(p);
            default -> {
                p.sendMessage(Msg.parse("<dark_red><bold>Вампирские катакомбы</bold> <gray>— с " + this.minLevel() + " уровня, группа строго из " + this.partySize()
                    + " игроков, нужен «Пропуск в подземелье» (Стройка, шахта 17)."));
                p.sendMessage(Msg.parse("<gray>Первое закрытие: <gold>" + this.c().getLong("dungeon.first-clear-exp", 20000L) + " опыта <gray>каждому. Рекорды: /dungeon top"));
                Set<UUID> party = this.parties.get(this.memberOf.getOrDefault(p.getUniqueId(), p.getUniqueId()));
                if (party != null) {
                    List<String> names = new ArrayList<>();
                    for (UUID u : party) {
                        names.add(Bukkit.getOfflinePlayer(u).getName());
                    }
                    p.sendMessage(Msg.parse("<gray>Группа: <white>" + String.join(", ", names)));
                }
                p.sendMessage(Msg.parse("<yellow>/dungeon invite <ник> | accept | leave | start | top"));
                if (d.dungeonClears > 0) {
                    p.sendMessage(Msg.parse("<gray>Ваши закрытия: <white>" + d.dungeonClears + "<gray>, лучший результат: <white>" + fmt(d.dungeonBestMs)));
                }
            }
        }
    }

    private void leave(UUID u) {
        UUID leader = this.memberOf.remove(u);
        if (leader == null) {
            return;
        }
        Set<UUID> party = this.parties.get(leader);
        if (party == null) {
            return;
        }
        party.remove(u);
        if (leader.equals(u)) {
            this.parties.remove(leader);
            for (UUID m : party) {
                this.memberOf.remove(m);
                Player pl = Bukkit.getPlayer(m);
                if (pl != null) {
                    pl.sendMessage(Msg.parse("<gray>Лидер распустил группу катакомб."));
                }
            }
        }
    }

    private void tellParty(Set<UUID> party, String msg) {
        for (UUID u : party) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null) {
                pl.sendMessage(Msg.parse(msg));
            }
        }
    }

    private static String fmt(long ms) {
        long s = ms / 1000L;
        return String.format(Locale.US, "%d:%02d.%01d", s / 60L, s % 60L, ms % 1000L / 100L);
    }

    private void top(Player p) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            List<String> rows = this.plugin.data().storage().topDungeon(10);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                p.sendMessage(Msg.parse("<dark_red><bold>Рекорды Вампирских катакомб</bold> <gray>(тайм-аттак):"));
                SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy");
                int i = 1;
                for (String r : rows) {
                    String[] parts = r.split("\\|");
                    if (parts.length < 3) {
                        continue;
                    }
                    p.sendMessage(Msg.parse("<yellow>" + i++ + ". <white>" + fmt(Long.parseLong(parts[1])) + " <gray>— " + parts[0] + " <dark_gray>(" + df.format(new Date(Long.parseLong(parts[2]))) + ")"));
                }
                if (rows.isEmpty()) {
                    p.sendMessage(Msg.parse("<gray>Рекордов пока нет."));
                }
            });
        });
    }

    // ===================== Запуск =====================

    private void start(Player leaderP) {
        UUID leader = leaderP.getUniqueId();
        Set<UUID> party = this.parties.get(leader);
        if (party == null || !leader.equals(this.memberOf.get(leader))) {
            leaderP.sendMessage(Msg.parse("<red>Запустить может только лидер группы. Соберите группу: /dungeon invite <ник>"));
            return;
        }
        if (party.size() != this.partySize()) {
            leaderP.sendMessage(Msg.parse("<red>Нужна группа строго из " + this.partySize() + " игроков (сейчас " + party.size() + ")."));
            return;
        }
        if (this.run != null) {
            leaderP.sendMessage(Msg.parse("<red>Катакомбы заняты другой группой. Попробуйте позже."));
            return;
        }
        Location npc = this.plugin.locations().get("npc.dungeon");
        Location start = this.plugin.locations().get("dungeon.room1");
        if (start == null) {
            leaderP.sendMessage(Msg.parse("<red>Катакомбы ещё не построены (/pa build)."));
            return;
        }
        List<Player> members = new ArrayList<>();
        for (UUID u : party) {
            Player pl = Bukkit.getPlayer(u);
            PlayerData pd = pl == null ? null : this.plugin.data().get(pl);
            if (pl == null || pd == null) {
                leaderP.sendMessage(Msg.parse("<red>Не все участники в сети."));
                return;
            }
            if (pd.level < this.minLevel()) {
                leaderP.sendMessage(Msg.parse("<red>" + pl.getName() + ": нужен " + this.minLevel() + " уровень."));
                return;
            }
            if (npc != null && (pl.getWorld() != npc.getWorld() || pl.getLocation().distanceSquared(npc) > 20 * 20)) {
                leaderP.sendMessage(Msg.parse("<red>" + pl.getName() + " должен стоять у входа в катакомбы (Стройка, шахта 17)."));
                return;
            }
            members.add(pl);
        }
        if (!this.plugin.specials().take(leaderP, "dungeon_pass", 1)) {
            leaderP.sendMessage(Msg.parse("<red>Нужен «Пропуск в подземелье» — редкий дроп при копании на Стройке."));
            return;
        }
        Run r = new Run();
        r.started = System.currentTimeMillis();
        r.deadline = r.started + this.c().getLong("dungeon.time-limit-minutes", 15L) * 60000L;
        for (Player pl : members) {
            r.players.add(pl.getUniqueId());
            r.alive.add(pl.getUniqueId());
            pl.teleport(start);
            pl.playSound(start, Sound.AMBIENT_CAVE, 1.0F, 0.6F);
            pl.sendMessage(Msg.parse("<dark_red><bold>Вампирские катакомбы.</bold> <gray>Таймер запущен! Зал 1/3 — истребите вампиров."));
        }
        this.run = r;
        this.room(1);
    }

    private void room(int n) {
        Run r = this.run;
        if (r == null) {
            return;
        }
        r.room = n;
        Location at = this.plugin.locations().get("dungeon.room" + n);
        if (at == null) {
            this.finish(false, "катакомбы не построены");
            return;
        }
        if (n > 1) {
            for (UUID u : r.alive) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null) {
                    pl.teleport(at);
                    pl.sendMessage(Msg.parse("<dark_red>Зал " + n + "/3" + (n == 3 ? " — <bold>Граф-вампир!</bold>" : " — стражи катакомб.")));
                }
            }
        }
        switch (n) {
            case 1 -> {
                for (int i = 0; i < 8; i++) {
                    this.mob(at, EntityType.ZOMBIE, "<dark_red>Вампир", 40.0, 6.0, null, i);
                }
            }
            case 2 -> {
                for (int i = 0; i < 6; i++) {
                    this.mob(at, EntityType.WITHER_SKELETON, "<dark_red>Страж крипты", 60.0, 8.0, null, i);
                }
                for (int i = 0; i < 4; i++) {
                    this.mob(at, EntityType.VEX, "<red>Кровосос", 20.0, 4.0, null, i + 6);
                }
            }
            default -> {
                LivingEntity boss = this.mob(at, EntityType.WITHER_SKELETON, "<dark_red><bold>Граф-вампир", this.c().getDouble("dungeon.boss-health", 600.0), 12.0, Material.NETHERITE_SWORD, 0);
                var scale = boss.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(1.4);
                }
                r.boss = boss.getUniqueId();
                r.lastAdds = System.currentTimeMillis();
            }
        }
    }

    private LivingEntity mob(Location at, EntityType type, String name, double hp, double dmg, Material hand, int i) {
        double ang = Math.PI * 2.0 * i / 8.0;
        Location l = at.clone().add(Math.cos(ang) * 4.0, 0.0, Math.sin(ang) * 4.0);
        LivingEntity e = (LivingEntity)at.getWorld().spawnEntity(l, type);
        e.customName(Msg.parse(name));
        e.setCustomNameVisible(true);
        e.setPersistent(false);
        e.setRemoveWhenFarAway(false);
        var mh = e.getAttribute(Attribute.MAX_HEALTH);
        if (mh != null) {
            mh.setBaseValue(Math.min(1024.0, hp));
            e.setHealth(Math.min(1024.0, hp));
        }
        var ad = e.getAttribute(Attribute.ATTACK_DAMAGE);
        if (ad != null) {
            ad.setBaseValue(dmg);
        }
        if (e.getEquipment() != null) {
            // Шлем защищает нежить от солнца; меч — оружие
            e.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
            e.getEquipment().setHelmetDropChance(0.0F);
            if (hand != null) {
                e.getEquipment().setItemInMainHand(new ItemStack(hand));
                e.getEquipment().setItemInMainHandDropChance(0.0F);
            }
        }
        e.getPersistentDataContainer().set(this.mobKey, PersistentDataType.BYTE, (byte)1);
        this.run.mobs.add(e.getUniqueId());
        return e;
    }

    private boolean isDungeonMob(Entity e) {
        return e.getPersistentDataContainer().has(this.mobKey, PersistentDataType.BYTE);
    }

    // ===================== Тик / события =====================

    private void tick() {
        Run r = this.run;
        if (r == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > r.deadline) {
            this.finish(false, "время вышло");
            return;
        }
        r.mobs.removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            return e == null || e.isDead() || !e.isValid();
        });
        for (UUID u : r.alive) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null) {
                pl.sendActionBar(Msg.parse("<dark_red>Катакомбы <gray>| зал " + r.room + "/3 | врагов: " + r.mobs.size() + " | <white>" + fmt(now - r.started)));
                for (UUID m : r.mobs) {
                    if (Bukkit.getEntity(m) instanceof Mob mob && (mob.getTarget() == null || !mob.getTarget().isValid())) {
                        mob.setTarget(pl);
                    }
                }
            }
        }
        if (r.room == 3 && r.boss != null && now - r.lastAdds > 30000L && Bukkit.getEntity(r.boss) instanceof LivingEntity boss && boss.isValid()) {
            r.lastAdds = now;
            for (int i = 0; i < 2; i++) {
                this.mob(boss.getLocation(), EntityType.ZOMBIE, "<dark_red>Вампир", 40.0, 6.0, null, i * 4);
            }
            for (UUID u : r.alive) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null) {
                    pl.sendMessage(Msg.parse("<dark_red>Граф-вампир призывает слуг!"));
                }
            }
        }
        if (r.mobs.isEmpty()) {
            if (r.room < 3) {
                this.room(r.room + 1);
            } else {
                this.finish(true, null);
            }
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (this.isDungeonMob(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    /** Граф-вампир — вампиризм: лечится на 10% от нанесённого урона. */
    @EventHandler(ignoreCancelled = true)
    public void onBossHit(EntityDamageByEntityEvent e) {
        Run r = this.run;
        if (r != null && r.boss != null && e.getDamager().getUniqueId().equals(r.boss) && e.getDamager() instanceof LivingEntity b) {
            double max = b.getAttribute(Attribute.MAX_HEALTH).getValue();
            b.setHealth(Math.min(max, b.getHealth() + e.getFinalDamage() * 0.10));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Run r = this.run;
        if (r != null && r.alive.remove(e.getEntity().getUniqueId())) {
            e.getEntity().sendMessage(Msg.parse("<red>Вы пали в катакомбах."));
            if (r.alive.isEmpty()) {
                this.finish(false, "вся группа погибла");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Run r = this.run;
        if (r != null && r.alive.remove(e.getPlayer().getUniqueId()) && r.alive.isEmpty()) {
            this.finish(false, "группа покинула катакомбы");
        }
        this.invites.remove(e.getPlayer().getUniqueId());
        this.leave(e.getPlayer().getUniqueId());
    }

    private void finish(boolean win, String reason) {
        Run r = this.run;
        this.run = null;
        if (r == null) {
            return;
        }
        for (UUID m : r.mobs) {
            Entity e = Bukkit.getEntity(m);
            if (e != null) {
                e.remove();
            }
        }
        long ms = System.currentTimeMillis() - r.started;
        Location exit = this.plugin.locations().get("npc.dungeon");
        if (exit == null) {
            exit = this.plugin.spawn();
        }
        List<String> names = new ArrayList<>();
        for (UUID u : r.players) {
            names.add(Bukkit.getOfflinePlayer(u).getName());
        }
        names.sort(String::compareToIgnoreCase);
        if (win) {
            String party = String.join(", ", names);
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.plugin.data().storage().saveDungeonRecord(party, ms, System.currentTimeMillis()));
            Bukkit.broadcast(Msg.parse("<dark_red>⚔ Группа <white>" + party + "</white> прошла Вампирские катакомбы за <gold>" + fmt(ms) + "<dark_red>!"));
        }
        long exp = this.c().getLong("dungeon.first-clear-exp", 20000L);
        double coins = this.c().getDouble("dungeon.repeat-coins", 1500.0);
        for (UUID u : r.players) {
            Player pl = Bukkit.getPlayer(u);
            PlayerData d = pl == null ? null : this.plugin.data().get(pl);
            if (pl == null || d == null) {
                continue;
            }
            if (r.alive.contains(u) && exit != null) {
                pl.teleport(exit);
            }
            if (!win) {
                pl.sendMessage(Msg.parse("<red>Катакомбы провалены: " + reason + "."));
                continue;
            }
            d.dungeonClears++;
            if (d.dungeonBestMs <= 0L || ms < d.dungeonBestMs) {
                d.dungeonBestMs = ms;
            }
            if (d.dungeonFirstClear <= 0L) {
                d.dungeonFirstClear = System.currentTimeMillis();
                this.plugin.mining().addBlocks(pl, d, exp);
                pl.sendMessage(Msg.parse("<gold><bold>Первое закрытие!</bold> <white>+" + exp + " опыта <gray>(очки прогресса уровня)"));
            }
            d.balance += coins;
            Items.give(pl, this.plugin.enchants().dust(10, 3));
            pl.sendMessage(Msg.parse("<green>Награда: $" + Msg.money(coins) + ", пыль 10% x3. <gray>Время: <white>" + fmt(ms)));
            pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
    }

    public void shutdown() {
        if (this.run != null) {
            this.finish(false, "перезагрузка сервера");
        }
    }
}
