package dev.prisonclassic.mine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * Реестр 24 игровых шахт режима (по ТЗ). Шахта 6 — Тюремный подвал из 3 шахтных комнат (6a, 6, 6b) + 4-я комната «Логово».
 * Имя шахты = номер (уровень входа), title — название локации.
 *
 * Цена блока за штуку: basePrice(level) × mult, где basePrice = 1.27^(level−1) / 64 (до 20 ур.), далее ×1.07 за уровень.
 * Руды без «Шёлка» дают переработанный дроп (уголь, сырое железо, алмаз...) по цене silk-factor (60%) от цены руды;
 * кирка с «Шёлком» копает руду целиком — продаётся по полной цене.
 */
public final class MineDefs {
    private static final List<Def> DEFS = new ArrayList<>();
    private static final Map<Material, Material> DROPS = new EnumMap<>(Material.class);
    private static final Map<Material, Material> ORE_DROPS = new EnumMap<>(Material.class);
    public static final double SILK_FACTOR = 0.6;
    private static final String[] NONE = new String[0];

    public static double basePrice(int level) {
        int l = Math.max(1, level);
        double v = l <= 20 ? Math.pow(1.27, l - 1) : Math.pow(1.27, 19.0) * Math.pow(1.07, l - 20);
        return v / 64.0;
    }

    private static Entry e(Material m, double weight, double mult) {
        return new Entry(m, weight, mult);
    }

    private static String[] a(String... s) {
        return s;
    }

    private static void d(String name, String title, int level, String[] bosses, String[] minions, Entry... comp) {
        DEFS.add(new Def(name, title, level, null, List.of(bosses), List.of(minions), List.of(comp), 21));
    }

    private static void ds(int size, String name, String title, int level, String[] bosses, String[] minions, Entry... comp) {
        DEFS.add(new Def(name, title, level, null, List.of(bosses), List.of(minions), List.of(comp), size));
    }

    private MineDefs() {
    }

    public static List<Def> all() {
        return DEFS;
    }

    public static Def get(String name) {
        for (Def d : DEFS) {
            if (d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    /** Дроп блока без «Шёлка». */
    public static Material dropFor(Material block) {
        if (block == Material.LAVA || block == Material.WATER || block == Material.AIR) {
            return null;
        }
        Material ore = ORE_DROPS.get(block);
        if (ore != null) {
            return ore;
        }
        Material m = DROPS.getOrDefault(block, block);
        return m.isItem() ? m : null;
    }

    /** Дроп блока с учётом «Шёлка»: руда выпадает целиком. */
    public static Material dropFor(Material block, boolean silk) {
        if (silk && ORE_DROPS.containsKey(block)) {
            return block;
        }
        return dropFor(block);
    }

    public static boolean isOre(Material m) {
        return ORE_DROPS.containsKey(m);
    }

    /** «Редкие руды» для навыка Удача (шанс x2 дропа). */
    public static boolean isRare(Material block) {
        return switch (block) {
            case DIAMOND_ORE, EMERALD_ORE, GOLD_ORE, LAPIS_ORE, NETHER_GOLD_ORE, DIAMOND_BLOCK, EMERALD_BLOCK, GOLD_BLOCK, LAPIS_BLOCK,
                OBSIDIAN, CRYING_OBSIDIAN, SEA_LANTERN, AMETHYST_BLOCK, GLOWSTONE, REDSTONE_ORE -> true;
            default -> false;
        };
    }

    public static Map<Material, Double> defaultPrices() {
        EnumMap<Material, Double> out = new EnumMap<>(Material.class);
        for (Def d : DEFS) {
            for (Entry en : d.comp()) {
                double price = d.price(en);
                Material plain = DROPS.getOrDefault(en.block(), en.block());
                if (plain.isItem()) {
                    out.merge(plain, price, Math::max);
                }
                Material ore = ORE_DROPS.get(en.block());
                if (ore != null) {
                    out.merge(ore, Math.max(0.01, Math.round(price * SILK_FACTOR * 100.0) / 100.0), Math::max);
                }
            }
        }
        return out;
    }

    /** Материалы, которые выкупает торговец конкретной шахты. */
    public static List<Material> sellables(Def d) {
        List<Material> out = new ArrayList<>();
        for (Entry en : d.comp()) {
            Material plain = DROPS.getOrDefault(en.block(), en.block());
            if (plain.isItem() && en.block() != Material.LAVA && !out.contains(plain)) {
                out.add(plain);
            }
            Material ore = ORE_DROPS.get(en.block());
            if (ore != null && !out.contains(ore)) {
                out.add(ore);
            }
        }
        if (d.name().equals("1")) {
            out.add(Material.FLINT);
        }
        if (d.name().equals("15") || d.name().equals("12")) {
            out.add(Material.SNOWBALL);
        }
        return out;
    }

    static {
        DROPS.put(Material.GRASS_BLOCK, Material.DIRT);
        ORE_DROPS.put(Material.COAL_ORE, Material.COAL);
        ORE_DROPS.put(Material.IRON_ORE, Material.RAW_IRON);
        ORE_DROPS.put(Material.GOLD_ORE, Material.RAW_GOLD);
        ORE_DROPS.put(Material.DIAMOND_ORE, Material.DIAMOND);
        ORE_DROPS.put(Material.EMERALD_ORE, Material.EMERALD);
        ORE_DROPS.put(Material.LAPIS_ORE, Material.LAPIS_LAZULI);
        ORE_DROPS.put(Material.REDSTONE_ORE, Material.REDSTONE);
        ORE_DROPS.put(Material.NETHER_QUARTZ_ORE, Material.QUARTZ);
        ORE_DROPS.put(Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET);

        d("1", "Землянка", 1, NONE, NONE,
            e(Material.DIRT, 30.0, 1.0), e(Material.SAND, 20.0, 1.0), e(Material.GRAVEL, 20.0, 1.0), e(Material.RED_SAND, 10.0, 1.2), e(Material.OAK_LOG, 20.0, 1.1));
        ds(15, "2", "Огород (арбузы в шерсти)", 2, NONE, NONE,
            e(Material.MELON, 40.0, 1.2), e(Material.LIGHT_GRAY_WOOL, 25.0, 1.1), e(Material.WHITE_WOOL, 10.0, 1.1), e(Material.OAK_LOG, 25.0, 1.1));
        d("3", "Каменная шахта", 3, NONE, a("rat", "rabbit"),
            e(Material.STONE, 30.0, 1.0), e(Material.COBBLESTONE, 25.0, 1.0), e(Material.COAL_ORE, 15.0, 1.6), e(Material.COAL_BLOCK, 4.0, 5.0),
            e(Material.IRON_ORE, 10.0, 2.2), e(Material.GOLD_ORE, 5.0, 3.5), e(Material.DIAMOND_ORE, 2.0, 7.0));
        d("4", "Терракотовая шахта", 4, NONE, a("rat"),
            e(Material.GREEN_TERRACOTTA, 80.0, 1.0), e(Material.YELLOW_TERRACOTTA, 18.0, 2.0), e(Material.RED_TERRACOTTA, 2.0, 6.0));
        d("5", "Карьер", 5, NONE, NONE,
            e(Material.GRANITE, 25.0, 1.0), e(Material.ANDESITE, 25.0, 1.0), e(Material.DIORITE, 20.0, 1.0),
            e(Material.CYAN_GLAZED_TERRACOTTA, 15.0, 1.4), e(Material.WHITE_GLAZED_TERRACOTTA, 10.0, 1.4), e(Material.IRON_ORE, 5.0, 2.5));
        // ===== Тюремный подвал (шахта 6): 4 комнаты, PvP. 1 — земляная, 2 — каменная, 3 — сверхдорогая лазуритовая, 4 — Логово =====
        ds(21, "6a", "Подвал: земляная комната", 6, NONE, NONE,
            e(Material.DIRT, 45.0, 0.6), e(Material.GRAVEL, 25.0, 0.7), e(Material.COARSE_DIRT, 20.0, 0.7), e(Material.CLAY, 10.0, 1.2));
        ds(21, "6", "Подвал: каменная комната", 6, a("sewer_slime"), a("basement_zombie"),
            e(Material.STONE, 25.0, 0.8), e(Material.COBBLESTONE, 15.0, 0.8), e(Material.COAL_ORE, 12.0, 1.3), e(Material.IRON_ORE, 14.0, 2.0),
            e(Material.GOLD_ORE, 12.0, 3.0), e(Material.GOLD_BLOCK, 4.0, 6.0), e(Material.DIAMOND_ORE, 8.0, 5.0), e(Material.EMERALD_ORE, 10.0, 6.0));
        ds(21, "6b", "Подвал: лазуритовая шахта", 6, NONE, NONE,
            e(Material.LAPIS_ORE, 45.0, 8.0), e(Material.LAPIS_BLOCK, 35.0, 14.0), e(Material.DIAMOND_BLOCK, 10.0, 12.0), e(Material.GOLD_BLOCK, 10.0, 8.0));
        d("7", "Адская шахта", 7, NONE, a("magma_mini"),
            e(Material.NETHERRACK, 35.0, 1.0), e(Material.SOUL_SAND, 20.0, 1.4), e(Material.NETHER_QUARTZ_ORE, 15.0, 2.5), e(Material.MAGMA_BLOCK, 12.0, 1.6),
            e(Material.BASALT, 8.0, 1.8), e(Material.NETHER_GOLD_ORE, 6.0, 2.8), e(Material.LAVA, 4.0, 0.0));
        d("8", "Сумеречный лес", 8, NONE, NONE,
            e(Material.DARK_OAK_LOG, 35.0, 1.0), e(Material.PODZOL, 20.0, 0.9), e(Material.MOSS_BLOCK, 15.0, 1.2), e(Material.SPRUCE_LOG, 15.0, 1.0),
            e(Material.COARSE_DIRT, 15.0, 0.8));
        d("9", "Бездна", 9, NONE, NONE,
            e(Material.DEEPSLATE, 35.0, 1.0), e(Material.BLACKSTONE, 20.0, 1.1), e(Material.TUFF, 20.0, 1.0), e(Material.COAL_BLOCK, 10.0, 2.0),
            e(Material.CRYING_OBSIDIAN, 5.0, 3.0), e(Material.REDSTONE_ORE, 10.0, 2.0));
        d("10", "Паучье логово", 10, a("brood_mother"), NONE,
            e(Material.COBWEB, 50.0, 1.0), e(Material.WHITE_WOOL, 30.0, 1.3), e(Material.BONE_BLOCK, 20.0, 1.4));
        d("11", "Изумрудная шахта", 11, NONE, a("mini_slime"),
            e(Material.EMERALD_BLOCK, 70.0, 1.0), e(Material.EMERALD_ORE, 15.0, 1.5), e(Material.GOLD_BLOCK, 10.0, 1.3), e(Material.DIAMOND_BLOCK, 5.0, 2.0));
        d("12", "Ледяная пещера", 12, NONE, NONE,
            e(Material.SNOW_BLOCK, 35.0, 1.0), e(Material.ICE, 25.0, 1.2), e(Material.PACKED_ICE, 25.0, 1.5), e(Material.BLUE_ICE, 15.0, 2.2));
        d("13", "Грибоедово", 13, a("yeti"), NONE,
            e(Material.MUSHROOM_STEM, 30.0, 1.0), e(Material.BROWN_MUSHROOM_BLOCK, 35.0, 1.0), e(Material.RED_MUSHROOM_BLOCK, 25.0, 1.0), e(Material.MYCELIUM, 10.0, 1.4));
        d("14", "Песчаный каньон", 14, NONE, a("cat"),
            e(Material.SANDSTONE, 30.0, 1.0), e(Material.SMOOTH_SANDSTONE, 25.0, 1.0), e(Material.CUT_SANDSTONE, 25.0, 1.1), e(Material.CHISELED_SANDSTONE, 20.0, 1.3));
        d("15", "Логово Йети", 15, NONE, NONE,
            e(Material.SNOW_BLOCK, 40.0, 1.0), e(Material.PACKED_ICE, 30.0, 1.2), e(Material.BLUE_ICE, 20.0, 1.6), e(Material.DIAMOND_ORE, 10.0, 3.0));
        d("16", "Затонувший город", 16, a("sea_guardian"), NONE,
            e(Material.PRISMARINE, 40.0, 1.0), e(Material.PRISMARINE_BRICKS, 30.0, 1.2), e(Material.DARK_PRISMARINE, 25.0, 1.4), e(Material.SEA_LANTERN, 5.0, 3.0));
        d("17", "Стройка", 17, NONE, NONE,
            e(Material.BRICKS, 35.0, 1.0), e(Material.CLAY, 20.0, 0.9), e(Material.TERRACOTTA, 20.0, 1.0), e(Material.WHITE_CONCRETE, 15.0, 1.2), e(Material.IRON_BLOCK, 10.0, 2.0));
        d("18", "Небесный остров", 18, a("sky_lord"), NONE,
            e(Material.QUARTZ_BLOCK, 35.0, 1.0), e(Material.SMOOTH_QUARTZ, 25.0, 1.0), e(Material.WHITE_WOOL, 20.0, 0.9), e(Material.GLOWSTONE, 20.0, 1.5));
        d("19", "Конюшня", 19, NONE, NONE,
            e(Material.HAY_BLOCK, 40.0, 1.0), e(Material.SPRUCE_PLANKS, 25.0, 1.0), e(Material.SPRUCE_LOG, 20.0, 1.1), e(Material.COARSE_DIRT, 15.0, 0.9));
        d("20", "Тыквенное поле", 20, a("evil_pumpkin"), NONE,
            e(Material.PUMPKIN, 40.0, 1.0), e(Material.HAY_BLOCK, 30.0, 0.9), e(Material.GLOWSTONE, 15.0, 1.4), e(Material.MOSSY_COBBLESTONE, 15.0, 0.8));
        d("21", "Обсидиановая шахта", 21, NONE, NONE,
            e(Material.OBSIDIAN, 60.0, 1.4), e(Material.CRYING_OBSIDIAN, 10.0, 2.5), e(Material.END_STONE, 30.0, 0.8));
        d("23", "Небесный остров: цитадель", 23, NONE, NONE,
            e(Material.PURPUR_BLOCK, 40.0, 1.0), e(Material.PURPUR_PILLAR, 25.0, 1.2), e(Material.END_STONE_BRICKS, 25.0, 1.1), e(Material.AMETHYST_BLOCK, 10.0, 2.5));
        d("25", "Монастырь", 25, NONE, NONE,
            e(Material.CALCITE, 35.0, 1.0), e(Material.POLISHED_DIORITE, 25.0, 1.0), e(Material.CHISELED_QUARTZ_BLOCK, 20.0, 1.3), e(Material.GOLD_BLOCK, 20.0, 1.6));
        d("26", "Кладбище", 26, NONE, NONE,
            e(Material.SOUL_SOIL, 30.0, 1.0), e(Material.PODZOL, 25.0, 0.9), e(Material.BONE_BLOCK, 25.0, 1.3), e(Material.SCULK, 20.0, 1.6));
    }

    /** Шахта-определение. Поле minion — первый миньон (совместимость), minions — все. */
    public record Def(String name, String title, int level, Material upper, List<String> bosses, List<String> minions, List<Entry> comp, int size) {
        public boolean basement() {
            return this.name.startsWith("6");
        }

        public String minion() {
            return this.minions.isEmpty() ? null : this.minions.get(0);
        }

        public double price(Entry e) {
            return Math.max(0.01, Math.round(MineDefs.basePrice(this.level) * e.mult() * 100.0) / 100.0);
        }
    }

    public record Entry(Material block, double weight, double mult) {
    }
}
