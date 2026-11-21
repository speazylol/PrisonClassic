package dev.prisonclassic.family;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class FamilyService implements Listener {
    private final PrisonClassic plugin;
    private final File file;
    private final Map<UUID, UUID> marriages = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, UUID> proposals = new HashMap<>();
    private final Map<UUID, Long> tpCooldown = new HashMap<>();

    public FamilyService(PrisonClassic var1) {
        this.plugin = var1;
        this.file = new File(var1.getDataFolder(), "families.yml");
        YamlConfiguration var2 = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection var3 = var2.getConfigurationSection("pairs");
        if (var3 != null) {
            for (String var5 : var3.getKeys(false)) {
                try {
                    UUID var6 = UUID.fromString(var5);
                    UUID var7 = UUID.fromString(var3.getString(var5 + ".partner"));
                    this.marriages.put(var6, var7);
                    this.names.put(var7, var3.getString(var5 + ".name", "?"));
                } catch (Exception var8) {
                }
            }
        }

        Bukkit.getScheduler().runTaskTimer(var1, this::tick, 100L, 100L);
    }

    private void save() {
        YamlConfiguration var1 = new YamlConfiguration();

        for (Entry var3 : this.marriages.entrySet()) {
            var1.set("pairs." + var3.getKey() + ".partner", ((UUID)var3.getValue()).toString());
            var1.set("pairs." + var3.getKey() + ".name", this.names.getOrDefault(var3.getValue(), "?"));
        }

        try {
            var1.save(this.file);
        } catch (IOException var4) {
            this.plugin.getLogger().warning("families.yml: " + var4.getMessage());
        }
    }

    public boolean married(PlayerData var1, PlayerData var2) {
        return var1 != null && var2 != null && var2.uuid.equals(this.marriages.get(var1.uuid));
    }

    public double sellBonus(PlayerData var1) {
        UUID var2 = this.marriages.get(var1.uuid);
        return var2 != null && Bukkit.getPlayer(var2) != null ? this.plugin.getConfig().getDouble("families.sell-bonus", 0.03) : 0.0;
    }

    public String partnerName(PlayerData var1) {
        UUID var2 = this.marriages.get(var1.uuid);
        return var2 == null ? "нет" : this.names.getOrDefault(var2, "?");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent var1) {
        PlayerData var2 = this.plugin.data().get(var1.getPlayer());
        if (var2 != null) {
            this.names.put(var2.uuid, var1.getPlayer().getName());
            var2.partner = this.marriages.get(var2.uuid);
            var2.partnerName = var2.partner == null ? null : this.names.get(var2.partner);
            if (var2.partner != null) {
                Player var3 = Bukkit.getPlayer(var2.partner);
                if (var3 != null) {
                    var3.sendMessage(Msg.parse("<light_purple>❤ " + var1.getPlayer().getName() + " зашёл в игру."));
                }
            }
        }
    }

    private void tick() {
        double var1 = this.plugin.getConfig().getDouble("families.regen-radius", 15.0);

        for (Entry var4 : this.marriages.entrySet()) {
            Player var5 = Bukkit.getPlayer((UUID)var4.getKey());
            Player var6 = Bukkit.getPlayer((UUID)var4.getValue());
            if (var5 != null && var6 != null && var5.getWorld() == var6.getWorld() && !(var5.getLocation().distanceSquared(var6.getLocation()) > var1 * var1)) {
                var5.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, true, false, true));
                var5.getWorld().spawnParticle(Particle.HEART, var5.getLocation().add(0.0, 2.1, 0.0), 1);
            }
        }
    }

    private void marry(Player var1, Player var2) {
        this.marriages.put(var1.getUniqueId(), var2.getUniqueId());
        this.marriages.put(var2.getUniqueId(), var1.getUniqueId());
        this.names.put(var1.getUniqueId(), var1.getName());
        this.names.put(var2.getUniqueId(), var2.getName());
        PlayerData var3 = this.plugin.data().get(var1);
        PlayerData var4 = this.plugin.data().get(var2);
        if (var3 != null) {
            var3.partner = var2.getUniqueId();
            var3.partnerName = var2.getName();
        }

        if (var4 != null) {
            var4.partner = var1.getUniqueId();
            var4.partnerName = var1.getName();
        }

        this.save();
    }

    public void command(Player var1, String[] var2) {
        PlayerData var3 = this.plugin.data().get(var1);
        if (var3 != null) {
            FileConfiguration var4 = this.plugin.getConfig();
            String var5 = var2.length == 0 ? "info" : var2[0].toLowerCase(Locale.ROOT);
            UUID var6 = this.marriages.get(var1.getUniqueId());
            switch (var5) {
                case "propose":
                    if (var6 != null) {
                        var1.sendMessage(Msg.parse("<red>Вы уже в браке."));
                        return;
                    }

                    Player var20 = var2.length < 2 ? null : Bukkit.getPlayerExact(var2[1]);
                    if (var20 == null || var20.equals(var1)) {
                        Msg.send(var1, "player-offline");
                        return;
                    }

                    if (this.marriages.containsKey(var20.getUniqueId())) {
                        var1.sendMessage(Msg.parse("<red>Этот игрок уже в браке."));
                        return;
                    }

                    int var24 = var4.getInt("families.min-level", 5);
                    if (var3.level < var24) {
                        var1.sendMessage(Msg.parse("<red>Создать семью можно с " + var24 + " уровня."));
                        return;
                    }

                    this.proposals.put(var20.getUniqueId(), var1.getUniqueId());
                    var1.sendMessage(Msg.parse("<light_purple>Вы сделали предложение " + var20.getName() + "."));
                    var20.sendMessage(
                        Msg.parse("<light_purple>❤ " + var1.getName() + " предлагает создать семью! <green>/family accept <gray>или <red>/family deny")
                    );
                    break;
                case "accept":
                    UUID var19 = this.proposals.remove(var1.getUniqueId());
                    Player var23 = var19 == null ? null : Bukkit.getPlayer(var19);
                    if (var23 == null || this.marriages.containsKey(var19) || var6 != null) {
                        var1.sendMessage(Msg.parse("<red>Нет активных предложений."));
                        return;
                    }

                    double var26 = var4.getDouble("families.wedding-cost", 500.0);
                    PlayerData var13 = this.plugin.data().get(var23);
                    if (var13 == null || var13.balance < var26) {
                        var1.sendMessage(Msg.parse("<red>У " + var23.getName() + " не хватает $" + Msg.money(var26) + " на свадьбу."));
                        return;
                    }

                    var13.balance -= var26;
                    this.marry(var23, var1);
                    Bukkit.broadcast(Msg.parse("<light_purple>❤ " + var23.getName() + " и " + var1.getName() + " создали семью! ❤"));

                    for (Player var15 : List.of(var23, var1)) {
                        var15.playSound(var15.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.5F);
                    }
                    break;
                case "deny":
                    UUID var18 = this.proposals.remove(var1.getUniqueId());
                    Player var22 = var18 == null ? null : Bukkit.getPlayer(var18);
                    if (var22 != null) {
                        var22.sendMessage(Msg.parse("<gray>" + var1.getName() + " отказал(а) вам."));
                    }

                    var1.sendMessage(Msg.parse("<gray>Предложение отклонено."));
                    break;
                case "divorce":
                    if (var6 == null) {
                        var1.sendMessage(Msg.parse("<red>Вы не в браке."));
                        return;
                    }

                    double var17 = var4.getDouble("families.divorce-cost", 1500.0);
                    if (var3.balance < var17) {
                        Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var17)));
                        return;
                    }

                    var3.balance -= var17;
                    this.marriages.remove(var1.getUniqueId());
                    this.marriages.remove(var6);
                    var3.partner = null;
                    var3.partnerName = null;
                    PlayerData var25 = this.plugin.data().get(var6);
                    if (var25 != null) {
                        var25.partner = null;
                        var25.partnerName = null;
                    }

                    this.save();
                    Player var28 = Bukkit.getPlayer(var6);
                    if (var28 != null) {
                        var28.sendMessage(Msg.parse("<gray>" + var1.getName() + " подал(а) на развод. Семья распалась."));
                    }

                    var1.sendMessage(Msg.parse("<gray>Вы развелись."));
                    break;
                case "tp":
                    Player var16 = var6 == null ? null : Bukkit.getPlayer(var6);
                    if (var16 == null) {
                        var1.sendMessage(Msg.parse("<red>Супруг(а) не в сети."));
                        return;
                    }

                    long var21 = System.currentTimeMillis();
                    long var27 = var4.getLong("families.tp-cooldown-seconds", 300L) * 1000L;
                    if (var21 < this.tpCooldown.getOrDefault(var1.getUniqueId(), 0L)) {
                        var1.sendMessage(Msg.parse("<red>Телепорт к семье через " + (this.tpCooldown.get(var1.getUniqueId()) - var21) / 1000L + " сек."));
                        return;
                    }

                    this.tpCooldown.put(var1.getUniqueId(), var21 + var27);
                    this.plugin.donate().go(var1, var16.getLocation());
                    break;
                case "msg":
                    Player var9 = var6 == null ? null : Bukkit.getPlayer(var6);
                    if (var9 == null || var2.length < 2) {
                        var1.sendMessage(Msg.parse("<red>/family msg <текст> (супруг(а) должен быть в сети)"));
                        return;
                    }

                    String var10 = String.join(" ", Arrays.copyOfRange(var2, 1, var2.length));

                    for (Player var12 : List.of(var1, var9)) {
                        var12.sendMessage(Msg.parse("<light_purple>[Семья] <white><n><gray>: <msg>", Msg.p("n", var1.getName()), Msg.p("msg", var10)));
                    }
                    break;
                default:
                    var1.sendMessage(
                        Msg.parse(
                            "<light_purple>Семья: <white>"
                                + (
                                    var6 == null
                                        ? "вы не в браке"
                                        : "супруг(а) "
                                            + this.names.getOrDefault(var6, "?")
                                            + (Bukkit.getPlayer(var6) != null ? " <green>(в сети)" : " <gray>(не в сети)")
                                )
                        )
                    );
                    var1.sendMessage(
                        Msg.parse(
                            "<gray>Бонусы: +"
                                + Math.round(var4.getDouble("families.sell-bonus", 0.03) * 100.0)
                                + "% к продаже, пока супруг(а) в сети; регенерация рядом друг с другом; /family tp; друг друга не бьёте."
                        )
                    );
                    var1.sendMessage(
                        Msg.parse(
                            "<gray>/family propose <игрок> ($"
                                + Msg.money(var4.getDouble("families.wedding-cost", 500.0))
                                + ") | accept | deny | tp | msg <текст> | divorce ($"
                                + Msg.money(var4.getDouble("families.divorce-cost", 1500.0))
                                + ")"
                        )
                    );
            }
        }
    }
}
