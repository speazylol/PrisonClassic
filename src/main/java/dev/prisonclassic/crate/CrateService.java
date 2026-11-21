package dev.prisonclassic.crate;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.booster.BoosterService;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.mine.MineDefs;
import dev.prisonclassic.mine.MiningService;
import dev.prisonclassic.treasure.TreasureService;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.upgrade.UpgradeService;
import dev.prisonclassic.util.Items;
import dev.prisonclassic.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

public final class CrateService implements Listener {
    public static final List<String> TIERS = List.of("common", "dk", "legendary");
    private final PrisonClassic plugin;

    public CrateService(PrisonClassic var1) {
        this.plugin = var1;
    }

    private ConfigurationSection crate(String var1) {
        return this.plugin.getConfig().getConfigurationSection("crates." + var1);
    }

    public String name(String var1) {
        ConfigurationSection var2 = this.crate(var1);
        return var2 == null ? MiningService.keyName(var1) : var2.getString("name", var1);
    }

    public void open(Player var1) {
        PlayerData var2 = this.plugin.data().get(var1);
        if (var2 != null) {
            Gui var3 = new Gui(27, Msg.parse("Ключи и сундуки"));
            var3.fill(Items.filler());
            Material[] var4 = new Material[]{Material.CHEST, Material.ENDER_CHEST, Material.TRAPPED_CHEST};
            int[] var5 = new int[]{11, 13, 15};

            for (int var6 = 0; var6 < 3; var6++) {
                String var7 = TIERS.get(var6);
                ArrayList var8 = new ArrayList();
                var8.add("<gray>Ваших ключей: <white>" + var2.key(var7));
                var8.add(" ");
                var8.add("<yellow>ЛКМ — открыть");
                var8.add("<yellow>ПКМ — что внутри");
                var3.set(var5[var6], Items.of(var4[var6], this.name(var7), var8), (var2x, var3x) -> {
                    if (var3x.getClick() != ClickType.RIGHT && var3x.getClick() != ClickType.SHIFT_RIGHT) {
                        this.openCrate(var2x, var7);
                    } else {
                        this.preview(var2x, var7);
                    }
                });
            }

            FileConfiguration var9 = this.plugin.getConfig();
            var3.set(
                22,
                Items.of(
                    Material.NETHER_STAR,
                    "<gold>Обмены (/exchange)",
                    "<gray>Звёзды: <yellow>" + this.plugin.specials().stars(var1, var2),
                    "<gray>" + var9.getInt("exchange.stars-per-dk", 8) + " звёзд → 1 ДК-ключ",
                    "<gray>" + var9.getInt("exchange.common-per-dk", 64) + " обычных → 1 ДК-ключ",
                    "<gray>" + var9.getInt("exchange.dk-per-legendary", 16) + " ДК → 1 легендарный",
                    "<gray>" + var9.getInt("exchange.dust-per-100", 15) + " пыли 10% → 1 пыль 100%"
                )
            );
            var3.open(var1);
        }
    }

    private void preview(Player var1, String var2) {
        ConfigurationSection var3 = this.crate(var2);
        if (var3 != null) {
            var1.closeInventory();
            var1.sendMessage(Msg.parse("<gold>" + this.name(var2) + "<gray> — возможное содержимое:"));
            double var4 = 0.0;

            for (Map var7 : var3.getMapList("loot")) {
                var4 += weight(var7);
            }

            for (Map var9 : var3.getMapList("loot")) {
                var1.sendMessage(
                    Msg.parse("<gray> • <white>" + this.describe(var9) + " <dark_gray>(" + String.format(Locale.US, "%.1f", weight(var9) * 100.0 / var4) + "%)")
                );
            }
        }
    }

    private static double weight(Map<?, ?> var0) {
        return var0.get("weight") instanceof Number var1 ? var1.doubleValue() : 1.0;
    }

    private static int num(Map<?, ?> var0, String var1, int var2) {
        return var0.get(var1) instanceof Number var3 ? var3.intValue() : var2;
    }

    private String describe(Map<?, ?> var1) {
        String var2 = String.valueOf(var1.get("type"));

        return switch (var2) {
            case "money" -> "Монеты (" + num(var1, "min", 50) + "–" + num(var1, "max", 200) + " блоков вашего уровня)";
            case "book" -> var1.get("ench") != null
                ? "Книга «" + (dev.prisonclassic.enchant.Ench.parse(String.valueOf(var1.get("ench"))) == null ? var1.get("ench") : dev.prisonclassic.enchant.Ench.parse(String.valueOf(var1.get("ench"))).title) + "»"
                : "Книга зачарования (" + MiningService.keyName(String.valueOf(var1.get("tier"))) + ")";
            case "pass" -> this.plugin.specials().def(String.valueOf(var1.get("id"))) == null ? "Пропуск" : PlainTextComponentSerializer.plainText().serialize(Msg.parse(this.plugin.specials().def(String.valueOf(var1.get("id"))).name()));
            case "rare_pickaxe" -> "Редкая кирка «Шёлк» (руда целиком)";
            case "fire_bow" -> "Огненный лук";
            case "custom_armor" -> "Кастомная броня (" + var1.get("material") + ") с чаром";
            case "dust" -> "Пыль " + num(var1, "pct", 10) + "% x" + num(var1, "amount", 1);
            case "key" -> MiningService.keyName(String.valueOf(var1.get("id"))) + " x" + num(var1, "amount", 1);
            case "treasure" -> "Случайное сокровище шахты";
            case "booster" -> (Boolean.TRUE.equals(var1.get("global")) ? "Глобальный" : "Личный")
                + " бустер "
                + (Boolean.FALSE.equals(var1.get("coin")) ? "блоков" : "монет")
                + " +"
                + var1.get("value")
                + " ("
                + var1.get("duration")
                + ")";
            case "pmine" -> "Личная шахта на " + num(var1, "minutes", 20) + " мин.";
            case "stars" -> "Звёзды x" + num(var1, "amount", 1);
            case "special" -> {
                String var7 = String.valueOf(var1.get("id"));
                switch (var7) {
                    case "toilet":
                        yield "Туалетная бумага x" + num(var1, "amount", 1);
                    case "soap":
                        yield "Мыло x" + num(var1, "amount", 1);
                    case "medicine":
                        yield "Лекарство x" + num(var1, "amount", 1);
                    default:
                        yield String.valueOf(var1.get("id"));
                }
            }
            default -> {
                Material var5 = Material.matchMaterial(String.valueOf(var1.get("material")));
                String var6 = var1.get("name") != null
                    ? PlainTextComponentSerializer.plainText().serialize(Msg.parse(String.valueOf(var1.get("name"))))
                    : (var5 == null ? "?" : var5.name().toLowerCase(Locale.ROOT));
                yield var6 + " x" + num(var1, "amount", 1);
            }
        };
    }

    private CrateService.Reward build(Player var1, PlayerData var2, Map<?, ?> var3) {
        ThreadLocalRandom var4 = ThreadLocalRandom.current();
        String var5 = String.valueOf(var3.get("type"));
        switch (var5) {
            case "money":
                int var23 = num(var3, "min", 50);
                int var28 = Math.max(var23, num(var3, "max", 200));
                double var31 = (var23 + var4.nextInt(var28 - var23 + 1)) * MineDefs.basePrice(Math.max(1, var2.level));
                return new CrateService.Reward(
                    Items.of(Material.GOLD_INGOT, "<green>$" + Msg.money(var31)), "$" + Msg.money(var31), () -> var2.balance += var31
                );
            case "book":
                dev.prisonclassic.enchant.Ench fixed = var3.get("ench") == null ? null : dev.prisonclassic.enchant.Ench.parse(String.valueOf(var3.get("ench")));
                ItemStack var22 = fixed != null
                    ? this.plugin.enchants().book(fixed, Math.max(1, Math.min(fixed.max, num(var3, "level", 1 + var4.nextInt(fixed.max)))), num(var3, "chance", 20))
                    : this.plugin.enchants().randomBook(String.valueOf(var3.get("tier")));
                return new CrateService.Reward(var22, "книга " + plainName(var22), () -> Items.give(var1, var22));
            case "pass":
                String passId = String.valueOf(var3.get("id"));
                ItemStack pass = this.plugin.specials().create(passId, num(var3, "amount", 1));
                if (pass == null) {
                    pass = new ItemStack(Material.PAPER);
                }
                ItemStack passF = pass;
                return new CrateService.Reward(passF, plainName(passF), () -> Items.give(var1, passF));
            case "rare_pickaxe": {
                ItemStack pk = new ItemStack(Material.DIAMOND_PICKAXE);
                org.bukkit.inventory.meta.ItemMeta pm = pk.getItemMeta();
                pm.displayName(Items.line("<aqua><bold>Редкая кирка «Шёлк»"));
                pm.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, num(var3, "efficiency", 2), true);
                pm.addEnchant(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1, true);
                pk.setItemMeta(pm);
                this.plugin.upgrades().mark(pk);
                this.plugin.enchants().refreshLore(pk);
                return new CrateService.Reward(pk, "редкая кирка «Шёлк»", () -> Items.give(var1, pk));
            }
            case "fire_bow": {
                ItemStack bow = new ItemStack(Material.BOW);
                org.bukkit.inventory.meta.ItemMeta bm = bow.getItemMeta();
                bm.displayName(Items.line("<gold><bold>Огненный лук"));
                bm.addEnchant(org.bukkit.enchantments.Enchantment.FLAME, 1, true);
                bow.setItemMeta(bm);
                this.plugin.upgrades().mark(bow);
                this.plugin.enchants().refreshLore(bow);
                return new CrateService.Reward(bow, "огненный лук", () -> Items.give(var1, bow));
            }
            case "custom_armor": {
                String[] parts = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
                String tier = String.valueOf(var3.get("material") == null ? "IRON" : var3.get("material"));
                Material am = Material.matchMaterial(tier + "_" + parts[var4.nextInt(parts.length)]);
                ItemStack ar = new ItemStack(am == null ? Material.IRON_CHESTPLATE : am);
                org.bukkit.inventory.meta.ItemMeta arm = ar.getItemMeta();
                arm.displayName(Items.line("<light_purple><bold>Кастомная броня"));
                arm.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, num(var3, "protection", 2), true);
                ar.setItemMeta(arm);
                this.plugin.upgrades().mark(ar);
                dev.prisonclassic.enchant.Ench[] pool = {dev.prisonclassic.enchant.Ench.NESOKRA, dev.prisonclassic.enchant.Ench.ENLIGHT, dev.prisonclassic.enchant.Ench.CACTUS,
                    dev.prisonclassic.enchant.Ench.WAVE, dev.prisonclassic.enchant.Ench.NINJA, dev.prisonclassic.enchant.Ench.VOODOO};
                this.plugin.enchants().apply(ar, pool[var4.nextInt(pool.length)], 1);
                this.plugin.enchants().refreshLore(ar);
                return new CrateService.Reward(ar, "кастомная броня " + plainName(ar), () -> Items.give(var1, ar));
            }
            case "dust":
                ItemStack var21 = this.plugin.enchants().dust(num(var3, "pct", 10), num(var3, "amount", 1));
                return new CrateService.Reward(var21, "пыль " + num(var3, "pct", 10) + "% x" + var21.getAmount(), () -> Items.give(var1, var21));
            case "key":
                String var20 = String.valueOf(var3.get("id"));
                int var27 = num(var3, "amount", 1);
                return new CrateService.Reward(
                    Items.of(Material.TRIPWIRE_HOOK, "<gold>" + MiningService.keyName(var20) + " x" + var27),
                    MiningService.keyName(var20) + " x" + var27,
                    () -> var2.addKey(var20, var27)
                );
            case "stars":
                int var19 = num(var3, "amount", 1);
                return new CrateService.Reward(Items.of(Material.NETHER_STAR, "<yellow>★ x" + var19), var19 + " звёзд", () -> this.plugin.specials().giveStars(var1, var19));
            case "treasure":
                ArrayList var18 = new ArrayList<>(this.plugin.treasures().all());
                ItemStack var26 = this.plugin.treasures().item(((TreasureService.Def)var18.get(var4.nextInt(var18.size()))).id(), 1);
                return new CrateService.Reward(var26, "сокровище " + plainName(var26), () -> Items.give(var1, var26));
            case "booster":
                boolean var17 = !Boolean.FALSE.equals(var3.get("coin"));
                boolean var25 = Boolean.TRUE.equals(var3.get("global"));
                double var30 = var3.get("value") instanceof Number var12 ? var12.doubleValue() : 0.25;
                long var32 = BoosterService.parseDuration(String.valueOf(var3.get("duration") == null ? "1d" : var3.get("duration")));
                ItemStack var14 = this.plugin.boosters().item(var17, var25, var30, var32);
                return new CrateService.Reward(var14, plainName(var14), () -> Items.give(var1, var14));
            case "pmine":
                ItemStack var16 = this.plugin.privateMines().voucher(num(var3, "minutes", 20));
                return new CrateService.Reward(var16, plainName(var16), () -> Items.give(var1, var16));
            case "special":
                int var15 = num(var3, "amount", 1);
                String var29 = String.valueOf(var3.get("id"));

                ItemStack var24 = switch (var29) {
                    case "soap" -> this.plugin.needs().soap(var15);
                    case "medicine" -> this.plugin.church().medicine(var15);
                    case "charm" -> this.plugin.church().charm(var15);
                    default -> this.plugin.needs().toiletPaper(var15);
                };
                return new CrateService.Reward(var24, plainName(var24) + " x" + var15, () -> Items.give(var1, var24));
            default:
                Material var8 = Material.matchMaterial(String.valueOf(var3.get("material")));
                if (var8 == null) {
                    var8 = Material.BREAD;
                }

                ItemStack var9 = new ItemStack(var8, num(var3, "amount", 1));
                if (var3.get("name") != null) {
                    ItemMeta var10 = var9.getItemMeta();
                    var10.displayName(Items.line(String.valueOf(var3.get("name"))));
                    var9.setItemMeta(var10);
                }

                if (UpgradeService.Chain.of(var8) != null) {
                    this.plugin.upgrades().mark(var9);
                    this.plugin.enchants().refreshLore(var9);
                }

                return new CrateService.Reward(var9, plainName(var9) + " x" + var9.getAmount(), () -> Items.give(var1, var9));
        }
    }

    private static String plainName(ItemStack var0) {
        ItemMeta var1 = var0.getItemMeta();
        return var1 != null && var1.hasDisplayName()
            ? PlainTextComponentSerializer.plainText().serialize(var1.displayName())
            : var0.getType().name().toLowerCase(Locale.ROOT);
    }

    private Map<?, ?> pick(List<Map<?, ?>> var1) {
        double var2 = 0.0;

        for (Map var5 : var1) {
            var2 += weight(var5);
        }

        double var8 = ThreadLocalRandom.current().nextDouble() * var2;

        for (Map var7 : var1) {
            var8 -= weight(var7);
            if (var8 <= 0.0) {
                return var7;
            }
        }

        return (Map<?, ?>)var1.get(var1.size() - 1);
    }

    public void openCrate(final Player var1, final String var2) {
        final PlayerData var3 = this.plugin.data().get(var1);
        ConfigurationSection var4 = this.crate(var2);
        if (var3 != null && var4 != null && !var4.getMapList("loot").isEmpty()) {
            if (var3.key(var2) <= 0) {
                var1.sendMessage(Msg.parse("<red>У вас нет ключа: " + MiningService.keyName(var2)));
            } else {
                var3.addKey(var2, -1);
                final List var5 = var4.getMapList("loot");
                final CrateService.Reward var6 = this.build(var1, var3, this.pick(var5));
                var6.give().run();
                final Gui var7 = new Gui(27, Msg.parse(this.name(var2)));
                var7.fill(Items.filler());
                var7.set(4, Items.of(Material.HOPPER, "<gray>▼"));
                var7.open(var1);
                (new BukkitRunnable() {
                    int step = 0;

                    @Override
                    public void run() {
                        if (!var1.isOnline()) {
                            this.cancel();
                        } else if (this.step >= 18) {
                            var7.set(13, var6.icon());
                            var1.playSound(var1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
                            var1.sendMessage(Msg.parse("<gold>" + CrateService.this.name(var2) + "<gray>: вам выпало <white>" + var6.text()));
                            this.cancel();
                        } else {
                            var7.set(13, CrateService.this.build(var1, var3, CrateService.this.pick(var5)).icon());
                            var1.playSound(var1.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, 1.5F + this.step * 0.02F);
                            this.step++;
                        }
                    }
                }).runTaskTimer(this.plugin, 2L, 2L);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.LOW
    )
    public void onChest(PlayerInteractEvent var1) {
        if (var1.getHand() == EquipmentSlot.HAND && var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block var2 = var1.getClickedBlock();
            if (var2 != null) {
                for (String var4 : TIERS) {
                    Location var5 = this.plugin.locations().get("crate." + var4);
                    if (var5 != null
                        && var5.getWorld() == var2.getWorld()
                        && var5.getBlockX() == var2.getX()
                        && var5.getBlockY() == var2.getY()
                        && var5.getBlockZ() == var2.getZ()) {
                        var1.setCancelled(true);
                        this.open(var1.getPlayer());
                        return;
                    }
                }
            }
        }
    }

    private record Reward(ItemStack icon, String text, Runnable give) {
    }
}
