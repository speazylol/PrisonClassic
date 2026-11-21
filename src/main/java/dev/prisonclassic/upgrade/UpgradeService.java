package dev.prisonclassic.upgrade;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.boss.BossManager;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.enchant.EnchantService;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class UpgradeService implements Listener {
    private final PrisonClassic plugin;
    private final NamespacedKey blocksKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey minedKey;
    private final Map<UpgradeService.Chain, List<UpgradeService.Stage>> chains = new EnumMap<>(UpgradeService.Chain.class);

    public UpgradeService(PrisonClassic var1) {
        this.plugin = var1;
        this.blocksKey = new NamespacedKey(var1, "item_blocks");
        this.itemKey = new NamespacedKey(var1, "prison_item");
        this.minedKey = new NamespacedKey(var1, "item_mined");
        this.reload();
    }

    public void reload() {
        this.chains.clear();
        ConfigurationSection var1 = this.plugin.getConfig().getConfigurationSection("upgrades");
        if (var1 != null) {
            for (UpgradeService.Chain var5 : UpgradeService.Chain.values()) {
                ArrayList var6 = new ArrayList();

                for (Map var8 : var1.getMapList(var5.name().toLowerCase(Locale.ROOT))) {
                    Map var9 = intMap(var8.get("kills"));
                    Map var10 = intMap(var8.get("mined"));
                    var6.add(
                        new UpgradeService.Stage(
                            String.valueOf(var8.get("material")).toUpperCase(Locale.ROOT),
                            num(var8.get("level"), 0),
                            num(var8.get("cost"), 0),
                            num(var8.get("chance"), 100),
                            num(var8.get("blocks"), 0),
                            var9,
                            num(var8.get("webs"), 0),
                            num(var8.get("player-level"), 0),
                            var8.get("fail") == null ? defFail(var5) : String.valueOf(var8.get("fail")),
                            var10
                        )
                    );
                }

                this.chains.put(var5, var6);
            }
        }
    }

    private static Map<String, Integer> intMap(Object var0) {
        LinkedHashMap<String, Integer> var1 = new LinkedHashMap<>();
        if (var0 instanceof Map<?, ?> var2) {
            for (Entry<?, ?> var4 : var2.entrySet()) {
                if (var4.getValue() instanceof Number var5) {
                    var1.put(String.valueOf(var4.getKey()), var5.intValue());
                }
            }
        }

        return var1;
    }

    private static String defFail(UpgradeService.Chain var0) {
        return switch (var0) {
            case SWORD -> "downgrade";
            case BOW, FIRE_BOW -> "break";
            default -> "keep";
        };
    }

    private static int num(Object var0, int var1) {
        return var0 instanceof Number var2 ? var2.intValue() : var1;
    }

    private static double num(Object var0, double var1) {
        return var0 instanceof Number var3 ? var3.doubleValue() : var1;
    }

    public void mark(ItemStack var1) {
        ItemMeta var2 = var1.getItemMeta();
        if (var2 != null) {
            var2.getPersistentDataContainer().set(this.itemKey, PersistentDataType.BYTE, (byte)1);
            var2.setUnbreakable(true);
            var1.setItemMeta(var2);
        }
    }

    public long blocks(ItemStack var1) {
        if (var1 != null && var1.hasItemMeta()) {
            Long var2 = var1.getItemMeta().getPersistentDataContainer().get(this.blocksKey, PersistentDataType.LONG);
            return var2 == null ? 0L : var2;
        } else {
            return 0L;
        }
    }

    private Map<String, Long> minedMap(ItemStack var1) {
        LinkedHashMap var2 = new LinkedHashMap();
        if (var1 != null && var1.hasItemMeta()) {
            String var3 = var1.getItemMeta().getPersistentDataContainer().get(this.minedKey, PersistentDataType.STRING);
            if (var3 != null && !var3.isEmpty()) {
                for (String var7 : var3.split(";")) {
                    String[] var8 = var7.split("=");
                    if (var8.length == 2) {
                        try {
                            var2.put(var8[0], Long.parseLong(var8[1]));
                        } catch (NumberFormatException var10) {
                        }
                    }
                }

                return var2;
            } else {
                return var2;
            }
        } else {
            return var2;
        }
    }

    public long mined(ItemStack var1, String var2) {
        Map<String, Long> var3 = this.minedMap(var1);
        long var4 = 0L;

        for (String var9 : var2.toUpperCase(Locale.ROOT).split("\\|")) {
            if (var9.startsWith("*")) {
                String var10 = var9.substring(1);

                for (Entry<String, Long> var12 : var3.entrySet()) {
                    if (((String)var12.getKey()).endsWith(var10)) {
                        var4 += var12.getValue();
                    }
                }
            } else {
                var4 += var3.getOrDefault(var9, 0L);
            }
        }

        return var4;
    }

    public static String label(String var0) {
        ArrayList var1 = new ArrayList();

        for (String var5 : var0.toUpperCase(Locale.ROOT).split("\\|")) {
            if (var5.equals("*_LOG")) {
                var1.add("дерево");
            } else if (var5.equals("*_WOOL")) {
                var1.add("шерсть");
            } else if (var5.startsWith("*")) {
                var1.add(var5.substring(2).toLowerCase(Locale.ROOT));
            } else {
                Material var6 = Material.matchMaterial(var5);
                var1.add(var6 == null ? var5.toLowerCase(Locale.ROOT) : "<lang:" + var6.translationKey() + ">");
            }
        }

        return String.join(" / ", var1);
    }

    public void addBlocks(ItemStack var1, Material var2, long var3) {
        if (var1 != null && !var1.getType().isAir()) {
            UpgradeService.Chain var5 = UpgradeService.Chain.of(var1.getType());
            if (var5 == UpgradeService.Chain.PICKAXE || var5 == UpgradeService.Chain.SHOVEL || var5 == UpgradeService.Chain.AXE) {
                if (var2 != null) {
                    Map<String, Long> var6 = this.minedMap(var1);
                    var6.merge(var2.name(), var3, Long::sum);
                    StringBuilder var7 = new StringBuilder();

                    for (Entry<String, Long> var9 : var6.entrySet()) {
                        if (var7.length() > 0) {
                            var7.append(';');
                        }

                        var7.append((String)var9.getKey()).append('=').append(var9.getValue());
                    }

                    ItemMeta var10 = var1.getItemMeta();
                    var10.getPersistentDataContainer().set(this.minedKey, PersistentDataType.STRING, var7.toString());
                    var1.setItemMeta(var10);
                }

                this.addBlocks(var1, var3);
            }
        }
    }

    public void addBlocks(ItemStack var1, long var2) {
        if (var1 != null && !var1.getType().isAir()) {
            UpgradeService.Chain var4 = UpgradeService.Chain.of(var1.getType());
            if (var4 == UpgradeService.Chain.PICKAXE || var4 == UpgradeService.Chain.SHOVEL || var4 == UpgradeService.Chain.AXE) {
                ItemMeta var5 = var1.getItemMeta();
                if (var5 != null) {
                    PersistentDataContainer var6 = var5.getPersistentDataContainer();
                    Long var7 = var6.get(this.blocksKey, PersistentDataType.LONG);
                    var6.set(this.blocksKey, PersistentDataType.LONG, (var7 == null ? 0L : var7) + var2);
                    var1.setItemMeta(var5);
                    this.plugin.enchants().refreshLore(var1);
                }
            }
        }
    }

    public String blocksLine(ItemStack var1) {
        UpgradeService.Chain var2 = var1 == null ? null : UpgradeService.Chain.of(var1.getType());
        return var2 != UpgradeService.Chain.PICKAXE && var2 != UpgradeService.Chain.SHOVEL && var2 != UpgradeService.Chain.AXE
            ? null
            : "<gray>Добыто блоков: <white>" + this.blocks(var1);
    }

    @EventHandler
    public void onDamage(PlayerItemDamageEvent var1) {
        ItemStack var2 = var1.getItem();
        if (var2.hasItemMeta() && var2.getItemMeta().getPersistentDataContainer().has(this.itemKey, PersistentDataType.BYTE)) {
            var1.setCancelled(true);
        }
    }

    public void giveStarterKit(Player var1, PlayerData var2) {
        if (!var2.starterKit) {
            var2.starterKit = true;

            for (String var4 : this.plugin.getConfig().getStringList("starter-kit")) {
                String[] var5 = var4.split(":");
                Material var6 = Material.matchMaterial(var5[0]);
                if (var6 != null) {
                    ItemStack var7 = new ItemStack(var6, var5.length > 1 ? Integer.parseInt(var5[1].trim()) : 1);
                    if (UpgradeService.Chain.of(var6) != null) {
                        this.mark(var7);
                        this.plugin.enchants().refreshLore(var7);
                    }

                    Items.give(var1, var7);
                }
            }

            var1.sendMessage(
                Msg.parse(
                    "<green>Вы получили стартовый набор. <gray>Рубите дерево в шахте 1, остальное — в /shop. Улучшение предметов: <yellow>/up</yellow> (предмет в руке)."
                )
            );
        }
    }

    private Material materialOf(UpgradeService.Stage var1, ItemStack var2, UpgradeService.Chain var3) {
        if (var3 == UpgradeService.Chain.ARMOR) {
            String var4 = var2.getType().name();
            String var5 = var4.substring(var4.lastIndexOf(95) + 1);
            return Material.matchMaterial(var1.material() + "_" + var5);
        } else {
            return Material.matchMaterial(var1.material());
        }
    }

    private boolean matches(UpgradeService.Stage var1, ItemStack var2, UpgradeService.Chain var3) {
        return this.materialOf(var1, var2, var3) == var2.getType();
    }

    public int stageOf(ItemStack var1) {
        if (var1 != null && !var1.getType().isAir()) {
            UpgradeService.Chain var2 = UpgradeService.Chain.of(var1);
            List var3 = var2 == null ? null : this.chains.get(var2);
            if (var3 != null && !var3.isEmpty()) {
                int var4 = var1.getEnchantmentLevel(var2.ench);
                int var5 = -1;
                int var6 = -1;

                for (int var7 = 0; var7 < var3.size(); var7++) {
                    UpgradeService.Stage var8 = (UpgradeService.Stage)var3.get(var7);
                    if (this.matches(var8, var1, var2)) {
                        if (var6 < 0) {
                            var6 = var7;
                        }

                        if (var8.level() <= var4) {
                            var5 = var7;
                        }
                    }
                }

                if (var5 < 0 && var6 >= 0) {
                    var5 = Math.max(0, var6 - 1);
                }

                return var5;
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public double cost(UpgradeService.Stage var1, PlayerData var2, UpgradeService.Chain var3) {
        double var4 = this.plugin.trainer().discount(var2);
        if (var3 == UpgradeService.Chain.ARMOR
            || var3 == UpgradeService.Chain.PICKAXE
            || var3 == UpgradeService.Chain.SHOVEL
            || var3 == UpgradeService.Chain.AXE) {
            var4 += this.plugin.points().upgradeDiscount(var2);
        }

        return Math.floor(var1.cost() * (1.0 - Math.min(0.5, var4)));
    }

    private String mobName(String var1) {
        BossManager.Type var2 = this.plugin.bosses().type(var1);
        return var2 == null ? var1 : var2.name();
    }

    private List<Entry<String, Boolean>> requirements(UpgradeService.Stage var1, ItemStack var2, PlayerData var3, UpgradeService.Chain var4) {
        ArrayList var5 = new ArrayList();
        double var6 = this.cost(var1, var3, var4);
        var5.add(Map.entry("Монеты: $" + Msg.money(var6) + " <dark_gray>(у вас $" + Msg.money(var3.balance) + ")", var3.balance >= var6));
        if (var1.playerLevel() > 0) {
            var5.add(Map.entry("Уровень игрока: " + var1.playerLevel(), var3.level >= var1.playerLevel()));
        }

        if (var1.blocks() > 0L) {
            var5.add(Map.entry("Блоков этим инструментом: " + this.blocks(var2) + "/" + var1.blocks(), this.blocks(var2) >= var1.blocks()));
        }

        for (Entry var9 : var1.mined().entrySet()) {
            long var10 = this.mined(var2, (String)var9.getKey());
            var5.add(
                Map.entry(
                    "Добыть этим инструментом: " + label((String)var9.getKey()) + " " + var10 + "/" + var9.getValue(),
                    var10 >= ((Integer)var9.getValue()).intValue()
                )
            );
        }

        if (var1.webs() > 0L) {
            var5.add(Map.entry("Добыто паутины: " + var3.stat("cobweb") + "/" + var1.webs(), var3.stat("cobweb") >= var1.webs()));
        }

        for (Entry var13 : var1.kills().entrySet()) {
            var5.add(
                Map.entry(
                    "Убито: <reset>" + this.mobName((String)var13.getKey()) + "<gray> " + var3.mob((String)var13.getKey()) + "/" + var13.getValue(),
                    var3.mob((String)var13.getKey()) >= (Integer)var13.getValue()
                )
            );
        }

        return var5;
    }

    /** Редкость ступени по доле пути цепочки: Обычный → Редкий → Эпический → Легендарный. */
    public String rarity(UpgradeService.Chain chain, int index) {
        List<UpgradeService.Stage> list = this.chains.get(chain);
        int n = list == null ? 1 : Math.max(1, list.size() - 1);
        double f = (double)Math.max(0, index) / n;
        if (f >= 0.85) {
            return "<gold>Легендарный";
        } else if (f >= 0.6) {
            return "<light_purple>Эпический";
        } else {
            return f >= 0.3 ? "<aqua>Редкий" : "<white>Обычный";
        }
    }

    public String rarityLine(ItemStack it) {
        UpgradeService.Chain c = it == null ? null : UpgradeService.Chain.of(it);
        if (c == null || it.getItemMeta() == null || !it.getItemMeta().getPersistentDataContainer().has(this.itemKey, PersistentDataType.BYTE)) {
            return null;
        }
        int st = this.stageOf(it);
        return st < 0 ? null : "<gray>Редкость: " + this.rarity(c, st);
    }

    private String stageName(UpgradeService.Stage var1, UpgradeService.Chain var2) {
        String var4 = var1.material();

        String var3 = switch (var4) {
            case "WOODEN" -> "Деревянный";
            case "STONE" -> "Каменный";
            case "IRON" -> "Железный";
            case "GOLDEN" -> "Золотой";
            case "DIAMOND" -> "Алмазный";
            case "NETHERITE" -> "Незеритовый";
            case "LEATHER" -> "Кожаный";
            case "CHAINMAIL" -> "Кольчужный";
            default -> var1.material().startsWith("WOODEN")
                ? "Деревянный"
                : (
                    var1.material().startsWith("STONE")
                        ? "Каменный"
                        : (
                            var1.material().startsWith("IRON")
                                ? "Железный"
                                : (
                                    var1.material().startsWith("GOLDEN")
                                        ? "Золотой"
                                        : (var1.material().startsWith("DIAMOND") ? "Алмазный" : (var1.material().startsWith("NETHERITE") ? "Незеритовый" : ""))
                                )
                        )
                );
        };

        var4 = switch (var2) {
            case SWORD -> "Острота";
            case BOW, FIRE_BOW -> "Сила";
            case ARMOR -> "Защита";
            default -> "Эффективность";
        };
        return (var3.isEmpty() ? var2.title : var3 + " " + var2.title.toLowerCase(Locale.ROOT))
            + (var1.level() > 0 ? " · " + var4 + " " + EnchantService.roman(var1.level()) : "");
    }

    public void open(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            ItemStack var3 = var1.getInventory().getItemInMainHand();
            if (this.plugin.gypsy() != null && this.plugin.gypsy().isFireSword(var3)) {
                var1.sendMessage(Msg.parse("<gold>Огненный меч улучшается только у Цыгана <gray>(NPC на спавне)."));
                return;
            }
            UpgradeService.Chain var4 = var3.getType().isAir() ? null : UpgradeService.Chain.of(var3);
            int var5 = this.stageOf(var3);
            if (var4 != null && var5 >= 0) {
                List var6 = this.chains.get(var4);
                Gui var7 = new Gui(27, Msg.parse("Улучшение предмета"));
                var7.fill(Items.filler());
                var7.set(11, var3.clone());
                if (var5 >= var6.size() - 1) {
                    var7.set(15, Items.of(Material.NETHER_STAR, "<gold>Максимальное улучшение", "<gray>Этот предмет улучшен до предела."));
                    var7.open(var1);
                } else {
                    UpgradeService.Stage var8 = (UpgradeService.Stage)var6.get(var5 + 1);
                    Material var9 = this.materialOf(var8, var3, var4);
                    ArrayList var10 = new ArrayList();
                    var10.add("<gray>Станет: <white>" + this.stageName(var8, var4));
                    var10.add("<gray>Редкость: " + this.rarity(var4, var5 + 1));
                    var10.add(" ");
                    var10.add("<yellow>Требования:");
                    boolean var11 = true;

                    for (Entry<String, Boolean> var13 : this.requirements(var8, var3, var2, var4)) {
                        var10.add((var13.getValue() ? "<green>✔ <gray>" : "<red>✘ <gray>") + (String)var13.getKey());
                        var11 &= var13.getValue();
                    }

                    var10.add(" ");
                    var10.add("<gray>Шанс успеха: <white>" + var8.chance() + "%");
                    String var14 = var8.fail();

                    var10.add("<gray>При неудаче: " + switch (var14) {
                        case "break" -> "<red>предмет сломается";
                        case "downgrade" -> "<gold>откат на шаг назад";
                        default -> "<yellow>монеты сгорят, предмет цел";
                    });
                    var7.set(13, Items.of(Material.ARROW, "<gold>→"));
                    var7.set(15, Items.of(var9 == null ? Material.BARRIER : var9, "<aqua>" + this.stageName(var8, var4), var10));
                    var7.set(
                        22,
                        Items.of(
                            var11 ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                            var11 ? "<green><bold>Улучшить" : "<red>Не все условия выполнены",
                            "<gray>Шанс: " + var8.chance() + "%"
                        ),
                        (var1x, var2x) -> {
                            this.attempt(var1x);
                            var1x.closeInventory();
                        }
                    );
                    var7.set(26, this.summary(var6, var5, var3, var2, var4));
                    var7.set(18, this.path(var6, var5, var3, var4, var2));
                    var7.open(var1);
                }
            } else {
                var1.sendMessage(Msg.parse("<red>Возьмите в руку инструмент, меч, лук или броню, которые можно улучшить."));
            }
        }
    }

    private ItemStack summary(List<UpgradeService.Stage> var1, int var2, ItemStack var3, PlayerData var4, UpgradeService.Chain var5) {
        double var6 = 0.0;
        long var8 = 0L;
        long var10 = 0L;
        int var12 = 0;
        LinkedHashMap<String, Integer> var13 = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> var14 = new LinkedHashMap<>();

        for (int var15 = var2 + 1; var15 < var1.size(); var15++) {
            UpgradeService.Stage var16 = (UpgradeService.Stage)var1.get(var15);
            var6 += this.cost(var16, var4, var5);
            var8 = Math.max(var8, var16.blocks());
            var10 = Math.max(var10, var16.webs());
            var12 = Math.max(var12, var16.playerLevel());
            var16.mined().forEach((var1x, var2x) -> var13.merge(var1x, var2x, Math::max));
            var16.kills().forEach((var1x, var2x) -> var14.merge(var1x, var2x, Math::max));
        }

        ArrayList var17 = new ArrayList();
        var17.add("<gray>Осталось улучшений: <white>" + (var1.size() - 1 - var2));
        var17.add((var4.balance >= var6 ? "<green>" : "<red>") + "Монеты всего: $" + Msg.money(var6) + " <dark_gray>(у вас $" + Msg.money(var4.balance) + ")");
        if (var12 > var4.level) {
            var17.add("<red>Уровень игрока: " + var12);
        }

        if (var8 > this.blocks(var3)) {
            var17.add("<yellow>Ещё блоков этим инструментом: <white>" + (var8 - this.blocks(var3)));
        }

        var13.forEach((var3x, var4x) -> {
            long var5x = var4x.intValue() - this.mined(var3, var3x);
            if (var5x > 0L) {
                var17.add("<yellow>Ещё добыть: <white>" + label(var3x) + " " + var5x);
            }
        });
        var14.forEach((var3x, var4x) -> {
            int var5x = var4x - var4.mob(var3x);
            if (var5x > 0) {
                var17.add("<yellow>Ещё убить: <reset>" + this.mobName(var3x) + "<white> " + var5x);
            }
        });
        if (var10 > var4.stat("cobweb")) {
            var17.add("<yellow>Ещё паутины: <white>" + (var10 - var4.stat("cobweb")));
        }

        return Items.of(Material.WRITABLE_BOOK, "<gold>Всего до максимума", var17);
    }

    private ItemStack path(List<UpgradeService.Stage> var1, int var2, ItemStack var3, UpgradeService.Chain var4, PlayerData var5) {
        ArrayList var6 = new ArrayList();

        for (int var7 = var2 + 1; var7 < var1.size() && var6.size() < 24; var7++) {
            UpgradeService.Stage var8 = (UpgradeService.Stage)var1.get(var7);
            StringBuilder var9 = new StringBuilder(
                "<gray>"
                    + (var7 - var2)
                    + ". <white>"
                    + this.stageName(var8, var4)
                    + " <gray>$"
                    + Msg.money(this.cost(var8, var5, var4))
                    + ", "
                    + var8.chance()
                    + "%"
            );
            var8.mined().forEach((var1x, var2x) -> var9.append(", ").append(label(var1x)).append(" ").append(var2x));
            if (var8.blocks() > 0L) {
                var9.append(", блоков ").append(var8.blocks());
            }

            var8.kills().forEach((var1x, var2x) -> var9.append(", ").append(var1x).append(" ").append(var2x));
            if (var8.webs() > 0L) {
                var9.append(", паутина ").append(var8.webs());
            }

            if (var8.playerLevel() > 0) {
                var9.append(", ур. ").append(var8.playerLevel());
            }

            var6.add(var9.toString());
        }

        return Items.of(Material.MAP, "<aqua>Путь улучшений", var6);
    }

    public void attempt(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            ItemStack var3 = var1.getInventory().getItemInMainHand();
            if (this.plugin.gypsy() != null && this.plugin.gypsy().isFireSword(var3)) {
                return;
            }
            UpgradeService.Chain var4 = var3.getType().isAir() ? null : UpgradeService.Chain.of(var3);
            int var5 = this.stageOf(var3);
            if (var4 != null && var5 >= 0) {
                List var6 = this.chains.get(var4);
                if (var5 < var6.size() - 1) {
                    UpgradeService.Stage var7 = (UpgradeService.Stage)var6.get(var5 + 1);

                    for (Entry<String, Boolean> var9 : this.requirements(var7, var3, var2, var4)) {
                        if (!(Boolean)var9.getValue()) {
                            var1.sendMessage(Msg.parse("<red>Не выполнено: <gray>" + (String)var9.getKey()));
                            return;
                        }
                    }

                    double var12 = this.cost(var7, var2, var4);
                    var2.balance -= var12;
                    if (ThreadLocalRandom.current().nextInt(100) < var7.chance()) {
                        ItemStack var13 = this.apply(var3, var7, var4);
                        var1.getInventory().setItemInMainHand(var13);
                        var1.playSound(var1.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F, 1.2F);
                        var1.sendMessage(Msg.parse("<green>Успех! <white>" + this.stageName(var7, var4)));
                    } else {
                        String var10 = var7.fail();
                        switch (var10) {
                            case "break":
                                var1.getInventory().setItemInMainHand(null);
                                var1.playSound(var1.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 0.8F);
                                var1.sendMessage(Msg.parse("<red>Неудача... Предмет сломался."));
                                break;
                            case "downgrade":
                                if (var5 > 0) {
                                    var1.getInventory().setItemInMainHand(this.apply(var3, (UpgradeService.Stage)var6.get(var5 - 1), var4));
                                }

                                var1.playSound(var1.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1.0F, 1.0F);
                                var1.sendMessage(Msg.parse("<gold>Неудача! Предмет откатился на шаг назад."));
                                break;
                            default:
                                var1.playSound(var1.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7F, 1.0F);
                                var1.sendMessage(Msg.parse("<yellow>Неудача. Монеты потрачены, предмет цел."));
                        }
                    }
                }
            }
        }
    }

    private ItemStack apply(ItemStack var1, UpgradeService.Stage var2, UpgradeService.Chain var3) {
        Material var4 = this.materialOf(var2, var1, var3);
        ItemStack var5 = var4 != null && var4 != var1.getType() ? var1.withType(var4) : var1.clone();
        ItemMeta var6 = var5.getItemMeta();
        if (var6 == null) {
            return var5;
        } else {
            var6.removeEnchant(var3.ench);
            if (var2.level() > 0) {
                var6.addEnchant(var3.ench, var2.level(), true);
            }

            var5.setItemMeta(var6);
            this.mark(var5);
            this.plugin.enchants().refreshLore(var5);
            return var5;
        }
    }

    public Map<UpgradeService.Chain, List<UpgradeService.Stage>> chains() {
        return this.chains;
    }

    public static enum Chain {
        PICKAXE("Кирка", Enchantment.EFFICIENCY),
        SHOVEL("Лопата", Enchantment.EFFICIENCY),
        AXE("Топор", Enchantment.EFFICIENCY),
        SWORD("Меч", Enchantment.SHARPNESS),
        BOW("Лук", Enchantment.POWER),
        FIRE_BOW("Огненный лук", Enchantment.POWER),
        ARMOR("Броня", Enchantment.PROTECTION);

        public final String title;
        public final Enchantment ench;

        private Chain(String var3, Enchantment var4) {
            this.title = var3;
            this.ench = var4;
        }

        /** С учётом зачарований: лук с «Заговором огня» (Flame) — отдельная цепочка «Огненный лук». */
        public static UpgradeService.Chain of(ItemStack it) {
            if (it == null || it.getType().isAir()) {
                return null;
            }
            if (it.getType() == Material.BOW && it.getEnchantmentLevel(Enchantment.FLAME) > 0) {
                return FIRE_BOW;
            }
            return of(it.getType());
        }

        public static UpgradeService.Chain of(Material var0) {
            String var1 = var0.name();
            if (var1.endsWith("_PICKAXE")) {
                return PICKAXE;
            } else if (var1.endsWith("_SHOVEL")) {
                return SHOVEL;
            } else if (var1.endsWith("_AXE")) {
                return AXE;
            } else if (var1.endsWith("_SWORD")) {
                return SWORD;
            } else if (var0 == Material.BOW) {
                return BOW;
            } else {
                return !var1.endsWith("_HELMET") && !var1.endsWith("_CHESTPLATE") && !var1.endsWith("_LEGGINGS") && !var1.endsWith("_BOOTS") ? null : ARMOR;
            }
        }
    }

    public record Stage(
        String material,
        int level,
        double cost,
        int chance,
        long blocks,
        Map<String, Integer> kills,
        long webs,
        int playerLevel,
        String fail,
        Map<String, Integer> mined
    ) {
    }
}
