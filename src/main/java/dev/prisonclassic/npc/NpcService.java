package dev.prisonclassic.npc;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public final class NpcService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey key;

    public NpcService(PrisonClassic var1) {
        this.plugin = var1;
        this.key = new NamespacedKey(var1, "npc");
    }

    /** id NPC -> имя; и какой житель сейчас стоит на точке. */
    private final java.util.Map<String, String> defs = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, java.util.UUID> alive = new java.util.HashMap<>();
    private boolean watching = false;

    public void refresh() {
        for (World var2 : Bukkit.getWorlds()) {
            for (Entity var4 : var2.getEntitiesByClass(Villager.class)) {
                if (var4.getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
                    var4.remove();
                }
            }
        }
        this.alive.clear();
        this.defs.clear();
        this.defs.put("quest", "<gold><bold>Квестовик");
        this.defs.put("trainer", "<aqua><bold>Тренер прокачки");
        this.defs.put("shop", "<green><bold>Магазин");
        this.defs.put("seller", "<yellow><bold>Скупщик");
        this.defs.put("gypsy", "<gold><bold>Цыган <dark_gray>(скрещивание)");
        this.defs.put("donate", "<yellow><bold>Донат <dark_gray>(привилегии)");
        this.defs.put("dungeon", "<dark_red><bold>Вход в Вампирские катакомбы");
        for (String zone : this.plugin.merchants().zones()) {
            this.defs.put("merchant." + zone, "<green><bold>" + this.plugin.merchants().title(zone) + " <dark_gray>(ПКМ — продать)");
        }
        this.defs.put("priest", "<white><bold>Священник");
        this.defs.put("smith", "<gray><bold>Кузнец <dark_gray>(/up)");
        this.defs.put("guide", "<aqua><bold>Проводник <dark_gray>(/warp)");
        this.defs.put("awaken", "<light_purple><bold>Пробудитель <dark_gray>(осколки)");
        this.ensure();
        if (!this.watching) {
            this.watching = true;
            // Жители на дальних островах исчезают при выгрузке чанка — возвращаем их, когда чанк снова загружен
            Bukkit.getScheduler().runTaskTimer(this.plugin, this::ensure, 100L, 100L);
        }
    }

    /** Поставить жителя на каждую точку, где его нет (только в загруженных чанках). */
    private void ensure() {
        for (java.util.Map.Entry<String, String> e : this.defs.entrySet()) {
            Location l = this.plugin.locations().get("npc." + e.getKey());
            if (l == null || l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            java.util.UUID id = this.alive.get(e.getKey());
            Entity ent = id == null ? null : Bukkit.getEntity(id);
            if (ent != null && ent.isValid()) continue;
            this.spawn(e.getKey(), e.getValue());
        }
    }

    private void spawn(String var1, String var2) {
        Location var3 = this.plugin.locations().get("npc." + var1);
        if (var3 != null && var3.getWorld() != null) {
            Villager var4 = var3.getWorld().spawn(var3, Villager.class);
            var4.customName(Msg.parse(var2));
            var4.setCustomNameVisible(true);
            var4.setAI(false);
            var4.setInvulnerable(true);
            var4.setSilent(true);
            var4.setPersistent(false);
            var4.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, var1);
            var4.setRemoveWhenFarAway(false);
            this.alive.put(var1, var4.getUniqueId());
            if (var1.startsWith("merchant.")) {
                var4.setProfession(org.bukkit.entity.Villager.Profession.TOOLSMITH);
            } else if (var1.equals("gypsy")) {
                var4.setProfession(org.bukkit.entity.Villager.Profession.CLERIC);
            } else if (var1.equals("donate")) {
                var4.setProfession(org.bukkit.entity.Villager.Profession.LIBRARIAN);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent var1) {
        String var2 = var1.getRightClicked().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        if (var2 != null) {
            var1.setCancelled(true);
            PlayerData var3 = this.plugin.data().get(var1.getPlayer());
            if (var3 != null) {
                Player var4 = var1.getPlayer();
                if (var2.startsWith("merchant.")) {
                    this.plugin.merchants().open(var4, var2.substring("merchant.".length()));
                    return;
                }

                switch (var2) {
                    case "gypsy":
                        this.plugin.gypsy().open(var4);
                        break;
                    case "donate":
                        this.plugin.donate().open(var4);
                        break;
                    case "dungeon":
                        this.plugin.dungeon().command(var4, new String[0]);
                        break;
                    case "trainer":
                        this.plugin.trainer().open(var4);
                        break;
                    case "shop":
                        this.plugin.shop().open(var4);
                        break;
                    case "priest":
                        this.plugin.church().open(var4);
                        break;
                    case "smith":
                        this.plugin.upgrades().open(var4);
                        break;
                    case "awaken":
                        this.plugin.awaken().open(var4);
                        break;
                    case "guide":
                        this.plugin.menus().warps(var4);
                        break;
                    case "seller":
                        if (!this.plugin.sell().canSell(var4, var3)) {
                            Msg.send(var4, "sell-premium-only");
                            break;
                        }

                        double var10 = this.plugin.sell().sellAll(var4, var3);
                        if (var10 <= 0.0) {
                            Msg.send(var4, "sell-nothing");
                        } else {
                            Msg.send(
                                var4,
                                "sell-done",
                                Msg.p("amount", Msg.money(var10)),
                                Msg.p("mult", String.format(Locale.US, "%.2f", this.plugin.sell().multiplier(var3)))
                            );
                        }
                        break;
                    default:
                        int brains = this.plugin.specials().count(var4, "brain");
                        int per = this.plugin.getConfig().getInt("quests.brain.per-reward", 5);
                        if (brains >= per) {
                            int sets = brains / per;
                            this.plugin.specials().take(var4, "brain", sets * per);
                            var3.addKey("common", sets);
                            var3.balance += sets * this.plugin.getConfig().getDouble("quests.brain.coins", 300.0);
                            var4.sendMessage(Msg.parse("<light_purple>Квестовик принял мозги (" + sets * per + "): <gold>+" + sets + " обычных ключей, <green>$"
                                + Msg.money(sets * this.plugin.getConfig().getDouble("quests.brain.coins", 300.0))));
                        } else if (brains > 0) {
                            var4.sendMessage(Msg.parse("<gray>Квестовик: «Принеси " + per + " мозгов Королевского зомби — дам ключ и монеты». У вас: " + brains));
                        }

                        var4.sendMessage(Msg.parse("<gold>Квестовик: <gray>Ежедневные задания и награды за блоки:"));

                        for (Component var8 : this.plugin.quests().describe(var3)) {
                            var4.sendMessage(var8);
                        }

                        String var9 = this.plugin.mining().nextMilestone(var3);
                        var4.sendMessage(Msg.parse(var9 == null ? "<green>Все награды за блоки получены!" : "<yellow>Следующая награда: <white>" + var9));
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent var1) {
        if (var1.getEntity().getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
            var1.setCancelled(true);
        }
    }
}
