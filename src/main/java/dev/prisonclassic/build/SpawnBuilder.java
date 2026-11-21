package dev.prisonclassic.build;

/**
 * Детальный спавн «Тюремный городок» (остров ~300×300).
 * Все постройки размечаются в воксельной сетке {@link Vox}, затем выгружаются в очередь WorldBuilder.
 *
 * Схема (север = -Z):
 *   центр — круглая площадь Ø64 с трёхъярусным фонтаном, павильоном сундуков (СЗ) и доской заданий (СВ);
 *   север — Столовая (З) и Спальня (В), в конце дороги — Землянка (шахта 1) с краном и лавкой торговца;
 *   восток — Санузел (туалеты + душевые), за ним Тренировочный двор с тренером;
 *   запад — Собор с колокольней, трансептом и апсидой (кубоид службы);
 *   юг — Торговая улица: Магазин и лавка скупщика, Кузница, Донат-храм, Шатёр Цыгана; в конце — Надвратная башня с порталом и Проводником;
 *   юго-восток — Огород (шахта 2) с полями, амбаром и пугалом; СЗ — пруд с причалом; ЮЗ — парк с беседкой; СВ — сад.
 *   По краю — крепостная стена с зубцами, галереей и 8 круглыми башнями.
 */
final class SpawnBuilder {
    static final int R = 150;
    static final int WALL_R = 136;
    static final int MIN_Y = -48;
    static final int MAX_Y = 60;

    final Vox v = new Vox(-R - 4, MIN_Y, -R - 4, R + 4, MAX_Y, R + 4);

    // Палитры
    static final String STONE = "pal:stone_bricks*6,cracked_stone_bricks*2,mossy_stone_bricks*2,andesite,stone";
    static final String COBBLE = "pal:cobblestone*4,mossy_cobblestone*2,stone*2,andesite";
    static final String ROAD = "pal:stone_bricks*5,cracked_stone_bricks*2,mossy_stone_bricks,polished_andesite*3,andesite*2,cobblestone*2";
    static final String ROAD_EDGE = "pal:coarse_dirt*2,dirt_path*4,rooted_dirt,packed_mud";
    static final String PLASTER = "pal:smooth_sandstone*5,sandstone*2,birch_planks";
    static final String WHITE = "pal:calcite*4,white_terracotta*2,diorite";
    static final String DIRT = "pal:dirt*6,coarse_dirt*2,rooted_dirt";
    static final String ROCK = "pal:stone*10,andesite*4,tuff*3,cobblestone*2,diorite,coal_ore";
    static final String OAK_LEAVES = "pal:oak_leaves[persistent=true]*5,azalea_leaves[persistent=true]*2";
    static final String CHERRY_LEAVES = "pal:cherry_leaves[persistent=true]*6,flowering_azalea_leaves[persistent=true]";

    /** Стиль здания. */
    record St(String frame, String lower, String upper, String plinth, String floor, String roof, String roofBlock, String trim, String glass, String door) {
        String stairs() {
            return roof + "_stairs";
        }

        String slab() {
            return roof + "_slab";
        }
    }

    static final St TIMBER = new St("dark_oak_log", STONE, PLASTER, STONE, "pal:spruce_planks*4,oak_planks", "dark_oak", "dark_oak_planks", "spruce", "glass_pane", "spruce_door");
    static final St SPRUCE = new St("spruce_log", COBBLE, "pal:spruce_planks*3,stripped_spruce_log", STONE, "pal:oak_planks*3,birch_planks", "spruce", "spruce_planks", "dark_oak", "glass_pane", "dark_oak_door");
    static final St BATH = new St("stripped_birch_log", STONE, WHITE, STONE, "pal:white_concrete,light_gray_concrete", "dark_prismarine", "dark_prismarine", "birch", "glass_pane", "birch_door");
    static final St SMITH = new St("dark_oak_log", COBBLE, "pal:stone_bricks*3,polished_andesite", COBBLE, "pal:stone*3,cobblestone,andesite", "deepslate_tile", "deepslate_tiles", "dark_oak", "glass_pane", "dark_oak_door");

    private final WorldBuilder wb;
    /** Параметры последнего построенного дома (для слуховых окон и крылец). */
    int lastT;
    int[] lastU;

    SpawnBuilder(WorldBuilder wb) {
        this.wb = wb;
    }

    // ------------------------------------------------------------------ примитивы

    void set(int x, int y, int z, String s) {
        v.set(x, y, z, s);
    }

    void fill(String s, int x1, int y1, int z1, int x2, int y2, int z2) {
        v.fill(s, x1, y1, z1, x2, y2, z2);
    }

    static String stairs(String mat, String facing, boolean top) {
        return mat + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** Направление от клетки к центру (для круглых крыш и ступенек). */
    static String toward(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    /** Выбор из палитры по координате — для неполных блоков (панели, ковры), которым нужны точные состояния. */
    static String pick(String pal, int x, int y, int z) {
        String[] parts = pal.split(",");
        int total = 0;
        int[] w = new int[parts.length];
        for (int k = 0; k < parts.length; k++) {
            int star = parts[k].lastIndexOf('*');
            w[k] = star > 0 ? Integer.parseInt(parts[k].substring(star + 1)) : 1;
            parts[k] = star > 0 ? parts[k].substring(0, star) : parts[k];
            total += w[k];
        }
        int r = Vox.hash(x, y, z, 77) % total;
        for (int k = 0; k < parts.length; k++) {
            r -= w[k];
            if (r < 0) {
                return parts[k];
            }
        }
        return parts[0];
    }

    // ------------------------------------------------------------------ сборка

    void build() {
        terrain();
        roads();
        plaza();
        diningHall();
        dormitory();
        mineOneArea();
        bathhouse();
        trainingYard();
        cathedral();
        shop();
        smithy();
        donateTemple();
        gypsyTent();
        farm();
        gatehouse();
        perimeterWall();
        pond();
        parkGazebo();
        orchard();
        streetLamps();
        nature();
        v.finish();
    }

    // ------------------------------------------------------------------ рельеф

    static double islandRadius(double ang) {
        return R - 4 + 3.0 * Math.sin(ang * 3 + 1.3) + 2.0 * Math.sin(ang * 7 + 0.4) + 1.5 * Math.sin(ang * 11 + 2.1);
    }

    private void terrain() {
        for (int x = -R - 3; x <= R + 3; x++) {
            for (int z = -R - 3; z <= R + 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                double ir = islandRadius(Math.atan2(z, x));
                if (r > ir) {
                    continue;
                }
                double t = r / ir;
                double n = Vox.fbm(x / 23.0, z / 23.0, 11, 3);
                int bottom = -4 - (int) (Math.pow(Math.max(0, 1 - Math.pow(t, 1.7)), 0.75) * 40 * (0.7 + 0.6 * n));
                bottom = Math.max(MIN_Y + 1, bottom);
                fill(ROCK, x, bottom, z, x, -6, z);
                fill(DIRT, x, Math.max(bottom, -5), z, x, -2, z);
                double patch = Vox.fbm(x / 9.0, z / 9.0, 5, 2);
                String top = "grass_block";
                if (r > ir - 1.5) {
                    top = "grass_block";
                } else if (patch > 0.74) {
                    top = Vox.rnd(x, 0, z, 3) < 0.5 ? "coarse_dirt" : "podzol";
                } else if (patch < 0.22 && Vox.rnd(x, 0, z, 4) < 0.6) {
                    top = "moss_block";
                }
                set(x, -1, z, top);
                // свисающие корни и мох под краем
                if (t > 0.86 && Vox.rnd(x, 1, z, 9) < 0.08) {
                    set(x, bottom - 1, z, "hanging_roots");
                }
            }
        }
    }

    // ------------------------------------------------------------------ дороги

    /** Мощёная дорога с рваными краями из тропы и грубой земли. */
    void road(int x1, int z1, int x2, int z2) {
        for (int x = Math.min(x1, x2) - 1; x <= Math.max(x1, x2) + 1; x++) {
            for (int z = Math.min(z1, z2) - 1; z <= Math.max(z1, z2) + 1; z++) {
                boolean edge = x < Math.min(x1, x2) || x > Math.max(x1, x2) || z < Math.min(z1, z2) || z > Math.max(z1, z2);
                if (edge) {
                    if (Vox.rnd(x, 0, z, 21) < 0.55 && "grass_block".equals(v.get(x, -1, z))) {
                        set(x, -1, z, ROAD_EDGE);
                    }
                } else {
                    boolean inner = x > Math.min(x1, x2) && x < Math.max(x1, x2) && z > Math.min(z1, z2) && z < Math.max(z1, z2);
                    set(x, -1, z, inner || Vox.rnd(x, 0, z, 22) < 0.7 ? ROAD : ROAD_EDGE);
                }
            }
        }
    }

    private void roads() {
        road(-4, -100, 4, -30);   // север: к Землянке
        road(-4, 30, 4, 120);     // юг: к Надвратной башне
        road(-46, -4, -30, 4);    // запад: к собору
        road(30, -4, 40, 4);      // восток: к санузлу
        road(30, -28, 34, -4);    // обход санузла
        road(30, -28, 92, -24);   // к тренировочному двору
        road(-64, 36, 64, 40);    // торговый переулок
        road(60, 36, 64, 46);     // в огород
        road(-64, 40, -60, 64);   // в парк
        road(-18, -72, -4, -68);  // к столовой
        road(4, -72, 18, -68);    // к спальне
    }

    // ------------------------------------------------------------------ фонари, лавки

    /** Двурогий уличный фонарь. alongX — рога вдоль оси X. */
    void lamp(int x, int z, boolean alongX) {
        set(x, 0, z, "stone_brick_wall");
        fill("dark_oak_fence", x, 1, z, x, 4, z);
        int dx = alongX ? 1 : 0, dz = alongX ? 0 : 1;
        set(x + dx, 4, z + dz, "dark_oak_fence");
        set(x - dx, 4, z - dz, "dark_oak_fence");
        set(x + dx, 3, z + dz, "lantern[hanging=true]");
        set(x - dx, 3, z - dz, "lantern[hanging=true]");
        set(x, 5, z, "dark_oak_trapdoor[half=bottom]");
    }

    /** Лавка: ступени спинкой в сторону back, подлокотники-люки по краям. */
    void bench(int x, int z, int len, boolean alongX, String back) {
        for (int k = 0; k < len; k++) {
            set(x + (alongX ? k : 0), 0, z + (alongX ? 0 : k), stairs("spruce_stairs", back, false));
        }
        if (alongX) {
            set(x - 1, 0, z, "spruce_trapdoor[facing=west,open=true]");
            set(x + len, 0, z, "spruce_trapdoor[facing=east,open=true]");
        } else {
            set(x, 0, z - 1, "spruce_trapdoor[facing=north,open=true]");
            set(x, 0, z + len, "spruce_trapdoor[facing=south,open=true]");
        }
    }

    void hangLantern(int x, int yTop, int z, int chain) {
        for (int k = 0; k < chain; k++) {
            set(x, yTop - k, z, "chain");
        }
        set(x, yTop - chain, z, "lantern[hanging=true]");
    }

    void barrelPile(int x, int z) {
        set(x, 0, z, "barrel[facing=up]");
        set(x + 1, 0, z, "barrel[facing=north]");
        set(x, 1, z, "barrel[facing=up]");
        set(x, 0, z + 1, "spruce_slab");
    }

    // ------------------------------------------------------------------ здания

    interface Perim {
        void at(int x, int z, int side, int t, int len, boolean corner);
    }

    /** Обход периметра: side 0=N,1=E,2=S,3=W; t — позиция вдоль стороны. */
    static void perimeter(int x1, int z1, int x2, int z2, Perim p) {
        for (int x = x1; x <= x2; x++) {
            p.at(x, z1, 0, x - x1, x2 - x1, x == x1 || x == x2);
            p.at(x, z2, 2, x - x1, x2 - x1, x == x1 || x == x2);
        }
        for (int z = z1 + 1; z < z2; z++) {
            p.at(x1, z, 3, z - z1, z2 - z1, false);
            p.at(x2, z, 1, z - z1, z2 - z1, false);
        }
    }

    static final String[] SIDE = {"north", "east", "south", "west"};
    static final int[] SX = {0, 1, 0, -1};
    static final int[] SZ = {-1, 0, 1, 0};

    static String axisAlong(int side) {
        return side == 0 || side == 2 ? "x" : "z";
    }

    /**
     * Этаж каркасной стены: брусья по углам и через 4 блока, окна с подоконниками (и ставнями), заполнение stoneRows рядов камнем снизу.
     */
    void wallLevel(int x1, int z1, int x2, int z2, int yA, int yB, St st, String fill, boolean shutters, int stoneRows) {
        perimeter(x1, z1, x2, z2, (x, z, side, t, len, corner) -> {
            boolean post = corner || t % 4 == 0;
            for (int y = yA; y <= yB; y++) {
                String b = post ? st.frame() : (y - yA < stoneRows ? st.lower() : fill);
                set(x, y, z, b);
            }
            int height = yB - yA + 1;
            if (height >= 8) {
                set(x, yA + 4, z, corner ? st.frame() : st.frame() + "[axis=" + axisAlong(side) + "]");
                if (!post && t % 4 == 2 && t >= 2 && t <= len - 2) {
                    set(x, yA + 5, z, st.glass());
                    set(x, yA + 6, z, st.glass());
                }
            }
            boolean window = !post && t % 4 == 2 && t >= 2 && t <= len - 2 && height >= 4;
            if (window) {
                int wTop = Math.min(yB - 1, yA + 3);
                String out = SIDE[side];
                for (int y = yA + 1; y <= wTop; y++) {
                    set(x, y, z, st.glass());
                    if (shutters) {
                        int ax = side % 2 == 0 ? 1 : 0, az = side % 2 == 0 ? 0 : 1;
                        set(x + SX[side] + ax, y, z + SZ[side] + az, st.trim() + "_trapdoor[facing=" + out + ",open=true]");
                        set(x + SX[side] - ax, y, z + SZ[side] - az, st.trim() + "_trapdoor[facing=" + out + ",open=true]");
                    }
                }
                set(x + SX[side], yA, z + SZ[side], st.trim() + "_trapdoor[facing=" + out + ",half=top]");
            }
        });
    }

    /** Горизонтальная обвязка из брёвен по периметру. */
    void beamRing(int x1, int z1, int x2, int z2, int y, St st) {
        perimeter(x1, z1, x2, z2, (x, z, side, t, len, corner) -> set(x, y, z, corner ? st.frame() : st.frame() + "[axis=" + axisAlong(side) + "]"));
    }

    /**
     * Двускатная крыша над прямоугольником (с выносом 1). alongX — конёк вдоль X. s — крутизна (1 или 2).
     */
    void roofGable(int fx1, int fz1, int fx2, int fz2, int T, boolean alongX, String stairs, String block, String slab, String gable, int s, String glass) {
        roofGable(fx1, fz1, fx2, fz2, T, alongX, stairs, block, slab, gable, s, glass, null);
    }

    void roofGable(int fx1, int fz1, int fx2, int fz2, int T, boolean alongX, String stairs, String block, String slab, String gable, int s, String glass, String frame) {
        int u1 = alongX ? fx1 : fz1, u2 = alongX ? fx2 : fz2;
        int v1 = alongX ? fz1 : fx1, v2 = alongX ? fz2 : fx2;
        String up = alongX ? "south" : "east", down = alongX ? "north" : "west";
        int vLow = v1 - 1, vHigh = v2 + 1;
        for (int k = 0; ; k++) {
            int a = vLow + k, b = vHigh - k, y = T + 1 + k * s;
            if (a < b) {
                for (int u = u1 - 1; u <= u2 + 1; u++) {
                    put(alongX, u, y, a, stairs + "[facing=" + up + ",half=bottom]");
                    put(alongX, u, y, b, stairs + "[facing=" + down + ",half=bottom]");
                    if (s > 1 && k > 0) {
                        for (int q = 1; q < s; q++) {
                            put(alongX, u, y - q, a, block);
                            put(alongX, u, y - q, b, block);
                        }
                    }
                }
            } else if (a == b) {
                for (int u = u1 - 1; u <= u2 + 1; u++) {
                    for (int q = 1; q <= s; q++) {
                        put(alongX, u, y - q, a, block);
                    }
                    put(alongX, u, y, a, slab + "[type=bottom]");
                }
                break;
            } else {
                int yl = y - s;
                for (int u = u1 - 1; u <= u2 + 1; u++) {
                    put(alongX, u, yl + 1, a - 1, slab + "[type=bottom]");
                    put(alongX, u, yl + 1, b + 1, slab + "[type=bottom]");
                }
                break;
            }
        }
        // фронтоны
        int vc = (v1 + v2) / 2;
        for (int u : new int[]{u1, u2}) {
            for (int vv = v1; vv <= v2; vv++) {
                int k = Math.min(vv - vLow, vHigh - vv);
                int top = T + 1 + k * s;
                for (int y = T + 1; y < top; y++) {
                    if (alongX ? v.air(u, y, vv) : v.air(vv, y, u)) {
                        String b = gable;
                        if (frame != null && ((vv - v1) % 4 == 0 || y == T + 5)) {
                            b = (vv - v1) % 4 == 0 ? frame : frame + "[axis=" + (alongX ? "z" : "x") + "]";
                        }
                        put(alongX, u, y, vv, b);
                    }
                }
            }
            int k = Math.min(vc - vLow, vHigh - vc);
            int top = T + 1 + k * s;
            if (top - (T + 1) >= 4 && glass != null) {
                put(alongX, u, T + 2, vc, glass);
                put(alongX, u, T + 3, vc, glass);
                if ((v2 - v1) % 2 == 1) {
                    put(alongX, u, T + 2, vc + 1, glass);
                    put(alongX, u, T + 3, vc + 1, glass);
                }
            }
        }
    }

    private void put(boolean alongX, int u, int y, int vv, String s) {
        if (s == null) {
            return;
        }
        if (alongX) {
            set(u, y, vv, s);
        } else {
            set(vv, y, u, s);
        }
    }

    /** Вальмовая (пирамидальная) крыша из ступеней. */
    void roofHip(int x1, int z1, int x2, int z2, int y, String stairs, String block, String slab) {
        for (int k = 0; ; k++) {
            int ax = x1 + k, bx = x2 - k, az = z1 + k, bz = z2 - k, yy = y + k;
            if (ax > bx || az > bz) {
                break;
            }
            if (ax == bx || az == bz) {
                fill(slab + "[type=bottom]", ax, yy, az, bx, yy, bz);
                break;
            }
            for (int x = ax; x <= bx; x++) {
                set(x, yy, az, stairs + "[facing=south,half=bottom]");
                set(x, yy, bz, stairs + "[facing=north,half=bottom]");
            }
            for (int z = az + 1; z < bz; z++) {
                set(ax, yy, z, stairs + "[facing=east,half=bottom]");
                set(bx, yy, z, stairs + "[facing=west,half=bottom]");
            }
            if (k > 0) {
                fill(block, ax + 1, yy - 1, az + 1, bx - 1, yy - 1, bz - 1);
            }
        }
    }

    /** Коническая крыша башни. */
    void cone(double cx, double cz, double r0, int y0, String stairs, String block, double step, String spire) {
        cone(cx, cz, r0, y0, stairs, block, step, spire, Integer.MAX_VALUE);
    }

    void cone(double cx, double cz, double r0, int y0, String stairs, String block, double step, String spire, int maxX) {
        int y = y0;
        for (double rr = r0; rr > 0.9; rr -= step, y++) {
            for (int x = (int) Math.floor(cx - rr - 1); x <= (int) Math.ceil(cx + rr + 1); x++) {
                for (int z = (int) Math.floor(cz - rr - 1); z <= (int) Math.ceil(cz + rr + 1); z++) {
                    double dx = x - cx, dz = z - cz, dd = Math.sqrt(dx * dx + dz * dz);
                    if (x > maxX) {
                        continue;
                    }
                    if (dd <= rr && dd > rr - 1.1) {
                        set(x, y, z, stairs + "[facing=" + toward(dx, dz) + ",half=bottom]");
                    } else if (dd <= rr - 1.1) {
                        set(x, y, z, block);
                    }
                }
            }
        }
        int sx = (int) Math.floor(cx), sz = (int) Math.floor(cz);
        set(sx, y, sz, block);
        if (spire != null) {
            set(sx, y + 1, sz, spire);
            set(sx, y + 2, sz, spire);
        }
    }

    /** Дверь (1 или 2 створки) в стене side на позиции pos; inward — направление внутрь. */
    void door(int x, int z, int side, int width, St st, boolean awning) {
        String in = opposite(SIDE[side]);
        int ax = side % 2 == 0 ? 1 : 0, az = side % 2 == 0 ? 0 : 1;
        for (int k = 0; k < width; k++) {
            int dx = x + ax * k, dz = z + az * k;
            String hinge = width == 2 ? (k == 0 ? "left" : "right") : "left";
            set(dx, 1, dz, st.door() + "[facing=" + in + ",half=lower,hinge=" + hinge + "]");
            set(dx, 2, dz, st.door() + "[facing=" + in + ",half=upper,hinge=" + hinge + "]");
            set(dx, 3, dz, st.frame() + "[axis=" + axisAlong(side) + "]");
            set(dx + SX[side], 0, dz + SZ[side], stairs("stone_brick_stairs", in, false));
        }
        if (awning) {
            for (int k = -1; k <= width; k++) {
                int dx = x + ax * k + SX[side], dz = z + az * k + SZ[side];
                set(dx, 4, dz, stairs(st.trim() + "_stairs", in, false));
            }
            set(x - ax + SX[side], 3, z - az + SZ[side], "lantern[hanging=true]");
            set(x + ax * width + SX[side], 3, z + az * width + SZ[side], "lantern[hanging=true]");
        }
    }

    /**
     * Каркасный дом: цоколь со «юбкой» из ступеней, каменный первый этаж, нависающий второй этаж (jetty), двускатная крыша.
     * Возвращает высоту верхнего обвязочного бруса T+1.
     */
    int house(int x1, int z1, int x2, int z2, int h1, int h2, boolean ridgeX, St st, boolean ceiling, int stoneRows, boolean jetty) {
        // фундамент и пол
        fill(st.plinth(), x1, -3, z1, x2, 0, z2);
        fill(st.floor(), x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1);
        fill(null, x1 + 1, 1, z1 + 1, x2 - 1, 60, z2 - 1);
        perimeter(x1 - 1, z1 - 1, x2 + 1, z2 + 1, (x, z, side, t, len, corner) -> set(x, 0, z, stairs("stone_brick_stairs", opposite(SIDE[side]), false)));
        wallLevel(x1, z1, x2, z2, 1, h1, st, h2 > 0 ? st.lower() : st.upper(), false, h2 > 0 ? h1 : stoneRows);
        int T;
        int ux1 = x1, uz1 = z1, ux2 = x2, uz2 = z2;
        if (h2 > 0) {
            int yb = h1 + 1;
            beamRing(x1, z1, x2, z2, yb, st);
            fill(st.roofBlock(), x1 + 1, yb, z1 + 1, x2 - 1, yb, z2 - 1);
            if (jetty) {
                perimeter(x1 - 1, z1 - 1, x2 + 1, z2 + 1, (x, z, side, t, len, corner) -> set(x, yb, z, stairs(st.trim() + "_stairs", opposite(SIDE[side]), true)));
                ux1--;
                uz1--;
                ux2++;
                uz2++;
            }
            T = yb + h2;
            wallLevel(ux1, uz1, ux2, uz2, yb + 1, T, st, st.upper(), true, 0);
            fill(null, ux1 + 1, yb + 1, uz1 + 1, ux2 - 1, T, uz2 - 1);
        } else {
            T = h1;
        }
        beamRing(ux1, uz1, ux2, uz2, T + 1, st);
        if (ceiling) {
            fill(st.roofBlock(), ux1 + 1, T + 1, uz1 + 1, ux2 - 1, T + 1, uz2 - 1);
        }
        roofGable(ux1, uz1, ux2, uz2, T, ridgeX, st.stairs(), st.roofBlock(), st.slab(), st.upper(), 1, st.glass(), st.frame());
        // кронштейны под карнизом вдоль скатов
        if (ridgeX) {
            for (int x = ux1; x <= ux2; x++) {
                set(x, T, uz1 - 1, stairs(st.trim() + "_stairs", "south", true));
                set(x, T, uz2 + 1, stairs(st.trim() + "_stairs", "north", true));
            }
        } else {
            for (int z = uz1; z <= uz2; z++) {
                set(ux1 - 1, T, z, stairs(st.trim() + "_stairs", "east", true));
                set(ux2 + 1, T, z, stairs(st.trim() + "_stairs", "west", true));
            }
        }
        this.lastT = T;
        this.lastU = new int[]{ux1, uz1, ux2, uz2};
        // освещение
        for (int x = x1 + 3; x <= x2 - 3; x += 6) {
            for (int z = z1 + 3; z <= z2 - 3; z += 6) {
                if (h2 > 0) {
                    hangLantern(x, h1, z, 1);
                }
            }
        }
        return T + 1;
    }

    /**
     * Крыльцо с двускатной крышей перед дверью. side — сторона стены, wall — координата стены,
     * from..to — пролёт вдоль стены, depth — вынос, h — высота столбов.
     */
    void porch(int side, int wall, int from, int to, int depth, int h, St st) {
        boolean ew = side == 1 || side == 3;
        int dir = side == 1 || side == 2 ? 1 : -1;
        int a = wall + dir, b = wall + dir * depth;
        int px1 = ew ? Math.min(a, b) : from, px2 = ew ? Math.max(a, b) : to;
        int pz1 = ew ? from : Math.min(a, b), pz2 = ew ? to : Math.max(a, b);
        fill(st.plinth(), px1, -2, pz1, px2, 0, pz2);
        fill(st.floor(), px1, 0, pz1, px2, 0, pz2);
        fill(null, px1, 1, pz1, px2, h + 8, pz2);
        // ступени у края
        for (int k = from; k <= to; k++) {
            int ox = ew ? b + dir : k, oz = ew ? k : b + dir;
            set(ox, 0, oz, stairs("stone_brick_stairs", opposite(SIDE[side]), false));
        }
        // столбы и перила
        int[][] posts = ew ? new int[][]{{b, from}, {b, to}} : new int[][]{{from, b}, {to, b}};
        for (int[] pp : posts) {
            fill(st.frame(), pp[0], 1, pp[1], pp[0], h, pp[1]);
            set(pp[0], 0, pp[1], "pal:stone_bricks,chiseled_stone_bricks");
        }
        for (int k = 1; k < depth; k++) {
            int c = wall + dir * k;
            if (ew) {
                set(c, 1, from, st.trim() + "_fence");
                set(c, 1, to, st.trim() + "_fence");
            } else {
                set(from, 1, c, st.trim() + "_fence");
                set(to, 1, c, st.trim() + "_fence");
            }
        }
        if (ew) {
            fill(st.frame() + "[axis=z]", b, h + 1, from, b, h + 1, to);
            fill(st.frame() + "[axis=x]", px1, h + 1, from, px2, h + 1, from);
            fill(st.frame() + "[axis=x]", px1, h + 1, to, px2, h + 1, to);
        } else {
            fill(st.frame() + "[axis=x]", from, h + 1, b, to, h + 1, b);
            fill(st.frame() + "[axis=z]", from, h + 1, pz1, from, h + 1, pz2);
            fill(st.frame() + "[axis=z]", to, h + 1, pz1, to, h + 1, pz2);
        }
        int rx1 = ew ? px1 - (dir > 0 ? 1 : 0) : px1, rx2 = ew ? px2 + (dir < 0 ? 1 : 0) : px2;
        int rz1 = ew ? pz1 : pz1 - (dir > 0 ? 1 : 0), rz2 = ew ? pz2 : pz2 + (dir < 0 ? 1 : 0);
        roofGable(rx1, rz1, rx2, rz2, h, ew, st.stairs(), st.roofBlock(), st.slab(), st.roofBlock(), 1, null);
        int cx = ew ? (px1 + px2) / 2 : (from + to) / 2, cz = ew ? (from + to) / 2 : (pz1 + pz2) / 2;
        set(cx, h, cz, "lantern[hanging=true]");
    }

    /** Слуховое окно на скате. u — позиция вдоль конька, vWall — линия стены, low — скат со стороны меньших координат. */
    void dormer(boolean alongX, int u, int vWall, boolean low, int T, St st) {
        int dir = low ? 1 : -1;
        String toPlus = alongX ? "east" : "south", toMinus = alongX ? "west" : "north";
        for (int j = 0; j <= 3; j++) {
            int vv = vWall + dir * j;
            for (int du = -1; du <= 1; du++) {
                for (int y = T + 1; y <= T + 3; y++) {
                    String b;
                    if (j == 0) {
                        b = du != 0 ? st.frame() : (y == T + 1 ? st.upper() : st.glass());
                    } else {
                        b = du != 0 ? st.upper() : null;
                    }
                    put(alongX, u + du, y, vv, b);
                    if (b == null) {
                        if (alongX) {
                            set(u + du, y, vv, null);
                        } else {
                            set(vv, y, u + du, null);
                        }
                    }
                }
            }
        }
        for (int j = -1; j <= 4; j++) {
            int vv = vWall + dir * j;
            put(alongX, u - 2, T + 3, vv, stairs(st.stairs(), toPlus, false));
            put(alongX, u + 2, T + 3, vv, stairs(st.stairs(), toMinus, false));
            put(alongX, u - 1, T + 4, vv, stairs(st.stairs(), toPlus, false));
            put(alongX, u + 1, T + 4, vv, stairs(st.stairs(), toMinus, false));
            put(alongX, u, T + 4, vv, st.roofBlock());
            put(alongX, u, T + 5, vv, st.slab() + "[type=bottom]");
        }
        put(alongX, u, T + 1, vWall - dir, st.trim() + "_trapdoor[facing=" + (alongX ? (low ? "north" : "south") : (low ? "west" : "east")) + ",half=top]");
    }

    /** Полусфера купола с рёбрами. */
    void dome(int cx, int y0, int cz, double r, String block, String rib) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++) {
            for (int y = 0; y <= ri; y++) {
                for (int z = -ri; z <= ri; z++) {
                    double dd = Math.sqrt(x * x + y * y + z * z);
                    if (dd <= r && dd > r - 1.3) {
                        boolean isRib = rib != null && (x == 0 || z == 0 || Math.abs(x) == Math.abs(z));
                        set(cx + x, y0 + y, cz + z, isRib ? rib : block);
                    }
                }
            }
        }
    }

    /** Яма шахты: стены, бедрок, пустота внутри (заполнит сброс шахты). */
    void pit(int x1, int z1, int size, int depth, String wall) {
        int x2 = x1 + size - 1, z2 = z1 + size - 1;
        fill(wall, x1 - 1, -depth - 1, z1 - 1, x2 + 1, -1, z2 + 1);
        fill("bedrock", x1, -depth - 1, z1, x2, -depth - 1, z2);
        fill(null, x1, -depth, z1, x2, 40, z2);
        // подпорки-крепь по стенам
        for (int x = x1; x <= x2; x += 5) {
            for (int z : new int[]{z1 - 1, z2 + 1}) {
                set(x, -depth, z, "stripped_dark_oak_log");
            }
        }
    }

    void chimney(int x, int z, int yFrom, int yTo) {
        fill("pal:bricks*4,cobblestone,mossy_cobblestone", x, yFrom, z, x, yTo, z);
        set(x, yTo + 1, z, "campfire[lit=true,signal_fire=false]");
    }

    // ------------------------------------------------------------------ деревья

    void oak(int x, int z, int size, int seed) {
        int trunk = 4 + size + Vox.hash(x, 0, z, seed) % 3;
        fill("oak_log", x, 0, z, x, trunk, z);
        if (size >= 3) {
            fill("oak_log", x + 1, 0, z, x + 1, trunk - 2, z);
            fill("oak_log", x, 0, z + 1, x, trunk - 3, z + 1);
            set(x - 1, 0, z, "oak_log[axis=x]");
            set(x, 0, z - 1, "oak_log[axis=z]");
        }
        int branches = 3 + size;
        double r = 2.2 + size * 0.55;
        blob(x, trunk + 1, z, r + 0.4, OAK_LEAVES, seed);
        for (int b = 0; b < branches; b++) {
            double a = Math.PI * 2 * (b + Vox.rnd(x, b, z, seed)) / branches;
            int len = 2 + size + Vox.hash(x, b, z, seed + 1) % 2;
            int by = trunk - 2 + Vox.hash(x, b, z, seed + 2) % 3;
            int ex = x, ez = z, ey = by;
            for (int k = 1; k <= len; k++) {
                ex = x + (int) Math.round(Math.cos(a) * k);
                ez = z + (int) Math.round(Math.sin(a) * k);
                ey = by + k / 2;
                String axis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? "x" : "z";
                set(ex, ey, ez, "oak_log[axis=" + axis + "]");
            }
            blob(ex, ey + 1, ez, r, OAK_LEAVES, seed + b);
        }
    }

    void cherry(int x, int z, int seed) {
        int trunk = 4 + Vox.hash(x, 1, z, seed) % 2;
        fill("cherry_log", x, 0, z, x, trunk, z);
        for (int b = 0; b < 4; b++) {
            double a = Math.PI / 2 * b + 0.4 + Vox.rnd(x, b, z, seed) * 0.6;
            int ex = x, ez = z, ey = trunk;
            for (int k = 1; k <= 4; k++) {
                ex = x + (int) Math.round(Math.cos(a) * k);
                ez = z + (int) Math.round(Math.sin(a) * k);
                ey = trunk - 1 + (k + 1) / 2;
                set(ex, ey, ez, "cherry_log[axis=" + (Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? "x" : "z") + "]");
            }
            blob(ex, ey + 1, ez, 3.2, CHERRY_LEAVES, seed + b);
            // свисающие лепестки
            for (int k = 0; k < 6; k++) {
                int hx = ex + Vox.hash(x, k, b, seed) % 7 - 3, hz = ez + Vox.hash(z, k, b, seed) % 7 - 3;
                for (int y = ey + 1; y > ey - 3; y--) {
                    if (v.get(hx, y, hz) == null && v.get(hx, y + 1, hz) != null && v.get(hx, y + 1, hz).contains("leaves")) {
                        set(hx, y, hz, "cherry_leaves[persistent=true]");
                        break;
                    }
                }
            }
        }
        blob(x, trunk + 2, z, 3.0, CHERRY_LEAVES, seed + 9);
        for (int k = 0; k < 14; k++) {
            int px = x + Vox.hash(k, 0, x, seed) % 11 - 5, pz = z + Vox.hash(k, 1, z, seed) % 11 - 5;
            if (v.get(px, 0, pz) == null && "grass_block".equals(v.get(px, -1, pz))) {
                set(px, 0, pz, "pink_petals[flower_amount=" + (1 + k % 4) + ",facing=north]");
            }
        }
    }

    void birch(int x, int z, int seed) {
        int trunk = 7 + Vox.hash(x, 2, z, seed) % 3;
        fill("birch_log", x, 0, z, x, trunk, z);
        for (int y = trunk - 4; y <= trunk + 1; y++) {
            double rr = y > trunk - 1 ? 1.4 : 2.3;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz <= rr * rr + Vox.rnd(x + dx, y, z + dz, seed) * 1.5) {
                        v.soft(x + dx, y, z + dz, "birch_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    void spruce(int x, int z, int seed) {
        int hgt = 10 + Vox.hash(x, 3, z, seed) % 5;
        fill("spruce_log", x, 0, z, x, hgt - 1, z);
        for (int y = 3; y <= hgt + 1; y++) {
            double rr = (hgt + 1 - y) * 0.42 + (y % 2 == 0 ? 0.6 : 0);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz <= rr * rr) {
                        v.soft(x + dx, y, z + dz, "spruce_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    void blob(int cx, int cy, int cz, double r, String leaves, int seed) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++) {
            for (int y = -ri; y <= ri; y++) {
                for (int z = -ri; z <= ri; z++) {
                    double dd = x * x + y * y * 1.6 + z * z;
                    if (dd <= r * r - Vox.rnd(cx + x, cy + y, cz + z, seed) * r * 1.3) {
                        v.soft(cx + x, cy + y, cz + z, leaves);
                    }
                }
            }
        }
    }

    void bush(int x, int z, int seed) {
        blob(x, 0, z, 1.8 + Vox.rnd(x, 0, z, seed), Vox.rnd(x, 1, z, seed) < 0.3 ? "flowering_azalea_leaves[persistent=true]" : OAK_LEAVES, seed);
    }

    void boulder(int x, int z, double r, int seed) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -1; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double dd = dx * dx + dy * dy * 1.8 + dz * dz;
                    if (dd <= r * r - Vox.rnd(x + dx, dy, z + dz, seed) * 2.5) {
                        set(x + dx, dy, z + dz, "pal:stone*3,andesite*2,mossy_cobblestone*2,cobblestone,tuff");
                    }
                }
            }
        }
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                for (int y = ri; y >= 0; y--) {
                    String b = v.get(x + dx, y, z + dz);
                    if (b != null) {
                        if (Vox.rnd(x + dx, y, z + dz, seed + 1) < 0.5 && v.air(x + dx, y + 1, z + dz)) {
                            set(x + dx, y + 1, z + dz, "moss_carpet");
                        }
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ площадь и фонтан

    private void plaza() {
        int pr = 32;
        for (int x = -pr - 1; x <= pr + 1; x++) {
            for (int z = -pr - 1; z <= pr + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > pr + 0.5) {
                    continue;
                }
                double ang = Math.atan2(z, x);
                double ray = Math.abs(Math.sin(4 * ang)) * r;
                String b;
                if (r > pr - 1) {
                    b = "polished_deepslate";
                } else if (Math.abs(r - 13) < 0.55 || Math.abs(r - 22) < 0.55) {
                    b = "polished_deepslate";
                } else if (r > 13.5 && r < 29 && ray < 1.1) {
                    b = "calcite";
                } else if (r > pr - 3) {
                    b = "pal:stone_bricks*3,cracked_stone_bricks,mossy_stone_bricks";
                } else {
                    b = "pal:polished_andesite*6,andesite*2,stone*1";
                }
                set(x, -1, z, b);
                set(x, -2, z, "stone");
            }
        }
        fountain();
        // клумбы с сакурой (ЮЗ, ЮВ)
        for (int sx : new int[]{-1, 1}) {
            int cx = 21 * sx, cz = 21;
            v.disc("grass_block", -1, cx, cz, 4.5);
            v.ring("pal:stone_bricks*3,mossy_stone_bricks", -1, cx, cz, 5.6, 4.5);
            v.ring("stone_brick_wall", 0, cx, cz, 5.6, 4.5);
            cherry(cx, cz, 40 + sx);
            for (int k = 0; k < 9; k++) {
                int fx = cx + Vox.hash(k, 3, sx, 1) % 7 - 3, fz = cz + Vox.hash(k, 4, sx, 1) % 7 - 3;
                if (v.get(fx, 0, fz) == null) {
                    set(fx, 0, fz, k % 3 == 0 ? "allium" : k % 3 == 1 ? "pink_tulip" : "white_tulip");
                }
            }
        }
        // павильон сундуков (СЗ) и доска заданий (СВ)
        cratePavilion(-19, -19);
        questBoard(19, -19);
        // скамейки вокруг фонтана
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k + Math.PI / 8;
            int bx = (int) Math.round(Math.cos(a) * 15.5), bz = (int) Math.round(Math.sin(a) * 15.5);
            boolean alongX = Math.abs(Math.sin(a)) > Math.abs(Math.cos(a));
            String back = alongX ? (bz > 0 ? "south" : "north") : (bx > 0 ? "east" : "west");
            if (alongX) {
                bench(bx - 1, bz, 3, true, back);
            } else {
                bench(bx, bz - 1, 3, false, back);
            }
        }
        // фонари площади
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k + Math.PI / 8;
            int lx = (int) Math.round(Math.cos(a) * 28), lz = (int) Math.round(Math.sin(a) * 28);
            if (Math.abs(lx + 19) < 9 && Math.abs(lz + 19) < 7) {
                continue;
            }
            if (Math.abs(lx - 19) < 7 && Math.abs(lz + 19) < 7) {
                continue;
            }
            lamp(lx, lz, Math.abs(lx) < Math.abs(lz));
        }
    }

    private void fountain() {
        // нижняя чаша
        v.disc("dark_prismarine", -1, 0, 0, 9.6);
        v.disc("stone", -2, 0, 0, 9.6);
        v.disc("water", 0, 0, 0, 9.6);
        v.ring("pal:stone_bricks*4,mossy_stone_bricks,cracked_stone_bricks", 0, 0, 0, 10.8, 9.6);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= 11.8 && r > 10.8) {
                    set(x, 0, z, stairs("stone_brick_stairs", toward(x, z), false));
                }
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            set((int) Math.round(Math.cos(a) * 7), -1, (int) Math.round(Math.sin(a) * 7), "sea_lantern");
        }
        // постамент и средний ярус
        v.disc("pal:stone_bricks*3,chiseled_stone_bricks", 0, 0, 0, 2.6);
        for (int y = 1; y <= 2; y++) {
            v.disc("pal:stone_bricks*3,mossy_stone_bricks", y, 0, 0, 2.6);
        }
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= 4.6 && r > 2.6) {
                    set(x, 2, z, stairs("stone_brick_stairs", toward(x, z), true));
                }
            }
        }
        v.disc("polished_andesite", 3, 0, 0, 4.6);
        v.disc("water", 4, 0, 0, 3.6);
        v.ring("stone_brick_wall", 4, 0, 0, 4.6, 3.6);
        // верхний ярус
        fill("chiseled_stone_bricks", 0, 4, 0, 0, 6, 0);
        v.disc("polished_andesite", 7, 0, 0, 2.2);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= 2.3 && r > 1.2) {
                    set(x, 6, z, stairs("stone_brick_stairs", toward(x, z), true));
                }
            }
        }
        v.disc("water", 8, 0, 0, 1.5);
        v.ring("stone_brick_wall", 8, 0, 0, 2.3, 1.5);
        set(0, 8, 0, "chiseled_stone_bricks");
        set(0, 9, 0, "stone_brick_wall");
        set(0, 10, 0, "stone_brick_wall");
        set(0, 11, 0, "lantern");
        set(0, 4, 0, "chiseled_stone_bricks");
        set(0, 5, 0, "chiseled_stone_bricks");
        set(0, 6, 0, "chiseled_stone_bricks");
        set(0, 7, 0, "chiseled_stone_bricks");
    }

    private void cratePavilion(int cx, int cz) {
        int x1 = cx - 8, x2 = cx + 8, z1 = cz - 5, z2 = cz + 5;
        fill("polished_blackstone_bricks", x1, -1, z1, x2, -1, z2);
        v.walls("gold_block", x1 + 1, -1, z1 + 1, x2 - 1, -1, z2 - 1);
        fill("pal:polished_blackstone_bricks*3,cracked_polished_blackstone_bricks", x1 + 2, -1, z1 + 2, x2 - 2, -1, z2 - 2);
        for (int x : new int[]{x1, cx - 3, cx + 3, x2}) {
            for (int z : new int[]{z1, z2}) {
                set(x, 0, z, "polished_blackstone");
                fill("quartz_pillar", x, 1, z, x, 4, z);
                set(x, 5, z, "chiseled_quartz_block");
            }
        }
        beamRing(x1, z1, x2, z2, 6, new St("polished_basalt", "", "", "", "", "", "", "", "", ""));
        for (int x = x1 + 1; x < x2; x++) {
            set(x, 5, z1, "polished_deepslate_stairs[facing=north,half=top]");
            set(x, 5, z2, "polished_deepslate_stairs[facing=south,half=top]");
        }
        fill("polished_deepslate", x1 + 1, 6, z1 + 1, x2 - 1, 6, z2 - 1);
        roofHip(x1 - 1, z1 - 1, x2 + 1, z2 + 1, 7, "deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab");
        set(cx, 13, cz, "gold_block");
        set(cx, 14, cz, "lightning_rod");
        hangLantern(cx - 5, 5, cz, 1);
        hangLantern(cx, 5, cz, 1);
        hangLantern(cx + 5, 5, cz, 1);
        // три постамента с сундуками
        int[] xs = {cx - 5, cx, cx + 5};
        String[] chests = {"chest[facing=south]", "ender_chest[facing=south]", "trapped_chest[facing=south]"};
        for (int k = 0; k < 3; k++) {
            set(xs[k], 0, cz, chests[k]);
            set(xs[k] - 1, 0, cz, "polished_blackstone_wall");
            set(xs[k] + 1, 0, cz, "polished_blackstone_wall");
            set(xs[k] - 1, 1, cz, "soul_lantern");
            set(xs[k] + 1, 1, cz, "soul_lantern");
            set(xs[k], 0, cz - 1, "polished_blackstone_bricks");
            set(xs[k], 1, cz - 1, k == 0 ? "iron_block" : k == 1 ? "amethyst_block" : "gold_block");
        }
    }

    private void questBoard(int cx, int cz) {
        int x1 = cx - 5, x2 = cx + 5, z1 = cz - 4, z2 = cz + 4;
        fill("pal:spruce_planks*3,stripped_spruce_log", x1, -1, z1, x2, -1, z2);
        for (int x : new int[]{x1, x2}) {
            for (int z : new int[]{z1, z2}) {
                fill("spruce_log", x, 0, z, x, 4, z);
            }
        }
        St wood = new St("spruce_log", "", "", "", "", "", "", "", "", "");
        beamRing(x1, z1, x2, z2, 5, wood);
        fill("spruce_planks", x1 + 1, 5, z1 + 1, x2 - 1, 5, z2 - 1);
        roofGable(x1, z1, x2, z2, 4, true, "spruce_stairs", "spruce_planks", "spruce_slab", "spruce_planks", 1, null);
        // доска объявлений
        fill("spruce_log", cx - 3, 0, z1 + 1, cx - 3, 3, z1 + 1);
        fill("spruce_log", cx + 3, 0, z1 + 1, cx + 3, 3, z1 + 1);
        fill("stripped_dark_oak_wood", cx - 2, 1, z1 + 1, cx + 2, 3, z1 + 1);
        fill("spruce_slab[type=top]", cx - 3, 4, z1 + 1, cx + 3, 4, z1 + 1);
        set(cx - 1, 2, z1 + 2, "white_wall_banner[facing=south]");
        set(cx + 1, 2, z1 + 2, "white_wall_banner[facing=south]");
        set(cx - 4, 0, cz + 2, "barrel[facing=up]");
        set(cx + 4, 0, cz + 2, "barrel[facing=up]");
        set(cx + 4, 1, cz + 2, "lantern");
        set(cx + 3, 0, cz - 1, "lectern[facing=west]");
        hangLantern(cx, 4, cz, 0);
    }

    // ------------------------------------------------------------------ север

    private void diningHall() {
        int x1 = -46, x2 = -18, z1 = -92, z2 = -48;
        house(x1, z1, x2, z2, 8, 0, false, TIMBER, false, 3, false);
        int T = lastT;
        for (int z : new int[]{-84, -58}) {
            dormer(false, z, x1, true, T, TIMBER);
            dormer(false, z, x2, false, T, TIMBER);
        }
        dormer(false, -71, x1, true, T, TIMBER);
        door(x2, -71, 1, 2, TIMBER, false);
        porch(1, x2, -74, -67, 4, 4, TIMBER);
        // внутренняя ферма крыши — поперечные балки
        for (int z = z1 + 4; z <= z2 - 4; z += 5) {
            fill("dark_oak_log[axis=x]", x1 + 1, 9, z, x2 - 1, 9, z);
            hangLantern(-39, 8, z, 1);
            hangLantern(-25, 8, z, 1);
        }
        // длинные столы со скамьями
        for (int tx : new int[]{-40, -32, -24}) {
            for (int z = z1 + 10; z <= z2 - 4; z++) {
                if ((z - z1) % 14 == 0) {
                    continue;
                }
                set(tx, 1, z, "spruce_slab[type=top]");
                set(tx - 1, 1, z, "spruce_stairs[facing=west,half=bottom]");
                set(tx + 1, 1, z, "spruce_stairs[facing=east,half=bottom]");
                if (z % 4 == 0) {
                    set(tx, 2, z, z % 8 == 0 ? "candle[candles=3,lit=true]" : "flower_pot");
                }
            }
        }
        // кухня у северной стены
        int kz = z1 + 1;
        for (int x = x1 + 1; x <= x2 - 1; x++) {
            String b = switch ((x - x1) % 6) {
                case 0 -> "smoker[facing=south,lit=true]";
                case 1 -> "furnace[facing=south,lit=true]";
                case 2 -> "barrel[facing=south]";
                case 3 -> "water_cauldron[level=3]";
                case 4 -> "crafting_table";
                default -> "spruce_planks";
            };
            set(x, 1, kz, b);
            set(x, 1, kz + 3, "spruce_slab[type=top]");
        }
        fill(null, x1 + 1, 1, kz + 3, x1 + 3, 1, kz + 3);
        fill(null, x2 - 3, 1, kz + 3, x2 - 1, 1, kz + 3);
        for (int x = x1 + 2; x <= x2 - 2; x += 4) {
            set(x, 3, kz, "spruce_trapdoor[facing=south,half=top]");
            set(x, 4, kz, "chain");
        }
        chimney(x1 + 4, z1, 1, 20);
        chimney(x2 - 4, z1, 1, 20);
        set(x1 + 4, 1, z1 + 1, "campfire[lit=true]");
        set(x2 - 4, 1, z1 + 1, "campfire[lit=true]");
        // вывеска
        set(x2 + 1, 6, -69, "dark_oak_fence");
        set(x2 + 2, 6, -69, "dark_oak_fence");
        set(x2 + 2, 5, -69, "dark_oak_hanging_sign[rotation=4,attached=false]");
        barrelPile(x2 + 3, -76);
        barrelPile(x2 + 3, -64);
    }

    private void dormitory() {
        int x1 = 18, x2 = 46, z1 = -92, z2 = -48;
        house(x1, z1, x2, z2, 5, 5, false, SPRUCE, true, 0, true);
        int T = lastT;
        for (int z : new int[]{-84, -71, -58}) {
            dormer(false, z, lastU[0], true, T, SPRUCE);
            dormer(false, z, lastU[2], false, T, SPRUCE);
        }
        door(x1, -71, 3, 2, SPRUCE, false);
        porch(3, x1, -74, -67, 4, 4, SPRUCE);
        // кровати вдоль стен с сундуками между ними
        for (int z = z1 + 2; z <= z2 - 2; z += 3) {
            if (z >= -73 && z <= -66) {
                continue;
            }
            set(x2 - 1, 1, z, "red_bed[facing=east,part=head]");
            set(x2 - 2, 1, z, "red_bed[facing=east,part=foot]");
            set(x1 + 1, 1, z, "red_bed[facing=west,part=head]");
            set(x1 + 2, 1, z, "red_bed[facing=west,part=foot]");
            set(x2 - 1, 1, z + 1, "chest[facing=west]");
            set(x1 + 1, 1, z + 1, "chest[facing=east]");
            set(x2 - 1, 2, z + 1, "lantern");
        }
        fill("red_carpet", x1 + 5, 1, z1 + 1, x2 - 5, 1, z2 - 1);
        fill("white_carpet", x1 + 6, 1, z1 + 1, x2 - 6, 1, z2 - 1);
        // второй этаж — тоже кровати (вид из окон)
        for (int z = z1 + 2; z <= z2 - 2; z += 3) {
            set(x2, 7, z, "blue_bed[facing=east,part=head]");
            set(x2 - 1, 7, z, "blue_bed[facing=east,part=foot]");
            set(x1, 7, z, "blue_bed[facing=west,part=head]");
            set(x1 + 1, 7, z, "blue_bed[facing=west,part=foot]");
        }
        for (int z = z1 + 3; z <= z2 - 3; z += 6) {
            hangLantern(32, 11, z, 1);
        }
        chimney(x1 + 6, z1 + 1, 1, 24);
        chimney(x2 - 6, z2 - 1, 1, 24);
        barrelPile(x1 - 4, -78);
        set(x1 - 4, 0, -63, "composter[level=7]");
    }

    private void mineOneArea() {
        // площадка перед шахтой
        fill(ROAD, -12, -1, -98, 12, -1, -91);
        // обод ямы 31×31 (x -15..15, z -129..-99)
        int px1 = -15, px2 = 15, pz1 = -129, pz2 = -99;
        fill("pal:stone_bricks*3,polished_andesite*2,cobblestone", px1 - 3, -1, pz1 - 3, px2 + 3, -1, pz2 + 1);
        pit(px1, pz1, 31, 12, "pal:stone_bricks*4,cracked_stone_bricks,mossy_stone_bricks,andesite");
        perimeter(px1 - 1, pz1 - 1, px2 + 1, pz2 + 1, (x, z, side, t, len, corner) -> {
            set(x, 0, z, corner || t % 5 == 0 ? "stone_bricks" : "oak_fence");
            if (corner || t % 5 == 0) {
                set(x, 1, z, "stone_brick_wall");
                if (t % 10 == 0) {
                    set(x, 2, z, "lantern");
                }
            }
        });
        // ворота шахты
        fill(null, -2, 0, pz2 + 1, 2, 2, pz2 + 1);
        for (int x : new int[]{-3, 3}) {
            fill("dark_oak_log", x, 0, pz2 + 1, x, 6, pz2 + 1);
            set(x, 7, pz2 + 1, "dark_oak_slab");
        }
        fill("dark_oak_log[axis=x]", -4, 6, pz2 + 1, 4, 6, pz2 + 1);
        set(-4, 6, pz2 + 1, "dark_oak_stairs[facing=east,half=top]");
        set(4, 6, pz2 + 1, "dark_oak_stairs[facing=west,half=top]");
        set(-2, 5, pz2 + 1, "dark_oak_stairs[facing=east,half=top]");
        set(2, 5, pz2 + 1, "dark_oak_stairs[facing=west,half=top]");
        hangLantern(0, 5, pz2 + 1, 1);
        // кран у ямы
        int cx = px2 + 4, cz = -116;
        fill("pal:cobblestone,stone_bricks", cx - 1, 0, cz - 1, cx + 1, 1, cz + 1);
        fill("spruce_log", cx, 2, cz, cx, 13, cz);
        fill("spruce_log[axis=x]", cx - 12, 13, cz, cx + 3, 13, cz);
        fill("spruce_fence", cx - 12, 12, cz, cx - 12, 12, cz);
        for (int y = 5; y <= 11; y++) {
            set(cx - 12, y, cz, "chain");
        }
        set(cx - 12, 4, cz, "barrel[facing=up]");
        set(cx + 3, 12, cz, "chain");
        set(cx + 3, 11, cz, "cobblestone");
        set(cx - 1, 12, cz, "spruce_stairs[facing=east,half=top]");
        set(cx + 1, 12, cz, "spruce_stairs[facing=west,half=top]");
        // рельсы с вагонетками-«грудами» руды
        for (int z = -112; z <= -100; z++) {
            set(-19, 0, z, "rail[shape=north_south]");
        }
        set(-21, 0, -105, "coal_ore");
        set(-21, 0, -106, "iron_ore");
        set(-21, 1, -105, "cobblestone_slab");
        barrelPile(-22, -110);
        set(-21, 0, -101, "chest[facing=east]");
        // лавка торговца (восточнее ворот)
        int sx1 = 6, sx2 = 13, sz1 = -97, sz2 = -92;
        fill("spruce_planks", sx1, -1, sz1, sx2, -1, sz2);
        for (int x : new int[]{sx1, sx2}) {
            for (int z : new int[]{sz1, sz2}) {
                fill("spruce_log", x, 0, z, x, 3, z);
            }
        }
        for (int x = sx1 - 1; x <= sx2 + 1; x++) {
            set(x, 4, sz1 - 1, "spruce_stairs[facing=south,half=bottom]");
            for (int z = sz1; z <= sz2; z++) {
                set(x, 4, z, (x + z) % 2 == 0 ? "yellow_wool" : "brown_wool");
            }
            set(x, 4, sz2 + 1, "spruce_stairs[facing=north,half=bottom]");
        }
        fill("barrel[facing=west]", sx2 - 1, 0, sz1 + 1, sx2 - 1, 0, sz2 - 1);
        set(sx2 - 1, 1, sz1 + 1, "lantern");
        set(sx1 + 1, 0, sz1, "crafting_table");
        hangLantern(10, 3, -94, 0);
    }

    // ------------------------------------------------------------------ восток

    private void bathhouse() {
        int x1 = 42, x2 = 74, z1 = -12, z2 = 12;
        house(x1, z1, x2, z2, 7, 0, true, BATH, true, 2, false);
        int T = lastT;
        for (int x : new int[]{50, 66}) {
            dormer(true, x, z1, true, T, BATH);
            dormer(true, x, z2, false, T, BATH);
        }
        door(x1, -1, 3, 2, BATH, false);
        porch(3, x1, -4, 3, 4, 4, BATH);
        // вентиляционная башенка на коньке
        int ry = T + 1 + 13;
        fill("stripped_birch_log", 57, ry - 2, -1, 59, ry + 2, 1);
        fill(null, 58, ry, -1, 58, ry + 1, 1);
        fill(null, 57, ry, 0, 59, ry + 1, 0);
        set(58, ry - 1, 0, "campfire[lit=true]");
        roofHip(56, -2, 60, 2, ry + 3, "dark_prismarine_stairs", "dark_prismarine", "dark_prismarine_slab");
        set(58, ry + 6, 0, "lightning_rod");
        // шахматный кафель
        for (int x = x1 + 1; x < x2; x++) {
            for (int z = z1 + 1; z < z2; z++) {
                set(x, 0, z, (x + z) % 2 == 0 ? "white_concrete" : "light_blue_terracotta");
            }
        }
        // туалетные кабинки (север)
        for (int x = x1 + 3; x <= x2 - 3; x += 3) {
            fill("smooth_quartz", x - 1, 1, z1 + 1, x - 1, 3, z1 + 4);
            set(x, 1, z1 + 1, "hopper[facing=down]");
            set(x, 2, z1 + 1, "lever[face=wall,facing=south]");
            set(x, 1, z1 + 4, "birch_door[facing=north,half=lower,hinge=left]");
            set(x, 2, z1 + 4, "birch_door[facing=north,half=upper,hinge=left]");
            set(x + 1, 1, z1 + 4, "smooth_quartz");
            set(x + 1, 2, z1 + 4, "smooth_quartz");
            set(x + 1, 3, z1 + 1, "lantern[hanging=true]");
        }
        // душевые (юг)
        for (int x = x1 + 3; x <= x2 - 3; x += 3) {
            fill("light_blue_stained_glass_pane", x - 1, 1, z2 - 4, x - 1, 3, z2 - 1);
            set(x, 1, z2 - 1, "water_cauldron[level=3]");
            set(x, 4, z2 - 1, "lightning_rod[facing=north]");
            set(x, 3, z2 - 1, "tripwire_hook[facing=north]");
        }
        // умывальники в центре
        for (int x = x1 + 6; x <= x2 - 6; x += 4) {
            set(x, 1, 0, "water_cauldron[level=3]");
            set(x, 1, -1, "smooth_quartz_slab[type=top]");
            set(x, 1, 1, "smooth_quartz_slab[type=top]");
            set(x, 3, 0, "lightning_rod[facing=down]");
        }
        for (int x = x1 + 4; x <= x2 - 4; x += 8) {
            hangLantern(x, 7, -4, 1);
            hangLantern(x, 7, 4, 1);
        }
        chimney(x2 - 3, z1, 1, 16);
        // корзины с бельём
        set(x1 - 3, 0, -7, "barrel[facing=up]");
        set(x1 - 3, 0, 7, "barrel[facing=up]");
        set(x1 - 3, 1, 7, "white_carpet");
    }

    private void trainingYard() {
        int x1 = 90, x2 = 126, z1 = -24, z2 = 22;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, -1, z, "pal:coarse_dirt*3,dirt_path*2,packed_mud,gravel");
            }
        }
        perimeter(x1 - 1, z1 - 1, x2 + 1, z2 + 1, (x, z, side, t, len, corner) -> {
            set(x, 0, z, corner || t % 6 == 0 ? "spruce_log" : "spruce_fence");
            if (corner || t % 6 == 0) {
                set(x, 1, z, "spruce_fence");
                set(x, 2, z, "lantern");
            }
        });
        fill(null, x1 - 1, 0, z1 - 1, x1 + 3, 2, z1 - 1); // вход с дороги
        // манекены
        for (int z = z1 + 6; z <= z2 - 6; z += 6) {
            int x = x1 + 12;
            set(x, 0, z, "spruce_fence");
            set(x, 1, z, "hay_block");
            set(x - 1, 1, z, "spruce_fence");
            set(x + 1, 1, z, "spruce_fence");
            set(x, 2, z, "carved_pumpkin[facing=west]");
        }
        // стрельбище
        for (int z = z1 + 4; z <= z2 - 4; z += 5) {
            set(x2 - 2, 0, z, "hay_block");
            set(x2 - 2, 1, z, "target");
        }
        fill("spruce_slab[type=top]", x1 + 22, 0, z1 + 3, x1 + 22, 0, z2 - 3);
        // оружейная стойка и навес тренера
        int sx = x1 + 2, sz = -4;
        fill("spruce_planks", sx, -1, sz, sx + 6, -1, sz + 8);
        for (int x : new int[]{sx, sx + 6}) {
            for (int z : new int[]{sz, sz + 8}) {
                fill("spruce_log", x, 0, z, x, 3, z);
            }
        }
        beamRing(sx, sz, sx + 6, sz + 8, 4, new St("spruce_log", "", "", "", "", "", "", "", "", ""));
        roofGable(sx, sz, sx + 6, sz + 8, 3, false, "spruce_stairs", "spruce_planks", "spruce_slab", "spruce_planks", 1, null);
        fill("spruce_trapdoor[facing=east,open=true]", sx, 1, sz + 1, sx, 1, sz + 7);
        set(sx + 1, 0, sz + 1, "grindstone[face=floor,facing=east]");
        set(sx + 1, 0, sz + 7, "smithing_table");
        set(sx + 1, 0, sz + 3, "barrel[facing=east]");
        set(sx + 1, 0, sz + 5, "anvil[facing=north]");
        hangLantern(sx + 3, 3, sz + 4, 0);
        // трибуны
        for (int k = 0; k < 3; k++) {
            fill("spruce_stairs[facing=south,half=bottom]", x1 + 14, k, z1 - 2 - k, x2 - 6, k, z1 - 2 - k);
            if (k > 0) {
                fill("spruce_planks", x1 + 14, 0, z1 - 2 - k, x2 - 6, k - 1, z1 - 2 - k);
            }
        }
    }

    // ------------------------------------------------------------------ запад: собор

    private void cathedral() {
        String wall = STONE;
        int nx1 = -104, nx2 = -56, nz1 = -11, nz2 = 11, H = 13;
        // фундамент
        fill(STONE, -116, -3, -24, -44, 0, 24);
        fill(null, -116, 1, -24, -44, 60, 24);
        // вернуть газон вокруг (фундамент только под зданием)
        for (int x = -116; x <= -44; x++) {
            for (int z = -24; z <= 24; z++) {
                boolean inNave = x >= nx1 && x <= nx2 && z >= nz1 && z <= nz2;
                boolean inTrans = x >= -84 && x <= -72 && z >= -21 && z <= 21;
                boolean inTower = x >= -56 && x <= -46 && z >= -5 && z <= 5;
                double dx = x + 104, dz = z;
                boolean inApse = x < nx1 && dx * dx + dz * dz <= 11 * 11;
                if (!inNave && !inTrans && !inTower && !inApse) {
                    fill(DIRT, x, -3, z, x, -2, z);
                    set(x, -1, z, "grass_block");
                    set(x, 0, z, null);
                }
            }
        }
        // неф и трансепт
        cathedralBox(-84, -21, -72, 21, H, false);
        cathedralBox(nx1, nz1, nx2, nz2, H, true);
        // апсида
        for (int x = -116; x < nx1; x++) {
            for (int z = -12; z <= 12; z++) {
                double dx = x - nx1, dd = Math.sqrt(dx * dx + z * z);
                if (dd <= 11.5) {
                    set(x, 0, z, "polished_andesite");
                    if (dd > 10.3) {
                        for (int y = 1; y <= H; y++) {
                            set(x, y, z, wall);
                        }
                        if (Math.abs(z) % 4 == 1 && Math.abs(z) < 9) {
                            fill(pick("pal:blue_stained_glass_pane,purple_stained_glass_pane,light_blue_stained_glass_pane", x, 0, z), x, 3, z, x, 10, z);
                        }
                    } else {
                        fill(null, x, 1, z, x, H, z);
                    }
                }
            }
        }
        cone(nx1, 0, 12.2, H + 1, "deepslate_tile_stairs", "deepslate_tiles", 0.5, null, nx1 - 1);
        // арка между нефом и апсидой
        fill(null, nx1, 1, -8, nx1, 10, 8);
        for (int z = -8; z <= 8; z++) {
            int y = 11 - Math.max(0, Math.abs(z) - 4) / 2;
            fill(null, nx1, 1, z, nx1, y, z);
        }
        // алтарь
        fill("quartz_bricks", -112, 1, -6, -101, 1, 6);
        fill("quartz_stairs[facing=west,half=bottom]", -100, 1, -6, -100, 1, 6);
        fill("red_carpet", -111, 2, -5, -102, 2, 5);
        fill("gold_block", -109, 2, -2, -109, 2, 2);
        fill("quartz_slab[type=top]", -108, 2, -2, -108, 2, 2);
        for (int z = -2; z <= 2; z += 2) {
            set(-108, 3, z, "candle[candles=4,lit=true]");
        }
        set(-110, 2, 0, "gold_block");
        set(-110, 3, 0, "gold_block");
        set(-110, 4, 0, "gold_block");
        set(-110, 5, 0, "gold_block");
        set(-110, 4, -1, "gold_block");
        set(-110, 4, 1, "gold_block");
        set(-111, 2, -4, "bookshelf");
        set(-111, 2, 4, "bookshelf");
        set(-104, 2, -5, "bell[attachment=floor,facing=east]");
        // скамьи
        fill("red_carpet", -99, 1, -1, -57, 1, 1);
        for (int x = -97; x <= -60; x += 2) {
            if (x >= -86 && x <= -70) {
                continue;
            }
            fill("spruce_stairs[facing=east,half=bottom]", x, 1, -9, x, 1, -3);
            fill("spruce_stairs[facing=east,half=bottom]", x, 1, 3, x, 1, 9);
            set(x, 1, -10, "spruce_trapdoor[facing=north,open=true]");
            set(x, 1, 10, "spruce_trapdoor[facing=south,open=true]");
        }
        // люстры
        for (int x = -96; x <= -60; x += 12) {
            chandelier(x, 0, 28, 14);
        }
        chandelier(-78, 0, 26, 12);
        // колокольня
        tower();
        // входная лестница
        for (int z = -2; z <= 2; z++) {
            set(-45, 0, z, "stone_brick_stairs[facing=west,half=bottom]");
        }
    }

    /** Корпус собора: контрфорсы, стрельчатые витражи, крутая черепичная крыша. */
    private void cathedralBox(int x1, int z1, int x2, int z2, int H, boolean ridgeX) {
        fill("pal:polished_andesite,calcite", x1, 0, z1, x2, 0, z2);
        for (int x = x1 + 1; x < x2; x++) {
            for (int z = z1 + 1; z < z2; z++) {
                set(x, 0, z, (x + z) % 2 == 0 ? "polished_andesite" : "calcite");
            }
        }
        fill(null, x1 + 1, 1, z1 + 1, x2 - 1, 40, z2 - 1);
        perimeter(x1, z1, x2, z2, (x, z, side, t, len, corner) -> {
            for (int y = 1; y <= H; y++) {
                set(x, y, z, corner ? "pal:chiseled_stone_bricks,stone_bricks" : STONE);
            }
            set(x, 1, z, "pal:cobblestone,mossy_cobblestone");
            boolean along = ridgeX ? side % 2 == 0 : side % 2 == 1;
            String out = SIDE[side];
            if (corner) {
                return;
            }
            if (along && t % 6 == 0 && t > 0 && t < len) {
                // контрфорс
                int ox = SX[side], oz = SZ[side];
                fill(STONE, x + ox, 0, z + oz, x + ox, H - 2, z + oz);
                fill(STONE, x + 2 * ox, 0, z + 2 * oz, x + 2 * ox, H - 6, z + 2 * oz);
                set(x + ox, H - 1, z + oz, stairs("stone_brick_stairs", opposite(out), false));
                set(x + 2 * ox, H - 5, z + 2 * oz, stairs("stone_brick_stairs", opposite(out), false));
            } else if (along && t % 6 == 3) {
                String glass = pick("pal:blue_stained_glass_pane*2,purple_stained_glass_pane,light_blue_stained_glass_pane,magenta_stained_glass_pane", x, 0, z);
                int ax = side % 2 == 0 ? 1 : 0, az = side % 2 == 0 ? 0 : 1;
                for (int k = -1; k <= 1; k++) {
                    fill(k == 0 ? glass : glass, x + ax * k, 3, z + az * k, x + ax * k, 10, z + az * k);
                }
                set(x, 11, z, glass);
                set(x - ax, 11, z - az, stairs("stone_brick_stairs", side % 2 == 0 ? "west" : "north", true));
                set(x + ax, 11, z + az, stairs("stone_brick_stairs", side % 2 == 0 ? "east" : "south", true));
                set(x + SX[side], 2, z + SZ[side], stairs("stone_brick_stairs", opposite(out), true));
            }
        });
        roofGable(x1, z1, x2, z2, H, ridgeX, "deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab", STONE, 2, null);
        // розетка на торце трансепта
        if (!ridgeX) {
            for (int z : new int[]{z1, z2}) {
                int cx = (x1 + x2) / 2, cy = 9;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        double r = Math.sqrt(dx * dx + dy * dy);
                        if (r <= 3.2) {
                            set(cx + dx, cy + dy, z, r < 1 ? "yellow_stained_glass_pane" : r < 2.2 ? "red_stained_glass_pane" : "blue_stained_glass_pane");
                        }
                    }
                }
                for (int x = cx - 1; x <= cx + 1; x++) {
                    fill(null, x, 1, z, x, 4, z);
                }
                set(cx, 0, z + (z < 0 ? -1 : 1), stairs("stone_brick_stairs", z < 0 ? "south" : "north", false));
                set(cx - 1, 0, z + (z < 0 ? -1 : 1), stairs("stone_brick_stairs", z < 0 ? "south" : "north", false));
                set(cx + 1, 0, z + (z < 0 ? -1 : 1), stairs("stone_brick_stairs", z < 0 ? "south" : "north", false));
                set(cx - 2, 3, z + (z < 0 ? -1 : 1), "lantern[hanging=false]");
            }
        }
    }

    private void chandelier(int x, int z, int yTop, int yBottom) {
        for (int y = yTop; y > yBottom; y--) {
            set(x, y, z, "chain");
        }
        set(x, yBottom, z, "dark_oak_fence");
        for (int k = 0; k < 4; k++) {
            int dx = SX[k] * 2, dz = SZ[k] * 2;
            set(x + SX[k], yBottom, z + SZ[k], "dark_oak_fence");
            set(x + dx, yBottom, z + dz, "dark_oak_fence");
            set(x + dx, yBottom - 1, z + dz, "lantern[hanging=true]");
        }
        set(x, yBottom - 1, z, "lantern[hanging=true]");
    }

    private void tower() {
        int x1 = -56, x2 = -46, z1 = -5, z2 = 5, top = 38, cx = -51;
        fill("pal:polished_andesite,calcite", x1, 0, z1, x2, 0, z2);
        fill(null, x1 + 1, 1, z1 + 1, x2 - 1, top, z2 - 1);
        perimeter(x1, z1, x2, z2, (x, z, side, t, len, corner) -> {
            for (int y = 1; y <= top; y++) {
                set(x, y, z, corner ? "chiseled_stone_bricks" : STONE);
            }
            if (!corner && side != 3) {
                for (int y = 8; y <= top; y += 10) {
                    set(x + SX[side], y, z + SZ[side], stairs("stone_brick_stairs", opposite(SIDE[side]), true));
                }
            }
        });
        // угловые пилоны с фасада
        for (int z : new int[]{z1 - 1, z2 + 1}) {
            fill(STONE, x2 + 1, 0, z, x2 + 1, top - 6, z);
            set(x2 + 1, top - 5, z, "stone_brick_wall");
            fill(STONE, x2, 0, z, x2, top, z);
        }
        // портал
        fill(null, x2, 1, -1, x2, 5, 1);
        set(x2, 5, -1, "stone_brick_stairs[facing=south,half=top]");
        set(x2, 5, 1, "stone_brick_stairs[facing=north,half=top]");
        set(x2, 6, 0, "chiseled_stone_bricks");
        set(x2 + 1, 6, 0, "stone_brick_stairs[facing=west,half=bottom]");
        fill(null, x1, 1, -1, x1, 5, 1);
        // окно над порталом
        fill(pick("pal:blue_stained_glass_pane", 0, 0, 0), x2, 18, -1, x2, 23, 1);
        // звонница: арки на каждой стороне
        for (int k = -1; k <= 1; k++) {
            fill(null, cx + k, 30, z1, cx + k, 35, z1);
            fill(null, cx + k, 30, z2, cx + k, 35, z2);
            fill(null, x1, 30, k, x1, 35, k);
            fill(null, x2, 30, k, x2, 35, k);
        }
        for (int[] e : new int[][]{{cx - 1, z1, 0}, {cx + 1, z1, 1}, {cx - 1, z2, 0}, {cx + 1, z2, 1}}) {
            set(e[0], 35, e[1], stairs("stone_brick_stairs", e[2] == 0 ? "west" : "east", true));
        }
        for (int[] e : new int[][]{{x1, -1, 0}, {x1, 1, 1}, {x2, -1, 0}, {x2, 1, 1}}) {
            set(e[0], 35, e[1], stairs("stone_brick_stairs", e[2] == 0 ? "north" : "south", true));
        }
        fill(STONE, x1 + 1, 28, z1 + 1, x2 - 1, 28, z2 - 1);
        fill("dark_oak_log[axis=z]", cx, 35, z1 + 1, cx, 35, z2 - 1);
        set(cx, 34, 0, "bell[attachment=ceiling,facing=east]");
        // зубцы и шпиль
        fill(STONE, x1, top, z1, x2, top, z2);
        perimeter(x1 - 1, z1 - 1, x2 + 1, z2 + 1, (x, z, side, t, len, corner) -> {
            set(x, top, z, stairs("stone_brick_stairs", opposite(SIDE[side]), true));
            if (corner || t % 2 == 0) {
                set(x, top + 1, z, "stone_brick_wall");
            }
        });
        for (int[] c : new int[][]{{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}, {x1 - 1, z2 + 1}, {x2 + 1, z2 + 1}}) {
            set(c[0], top + 1, c[1], "stone_bricks");
            set(c[0], top + 2, c[1], "stone_brick_wall");
            set(c[0], top + 3, c[1], "lantern");
        }
        int sy = top + 1;
        int ty = sy;
        for (int k = 0; k < 6; k++) {
            int ax = x1 + 1 + k, bx = x2 - 1 - k, az = z1 + 1 + k, bz = z2 - 1 - k;
            if (ax > bx) {
                break;
            }
            for (int q = 0; q < 2; q++) {
                int yy = sy + k * 2 + q;
                ty = yy + 1;
                if (ax == bx) {
                    set(ax, yy, az, "deepslate_tiles");
                    continue;
                }
                String n = q == 1 ? "deepslate_tile_stairs[facing=south,half=bottom]" : "deepslate_tiles";
                String so = q == 1 ? "deepslate_tile_stairs[facing=north,half=bottom]" : "deepslate_tiles";
                String e = q == 1 ? "deepslate_tile_stairs[facing=west,half=bottom]" : "deepslate_tiles";
                String w = q == 1 ? "deepslate_tile_stairs[facing=east,half=bottom]" : "deepslate_tiles";
                for (int x = ax; x <= bx; x++) {
                    set(x, yy, az, n);
                    set(x, yy, bz, so);
                }
                for (int z = az + 1; z < bz; z++) {
                    set(ax, yy, z, w);
                    set(bx, yy, z, e);
                }
            }
        }
        fill("gold_block", cx, ty, 0, cx, ty + 3, 0);
        set(cx, ty + 2, -1, "gold_block");
        set(cx, ty + 2, 1, "gold_block");
        set(cx, ty + 4, 0, "lightning_rod");
        // циферблат на фасаде
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dz * dz + dy * dy <= 5) {
                    set(x2 + 1, 13 + dy, dz, dz == 0 && dy >= 0 || dy == 0 && dz >= 0 ? "polished_blackstone" : "calcite");
                }
            }
        }
    }

    // ------------------------------------------------------------------ юг: торговая улица

    private void shop() {
        int x1 = -36, x2 = -14, z1 = 46, z2 = 66;
        house(x1, z1, x2, z2, 5, 4, false, TIMBER, true, 0, true);
        for (int z : new int[]{50, 62}) {
            dormer(false, z, lastU[0], true, lastT, TIMBER);
            dormer(false, z, lastU[2], false, lastT, TIMBER);
        }
        door(x2, 55, 1, 2, TIMBER, false);
        porch(1, x2, 52, 59, 3, 4, TIMBER);
        // прилавок
        for (int z = z1 + 3; z <= z2 - 3; z++) {
            if (z == 55 || z == 56) {
                continue;
            }
            set(-21, 1, z, z % 3 == 0 ? "barrel[facing=east]" : "spruce_slab[type=top]");
        }
        fill("spruce_slab[type=top]", -21, 1, 55, -21, 1, 56);
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            set(x1 + 1, 1, z, z % 2 == 0 ? "barrel[facing=east]" : "chest[facing=east]");
            set(x1 + 1, 2, z, z % 2 == 0 ? "bookshelf" : "barrel[facing=east]");
            set(x1 + 1, 3, z, z % 3 == 0 ? "lantern" : "flower_pot");
        }
        // наружные лотки с навесами
        for (int k = 0; k < 2; k++) {
            int zz = k == 0 ? z1 + 2 : z2 - 4;
            for (int z = zz; z <= zz + 2; z++) {
                set(x2 + 2, 0, z, "spruce_planks");
                set(x2 + 2, 1, z, z % 2 == 0 ? "melon" : "pumpkin");
                set(x2 + 3, 3, z, (z % 2 == 0 ? "red" : "white") + "_wool");
                set(x2 + 2, 3, z, (z % 2 == 0 ? "red" : "white") + "_wool");
            }
            set(x2 + 3, 0, zz, "spruce_fence");
            set(x2 + 3, 1, zz, "spruce_fence");
            set(x2 + 3, 2, zz, "spruce_fence");
            set(x2 + 3, 0, zz + 2, "spruce_fence");
            set(x2 + 3, 1, zz + 2, "spruce_fence");
            set(x2 + 3, 2, zz + 2, "spruce_fence");
        }
        chimney(x1 + 3, z1 + 1, 1, 22);
        // лавка скупщика (у переулка)
        int sx1 = -34, sx2 = -26, sz1 = 30, sz2 = 33;
        fill("pal:spruce_planks*3,stripped_spruce_wood", sx1, -1, sz1, sx2, -1, sz2 + 1);
        for (int x : new int[]{sx1, sx2}) {
            for (int z : new int[]{sz1, sz2}) {
                fill("dark_oak_fence", x, 0, z, x, 3, z);
            }
        }
        for (int x = sx1 - 1; x <= sx2 + 1; x++) {
            for (int z = sz1 - 1; z <= sz2 + 1; z++) {
                set(x, 4, z, ((x - sx1) / 2) % 2 == 0 ? "green_wool" : "white_wool");
            }
            set(x, 3, sz2 + 1, ((x - sx1) / 2) % 2 == 0 ? "green_carpet" : "white_carpet");
        }
        fill("spruce_slab[type=top]", sx1 + 1, 0, sz2, sx2 - 1, 0, sz2);
        set(sx1 + 1, 0, sz1 + 1, "barrel[facing=up]");
        set(sx2 - 1, 0, sz1 + 1, "chest[facing=south]");
        set(sx1 + 2, 1, sz2, "gold_block");
        set(sx2 - 2, 1, sz2, "emerald_block");
        hangLantern(-30, 3, 31, 0);
    }

    private void smithy() {
        int x1 = -38, x2 = -14, z1 = 74, z2 = 96;
        house(x1, z1, x2, z2, 7, 0, false, SMITH, false, 7, false);
        // открытый фасад кузницы
        for (int z = z1 + 2; z <= z2 - 2; z++) {
            if (z % 4 == 1) {
                continue;
            }
            fill(null, x2, 1, z, x2, 5, z);
        }
        for (int z = z1; z <= z2; z += 4) {
            fill("dark_oak_log", x2, 1, z, x2, 7, z);
        }
        fill("dark_oak_log[axis=z]", x2, 6, z1, x2, 6, z2);
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            set(x2 + 1, 6, z, "dark_oak_stairs[facing=west,half=top]");
        }
        // горн
        int fx = x1 + 2, fz = 85;
        fill("pal:bricks*2,polished_blackstone_bricks", fx - 1, 1, fz - 2, fx + 1, 3, fz + 2);
        fill("magma_block", fx, 1, fz - 1, fx, 1, fz + 1);
        fill("campfire[lit=true]", fx + 1, 1, fz - 1, fx + 1, 1, fz + 1);
        fill(null, fx + 1, 2, fz - 1, fx + 1, 2, fz + 1);
        fill("pal:bricks*3,cobblestone", fx - 1, 4, fz - 1, fx + 1, 20, fz + 1);
        set(fx, 21, fz, "campfire[lit=true]");
        fill(null, fx, 4, fz, fx, 20, fz);
        // инструменты
        set(x1 + 6, 1, fz - 4, "anvil[facing=north]");
        set(x1 + 6, 1, fz + 4, "chipped_anvil[facing=north]");
        set(x1 + 1, 1, z1 + 2, "blast_furnace[facing=east,lit=true]");
        set(x1 + 1, 1, z1 + 3, "blast_furnace[facing=east,lit=true]");
        set(x1 + 1, 1, z2 - 2, "smithing_table");
        set(x1 + 1, 1, z2 - 3, "grindstone[face=floor,facing=east]");
        set(x1 + 1, 1, z2 - 4, "stonecutter[facing=east]");
        fill("barrel[facing=up]", x1 + 1, 1, z1 + 5, x1 + 1, 1, z1 + 7);
        set(x1 + 10, 1, fz, "water_cauldron[level=3]");
        for (int z = z1 + 2; z <= z2 - 2; z += 5) {
            hangLantern(x1 + 8, 7, z, 1);
            hangLantern(x1 + 16, 7, z, 1);
        }
        set(x2 + 2, 0, z1 + 1, "anvil[facing=west]");
        barrelPile(x2 + 2, z2 - 2);
        fill("chain", x2 - 1, 4, z1 + 2, x2 - 1, 6, z1 + 2);
        set(x2 - 1, 3, z1 + 2, "iron_block");
    }

    private void donateTemple() {
        int x1 = 14, x2 = 36, z1 = 46, z2 = 68, H = 9;
        fill("pal:quartz_bricks*3,smooth_quartz", x1 - 5, -3, z1 - 1, x2 + 1, 0, z2 + 1);
        fill(null, x1 - 5, 1, z1 - 1, x2 + 1, 50, z2 + 1);
        // ступени портика
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            set(x1 - 6, 0, z, "quartz_stairs[facing=east,half=bottom]");
        }
        for (int x = x1 - 6; x <= x2 + 1; x++) {
            set(x, 0, z1 - 2, "quartz_stairs[facing=south,half=bottom]");
            set(x, 0, z2 + 2, "quartz_stairs[facing=north,half=bottom]");
        }
        // стены целлы
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, 0, z, (x + z) % 2 == 0 ? "gold_block" : "smooth_quartz");
            }
        }
        fill("purple_carpet", x1 + 1, 1, 56, x2 - 4, 1, 58);
        perimeter(x1, z1, x2, z2, (x, z, side, t, len, corner) -> {
            for (int y = 1; y <= H; y++) {
                set(x, y, z, corner || t % 4 == 0 ? "quartz_pillar" : "quartz_bricks");
            }
            if (!corner && t % 4 == 2 && t > 1 && t < len - 1) {
                fill("yellow_stained_glass_pane", x, 2, z, x, 6, z);
            }
            set(x, H, z, "chiseled_quartz_block");
            set(x, 1, z, "smooth_quartz");
        });
        // колоннада портика (запад)
        for (int z = z1; z <= z2; z += 4) {
            set(x1 - 4, 1, z, "quartz_bricks");
            fill("quartz_pillar", x1 - 4, 2, z, x1 - 4, H - 1, z);
            set(x1 - 4, H, z, "chiseled_quartz_block");
        }
        fill("smooth_quartz", x1 - 5, H + 1, z1 - 1, x2 + 1, H + 1, z2 + 1);
        perimeter(x1 - 6, z1 - 2, x2 + 2, z2 + 2, (x, z, side, t, len, corner) -> set(x, H + 1, z, stairs("smooth_quartz_stairs", opposite(SIDE[side]), true)));
        perimeter(x1 - 5, z1 - 1, x2 + 1, z2 + 1, (x, z, side, t, len, corner) -> set(x, H + 2, z, corner || t % 4 == 0 ? "quartz_pillar" : "diorite_wall"));
        perimeter(x1 - 5, z1 - 1, x2 + 1, z2 + 1, (x, z, side, t, len, corner) -> {
            if (corner || t % 4 == 0) {
                set(x, H + 3, z, "gold_block");
            }
        });
        // барабан и купол
        int dcx = 25, dcz = 57;
        for (int y = H + 2; y <= H + 5; y++) {
            v.ring("quartz_bricks", y, dcx, dcz, 6.5, 5.3);
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            int wx = (int) Math.round(dcx + Math.cos(a) * 6), wz = (int) Math.round(dcz + Math.sin(a) * 6);
            set(wx, H + 3, wz, "yellow_stained_glass");
            set(wx, H + 4, wz, "yellow_stained_glass");
        }
        for (int x = dcx - 8; x <= dcx + 8; x++) {
            for (int z = dcz - 8; z <= dcz + 8; z++) {
                double dx = x - dcx, dz = z - dcz, dd = Math.sqrt(dx * dx + dz * dz);
                if (dd <= 7.5 && dd > 6.3) {
                    set(x, H + 6, z, stairs("smooth_quartz_stairs", toward(dx, dz), true));
                }
            }
        }
        v.disc("smooth_quartz", H + 6, dcx, dcz, 6.3);
        dome(dcx, H + 6, dcz, 7.0, "dark_prismarine", "gold_block");
        set(dcx, H + 14, dcz, "gold_block");
        set(dcx, H + 15, dcz, "end_rod[facing=up]");
        set(dcx, H + 16, dcz, "end_rod[facing=up]");
        // фронтон-купол над входом
        fill(null, x1, 1, 56, x1, 4, 58);
        set(x1, 4, 56, "quartz_stairs[facing=north,half=top]");
        set(x1, 4, 58, "quartz_stairs[facing=south,half=top]");
        // интерьер: трон-витрина
        fill("purpur_block", x2 - 3, 1, 54, x2 - 1, 1, 60);
        fill("purpur_stairs[facing=west,half=bottom]", x2 - 4, 1, 54, x2 - 4, 1, 60);
        set(x2 - 2, 2, 57, "beacon");
        set(x2 - 2, 2, 55, "amethyst_cluster[facing=up]");
        set(x2 - 2, 2, 59, "amethyst_cluster[facing=up]");
        for (int z = z1 + 2; z <= z2 - 2; z += 4) {
            set(x1 + 1, 1, z, "gold_block");
            set(x1 + 1, 2, z, "end_rod[facing=up]");
            set(x2 - 1, 1, z, "gold_block");
            set(x2 - 1, 2, z, "end_rod[facing=up]");
        }
        for (int x = x1 + 4; x <= x2 - 4; x += 6) {
            for (int z = z1 + 4; z <= z2 - 4; z += 7) {
                hangLantern(x, H - 1, z, 2);
            }
        }
        fill("smooth_quartz", x1 + 1, H, z1 + 1, x2 - 1, H, z2 - 1);
        fill("glowstone", 24, H, 56, 26, H, 58);
        // гербовые знамёна
        set(x1 - 1, 6, 53, "purple_wall_banner[facing=west]");
        set(x1 - 1, 6, 61, "purple_wall_banner[facing=west]");
    }

    private void gypsyTent() {
        int cx = 26, cz = 88;
        double r0 = 10.5;
        String[] col = {"red_wool", "yellow_wool", "purple_wool", "yellow_wool"};
        v.disc("pal:spruce_planks*3,stripped_spruce_wood", -1, cx, cz, r0 + 0.5);
        for (int y = 0; y <= 11; y++) {
            double rr = y < 3 ? r0 : r0 - (y - 2) * 1.1;
            if (rr < 0.8) {
                break;
            }
            for (int x = (int) (cx - rr - 1); x <= cx + rr + 1; x++) {
                for (int z = (int) (cz - rr - 1); z <= cz + rr + 1; z++) {
                    double dx = x - cx, dz = z - cz, dd = Math.sqrt(dx * dx + dz * dz);
                    if (dd <= rr && dd > rr - 1.3) {
                        int sector = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2) * 16);
                        set(x, y, z, col[sector % 4]);
                    }
                }
            }
        }
        // вход с запада и пологи
        for (int z = cz - 1; z <= cz + 1; z++) {
            fill(null, cx - 12, 0, z, cx - 7, 3, z);
        }
        set(cx - 11, 0, cz - 2, "dark_oak_fence");
        set(cx - 11, 1, cz - 2, "dark_oak_fence");
        set(cx - 11, 2, cz - 2, "lantern");
        set(cx - 11, 0, cz + 2, "dark_oak_fence");
        set(cx - 11, 1, cz + 2, "dark_oak_fence");
        set(cx - 11, 2, cz + 2, "lantern");
        // центральный шест и флаг
        fill("dark_oak_fence", cx, 0, cz, cx, 13, cz);
        set(cx, 14, cz, "red_banner[rotation=4]");
        // интерьер
        for (int x = cx - 9; x <= cx + 9; x++) {
            for (int z = cz - 9; z <= cz + 9; z++) {
                double dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz < 72 && v.air(x, 0, z)) {
                    set(x, 0, z, (Math.abs(x - cx) + Math.abs(z - cz)) % 3 == 0 ? "purple_carpet" : "red_carpet");
                }
            }
        }
        set(cx + 3, 0, cz, "dark_oak_fence");
        set(cx + 3, 1, cz, "purple_stained_glass");
        set(cx + 4, 0, cz - 2, "brewing_stand");
        set(cx + 4, 0, cz + 2, "cauldron");
        set(cx + 6, 0, cz, "chest[facing=west]");
        set(cx + 5, 0, cz - 4, "bookshelf");
        set(cx + 5, 0, cz + 4, "bookshelf");
        set(cx + 5, 1, cz - 4, "candle[candles=3,lit=true]");
        set(cx + 5, 1, cz + 4, "candle[candles=2,lit=true]");
        hangLantern(cx - 3, 6, cz, 2);
        hangLantern(cx, 9, cz - 3, 2);
        hangLantern(cx, 9, cz + 3, 2);
        // повозка у шатра
        int wx = cx + 2, wz = cz + 14;
        fill("spruce_planks", wx, 1, wz, wx + 5, 1, wz + 2);
        fill("spruce_fence", wx, 2, wz, wx + 5, 2, wz);
        fill("spruce_fence", wx, 2, wz + 2, wx + 5, 2, wz + 2);
        set(wx + 1, 0, wz - 1, "dark_oak_trapdoor[facing=north,open=true]");
        set(wx + 4, 0, wz - 1, "dark_oak_trapdoor[facing=north,open=true]");
        set(wx + 1, 0, wz + 3, "dark_oak_trapdoor[facing=south,open=true]");
        set(wx + 4, 0, wz + 3, "dark_oak_trapdoor[facing=south,open=true]");
        set(wx + 2, 2, wz + 1, "barrel[facing=up]");
        set(wx + 3, 2, wz + 1, "red_wool");
        set(wx + 4, 2, wz + 1, "yellow_wool");
    }

    // ------------------------------------------------------------------ юго-восток: огород (шахта 2)

    private void farm() {
        // яма 25×25: x 64..88, z 50..74
        int px1 = 64, px2 = 88, pz1 = 50, pz2 = 74;
        fill("pal:hay_block*2,oak_planks*3,stripped_oak_log", px1 - 2, -1, pz1 - 2, px2 + 2, -1, pz2 + 2);
        pit(px1, pz1, 25, 10, "pal:oak_planks*3,stripped_oak_log,dirt");
        perimeter(px1 - 1, pz1 - 1, px2 + 1, pz2 + 1, (x, z, side, t, len, corner) -> {
            set(x, 0, z, corner || t % 4 == 0 ? "oak_log" : "oak_fence");
            if (corner || t % 4 == 0) {
                set(x, 1, z, t % 8 == 0 ? "lantern" : "oak_fence");
            }
        });
        fill(null, 75, 0, pz1 - 1, 77, 1, pz1 - 1);
        // поля вокруг
        for (int[] f : new int[][]{{50, 80, 60, 108}, {94, 52, 108, 76}, {66, 82, 90, 96}, {94, 82, 100, 88}}) {
            for (int x = f[0]; x <= f[2]; x++) {
                for (int z = f[1]; z <= f[3]; z++) {
                    boolean edge = x == f[0] || x == f[2] || z == f[1] || z == f[3];
                    if (edge) {
                        set(x, -1, z, "oak_log[axis=" + (x == f[0] || x == f[2] ? "z" : "x") + "]");
                        continue;
                    }
                    if ((x - f[0]) % 5 == 0) {
                        set(x, -1, z, "water");
                        continue;
                    }
                    set(x, -1, z, "farmland[moisture=7]");
                    int crop = Math.floorMod(x / 5 + z / 7, 4);
                    set(x, 0, z, switch (crop) {
                        case 0 -> "wheat[age=7]";
                        case 1 -> "carrots[age=7]";
                        case 2 -> "potatoes[age=7]";
                        default -> "beetroots[age=3]";
                    });
                }
            }
        }
        // пугало
        set(80, 0, 89, "oak_fence");
        set(80, 1, 89, "hay_block");
        set(79, 1, 89, "oak_fence");
        set(81, 1, 89, "oak_fence");
        set(80, 2, 89, "carved_pumpkin[facing=north]");
        // амбар торговца (север ямы)
        int bx1 = 66, bx2 = 86, bz1 = 34, bz2 = 44;
        fill("pal:oak_planks*2,spruce_planks", bx1, -1, bz1, bx2, -1, bz2);
        for (int x = bx1; x <= bx2; x += 4) {
            for (int z : new int[]{bz1, bz2}) {
                fill("stripped_spruce_log", x, 0, z, x, 4, z);
            }
        }
        fill("red_terracotta", bx1, 0, bz1, bx2, 4, bz1);
        for (int x = bx1; x <= bx2; x += 4) {
            fill("stripped_spruce_log", x, 0, bz1, x, 4, bz1);
        }
        fill("red_terracotta", bx1, 0, bz1, bx1, 4, bz2);
        fill("red_terracotta", bx2, 0, bz1, bx2, 4, bz2);
        fill("stripped_spruce_log", bx1, 0, bz2, bx1, 4, bz2);
        fill("stripped_spruce_log", bx2, 0, bz2, bx2, 4, bz2);
        fill("stripped_spruce_log[axis=x]", bx1, 5, bz1, bx2, 5, bz1);
        fill("stripped_spruce_log[axis=x]", bx1, 5, bz2, bx2, 5, bz2);
        fill("stripped_spruce_log[axis=z]", bx1, 5, bz1, bx1, 5, bz2);
        fill("stripped_spruce_log[axis=z]", bx2, 5, bz1, bx2, 5, bz2);
        roofGable(bx1, bz1, bx2, bz2, 4, true, "spruce_stairs", "spruce_planks", "spruce_slab", "red_terracotta", 1, "glass_pane");
        fill("hay_block[axis=x]", bx1 + 1, 0, bz1 + 1, bx1 + 4, 1, bz1 + 2);
        fill("hay_block[axis=z]", bx2 - 3, 0, bz1 + 1, bx2 - 1, 2, bz1 + 2);
        fill("spruce_slab[type=top]", 74, 0, bz2, 79, 0, bz2);
        set(73, 0, bz2 - 1, "composter[level=4]");
        set(80, 0, bz2 - 1, "barrel[facing=up]");
        hangLantern(76, 4, 41, 0);
        hangLantern(70, 4, 39, 0);
        hangLantern(82, 4, 39, 0);
        // колодец
        int wx = 56, wz = 50;
        v.ring("pal:cobblestone,mossy_cobblestone", 0, wx, wz, 2.2, 1.1);
        v.disc("water", 0, wx, wz, 1.1);
        v.disc("water", -1, wx, wz, 1.1);
        for (int[] c : new int[][]{{-2, 0}, {2, 0}}) {
            fill("spruce_fence", wx + c[0], 1, wz, wx + c[0], 2, wz);
        }
        roofGable(wx - 2, wz - 1, wx + 2, wz + 1, 2, true, "spruce_stairs", "spruce_planks", "spruce_slab", "spruce_planks", 1, null);
        set(wx, 3, wz, "chain");
        set(wx, 2, wz, "chain");
        fill("spruce_log[axis=x]", wx - 1, 3, wz, wx + 1, 3, wz);
    }

    // ------------------------------------------------------------------ надвратная башня и стена

    private void gatehouse() {
        int z1 = 118, z2 = 134, H = 15;
        fill(STONE, -10, -3, z1, 10, 0, z2);
        perimeter(-10, z1, 10, z2, (x, z, side, t, len, corner) -> {
            for (int y = 1; y <= H; y++) {
                set(x, y, z, STONE);
            }
        });
        fill(STONE, -9, 1, z1 + 1, 9, H, z2 - 1);
        // арка
        fill(ROAD, -3, -1, z1, 3, -1, z2);
        fill(null, -3, 0, z1, 3, 8, z2);
        for (int z = z1; z <= z2; z++) {
            set(-3, 8, z, "stone_brick_stairs[facing=east,half=top]");
            set(3, 8, z, "stone_brick_stairs[facing=west,half=top]");
            set(-2, 9, z, "stone_brick_stairs[facing=east,half=top]");
            set(2, 9, z, "stone_brick_stairs[facing=west,half=top]");
            fill(null, -1, 9, z, 1, 9, z);
        }
        fill("iron_bars", -2, 7, z1, 2, 8, z1);
        // портал
        int pz = z2 - 1;
        fill("crying_obsidian", -3, -1, pz, 3, 9, pz);
        fill("pal:purple_stained_glass*3,magenta_stained_glass", -2, 0, pz, 2, 8, pz);
        for (int y = 2; y <= 8; y += 3) {
            set(-3, y, pz - 1, "amethyst_cluster[facing=north]");
            set(3, y, pz - 1, "amethyst_cluster[facing=north]");
        }
        set(-3, 0, pz - 1, "soul_lantern");
        set(3, 0, pz - 1, "soul_lantern");
        // зубцы и машикули
        perimeter(-11, z1 - 1, 11, z2 + 1, (x, z, side, t, len, corner) -> {
            set(x, H, z, stairs("stone_brick_stairs", opposite(SIDE[side]), true));
            set(x, H + 1, z, corner || t % 2 == 0 ? "stone_bricks" : "stone_brick_slab[type=bottom]");
        });
        fill(STONE, -10, H + 1, z1, 10, H + 1, z2);
        for (int x = -11; x <= 11; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean edge = x == -11 || x == 11 || z == z1 - 1 || z == z2 + 1;
                if (edge) {
                    set(x, H + 2, z, (x + z) % 2 == 0 ? "stone_bricks" : null);
                }
            }
        }
        // знамёна над аркой
        for (int x : new int[]{-6, 6}) {
            set(x, 11, z1 - 1, "red_wall_banner[facing=north]");
            set(x, 12, z1 - 1, "stone_bricks");
        }
        set(-5, 3, z1 - 1, "lantern");
        set(5, 3, z1 - 1, "lantern");
        set(-5, 2, z1 - 1, "stone_brick_wall");
        set(5, 2, z1 - 1, "stone_brick_wall");
        // фланкирующие башни
        for (int sx : new int[]{-1, 1}) {
            roundTower(sx * 14, 124, 5.5, 20);
        }
        // площадка у портала
        fill(ROAD, -10, -1, 102, 10, -1, 117);
        v.disc("polished_deepslate", -1, 0, 110, 6.6);
        v.disc("pal:polished_blackstone_bricks*3,cracked_polished_blackstone_bricks", -1, 0, 110, 5.6);
        v.disc("amethyst_block", -1, 0, 110, 1.2);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            set((int) Math.round(Math.cos(a) * 4), -1, 110 + (int) Math.round(Math.sin(a) * 4), "calcite");
        }
        for (int x : new int[]{-8, 8}) {
            lamp(x, 108, false);
        }
    }

    void roundTower(int cx, int cz, double r, int H) {
        for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
            for (int z = (int) (cz - r - 1); z <= cz + r + 1; z++) {
                double dx = x - cx, dz = z - cz, dd = Math.sqrt(dx * dx + dz * dz);
                if (dd <= r) {
                    fill(STONE, x, -3, z, x, 0, z);
                    if (dd > r - 1.2) {
                        fill(STONE, x, 1, z, x, H, z);
                        double a = Math.atan2(dz, dx);
                        for (int y = 4; y < H - 2; y += 5) {
                            if (Math.abs(Math.sin(a * 2)) < 0.15) {
                                set(x, y, z, null);
                                set(x, y + 1, z, null);
                            }
                        }
                    } else {
                        fill(null, x, 1, z, x, H, z);
                        set(x, H, z, "spruce_planks");
                    }
                }
                if (dd <= r + 1 && dd > r) {
                    set(x, H, z, stairs("stone_brick_stairs", toward(dx, dz), true));
                    if (Vox.hash(x, 0, z, 5) % 2 == 0) {
                        set(x, H + 1, z, "stone_brick_wall");
                    }
                }
            }
        }
        cone(cx, cz, r + 0.8, H + 2, "deepslate_tile_stairs", "deepslate_tiles", 0.55, "lightning_rod");
        hangLantern(cx, H - 1, cz, 1);
    }

    private void perimeterWall() {
        for (int x = -WALL_R - 3; x <= WALL_R + 3; x++) {
            for (int z = -WALL_R - 3; z <= WALL_R + 3; z++) {
                double dd = Math.sqrt(x * x + z * z);
                if (dd > WALL_R + 1.5 || dd < WALL_R - 1.5) {
                    continue;
                }
                if (Math.abs(x) <= 11 && z > 100) {
                    continue;
                }
                fill(STONE, x, -3, z, x, 7, z);
                set(x, 0, z, "pal:cobblestone,mossy_cobblestone,stone_bricks");
                if (dd > WALL_R + 0.5) {
                    int k = (int) Math.round(Math.atan2(z, x) * WALL_R);
                    set(x, 8, z, Math.floorMod(k, 3) == 0 ? "stone_brick_slab[type=bottom]" : "stone_bricks");
                    if (Math.floorMod(k, 3) == 1) {
                        set(x, 9, z, "stone_brick_slab[type=bottom]");
                    }
                } else if (dd < WALL_R - 0.5) {
                    set(x, 8, z, "stone_brick_wall");
                    int k = (int) Math.round(Math.atan2(z, x) * WALL_R);
                    if (Math.floorMod(k, 24) == 0) {
                        set(x, 9, z, "lantern");
                    }
                }
            }
        }
        // плющ по стене
        for (int k = 0; k < 900; k++) {
            double a = k * 0.0071 * Math.PI * 2;
            int x = (int) Math.round(Math.cos(a) * (WALL_R - 2)), z = (int) Math.round(Math.sin(a) * (WALL_R - 2));
            if (Vox.rnd(x, 0, z, 33) < 0.35 && v.air(x, 3, z)) {
                int len = 2 + Vox.hash(x, 1, z, 33) % 5;
                String face = toward(-x, -z);
                int fx = face.equals("east") ? 1 : face.equals("west") ? -1 : 0, fz = face.equals("south") ? 1 : face.equals("north") ? -1 : 0;
                for (int y = 7; y > 7 - len; y--) {
                    if (v.air(x, y, z) && Vox.full(v.get(x + fx, y, z + fz))) {
                        set(x, y, z, "vine[" + face + "=true]");
                    }
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            roundTower((int) Math.round(Math.cos(a) * WALL_R), (int) Math.round(Math.sin(a) * WALL_R), 6.5, 14);
        }
    }

    // ------------------------------------------------------------------ природа

    private void pond() {
        int cx = -84, cz = -66;
        for (int x = cx - 20; x <= cx + 20; x++) {
            for (int z = cz - 16; z <= cz + 16; z++) {
                double dx = (x - cx) / 17.0, dz = (z - cz) / 13.0;
                double dd = Math.sqrt(dx * dx + dz * dz) + (Vox.fbm(x / 7.0, z / 7.0, 17, 2) - 0.5) * 0.35;
                if (dd < 1.0) {
                    int depth = dd < 0.55 ? 3 : dd < 0.8 ? 2 : 1;
                    fill("water", x, -depth, z, x, -1, z);
                    set(x, -depth - 1, z, Vox.rnd(x, 0, z, 18) < 0.4 ? "clay" : "sand");
                    if (depth >= 2 && Vox.rnd(x, 1, z, 19) < 0.18) {
                        set(x, -depth, z, "seagrass");
                    }
                    if (Vox.rnd(x, 2, z, 20) < 0.05 && dd > 0.3) {
                        set(x, 0, z, "lily_pad");
                    }
                } else if (dd < 1.18) {
                    set(x, -1, z, Vox.rnd(x, 3, z, 21) < 0.6 ? "sand" : "grass_block");
                    boolean wet = "water".equals(v.get(x + 1, -1, z)) || "water".equals(v.get(x - 1, -1, z)) || "water".equals(v.get(x, -1, z + 1)) || "water".equals(v.get(x, -1, z - 1));
                    if (wet && Vox.rnd(x, 4, z, 22) < 0.35 && v.air(x, 0, z)) {
                        int hgt = 1 + Vox.hash(x, 5, z, 22) % 3;
                        fill("sugar_cane", x, 0, z, x, hgt - 1, z);
                    }
                }
            }
        }
        // причал
        fill("spruce_planks", cx + 6, -1, cz - 1, cx + 18, -1, cz + 1);
        for (int x = cx + 6; x <= cx + 18; x += 4) {
            fill("spruce_log", x, -3, cz - 2, x, 0, cz - 2);
            fill("spruce_log", x, -3, cz + 2, x, 0, cz + 2);
            set(x, 1, cz - 2, "lantern");
        }
        fill("spruce_fence", cx + 6, 0, cz - 2, cx + 18, 0, cz - 2);
        fill("spruce_fence", cx + 6, 0, cz + 2, cx + 18, 0, cz + 2);
        set(cx + 7, 0, cz, "barrel[facing=up]");
        set(cx + 8, 0, cz + 1, "spruce_stairs[facing=south]");
        // ивы у воды
        oak(cx - 16, cz - 10, 3, 70);
        oak(cx + 14, cz + 13, 2, 71);
        birch(cx - 18, cz + 8, 72);
        birch(cx + 2, cz - 16, 73);
    }

    private void parkGazebo() {
        int cx = -84, cz = 78;
        double r = 6;
        v.disc("pal:spruce_planks*3,stripped_spruce_wood", 0, cx, cz, r);
        fill(null, cx - 7, 1, cz - 7, cx + 7, 12, cz + 7);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            int x = (int) Math.round(cx + Math.cos(a) * (r - 0.5)), z = (int) Math.round(cz + Math.sin(a) * (r - 0.5));
            fill("stripped_dark_oak_log", x, 1, z, x, 4, z);
            if (k != 4) {
                set(x, 1, z, "stripped_dark_oak_log");
            }
        }
        for (int x = cx - 7; x <= cx + 7; x++) {
            for (int z = cz - 7; z <= cz + 7; z++) {
                double dx = x - cx, dz = z - cz, dd = Math.sqrt(dx * dx + dz * dz);
                if (dd <= r + 1 && dd > r - 1) {
                    if (v.get(x, 1, z) == null && dd <= r && dd > r - 1 && !(dx < -4 && Math.abs(dz) < 2)) {
                        set(x, 1, z, "dark_oak_fence");
                    }
                    set(x, 5, z, stairs("dark_oak_stairs", toward(dx, dz), true));
                }
            }
        }
        v.disc("dark_oak_planks", 5, cx, cz, r - 1);
        cone(cx, cz, r + 1.5, 6, "dark_oak_stairs", "dark_oak_planks", 0.85, null);
        hangLantern(cx, 4, cz, 1);
        set(cx, 1, cz, "spruce_slab[type=top]");
        set(cx, 2, cz, "flower_pot");
        bench(cx + 3, cz - 1, 3, false, "east");
        // клумбы вокруг
        for (int k = 0; k < 6; k++) {
            double a = Math.PI / 3 * k + 0.3;
            int fx = (int) Math.round(cx + Math.cos(a) * 12), fz = (int) Math.round(cz + Math.sin(a) * 12);
            bush(fx, fz, 80 + k);
        }
        // вишни и дубы парка
        cherry(-62, 100, 81);
        cherry(-104, 56, 82);
        oak(-100, 98, 3, 83);
        oak(-70, 58, 2, 84);
        spruce(-112, 80, 85);
        spruce(-58, 116, 86);
        birch(-92, 110, 87);
        boulder(-74, 94, 2.6, 88);
        boulder(-108, 66, 2.2, 89);
    }

    private void orchard() {
        for (int x = 60; x <= 112; x += 9) {
            for (int z = -110; z <= -44; z += 9) {
                if (Math.sqrt(x * x + z * z) > WALL_R - 10) {
                    continue;
                }
                int jx = x + Vox.hash(x, 0, z, 90) % 3 - 1, jz = z + Vox.hash(x, 1, z, 90) % 3 - 1;
                oak(jx, jz, 1, 91 + x + z);
                if (Vox.rnd(x, 2, z, 92) < 0.5) {
                    set(jx + 2, 0, jz + 1, "barrel[facing=up]");
                }
            }
        }
        // хвойная роща за спальней и северные ели
        for (int[] t : new int[][]{{-60, -110}, {-74, -100}, {-48, -122}, {-30, -118}, {30, -118}, {46, -110}, {-104, -40}, {-118, -26}}) {
            spruce(t[0], t[1], t[0] * 7 + t[1]);
        }
        for (int[] t : new int[][]{{-118, 30}, {-60, -30}, {60, -36}, {112, 40}, {100, 112}, {-30, 118}, {30, 112}, {118, -54}}) {
            oak(t[0], t[1], 2, t[0] + t[1] * 3);
        }
        for (int[] b : new int[][]{{-50, -100}, {50, -30}, {-110, 0}, {110, 60}, {40, 120}}) {
            boulder(b[0], b[1], 2.4, b[0] + b[1]);
        }
    }

    private void streetLamps() {
        for (int z = -90; z <= -40; z += 14) {
            lamp(-6, z, true);
            lamp(6, z, true);
        }
        for (int z = 44; z <= 100; z += 14) {
            lamp(-6, z, true);
            lamp(6, z, true);
        }
        for (int x = -42; x >= -44; x -= 14) {
            lamp(x, -6, false);
            lamp(x, 6, false);
        }
        for (int x = 46; x <= 88; x += 14) {
            lamp(x, -30, false);
        }
        for (int x = -56; x <= 56; x += 14) {
            if (Math.abs(x) < 8) {
                continue;
            }
            lamp(x, 42, false);
        }
    }

    private void nature() {
        String[] flowers = {"poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "red_tulip", "lily_of_the_valley"};
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                if (!"grass_block".equals(v.get(x, -1, z)) || !v.air(x, 0, z)) {
                    continue;
                }
                double r = Vox.rnd(x, 0, z, 61);
                double meadow = Vox.fbm(x / 14.0, z / 14.0, 62, 2);
                if (meadow > 0.62 && r < 0.18) {
                    set(x, 0, z, flowers[(int) (Vox.fbm(x / 6.0, z / 6.0, 63, 1) * flowers.length * 1.7) % flowers.length]);
                } else if (r < 0.16) {
                    set(x, 0, z, "short_grass");
                } else if (r < 0.175 && v.air(x, 1, z)) {
                    set(x, 0, z, "tall_grass[half=lower]");
                    set(x, 1, z, "tall_grass[half=upper]");
                } else if (r < 0.18) {
                    set(x, 0, z, "fern");
                }
                if (Vox.rnd(x, 7, z, 64) < 0.0012 && Math.sqrt(x * x + z * z) < WALL_R - 6 && Math.sqrt(x * x + z * z) > 40) {
                    bush(x, z, x * 31 + z);
                }
            }
        }
    }

    // ------------------------------------------------------------------ выгрузка и функции

    /** Выгружает сетку в очередь стройки и регистрирует NPC, шахты, голограммы. */
    void emit() {
        wb.fill(org.bukkit.Material.AIR, -R - 4, MIN_Y, -R - 4, R + 4, MAX_Y, R + 4);
        v.emit((data, x, y, z1, z2) -> wb.put(data, x, y, z1, x, y, z2));
    }
}
