package dev.prisonclassic.gang;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.cell.Cell;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.util.Msg;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class GangService implements Listener {
    private final PrisonClassic plugin;
    private final File file;
    private final Map<String, GangService.Gang> gangs = new LinkedHashMap<>();
    private final Map<UUID, GangService.Invite> invites = new HashMap<>();

    public GangService(PrisonClassic var1) {
        this.plugin = var1;
        this.file = new File(var1.getDataFolder(), "gangs.yml");
        this.load();
    }

    private void load() {
        YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection var2 = var1.getConfigurationSection("gangs");
        if (var2 != null) {
            for (String var4 : var2.getKeys(false)) {
                ConfigurationSection var5 = var2.getConfigurationSection(var4);

                try {
                    GangService.Gang var6 = new GangService.Gang(var4, var5.getString("name", var4), UUID.fromString(var5.getString("leader")));
                    var6.bank = var5.getDouble("bank");
                    var6.level = Math.max(1, var5.getInt("level", 1));
                    ConfigurationSection var7 = var5.getConfigurationSection("members");
                    if (var7 != null) {
                        for (String var9 : var7.getKeys(false)) {
                            var6.members.put(UUID.fromString(var9), var7.getString(var9));
                        }
                    }

                    this.gangs.put(var4, var6);
                } catch (Exception var10) {
                    this.plugin.getLogger().warning("gangs.yml: банда " + var4 + " повреждена");
                }
            }
        }
    }

    public void save() {
        YamlConfiguration var1 = new YamlConfiguration();

        for (GangService.Gang var3 : this.gangs.values()) {
            String var4 = "gangs." + var3.id + ".";
            var1.set(var4 + "name", var3.name);
            var1.set(var4 + "leader", var3.leader.toString());
            var1.set(var4 + "bank", var3.bank);
            var1.set(var4 + "level", var3.level);

            for (Entry var6 : var3.members.entrySet()) {
                var1.set(var4 + "members." + var6.getKey(), var6.getValue());
            }
        }

        try {
            var1.save(this.file);
        } catch (IOException var7) {
            this.plugin.getLogger().warning("gangs.yml: " + var7.getMessage());
        }
    }

    public GangService.Gang of(PlayerData var1) {
        if (var1 != null && var1.gang != null) {
            GangService.Gang var2 = this.gangs.get(var1.gang);
            return var2 != null && var2.members.containsKey(var1.uuid) ? var2 : null;
        } else {
            return null;
        }
    }

    public GangService.Gang byId(String id) {
        return id == null ? null : this.gangs.get(id);
    }

    public GangService.Gang ofPlayer(UUID var1) {
        for (GangService.Gang var3 : this.gangs.values()) {
            if (var3.members.containsKey(var1)) {
                return var3;
            }
        }

        return null;
    }

    public boolean sameGang(PlayerData var1, PlayerData var2) {
        GangService.Gang var3 = this.of(var1);
        return var3 != null && var3 == this.of(var2);
    }

    private ConfigurationSection lvl(int var1) {
        return this.plugin.getConfig().getConfigurationSection("gangs.levels." + var1);
    }

    private int maxLevel() {
        ConfigurationSection var1 = this.plugin.getConfig().getConfigurationSection("gangs.levels");
        return var1 == null ? 1 : var1.getKeys(false).size();
    }

    public double sellBonus(PlayerData var1) {
        GangService.Gang var2 = this.of(var1);
        ConfigurationSection var3 = var2 == null ? null : this.lvl(var2.level);
        return var3 == null ? 0.0 : var3.getDouble("sell-bonus", 0.0);
    }

    public double blockBonus(PlayerData var1) {
        GangService.Gang var2 = this.of(var1);
        ConfigurationSection var3 = var2 == null ? null : this.lvl(var2.level);
        return var3 == null ? 0.0 : var3.getDouble("block-bonus", 0.0);
    }

    private int slots(GangService.Gang var1) {
        ConfigurationSection var2 = this.lvl(var1.level);
        return var2 == null ? 5 : var2.getInt("members", 5);
    }

    public boolean cellAccess(UUID var1, Cell var2) {
        if (var2.owner == null) {
            return false;
        } else {
            GangService.Gang var3 = this.ofPlayer(var2.owner);
            return var3 != null && var3.leader.equals(var2.owner) && var3.members.containsKey(var1);
        }
    }

    public String tag(PlayerData var1) {
        GangService.Gang var2 = this.of(var1);
        return var2 == null ? "нет" : var2.name + " [" + var2.level + "]";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent var1) {
        PlayerData var2 = this.plugin.data().get(var1.getPlayer());
        if (var2 != null) {
            GangService.Gang var3 = this.ofPlayer(var2.uuid);
            var2.gang = var3 == null ? null : var3.id;
            if (var3 != null) {
                var3.members.put(var2.uuid, var1.getPlayer().getName());
            }
        }
    }

    private void tell(GangService.Gang var1, String var2) {
        for (UUID var4 : var1.members.keySet()) {
            Player var5 = Bukkit.getPlayer(var4);
            if (var5 != null) {
                var5.sendMessage(Msg.parse("<dark_aqua>[Банда " + var1.name + "] <reset>" + var2));
            }
        }
    }

    public void command(Player var1, String[] var2) {
        PlayerData var3 = this.plugin.data().get(var1);
        if (var3 != null) {
            FileConfiguration var4 = this.plugin.getConfig();
            String var5 = var2.length == 0 ? "info" : var2[0].toLowerCase(Locale.ROOT);
            GangService.Gang var6 = this.of(var3);
            switch (var5) {
                case "create":
                    if (var6 != null) {
                        var1.sendMessage(Msg.parse("<red>Вы уже в банде."));
                        return;
                    }

                    if (var2.length < 2 || !var2[1].matches("[A-Za-zА-Яа-яЁё0-9_]{3,12}")) {
                        var1.sendMessage(Msg.parse("<red>/gang create <название> <gray>(3–12 букв/цифр)"));
                        return;
                    }

                    int var25 = var4.getInt("gangs.min-level", 5);
                    if (var3.level < var25) {
                        var1.sendMessage(Msg.parse("<red>Создать банду можно с " + var25 + " уровня."));
                        return;
                    }

                    String var32 = var2[1].toLowerCase(Locale.ROOT);
                    if (this.gangs.containsKey(var32)) {
                        var1.sendMessage(Msg.parse("<red>Такая банда уже есть."));
                        return;
                    }

                    double var36 = var4.getDouble("gangs.create-cost", 2500.0);
                    if (var3.balance < var36) {
                        Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var36)));
                        return;
                    }

                    var3.balance -= var36;
                    GangService.Gang var37 = new GangService.Gang(var32, var2[1], var1.getUniqueId());
                    var37.members.put(var1.getUniqueId(), var1.getName());
                    this.gangs.put(var32, var37);
                    var3.gang = var32;
                    this.save();
                    Bukkit.broadcast(Msg.parse("<dark_aqua>" + var1.getName() + " основал банду <white>" + var2[1]));
                    break;
                case "invite":
                    if (var6 == null || !var6.leader.equals(var1.getUniqueId())) {
                        var1.sendMessage(Msg.parse("<red>Приглашать может только главарь банды."));
                        return;
                    }

                    Player var24 = var2.length < 2 ? null : Bukkit.getPlayerExact(var2[1]);
                    PlayerData var31 = var24 == null ? null : this.plugin.data().get(var24);
                    if (var31 == null) {
                        Msg.send(var1, "player-offline");
                        return;
                    }

                    if (this.of(var31) != null) {
                        var1.sendMessage(Msg.parse("<red>Игрок уже в банде."));
                        return;
                    }

                    if (var6.members.size() >= this.slots(var6)) {
                        var1.sendMessage(Msg.parse("<red>Нет мест (" + this.slots(var6) + "). Улучшите банду: /gang upgrade"));
                        return;
                    }

                    this.invites.put(var24.getUniqueId(), new GangService.Invite(var6.id, System.currentTimeMillis() + 60000L));
                    var1.sendMessage(Msg.parse("<green>Приглашение отправлено " + var24.getName()));
                    var24.sendMessage(
                        Msg.parse(
                            "<dark_aqua>Вас зовут в банду <white>" + var6.name + "<dark_aqua>. <green>/gang accept <gray>или <red>/gang deny <gray>(60 сек)"
                        )
                    );
                    break;
                case "accept":
                    GangService.Invite var23 = this.invites.remove(var1.getUniqueId());
                    GangService.Gang var30 = var23 != null && var23.until() >= System.currentTimeMillis() ? this.gangs.get(var23.gang()) : null;
                    if (var30 == null) {
                        var1.sendMessage(Msg.parse("<red>Нет активных приглашений."));
                        return;
                    }

                    if (var6 != null) {
                        var1.sendMessage(Msg.parse("<red>Сначала выйдите из своей банды."));
                        return;
                    }

                    if (var30.members.size() >= this.slots(var30)) {
                        var1.sendMessage(Msg.parse("<red>В банде нет мест."));
                        return;
                    }

                    var30.members.put(var1.getUniqueId(), var1.getName());
                    var3.gang = var30.id;
                    this.save();
                    this.tell(var30, "<green>" + var1.getName() + " вступил в банду!");
                    break;
                case "deny":
                    this.invites.remove(var1.getUniqueId());
                    var1.sendMessage(Msg.parse("<gray>Приглашение отклонено."));
                    break;
                case "leave":
                    if (var6 == null) {
                        var1.sendMessage(Msg.parse("<red>Вы не в банде."));
                        return;
                    }

                    if (var6.leader.equals(var1.getUniqueId())) {
                        var1.sendMessage(Msg.parse("<red>Главарь не может выйти. Передайте банду (/gang transfer) или распустите (/gang disband)."));
                        return;
                    }

                    var6.members.remove(var1.getUniqueId());
                    var3.gang = null;
                    this.save();
                    this.tell(var6, "<yellow>" + var1.getName() + " покинул банду.");
                    var1.sendMessage(Msg.parse("<yellow>Вы покинули банду."));
                    break;
                case "kick":
                    if (var6 == null || !var6.leader.equals(var1.getUniqueId()) || var2.length < 2) {
                        var1.sendMessage(Msg.parse("<red>/gang kick <игрок> — только главарь."));
                        return;
                    }

                    UUID var22 = this.find(var6, var2[1]);
                    if (var22 == null || var22.equals(var6.leader)) {
                        var1.sendMessage(Msg.parse("<red>Нет такого участника."));
                        return;
                    }

                    String var29 = var6.members.remove(var22);
                    PlayerData var35 = this.plugin.data().get(var22);
                    if (var35 != null) {
                        var35.gang = null;
                    }

                    this.save();
                    this.tell(var6, "<red>" + var29 + " исключён из банды.");
                    break;
                case "transfer":
                    if (var6 == null || !var6.leader.equals(var1.getUniqueId()) || var2.length < 2) {
                        var1.sendMessage(Msg.parse("<red>/gang transfer <игрок> — только главарь."));
                        return;
                    }

                    UUID var21 = this.find(var6, var2[1]);
                    if (var21 == null) {
                        var1.sendMessage(Msg.parse("<red>Нет такого участника."));
                        return;
                    }

                    var6.leader = var21;
                    this.save();
                    this.tell(var6, "<gold>Новый главарь: " + var6.members.get(var21));
                    break;
                case "disband":
                    if (var6 == null || !var6.leader.equals(var1.getUniqueId())) {
                        var1.sendMessage(Msg.parse("<red>Распустить банду может только главарь."));
                        return;
                    }

                    this.tell(var6, "<red>Банда распущена. Казна ($" + Msg.money(var6.bank) + ") отдана главарю.");
                    var3.balance = var3.balance + var6.bank;

                    for (UUID var28 : var6.members.keySet()) {
                        PlayerData var34 = this.plugin.data().get(var28);
                        if (var34 != null) {
                            var34.gang = null;
                        }
                    }

                    this.gangs.remove(var6.id);
                    this.save();
                    break;
                case "deposit":
                case "withdraw":
                    if (var6 == null || var2.length < 2) {
                        var1.sendMessage(Msg.parse("<red>/gang " + var5 + " <сумма>"));
                        return;
                    }

                    double var19;
                    try {
                        var19 = Double.parseDouble(var2[1]);
                    } catch (NumberFormatException var15) {
                        Msg.send(var1, "bad-number");
                        return;
                    }

                    if (var19 <= 0.0) {
                        return;
                    }

                    if (var5.equals("deposit")) {
                        if (var3.balance < var19) {
                            Msg.send(var1, "not-enough", Msg.p("cost", Msg.money(var19)));
                            return;
                        }

                        var3.balance -= var19;
                        var6.bank += var19;
                        this.tell(var6, "<green>" + var1.getName() + " положил в казну $" + Msg.money(var19));
                    } else {
                        if (!var6.leader.equals(var1.getUniqueId())) {
                            var1.sendMessage(Msg.parse("<red>Снимать из казны может только главарь."));
                            return;
                        }

                        if (var6.bank < var19) {
                            var1.sendMessage(Msg.parse("<red>В казне только $" + Msg.money(var6.bank)));
                            return;
                        }

                        var6.bank -= var19;
                        var3.balance += var19;
                        this.tell(var6, "<yellow>Главарь снял из казны $" + Msg.money(var19));
                    }

                    this.save();
                    break;
                case "upgrade":
                    if (var6 == null || !var6.leader.equals(var1.getUniqueId())) {
                        var1.sendMessage(Msg.parse("<red>Улучшать банду может только главарь."));
                        return;
                    }

                    if (var6.level >= this.maxLevel()) {
                        var1.sendMessage(Msg.parse("<gold>У банды максимальный уровень."));
                        return;
                    }

                    ConfigurationSection var18 = this.lvl(var6.level + 1);
                    double var27 = var18.getDouble("cost", 1000000.0);
                    if (var6.bank < var27) {
                        var1.sendMessage(Msg.parse("<red>В казне нужно $" + Msg.money(var27) + " (есть $" + Msg.money(var6.bank) + "). /gang deposit"));
                        return;
                    }

                    var6.bank -= var27;
                    var6.level++;
                    this.save();
                    this.tell(
                        var6,
                        "<gold>Банда достигла "
                            + var6.level
                            + " уровня! Мест: "
                            + this.slots(var6)
                            + ", продажа +"
                            + Math.round(var18.getDouble("sell-bonus") * 100.0)
                            + "%, блоки +"
                            + Math.round(var18.getDouble("block-bonus") * 100.0)
                            + "%"
                    );
                    break;
                case "home":
                    if (var6 == null) {
                        var1.sendMessage(Msg.parse("<red>Вы не в банде."));
                        return;
                    }

                    Cell var17 = this.plugin.cells().ownedBy(var6.leader);
                    if (var17 == null || var17.spawn == null) {
                        var1.sendMessage(Msg.parse("<red>У главаря нет камеры."));
                        return;
                    }

                    this.plugin.donate().go(var1, var17.spawn);
                    break;
                case "top":
                    ArrayList<GangService.Gang> var16 = new ArrayList<>(this.gangs.values());
                    var16.sort((var0, var1x) -> var0.level != var1x.level ? var1x.level - var0.level : Double.compare(var1x.bank, var0.bank));
                    var1.sendMessage(Msg.parse("<gold>Топ банд:"));

                    for (int var26 = 0; var26 < Math.min(10, var16.size()); var26++) {
                        GangService.Gang var33 = (GangService.Gang)var16.get(var26);
                        var1.sendMessage(
                            Msg.parse(
                                "<yellow>"
                                    + (var26 + 1)
                                    + ". <white>"
                                    + var33.name
                                    + " <gray>— ур. "
                                    + var33.level
                                    + ", "
                                    + var33.members.size()
                                    + " чел., казна $"
                                    + Msg.money(var33.bank)
                            )
                        );
                    }
                    break;
                case "info":
                    GangService.Gang var9 = var2.length >= 2 ? this.gangs.get(var2[1].toLowerCase(Locale.ROOT)) : var6;
                    if (var9 == null) {
                        var1.sendMessage(
                            Msg.parse(
                                "<gray>Вы не в банде. <white>/gang create <название> <gray>($"
                                    + Msg.money(var4.getDouble("gangs.create-cost", 2500.0))
                                    + ", с "
                                    + var4.getInt("gangs.min-level", 5)
                                    + " ур.)"
                            )
                        );
                        this.help(var1);
                        return;
                    }

                    ConfigurationSection var10 = this.lvl(var9.level);
                    var1.sendMessage(
                        Msg.parse(
                            "<dark_aqua>Банда <white>"
                                + var9.name
                                + " <gray>| уровень <white>"
                                + var9.level
                                + "/"
                                + this.maxLevel()
                                + " <gray>| казна <green>$"
                                + Msg.money(var9.bank)
                        )
                    );
                    var1.sendMessage(
                        Msg.parse(
                            "<gray>Бонусы: продажа +"
                                + Math.round((var10 == null ? 0.0 : var10.getDouble("sell-bonus")) * 100.0)
                                + "%, блоки +"
                                + Math.round((var10 == null ? 0.0 : var10.getDouble("block-bonus")) * 100.0)
                                + "%"
                        )
                    );
                    StringBuilder var11 = new StringBuilder();

                    for (Entry var13 : var9.members.entrySet()) {
                        boolean var14 = Bukkit.getPlayer((UUID)var13.getKey()) != null;
                        var11.append(var14 ? "<green>" : "<gray>")
                            .append(((UUID)var13.getKey()).equals(var9.leader) ? "★" : "")
                            .append((String)var13.getValue())
                            .append(" ");
                    }

                    var1.sendMessage(Msg.parse("<gray>Участники (" + var9.members.size() + "/" + this.slots(var9) + "): " + var11));
                    if (var9 == var6) {
                        this.help(var1);
                    }
                    break;
                default:
                    this.help(var1);
            }
        }
    }

    private void help(Player var1) {
        var1.sendMessage(Msg.parse("<gray>/gang create|invite|accept|deny|leave|kick|transfer|disband|deposit|withdraw|upgrade|home|top|info, чат: /g <текст>"));
    }

    private UUID find(GangService.Gang var1, String var2) {
        for (Entry var4 : var1.members.entrySet()) {
            if (((String)var4.getValue()).equalsIgnoreCase(var2)) {
                return (UUID)var4.getKey();
            }
        }

        return null;
    }

    public void chat(Player var1, String var2) {
        GangService.Gang var3 = this.of(this.plugin.data().get(var1));
        if (var3 == null) {
            var1.sendMessage(Msg.parse("<red>Вы не в банде."));
        } else {
            for (UUID var5 : var3.members.keySet()) {
                Player var6 = Bukkit.getPlayer(var5);
                if (var6 != null) {
                    var6.sendMessage(Msg.parse("<dark_aqua>[Банда] <white><n><gray>: <msg>", Msg.p("n", var1.getName()), Msg.p("msg", var2)));
                }
            }
        }
    }

    public Collection<GangService.Gang> all() {
        return this.gangs.values();
    }

    public static final class Gang {
        public final String id;
        public String name;
        public UUID leader;
        public final Map<UUID, String> members = new LinkedHashMap<>();
        public double bank = 0.0;
        public int level = 1;

        Gang(String var1, String var2, UUID var3) {
            this.id = var1;
            this.name = var2;
            this.leader = var3;
        }
    }

    private record Invite(String gang, long until) {
    }
}
