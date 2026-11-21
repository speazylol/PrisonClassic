package dev.prisonclassic.build;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.cell.Cell;
import dev.prisonclassic.mine.Mine;
import dev.prisonclassic.mine.MineDefs;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Region;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public final class WorldBuilder {
    private static boolean busy = false;
    private static final int DEPTH = 17;
    private static final int HEIGHT = 14;
    private static final int PRIVATE_SLOTS = 6;
    private static final int MINE_COLS = 5;
    private static final int MINE_STEP = 130;
    private static final String[] FACTION_IDS = new String[]{"guards", "thieves", "miners"};
    private static final int[] FACTION_X = new int[]{-40, 0, 40};
    private static final Material[] FACTION_FLOOR = new Material[]{Material.BLUE_CONCRETE, Material.RED_CONCRETE, Material.ORANGE_CONCRETE};
    final PrisonClassic plugin;
    private final World world;
    private final int ox;
    private final int g;
    private final int oz;
    private int sx = 0;
    private int sz = 0;
    private final List<WorldBuilder.Op> ops = new ArrayList<>();
    final List<Runnable> registrations = new ArrayList<>();
    private final NamespacedKey holoKey;

    public WorldBuilder(PrisonClassic var1, Location var2) {
        this.plugin = var1;
        this.world = var2.getWorld();
        this.ox = var2.getBlockX();
        this.g = var2.getBlockY();
        this.oz = var2.getBlockZ();
        this.holoKey = new NamespacedKey(var1, "build_holo");
    }

    public static boolean isBusy() {
        return busy;
    }

    public boolean heightOk() {
        return this.g + SpawnBuilder.MIN_Y - 1 >= this.world.getMinHeight() && this.g + SpawnBuilder.MAX_Y + 1 < this.world.getMaxHeight();
    }

    private void at(int var1, int var2) {
        this.sx = var1;
        this.sz = var2;
    }

    void fill(Material var1, int var2, int var3, int var4, int var5, int var6, int var7) {
        this.ops
            .add(
                new WorldBuilder.Op(
                    var1,
                    null,
                    this.ox + this.sx + Math.min(var2, var5),
                    this.g + Math.min(var3, var6),
                    this.oz + this.sz + Math.min(var4, var7),
                    this.ox + this.sx + Math.max(var2, var5),
                    this.g + Math.max(var3, var6),
                    this.oz + this.sz + Math.max(var4, var7)
                )
            );
    }

    /**
     * Блок с данными или палитрой (в относительных координатах острова).
     * data: "oak_stairs[facing=north,half=top]" или палитра "pal:stone_bricks*3,cracked_stone_bricks,mossy_stone_bricks".
     */
    void put(String data, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.ops.add(new WorldBuilder.Op(null, data,
            this.ox + this.sx + Math.min(x1, x2), this.g + Math.min(y1, y2), this.oz + this.sz + Math.min(z1, z2),
            this.ox + this.sx + Math.max(x1, x2), this.g + Math.max(y1, y2), this.oz + this.sz + Math.max(z1, z2)));
    }

    void put(String data, int x, int y, int z) {
        this.put(data, x, y, z, x, y, z);
    }

    private final Map<String, org.bukkit.block.data.BlockData[]> dataCache = new java.util.HashMap<>();

    private org.bukkit.block.data.BlockData[] resolve(String data) {
        return this.dataCache.computeIfAbsent(data, d -> {
            List<org.bukkit.block.data.BlockData> out = new ArrayList<>();
            String body = d.startsWith("pal:") ? d.substring(4) : d;
            for (String part : d.startsWith("pal:") ? body.split(",(?![^\\[]*\\])") : new String[]{body}) {
                int w = 1;
                String id = part.trim();
                int star = id.lastIndexOf('*');
                if (star > 0 && id.indexOf(']', star) < 0) {
                    w = Integer.parseInt(id.substring(star + 1));
                    id = id.substring(0, star);
                }
                org.bukkit.block.data.BlockData bd;
                try {
                    bd = Bukkit.createBlockData(id.contains(":") ? id : "minecraft:" + id);
                } catch (IllegalArgumentException e) {
                    this.plugin.getLogger().warning("Стройка: неизвестный блок " + id);
                    bd = Material.STONE.createBlockData();
                }
                for (int i = 0; i < w; i++) {
                    out.add(bd);
                }
            }
            return out.toArray(new org.bukkit.block.data.BlockData[0]);
        });
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    void set(Material var1, int var2, int var3, int var4) {
        this.fill(var1, var2, var3, var4, var2, var3, var4);
    }

    private void box(Material var1, Material var2, int var3, int var4, int var5, int var6, int var7) {
        this.fill(var2, var3, -1, var4, var5, -1, var6);
        this.fill(var1, var3, 0, var4, var5, var7, var6);
        this.fill(Material.AIR, var3 + 1, 0, var4 + 1, var5 - 1, var7, var6 - 1);
        this.fill(var1, var3, var7 + 1, var4, var5, var7 + 1, var6);
    }

    Location loc(double var1, double var3) {
        return new Location(this.world, this.ox + this.sx + var1, this.g, this.oz + this.sz + var3, 0.0F, 0.0F);
    }

    Location loc(double var1, double var3, double var5) {
        return new Location(this.world, this.ox + this.sx + var1, this.g + var3, this.oz + this.sz + var5, 0.0F, 0.0F);
    }

    Location facing(Location var1, float var2) {
        var1.setYaw(var2);
        return var1;
    }

    Region region(int var1, int var2, int var3, int var4, int var5, int var6) {
        return new Region(
            this.world.getName(),
            this.ox + this.sx + Math.min(var1, var4),
            this.g + Math.min(var2, var5),
            this.oz + this.sz + Math.min(var3, var6),
            this.ox + this.sx + Math.max(var1, var4),
            this.g + Math.max(var2, var5),
            this.oz + this.sz + Math.max(var3, var6)
        );
    }

    private void island(int var1, int var2, Material var3, Material var4) {
        this.fill(Material.AIR, -var1 - 2, -25, -var2 - 2, var1 + 2, 14, var2 + 2);

        for (int var5 = -var2; var5 <= var2; var5++) {
            int var6 = (int)Math.round(var1 * Math.sqrt(1.0 - (double)var5 * var5 / ((double)var2 * var2)));
            this.fill(var4, -var6, -4, var5, var6, -2, var5);
            this.fill(var3, -var6, -1, var5, var6, -1, var5);

            for (int var7 = 1; var7 <= 6; var7++) {
                int var8 = (int)(var6 * (1.0 - var7 * 0.15));
                int var9 = (int)(var5 * (1.0 - var7 * 0.12));
                if (var8 > 0) {
                    this.fill(var7 < 3 ? var4 : Material.STONE, -var8, -4 - var7 * 2, var9, var8, -3 - var7 * 2, var9);
                }
            }
        }
    }

    private void islandRect(int var1, int var2, int var3, int var4, Material var5) {
        this.fill(Material.AIR, var1 - 2, -25, var2 - 2, var3 + 2, 14, var4 + 2);
        this.fill(Material.DIRT, var1, -4, var2, var3, -2, var4);
        this.fill(var5, var1, -1, var2, var3, -1, var4);

        for (int var6 = 1; var6 <= 5; var6++) {
            int var7 = var6 * 3;
            if (var1 + var7 < var3 - var7 && var2 + var7 < var4 - var7) {
                this.fill(Material.STONE, var1 + var7, -4 - var6 * 2, var2 + var7, var3 - var7, -3 - var6 * 2, var4 - var7);
            }
        }
    }

    void lamp(int var1, int var2) {
        this.fill(Material.DARK_OAK_FENCE, var1, 0, var2, var1, 2, var2);
        this.set(Material.LANTERN, var1, 3, var2);
    }

    private void tree(int var1, int var2, TreeType var3) {
        if (var3 != null) {
            Location var4 = this.loc(var1 + 0.5, var2 + 0.5);
            this.registrations.add(() -> this.world.generateTree(var4, var3));
        }
    }

    private void flowers(int var1, int var2, int var3) {
        int var4 = this.ox + this.sx;
        int var5 = this.oz + this.sz;
        this.registrations
            .add(
                () -> {
                    Material[] var6 = new Material[]{
                        Material.POPPY,
                        Material.DANDELION,
                        Material.CORNFLOWER,
                        Material.SHORT_GRASS,
                        Material.SHORT_GRASS,
                        Material.AZURE_BLUET,
                        Material.OXEYE_DAISY
                    };
                    ThreadLocalRandom var7 = ThreadLocalRandom.current();

                    for (int var8 = 0; var8 < var3; var8++) {
                        Block var9 = this.world.getBlockAt(var4 + var7.nextInt(-var1, var1 + 1), this.g, var5 + var7.nextInt(-var2, var2 + 1));
                        if (var9.getType().isAir() && var9.getRelative(BlockFace.DOWN).getType() == Material.GRASS_BLOCK) {
                            var9.setType(var6[var7.nextInt(var6.length)], false);
                        }
                    }
                }
            );
    }

    void holo(double var1, double var3, double var5, String var7) {
        Location var8 = this.loc(var1, var3, var5);
        this.registrations.add(() -> {
            TextDisplay var3x = this.world.spawn(var8, TextDisplay.class);
            var3x.text(Msg.parse(var7));
            var3x.setBillboard(Billboard.CENTER);
            var3x.setPersistent(true);
            var3x.getPersistentDataContainer().set(this.holoKey, PersistentDataType.BYTE, (byte)1);
        });
    }

    void bed(int var1, int var2) {
        Location var3 = this.loc(var1, 0.0, var2);
        this.registrations.add(() -> {
            Block var1x = var3.getBlock();
            Block var2x = var1x.getRelative(BlockFace.NORTH);
            Bed var3x = (Bed)Material.RED_BED.createBlockData();
            var3x.setFacing(BlockFace.NORTH);
            var3x.setPart(Part.FOOT);
            Bed var4 = (Bed)Material.RED_BED.createBlockData();
            var4.setFacing(BlockFace.NORTH);
            var4.setPart(Part.HEAD);
            var1x.setBlockData(var3x, false);
            var2x.setBlockData(var4, false);
        });
    }

    /** Табличка «Продать всё» у торговца шахты. */
    void sellSign(String zone, int x, int z) {
        this.set(Material.OAK_SIGN, x, 0, z);
        Location l = this.loc(x, 0.0, z);
        this.registrations.add(() -> {
            org.bukkit.block.Block b = l.getBlock();
            if (b.getState() instanceof org.bukkit.block.Sign sign) {
                var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
                side.line(0, Msg.parse("<dark_green><bold>[Торговец]"));
                side.line(1, Msg.parse("<black>Продать всё"));
                side.line(2, Msg.parse("<dark_gray>ресурсы шахты"));
                sign.update(true, false);
            }
            this.plugin.locations().set("sign.merchant." + zone, l);
        });
    }

    void boss(String var1, Location var2) {
        this.registrations.add(() -> {
            if (this.plugin.bosses().type(var1) != null) {
                this.plugin.bosses().addPoint(var1, var2);
            }
        });
    }

    void warp(String var1, Location var2) {
        this.registrations.add(() -> this.plugin.locations().set(var1, var2));
    }

    void mine(String var1, String var2, int var3, Region var4, Location var5, boolean var6, boolean var7, Map<Material, Double> var8, Material var9) {
        this.registrations.add(() -> {
            Mine var10 = this.plugin.mines().create(var1, var4, var3, var5);
            var10.title = var2;
            var10.base = var6;
            var10.hidden = var7;
            var10.upper = var9;
            var10.resetSeconds = 120;
            var10.composition.clear();
            var10.composition.putAll(var8);
            var10.scheduleNext();
            this.plugin.mines().save();
            this.plugin.mines().reset(var10);
        });
    }

    static Map<Material, Double> comp(MineDefs.Def var0) {
        LinkedHashMap var1 = new LinkedHashMap();

        for (MineDefs.Entry var3 : var0.comp()) {
            var1.put(var3.block(), var3.weight());
        }

        return var1;
    }

    Region pit(int var1, int var2, int var3, int var4, Material var5) {
        int var6 = var1 + var3 - 1;
        int var7 = var2 + var3 - 1;
        this.fill(Material.BEDROCK, var1, -var4 - 1, var2, var6, -var4 - 1, var7);
        this.fill(var5, var1 - 1, -var4 - 1, var2 - 1, var1 - 1, 0, var7 + 1);
        this.fill(var5, var6 + 1, -var4 - 1, var2 - 1, var6 + 1, 0, var7 + 1);
        this.fill(var5, var1 - 1, -var4 - 1, var2 - 1, var6 + 1, 0, var2 - 1);
        this.fill(var5, var1 - 1, -var4 - 1, var7 + 1, var6 + 1, 0, var7 + 1);
        this.fill(Material.AIR, var1, -var4, var2, var6, -1, var7);
        return this.region(var1, -var4, var2, var6, -1, var7);
    }

    private void plan() {
        this.registrations.add(this.plugin.bosses()::clearPoints);
        // Все локации — отдельные острова в 500 блоках друг от друга; переход — через меню (/warp, /mine, NPC Проводник)
        this.at(0, 0);
        this.planSpawn();
        this.at(500, 0);
        this.planCells();
        this.at(-500, 0);
        this.planBase();
        this.at(0, 500);
        this.planBasement();
        this.at(0, -500);
        this.planVillage();
        this.at(500, 500);
        this.planCatacombs();
        this.at(-500, 500);
        this.planPrivateMines();
        this.planMineIslands();
        this.at(0, 0);
    }

    /**
     * Здание: пол, стены с колоннами из брёвен, окна, плоская крыша с карнизом, свет под потолком.
     * door: 'N','S','E','W' — сторона проёма шириной 3 и высотой 3.
     */
    private void house(int x1, int z1, int x2, int z2, int h, Material wall, Material floor, Material roof, Material pillar, char door) {
        this.fill(floor, x1, -1, z1, x2, -1, z2);
        this.fill(wall, x1, 0, z1, x2, h - 1, z2);
        this.fill(Material.AIR, x1 + 1, 0, z1 + 1, x2 - 1, h - 1, z2 - 1);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            this.fill(pillar, c[0], 0, c[1], c[0], h - 1, c[1]);
        }
        for (int x = x1 + 2; x <= x2 - 2; x += 3) {
            this.fill(Material.GLASS_PANE, x, 1, z1, x, 2, z1);
            this.fill(Material.GLASS_PANE, x, 1, z2, x, 2, z2);
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 3) {
            this.fill(Material.GLASS_PANE, x1, 1, z, x1, 2, z);
            this.fill(Material.GLASS_PANE, x2, 1, z, x2, 2, z);
        }
        this.fill(roof, x1 - 1, h, z1 - 1, x2 + 1, h, z2 + 1);
        this.fill(roof, x1 + 1, h + 1, z1 + 1, x2 - 1, h + 1, z2 - 1);
        for (int x = x1 + 3; x <= x2 - 3; x += 6) {
            for (int z = z1 + 3; z <= z2 - 3; z += 6) {
                this.set(Material.SEA_LANTERN, x, h, z);
            }
        }
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        switch (door) {
            case 'N' -> this.fill(Material.AIR, cx - 1, 0, z1, cx + 1, 2, z1);
            case 'S' -> this.fill(Material.AIR, cx - 1, 0, z2, cx + 1, 2, z2);
            case 'E' -> this.fill(Material.AIR, x2, 0, cz - 1, x2, 2, cz + 1);
            default -> this.fill(Material.AIR, x1, 0, cz - 1, x1, 2, cz + 1);
        }
    }

    private void path(int x1, int z1, int x2, int z2) {
        this.fill(Material.STONE_BRICKS, x1, -1, z1, x2, -1, z2);
    }

    /**
     * Детальный спавн «Тюремный городок» — см. {@link SpawnBuilder}. Здесь — функциональная часть:
     * шахты 1 и 2, NPC, сундуки, кубоид церкви, голограммы, точки кроликов.
     */
    private void planSpawn() {
        SpawnBuilder sb = new SpawnBuilder(this);
        sb.build();
        sb.emit();

        // ===== Шахта 1 (Землянка) — в конце северной дороги =====
        MineDefs.Def d1 = MineDefs.get("1");
        if (d1 != null) {
            Region r1 = this.region(-15, -12, -129, 15, -1, -99);
            this.mine(d1.name(), d1.title(), d1.level(), r1, this.facing(this.loc(0.5, 0.0, -95.5), 180.0F), false, false, comp(d1), null);
            this.holo(0.5, 8.2, -98.5, "<gold><bold>" + d1.title() + "</bold>\n<gray>шахта 1 · земля, песок, гравий, дерево");
            this.warp("npc.merchant.1", this.facing(this.loc(10.5, 0.0, -94.5), 90.0F));
            this.sellSign("1", 8, -91);
        }
        // ===== Шахта 2 (Огород: арбузы в шерсти) — юго-восток =====
        MineDefs.Def d2 = MineDefs.get("2");
        if (d2 != null) {
            Region r2 = this.region(64, -10, 50, 88, -1, 74);
            this.mine(d2.name(), d2.title(), d2.level(), r2, this.facing(this.loc(76.5, 0.0, 47.5), 0.0F), false, false, comp(d2), null);
            this.holo(76.5, 7.0, 45.5, "<gold><bold>" + d2.title() + "</bold>\n<gray>шахта 2 · арбузы и шерсть");
            this.warp("npc.merchant.2", this.facing(this.loc(76.5, 0.0, 42.5), 0.0F));
            this.sellSign("2", 79, 46);
        }

        // ===== Голограммы =====
        this.holo(-19.0, 3.4, -18.5, "<gold><bold>Сундуки с ключами</bold>\n<gray>Обычный · Древний (ДК) · Легендарный\n<gray>ПКМ — открыть · /keys");
        this.holo(19.5, 3.6, -17.5, "<yellow><bold>Доска заданий</bold>\n<gray>Квестовик — ежедневные задания");
        this.holo(-15.0, 6.6, -69.5, "<gold><bold>Столовая</bold>\n<gray>еда — в /shop");
        this.holo(15.5, 6.6, -69.5, "<aqua><bold>Спальня</bold>\n<gray>ПКМ по кровати — отдохнуть (нужда «Сон»)");
        this.holo(35.0, 6.5, 0.0, "<white><bold>Санузел</bold>\n<gray>север — туалеты (воронки) · юг — душ (котлы)\n<gray>бумага и мыло — в /shop");
        this.holo(95.5, 6.2, 0.5, "<aqua><bold>Тренировочный двор</bold>\n<gray>Тренер — навыки персонажа");
        this.holo(-42.5, 8.5, 0.5, "<gold><bold>Собор</bold>\n<gray>служба 00:55 · 06:55 · 12:55 · 18:55 (МСК)\n<gray>стойте внутри — Спешка и множитель продажи");
        this.holo(-11.0, 6.5, 56.0, "<green><bold>Магазин</bold>\n<gray>еда · бумага · мыло · инструменты");
        this.holo(-30.0, 6.2, 36.5, "<green><bold>Скупщик</bold>\n<gray>продажа ресурсов");
        this.holo(-11.0, 8.5, 85.5, "<gray><bold>Кузница</bold>\n<gray>/up — улучшение предметов");
        this.holo(7.0, 12.5, 57.5, "<yellow><bold>Донат-храм</bold>\n<gray>Древний ключ — 14★ · AutoSell_Premium");
        this.holo(13.5, 6.0, 88.5, "<gold><bold>Шатёр Цыгана</bold>\n<gray>скрещивание · Огненный меч");
        this.holo(0.5, 12.5, 116.5, "<light_purple><bold>Портал</bold>\n<gray>Проводник: шахты, камеры, База, подвал, деревня");

        // ===== Кролики на лугах =====
        for (int[] rb : new int[][]{{-100, 40}, {-75, 105}, {85, -95}, {105, -55}, {-40, 110}}) {
            this.boss("rabbit", this.loc(rb[0] + 0.5, rb[1] + 0.5));
        }

        // ===== Церковь (кубоид службы) =====
        this.warp("church", this.facing(this.loc(-42.5, 0.0, 0.5), 90.0F));
        this.warp("church.a", this.loc(-116.0, 0.0, -22.0));
        this.warp("church.b", this.loc(-45.0, 45.0, 22.0));
        this.warp("npc.priest", this.facing(this.loc(-104.5, 2.0, 0.5), -90.0F));

        // ===== NPC в подходящих местах =====
        this.registrations.add(() -> {
            this.plugin.locations().set("spawn", this.facing(this.loc(0.5, 0.0, 19.5), 180.0F));
            this.plugin.locations().set("crate.common", this.loc(-24.0, 0.0, -19.0));
            this.plugin.locations().set("crate.dk", this.loc(-19.0, 0.0, -19.0));
            this.plugin.locations().set("crate.legendary", this.loc(-14.0, 0.0, -19.0));
            this.plugin.locations().set("npc.quest", this.facing(this.loc(19.5, 0.0, -19.5), 0.0F));
            this.plugin.locations().set("npc.guide", this.facing(this.loc(0.5, 0.0, 112.5), 180.0F));
            this.plugin.locations().set("npc.trainer", this.facing(this.loc(95.5, 0.0, 0.5), -90.0F));
            this.plugin.locations().set("npc.shop", this.facing(this.loc(-23.5, 1.0, 55.5), -90.0F));
            this.plugin.locations().set("npc.seller", this.facing(this.loc(-30.5, 0.0, 31.5), 0.0F));
            this.plugin.locations().set("npc.smith", this.facing(this.loc(-20.5, 1.0, 85.5), -90.0F));
            this.plugin.locations().set("npc.donate", this.facing(this.loc(30.5, 1.0, 57.5), 90.0F));
            this.plugin.locations().set("npc.gypsy", this.facing(this.loc(30.5, 0.0, 88.5), 90.0F));
        });
    }

    private void planCells() {
        this.islandRect(-30, -16, 66, 16, Material.GRAVEL);
        this.fill(Material.POLISHED_ANDESITE, -28, -1, -14, 64, -1, 14);
        this.fill(Material.STONE_BRICKS, -17, -1, -1, 62, -1, 1);
        this.fill(Material.STONE_BRICKS, -18, 0, -2, -18, 3, 2);
        int var1 = 1;

        for (int var2 = 0; var2 < 2; var2++) {
            int var3 = var2 == 0 ? -2 : 2;
            int var4 = var2 == 0 ? -8 : 2;
            int var5 = var2 == 0 ? -2 : 8;
            int var6 = var2 == 0 ? -7 : 3;
            int var7 = var2 == 0 ? -3 : 7;
            int var8 = var2 == 0 ? -5 : 5;

            for (int var9 = 0; var9 < 10; var9++) {
                int var10 = -17 + var9 * 7;
                this.fill(Material.SMOOTH_STONE, var10, -1, var4, var10 + 6, -1, var5);
                this.fill(Material.STONE_BRICKS, var10, 0, var4, var10 + 6, 3, var5);
                this.fill(Material.AIR, var10 + 1, 0, var6, var10 + 5, 3, var7);
                this.fill(Material.STONE_BRICKS, var10, 4, var4, var10 + 6, 4, var5);
                this.fill(Material.IRON_BARS, var10 + 1, 0, var3, var10 + 5, 3, var3);
                this.fill(Material.AIR, var10 + 3, 0, var3, var10 + 3, 1, var3);
                this.set(Material.SEA_LANTERN, var10 + 3, 3, var8);
                // Мебель камеры: сито (компостер), душ (котёл с водой), полка коллекций (кафедра), меню камеры (бочка)
                int far = var2 == 0 ? var6 : var7;
                this.set(Material.COMPOSTER, var10 + 1, 0, far);
                this.set(Material.WATER_CAULDRON, var10 + 2, 0, far);
                this.set(Material.LECTERN, var10 + 4, 0, far);
                this.set(Material.BARREL, var10 + 5, 0, far);
                String var11 = "c" + var1++;
                Region var12 = this.region(var10 + 1, 0, var6, var10 + 5, 3, var7);
                Location var13 = this.loc(var10 + 3.5, var8 + 0.5);
                this.registrations.add(() -> {
                    Cell var4x = this.plugin.cells().create(var11, var12, var13, this.plugin.getConfig().getDouble("cells.price", 250.0));
                    var4x.owner = null;
                });
            }
        }

        this.fill(Material.COBBLESTONE_WALL, -29, 0, -15, 65, 1, -15);
        this.fill(Material.COBBLESTONE_WALL, -29, 0, 15, 65, 1, 15);
        this.fill(Material.COBBLESTONE_WALL, -29, 0, -15, -29, 1, 15);
        this.fill(Material.COBBLESTONE_WALL, 65, 0, -15, 65, 1, 15);

        for (int[] var17 : new int[][]{{-28, -14}, {64, -14}, {-28, 14}, {64, 14}}) {
            this.fill(Material.STONE_BRICKS, var17[0], 0, var17[1], var17[0], 5, var17[1]);
            this.set(Material.LANTERN, var17[0], 6, var17[1]);
        }

        this.holo(60.5, 3.0, 0.5, "<gray><bold>Тюремный блок</bold>\n<white>камеры c1–c20 · /cell list (с 8 ур.)");
        this.warp("warp.cells", this.facing(this.loc(60.5, 0.5), 90.0F));
    }

    /**
     * База фракций (большая): площадь прибытия на юге, три дома фракций со своими шахтами, центральная шахта База,
     * три точки захвата, крысы и зомби между базами, арена Королевского зомби на севере.
     */
    private void planBase() {
        this.islandRect(-120, -180, 120, 80, Material.GRASS_BLOCK);
        this.fill(Material.COARSE_DIRT, -110, -1, -170, 110, -1, -70);
        this.path(-3, -150, 3, 75);
        this.path(-95, -25, 95, -21);
        this.fill(Material.POLISHED_ANDESITE, -15, -1, 50, 15, -1, 72);
        int[] fx = {-70, 0, 70};
        LinkedHashMap<Material, Double> house = new LinkedHashMap<>();
        house.put(Material.STONE, 60.0);
        house.put(Material.COBBLESTONE, 20.0);
        house.put(Material.COAL_ORE, 12.0);
        house.put(Material.IRON_ORE, 6.0);
        house.put(Material.GOLD_ORE, 2.0);
        for (int i = 0; i < 3; i++) {
            int c = fx[i];
            String id = FACTION_IDS[i];
            this.house(c - 16, -62, c + 16, -28, 8, Material.STONE_BRICKS, FACTION_FLOOR[i], Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE, 'S');
            this.fill(Material.STONE_BRICKS, c - 7, -9, -54, c + 7, -1, -40);
            this.fill(Material.BEDROCK, c - 6, -9, -53, c + 6, -9, -41);
            Region r = this.region(c - 6, -8, -53, c + 6, -1, -41);
            this.mine("base_" + id, "Шахта базы", 5, r, this.loc(c + 0.5, -35.5), true, true, house, null);
            this.holo(c + 0.5, 6.0, -26.5, "<bold>База фракции</bold>\n<gray>своя шахта внутри");
            this.registrations.add(() -> {
                if (this.plugin.factions().get(id) != null) {
                    this.plugin.locations().set("base." + id, this.facing(this.loc(c + 0.5, -24.5), 180.0F));
                }
            });
        }
        LinkedHashMap<Material, Double> center = new LinkedHashMap<>(house);
        center.put(Material.DIAMOND_ORE, 1.0);
        Region mid = this.pit(-12, -112, 25, 12, Material.STONE_BRICKS);
        this.fill(Material.AIR, -1, 0, -87, 1, 0, -87);
        this.mine("base", "База (центр)", 5, mid, this.facing(this.loc(0.5, -82.5), 180.0F), true, false, center, null);
        this.holo(0.5, 3.0, -84.5, "<gold><bold>Центральная шахта Базы</bold>\n<gray>x2/x3/x4 блоков за точки фракции");
        int[][] pts = {{-75, -110}, {75, -110}, {0, -140}};
        String[] ids = {"a", "b", "c"};
        for (int i = 0; i < 3; i++) {
            int x = pts[i][0];
            int z = pts[i][1];
            this.fill(Material.WHITE_CONCRETE, x - 3, -1, z - 3, x + 3, -1, z + 3);
            this.set(Material.GOLD_BLOCK, x, -1, z);
            this.lamp(x - 4, z - 4);
            this.lamp(x + 4, z + 4);
            this.warp("point." + ids[i], this.loc(x + 0.5, z + 0.5));
        }
        this.registrations.add(() -> Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plugin.points().refreshHolograms(), 20L));
        for (int[] r : new int[][]{{-35, -75}, {35, -75}, {-95, -120}, {95, -120}, {-40, -130}, {40, -130}, {-60, -5}, {60, -5}, {-100, -60}, {100, -60}}) {
            this.boss("rat", this.loc(r[0] + 0.5, r[1] + 0.5));
        }
        for (int[] z : new int[][]{{-25, -150}, {25, -150}, {-100, -150}, {100, -150}}) {
            this.boss("zombie", this.loc(z[0] + 0.5, z[1] + 0.5));
        }
        this.box(Material.STONE_BRICKS, Material.DEEPSLATE_BRICKS, -16, -178, 16, -156, 8);
        this.fill(Material.AIR, -1, 0, -156, 1, 2, -156);
        this.set(Material.SEA_LANTERN, 0, 8, -167);
        this.boss("base_zombie", this.loc(0.5, -167.5));
        this.holo(0.5, 4.0, -154.5, "<red><bold>Логово Королевского зомби</bold>\n<gray>раз в 20 минут · 400 монет + Мозг");
        for (int[] t : new int[][]{{-100, 20}, {100, 20}, {-100, 60}, {100, 60}, {-50, 40}, {50, 40}, {-110, -100}, {110, -100}}) {
            this.tree(t[0], t[1], TreeType.TREE);
        }
        for (int[] l : new int[][]{{-6, 40}, {6, 40}, {-6, 10}, {6, 10}, {-6, -20}, {6, -20}, {-6, -60}, {6, -60}}) {
            this.lamp(l[0], l[1]);
        }
        this.holo(0.5, 3.0, 66.5, "<gold><bold>База фракций</bold>\n<gray>точки захвата · крысы · Королевский зомби");
        this.warp("warp.base", this.facing(this.loc(0.5, 64.5), 180.0F));
        this.warp("npc.merchant.base", this.facing(this.loc(8.5, 64.5), 90.0F));
        this.sellSign("base", 8, 62);
    }

    private void planBasement() {
        this.fill(Material.AIR, -42, -25, -32, 42, 9, 32);
        this.fill(Material.DEEPSLATE_TILES, -41, -1, -31, 41, -1, 31);
        this.fill(Material.STONE, -41, -4, -31, 41, -2, 31);
        this.fill(Material.STONE_BRICKS, -41, 0, -31, 41, 6, 31);
        this.fill(Material.AIR, -40, 0, -30, 40, 5, 30);
        this.fill(Material.COBBLESTONE, -41, 7, -31, 41, 7, 31);
        this.fill(Material.MOSSY_STONE_BRICKS, -40, 0, -4, 40, 5, -4);
        this.fill(Material.MOSSY_STONE_BRICKS, -40, 0, 4, 40, 5, 4);
        this.fill(Material.MOSSY_STONE_BRICKS, -13, 0, -30, -13, 5, -4);
        this.fill(Material.MOSSY_STONE_BRICKS, 13, 0, -30, 13, 5, -4);
        this.fill(Material.MOSSY_STONE_BRICKS, 11, 0, 4, 11, 5, 30);
        int[] var1 = new int[]{-27, 0, 27};

        for (int var5 : var1) {
            this.fill(Material.AIR, var5 - 1, 0, -4, var5 + 1, 2, -4);
        }

        this.fill(Material.AIR, -14, 0, 4, -12, 2, 4);
        this.fill(Material.IRON_BARS, 12, 0, 5, 40, 3, 5);

        for (byte var12 = -36; var12 <= 36; var12 += 8) {
            this.set(Material.LANTERN, var12, 5, 0);
        }

        for (byte var13 = -36; var13 <= 36; var13 += 12) {
            this.set(Material.SHROOMLIGHT, var13, 6, -17);
            this.set(Material.SHROOMLIGHT, var13, 6, 17);
        }

        ArrayList var14 = new ArrayList();

        for (MineDefs.Def var17 : MineDefs.all()) {
            if (var17.basement()) {
                var14.add(var17);
            }
        }

        int[] var16 = new int[]{-27, 0, 27};

        for (int var18 = 0; var18 < Math.min(3, var14.size()); var18++) {
            MineDefs.Def var19 = (MineDefs.Def)var14.get(var18);
            int var6 = Math.min(21, var19.size());
            Region var7 = this.pit(var16[var18] - var6 / 2, -26, var6, 10, Material.DEEPSLATE_BRICKS);
            this.mine(
                var19.name(),
                var19.title(),
                var19.level(),
                var7,
                this.facing(this.loc(var16[var18] + 0.5, -6.5), 180.0F),
                false,
                false,
                comp(var19),
                var19.upper()
            );
            this.holo(var16[var18] + 0.5, 3.0, -5.5, "<gold>" + var19.title() + "\n<gray>с " + var19.level() + " уровня");

            for (String var9 : var19.bosses()) {
                this.boss(var9, this.loc(-14.5, 17.5));
            }

            if (var19.minion() != null) {
                for (int[] var11 : new int[][]{{-34, 9}, {-20, 9}, {-34, 26}, {-20, 26}, {-6, 9}, {-6, 26}}) {
                    this.boss(var19.minion(), this.loc(var11[0] + 0.5, var11[1] + 0.5));
                }
            }
        }

        this.holo(-14.5, 4.0, 6.5, "<red><bold>Логово (комната 4)</bold>\n<gray>Сточный слизень и зомби подвала");
        this.holo(37.5, 3.0, 0.5, "<dark_gray><bold>Тюремный подвал</bold>\n<gray>с 6 уровня · по пропуску · PvP");
        this.warp("warp.basement", this.facing(this.loc(37.5, 0.5), 90.0F));
        this.warp("basement.a", this.loc(-41.0, -26.0, -31.0));
        this.warp("basement.b", this.loc(41.0, 7.0, 31.0));
        this.warp("npc.merchant.6", this.facing(this.loc(33.5, 2.5), 180.0F));
        this.sellSign("6", 31, 3);
        this.holo(0.5, 4.0, -2.5, "<gray>1 · земляная  <dark_gray>|</dark_gray>  2 · каменная  <dark_gray>|</dark_gray>  <blue>3 · лазуритовая</blue>  <dark_gray>|</dark_gray>  <red>4 · Логово");
    }

    private void planVillage() {
        this.island(52, 52, Material.GRASS_BLOCK, Material.DIRT);
        this.fill(Material.COBBLESTONE_WALL, -19, 0, -39, 19, 2, -39);
        this.fill(Material.COBBLESTONE_WALL, -19, 0, 39, 19, 2, 39);
        this.fill(Material.COBBLESTONE_WALL, -19, 0, -39, -19, 2, 39);
        this.fill(Material.COBBLESTONE_WALL, 19, 0, -39, 19, 2, 39);
        this.fill(Material.AIR, -19, 0, 25, -19, 2, 29);
        this.fill(Material.AIR, 19, 0, -2, 19, 2, 2);
        this.fill(Material.DIRT_PATH, -32, -1, 25, 18, -1, 29);
        this.fill(Material.DIRT_PATH, -2, -1, -33, 2, -1, 35);
        this.fill(Material.DIRT_PATH, -2, -1, -2, 30, -1, 2);

        for (int[] var4 : new int[][]{{-13, 7}, {-13, -18}, {8, 7}, {8, -18}, {-13, 31}, {8, 31}}) {
            int var5 = var4[0];
            int var6 = var4[1];
            this.box(Material.OAK_PLANKS, Material.SPRUCE_PLANKS, var5, var6, var5 + 6, var6 + 6, 3);
            this.fill(Material.SPRUCE_PLANKS, var5 - 1, 5, var6 - 1, var5 + 7, 5, var6 + 7);
            this.fill(Material.AIR, var5 + 3, 0, var6 + 6, var5 + 3, 1, var6 + 6);
            this.set(Material.GLASS_PANE, var5, 1, var6 + 3);
            this.fill(Material.OAK_LOG, var5, 0, var6, var5, 3, var6);
            this.fill(Material.OAK_LOG, var5 + 6, 0, var6 + 6, var5 + 6, 3, var6 + 6);
        }

        this.fill(Material.COBBLESTONE, -2, 0, -3, 2, 0, 1);
        this.fill(Material.WATER, -1, 0, -2, 1, 0, 0);
        this.fill(Material.OAK_FENCE, -2, 1, -3, -2, 2, -3);
        this.fill(Material.OAK_FENCE, 2, 1, 1, 2, 2, 1);

        for (int[] var13 : new int[][]{{-7, -8}, {7, -8}, {-7, 17}, {7, 17}, {0, -28}, {0, 35}, {15, -28}, {-15, -28}}) {
            this.boss("lufonia_zombie", this.loc(var13[0] + 0.5, var13[1] + 0.5));
        }

        this.fill(Material.SAND, 22, -1, -14, 46, -1, 14);
        this.fill(Material.SAND, 23, -7, -13, 45, -7, 13);
        this.fill(Material.WATER, 24, -6, -12, 44, -1, 12);
        this.fill(Material.PRISMARINE_BRICKS, 22, 0, -14, 46, 0, -14);
        this.fill(Material.PRISMARINE_BRICKS, 22, 0, 14, 46, 0, 14);
        this.fill(Material.PRISMARINE_BRICKS, 46, 0, -14, 46, 0, 14);
        this.set(Material.SEA_LANTERN, 34, -7, 0);
        this.boss("leviathan", this.loc(34.5, -4.0, 0.5));
        // Свинозомби деревни (коллекция «Ведьмак»: Контракт)
        for (int[] pz : new int[][]{{-26, 10}, {26, 20}, {-10, -30}, {10, 30}}) {
            this.boss("pigzombie", this.loc(pz[0] + 0.5, pz[1] + 0.5));
        }
        // Контрольные точки Семей (войны кланов)
        String[] fpIds = {"west", "east", "south"};
        int[][] fpPos = {{-40, -28}, {40, -28}, {0, 46}};
        for (int i = 0; i < 3; i++) {
            int fx = fpPos[i][0];
            int fz = fpPos[i][1];
            this.fill(Material.PURPLE_CONCRETE, fx - 3, -1, fz - 3, fx + 3, -1, fz + 3);
            this.set(Material.AMETHYST_BLOCK, fx, -1, fz);
            this.lamp(fx - 3, fz - 3);
            this.lamp(fx + 3, fz + 3);
            this.warp("fpoint." + fpIds[i], this.loc(fx + 0.5, fz + 0.5));
        }
        this.registrations.add(() -> Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plugin.familyWar().refreshHolograms(), 40L));
        this.holo(26.5, 3.0, 4.5, "<dark_aqua><bold>Левиафан</bold>\n<gray>босс деревни · с 20 уровня");

        for (int[] var14 : new int[][]{{-35, -20}, {-38, 5}, {-30, 40}, {30, -35}, {35, 30}, {-28, -38}}) {
            this.tree(var14[0], var14[1], TreeType.TREE);
        }

        this.flowers(48, 48, 100);
        this.warp("npc.awaken", this.loc(0.5, 4.5));
        this.holo(-28.5, 3.0, 27.5, "<light_purple><bold>Деревня Люфония</bold>\n<gray>осколки · пробуждение вещей · с 20 уровня");
        this.warp("lufonia", this.facing(this.loc(-30.5, 27.5), -90.0F));
    }

    /** Вампирские катакомбы: 3 закрытых зала (вход — телепорт от NPC на Стройке). */
    private void planCatacombs() {
        this.fill(Material.AIR, -40, -6, -14, 40, 12, 14);
        for (int i = 0; i < 3; i++) {
            int cx = -26 + i * 26;
            this.fill(Material.DEEPSLATE_BRICKS, cx - 11, -1, -11, cx + 11, 8, 11);
            this.fill(Material.AIR, cx - 10, 0, -10, cx + 10, 7, 10);
            this.fill(i == 2 ? Material.CRIMSON_NYLIUM : Material.POLISHED_BLACKSTONE_BRICKS, cx - 10, -1, -10, cx + 10, -1, 10);
            for (int[] lp : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
                this.fill(Material.POLISHED_BLACKSTONE_WALL, cx + lp[0], 0, lp[1], cx + lp[0], 3, lp[1]);
                this.set(Material.SOUL_LANTERN, cx + lp[0], 4, lp[1]);
            }
            for (int c = -6; c <= 6; c += 6) {
                this.set(Material.COBWEB, cx + c, 7, -9);
                this.set(Material.COBWEB, cx + c, 7, 9);
            }
            if (i == 2) {
                this.fill(Material.RED_CARPET, cx - 1, 0, -8, cx + 1, 0, 8);
                this.fill(Material.CRYING_OBSIDIAN, cx - 2, 0, 9, cx + 2, 2, 9);
                this.set(Material.REDSTONE_BLOCK, cx, 3, 9);
            }
            this.warp("dungeon.room" + (i + 1), this.facing(this.loc(cx + 0.5, -6.5), 0.0F));
        }
    }

    private void planPrivateMines() {
        this.islandRect(-72, -14, 72, 18, Material.GRASS_BLOCK);
        this.fill(Material.STONE_BRICKS, -70, -1, 12, 70, -1, 16);

        for (int var1 = 0; var1 < 6; var1++) {
            int var2 = -65 + var1 * 22;
            byte var3 = -9;
            Region var4 = this.pit(var2, var3, 15, 10, Material.STONE_BRICKS);
            this.fill(Material.AIR, var2 + 6, 0, var3 + 15, var2 + 8, 0, var3 + 15);
            Location var5 = this.facing(this.loc(var2 + 7.5, 13.5), 180.0F);
            String var6 = "p" + (var1 + 1);
            this.registrations.add(() -> {
                Mine var4x = this.plugin.mines().create(var6, var4, 1, var5);
                var4x.title = "Личная шахта (свободна)";
                var4x.privateSlot = true;
                var4x.hidden = true;
                var4x.composition.clear();
                var4x.composition.put(Material.STONE, 1.0);
                this.plugin.mines().save();
                this.plugin.mines().reset(var4x);
            });
            this.lamp(var2 - 2, 12);
        }
    }

    private static WorldBuilder.Theme theme(String var0) {
        return switch (var0) {
            case "1", "1b", "14", "14b" -> new WorldBuilder.Theme(Material.GRASS_BLOCK, Material.DIRT, TreeType.TREE, Material.OAK_LOG);
            case "7" -> new WorldBuilder.Theme(Material.NETHERRACK, Material.NETHERRACK, null, Material.NETHER_BRICKS);
            case "11", "21" -> new WorldBuilder.Theme(Material.MYCELIUM, Material.DIRT, TreeType.RED_MUSHROOM, Material.MUSHROOM_STEM);
            case "12" -> new WorldBuilder.Theme(Material.SNOW_BLOCK, Material.PACKED_ICE, TreeType.REDWOOD, Material.PACKED_ICE);
            case "13" -> new WorldBuilder.Theme(Material.SAND, Material.SANDSTONE, null, Material.CUT_SANDSTONE);
            case "15" -> new WorldBuilder.Theme(Material.SAND, Material.PRISMARINE, null, Material.PRISMARINE_BRICKS);
            case "16", "28" -> new WorldBuilder.Theme(Material.END_STONE, Material.END_STONE, null, Material.PURPUR_BLOCK);
            case "18" -> new WorldBuilder.Theme(Material.GRASS_BLOCK, Material.DIRT, TreeType.BIRCH, Material.QUARTZ_BRICKS);
            case "9" -> new WorldBuilder.Theme(Material.PODZOL, Material.DIRT, TreeType.DARK_OAK, Material.DARK_OAK_LOG);
            case "33" -> new WorldBuilder.Theme(Material.PODZOL, Material.DIRT, TreeType.DARK_OAK, Material.SPRUCE_LOG);
            case "38" -> new WorldBuilder.Theme(Material.SCULK, Material.DEEPSLATE, null, Material.DEEPSLATE_BRICKS);
            case "45" -> new WorldBuilder.Theme(Material.CALCITE, Material.STONE, null, Material.AMETHYST_BLOCK);
            case "20" -> new WorldBuilder.Theme(Material.MOSS_BLOCK, Material.DIRT, TreeType.JUNGLE_BUSH, Material.MOSSY_STONE_BRICKS);
            case "30" -> new WorldBuilder.Theme(Material.GRASS_BLOCK, Material.DIRT, TreeType.ACACIA, Material.BRICKS);
            default -> new WorldBuilder.Theme(Material.GRASS_BLOCK, Material.DIRT, TreeType.TREE, Material.STONE_BRICKS);
        };
    }

    private void planMineIslands() {
        int var1 = 0;

        for (MineDefs.Def var3 : MineDefs.all()) {
            if (!var3.basement() && !var3.name().equals("1") && !var3.name().equals("2")) {
                this.at(1000 + var1 % 5 * 500, (var1 / 5 - 2) * 500);
                var1++;
                WorldBuilder.Theme var4 = theme(var3.name());
                int var5 = Math.max(15, Math.min(41, var3.size() + 12));
                int var6 = var5 >= 31 ? 15 : 12;
                this.island(64, 82, var4.ground(), var4.under());
                int var7 = -var5 / 2;
                int var8 = -2 - var5 / 2;
                this.fill(Material.POLISHED_ANDESITE, var7 - 4, -1, var8 - 4, var7 + var5 + 3, -1, var8 + var5 + 3);
                Region var9 = this.pit(var7, var8, var5, var6, var4.wall());
                this.fill(Material.AIR, -1, 0, var8 + var5, 1, 0, var8 + var5);
                this.fill(Material.STONE_BRICKS, -2, -1, var8 + var5 + 4, 2, -1, 60);
                this.fill(Material.POLISHED_ANDESITE, -10, -1, 60, 10, -1, 70);
                this.lamp(-10, 60);
                this.lamp(10, 60);
                this.lamp(-10, 70);
                this.lamp(10, 70);
                this.lamp(var7 - 4, var8 - 4);
                this.lamp(var7 + var5 + 3, var8 - 4);
                this.lamp(var7 - 4, var8 + var5 + 3);
                this.lamp(var7 + var5 + 3, var8 + var5 + 3);
                Location var10 = this.facing(this.loc(0.5, 64.5), 180.0F);
                this.holo(0.5, 3.0, 60.5, "<gold><bold>" + var3.title() + "</bold>\n<gray>шахта " + var3.name() + " · с " + var3.level() + " уровня");
                // Торговец шахты + табличка «Продать всё»
                this.warp("npc.merchant." + var3.name(), this.facing(this.loc(6.5, 64.5), 90.0F));
                this.sellSign(var3.name(), 6, 67);
                if (var3.name().equals(this.plugin.getConfig().getString("dungeon.pass-mine", "17"))) {
                    this.warp("npc.dungeon", this.facing(this.loc(-6.5, 64.5), -90.0F));
                    this.holo(-6.5, 3.2, 64.5, "<dark_red><bold>Вампирские катакомбы</bold>\n<gray>с 17 ур. · группа из 3 · пропуск");
                }
                ArrayList<Location> var11 = new ArrayList<>();
                ArrayList<Location> var12 = new ArrayList<>();
                boolean pumpkin = var3.bosses().contains(this.plugin.getConfig().getString("bosses.evil-pumpkin.type", "evil_pumpkin"));
                if (pumpkin) {
                    this.pumpkinArena(var8, var4.wall(), var11);
                } else if (!var3.bosses().isEmpty() || var3.minion() != null) {
                    int var13 = -68;
                    int var14 = var8 - 6;
                    this.fill(Material.STONE_BRICKS, -2, -1, var14 + 1, 2, -1, var8 - 5);
                    this.fill(var4.wall(), -15, -1, var13, 15, 8, var14);
                    this.fill(Material.AIR, -14, 0, var13 + 1, 14, 7, var14 - 1);
                    this.fill(Material.DEEPSLATE_BRICKS, -14, -1, var13 + 1, 14, -1, var14 - 1);
                    this.fill(Material.AIR, -1, 0, var14, 1, 2, var14);
                    this.set(Material.SEA_LANTERN, -4, 6, (var13 + var14) / 2);
                    this.set(Material.SEA_LANTERN, 4, 6, (var13 + var14) / 2);
                    int var15 = (var13 + var14) / 2;

                    for (int var16 = 0; var16 < var3.bosses().size(); var16++) {
                        var11.add(this.loc(0.5 + (var3.bosses().size() > 1 ? (var16 == 0 ? -3 : 3) : 0), var15 + 0.5));
                    }

                    var12.addAll(List.of(this.loc(-10.5, var13 + 4.5), this.loc(11.5, var13 + 4.5), this.loc(-10.5, var14 - 3.5), this.loc(11.5, var14 - 3.5)));
                    this.holo(0.5, 4.0, var14 + 1.5, "<red><bold>Арена босса</bold>");
                }

                for (int[] var21 : new int[][]{{-40, -20}, {40, -20}, {-42, 20}, {42, 20}, {-30, 50}, {30, 50}, {-45, -50}, {45, -50}}) {
                    this.tree(var21[0], var21[1], var4.tree());
                }

                if (var4.ground() == Material.GRASS_BLOCK) {
                    this.flowers(60, 78, 150);
                }

                Map var18 = comp(var3);
                this.mine(var3.name(), var3.title(), var3.level(), var9, var10, false, false, var18, var3.upper());
                this.registrations.add(() -> {
                    for (int var4x = 0; var4x < var3.bosses().size(); var4x++) {
                        if (this.plugin.bosses().type(var3.bosses().get(var4x)) != null) {
                            this.plugin.bosses().addPoint(var3.bosses().get(var4x), (Location)var11.get(var4x));
                        }
                    }

                    int mi = 0;
                    for (Location var5x : var12) {
                        if (var3.minions().isEmpty()) {
                            break;
                        }
                        String mt = var3.minions().get(mi++ % var3.minions().size());
                        if (this.plugin.bosses().type(mt) != null) {
                            this.plugin.bosses().addPoint(mt, var5x);
                        }
                    }
                });
            }
        }
    }

    /**
     * Арена Злого Тыкова: круглая площадка радиуса 15 за островом шахты 20, стены высотой 6 с 8 дропперами,
     * точка спавна босса — в центре. Пол из каменных кирпичей (на 1-й стадии превращается в магму).
     */
    private void pumpkinArena(int pitTop, Material wall, List<Location> bossLocs) {
        int cz = -100;
        int r = 15;
        this.fill(Material.STONE_BRICKS, -2, -1, -49, 2, -1, pitTop - 5);
        this.fill(Material.STONE_BRICKS, -2, -1, cz + r, 2, -1, -49);
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r) {
                    this.set(Material.SMOOTH_STONE, x, -1, cz + z);
                    this.set(Material.STONE_BRICKS, x, -2, cz + z);
                } else if (d <= r + 1.0) {
                    this.fill(wall, x, -1, cz + z, x, 6, cz + z);
                }
            }
        }
        this.fill(Material.AIR, -1, 0, cz + r, 1, 2, cz + r + 1);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0;
            int x = (int)Math.round(Math.cos(a) * (r + 1));
            int z = (int)Math.round(Math.sin(a) * (r + 1));
            if (z >= r - 1 && Math.abs(x) <= 2) {
                continue;
            }
            this.set(Material.DISPENSER, x, 2, cz + z);
            this.set(Material.JACK_O_LANTERN, x, 4, cz + z);
        }
        this.set(Material.JACK_O_LANTERN, 0, 7, cz);
        this.holo(0.5, 4.0, cz + r + 3.5, "<gold><bold>Арена Злого Тыкова</bold>\n<gray>вход только с Боевым пропуском\n<gray>3 стадии: магма · стрелы · тотемы");
        bossLocs.add(this.loc(0.5, cz + 0.5));
    }

    private static boolean online(CommandSender var0) {
        return !(var0 instanceof Player var1 && !var1.isOnline());
    }

    public void start(final CommandSender var1) {
        busy = true;

        for (TextDisplay var3 : this.world.getEntitiesByClass(TextDisplay.class)) {
            if (var3.getPersistentDataContainer().has(this.holoKey, PersistentDataType.BYTE)) {
                var3.remove();
            }
        }

        this.plan();
        long var7a = 0L;

        for (WorldBuilder.Op var5 : this.ops) {
            var7a += var5.volume();
        }
        final long var7 = var7a;

        final int var6 = this.plugin.getConfig().getInt("build.blocks-per-tick", 15000);
        var1.sendMessage(
            Msg.parse("<yellow>Строительство запущено: <white>" + var7 + " <yellow>блоков, около <white>" + Math.max(1L, var7 / var6 / 20L) + " <yellow>сек.")
        );
        (new BukkitRunnable() {
                int idx = 0;
                int x;
                int y;
                int z;
                boolean started = false;
                long done = 0L;
                int ticks = 0;

                @Override
                public void run() {
                    try {
                        int var1x = var6;

                        while (var1x-- > 0 && this.idx < WorldBuilder.this.ops.size()) {
                            WorldBuilder.Op var2 = WorldBuilder.this.ops.get(this.idx);
                            if (!this.started) {
                                this.x = var2.x1();
                                this.y = var2.y1();
                                this.z = var2.z1();
                                this.started = true;
                            }

                            Block var3x = WorldBuilder.this.world.getBlockAt(this.x, this.y, this.z);
                            if (var2.mat() != null) {
                                if (var3x.getType() != var2.mat()) {
                                    var3x.setType(var2.mat(), false);
                                }
                            } else {
                                org.bukkit.block.data.BlockData[] pal = WorldBuilder.this.resolve(var2.data());
                                org.bukkit.block.data.BlockData bd = pal.length == 1 ? pal[0] : pal[WorldBuilder.hash(this.x, this.y, this.z) % pal.length];
                                var3x.setBlockData(bd, false);
                            }

                            this.done++;
                            if (++this.z > var2.z2()) {
                                this.z = var2.z1();
                                if (++this.y > var2.y2()) {
                                    this.y = var2.y1();
                                    if (++this.x > var2.x2()) {
                                        this.idx++;
                                        this.started = false;
                                    }
                                }
                            }
                        }

                        if (++this.ticks % 100 == 0 && WorldBuilder.online(var1)) {
                            var1.sendMessage(Msg.parse("<gray>Строительство: <white>" + this.done * 100L / var7 + "%"));
                        }

                        if (this.idx >= WorldBuilder.this.ops.size()) {
                            this.cancel();

                            for (Runnable var8 : WorldBuilder.this.registrations) {
                                try {
                                    var8.run();
                                } catch (Throwable var5) {
                                    WorldBuilder.this.plugin.getLogger().warning("Стройка: " + var5);
                                }
                            }

                            WorldBuilder.busy = false;
                            Bukkit.getScheduler().runTaskLater(WorldBuilder.this.plugin, () -> WorldBuilder.this.plugin.npc().refresh(), 40L);
                            if (WorldBuilder.online(var1)) {
                                var1.sendMessage(
                                    Msg.parse(
                                        "<green>Готово! Локации: спавн (Цыган, Донат-магазин), камеры (сито/душ/полка), База, Тюремный подвал (4 комнаты), деревня Люфония (точки Семей), 23 острова шахт с торговцами, арена Злого Тыкова, Вампирские катакомбы. Переходы: <white>/warp"
                                    )
                                );
                            }
                        }
                    } catch (Throwable var6x) {
                        this.cancel();
                        WorldBuilder.busy = false;
                        WorldBuilder.this.plugin.getLogger().severe("Ошибка строительства: " + var6x);
                        if (WorldBuilder.online(var1)) {
                            var1.sendMessage(Msg.parse("<red>Ошибка строительства, см. консоль."));
                        }
                    }
                }
            })
            .runTaskTimer(this.plugin, 1L, 1L);
    }

    private record Op(Material mat, String data, int x1, int y1, int z1, int x2, int y2, int z2) {
        long volume() {
            return (long)(this.x2 - this.x1 + 1) * (this.y2 - this.y1 + 1) * (this.z2 - this.z1 + 1);
        }
    }

    private record Theme(Material ground, Material under, TreeType tree, Material wall) {
    }
}
