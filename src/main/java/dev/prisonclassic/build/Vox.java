package dev.prisonclassic.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Воксельная сетка для детальных построек. Хранит строки BlockData ("spruce_stairs[facing=north]")
 * или палитры ("pal:stone_bricks*3,cracked_stone_bricks"), которые раскладываются при установке по хэшу координат.
 * После разметки {@link #finish()} проставляет соединения заборов, панелей, решёток и стен, а также формы ступенек —
 * так, как это сделала бы игра при ручной постройке.
 */
final class Vox {
    final int x0, y0, z0, w, h, d;
    private final short[] g;
    private final List<String> ids = new ArrayList<>();
    private final Map<String, Short> index = new HashMap<>();

    Vox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.x0 = minX;
        this.y0 = minY;
        this.z0 = minZ;
        this.w = maxX - minX + 1;
        this.h = maxY - minY + 1;
        this.d = maxZ - minZ + 1;
        this.g = new short[this.w * this.h * this.d];
        this.ids.add(null);
    }

    boolean in(int x, int y, int z) {
        return x >= x0 && y >= y0 && z >= z0 && x < x0 + w && y < y0 + h && z < z0 + d;
    }

    private int i(int x, int y, int z) {
        return ((x - x0) * h + (y - y0)) * d + (z - z0);
    }

    private short id(String s) {
        if (s == null || s.equals("air")) {
            return 0;
        }
        Short v = index.get(s);
        if (v == null) {
            ids.add(s);
            v = (short) (ids.size() - 1);
            index.put(s, v);
        }
        return v;
    }

    void set(int x, int y, int z, String s) {
        if (in(x, y, z)) {
            g[i(x, y, z)] = id(s);
        }
    }

    /** Ставит блок, только если там воздух. */
    void soft(int x, int y, int z, String s) {
        if (in(x, y, z) && g[i(x, y, z)] == 0) {
            g[i(x, y, z)] = id(s);
        }
    }

    String get(int x, int y, int z) {
        return in(x, y, z) ? ids.get(g[i(x, y, z)]) : null;
    }

    boolean air(int x, int y, int z) {
        return get(x, y, z) == null;
    }

    void fill(String s, int x1, int y1, int z1, int x2, int y2, int z2) {
        short v = id(s);
        for (int x = Math.max(Math.min(x1, x2), x0); x <= Math.min(Math.max(x1, x2), x0 + w - 1); x++) {
            for (int y = Math.max(Math.min(y1, y2), y0); y <= Math.min(Math.max(y1, y2), y0 + h - 1); y++) {
                int base = ((x - x0) * h + (y - y0)) * d;
                for (int z = Math.max(Math.min(z1, z2), z0); z <= Math.min(Math.max(z1, z2), z0 + d - 1); z++) {
                    g[base + z - z0] = v;
                }
            }
        }
    }

    /** Полая коробка-периметр (стены без пола и потолка). */
    void walls(String s, int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(s, x1, y1, z1, x2, y2, z1);
        fill(s, x1, y1, z2, x2, y2, z2);
        fill(s, x1, y1, z1, x1, y2, z2);
        fill(s, x2, y1, z1, x2, y2, z2);
    }

    void disc(String s, int y, double cx, double cz, double r) {
        for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++) {
            for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++) {
                double dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= r * r + 0.5) {
                    set(x, y, z, s);
                }
            }
        }
    }

    void ring(String s, int y, double cx, double cz, double rOut, double rIn) {
        for (int x = (int) Math.floor(cx - rOut); x <= (int) Math.ceil(cx + rOut); x++) {
            for (int z = (int) Math.floor(cz - rOut); z <= (int) Math.ceil(cz + rOut); z++) {
                double dx = x - cx, dz = z - cz, q = dx * dx + dz * dz;
                if (q <= rOut * rOut + 0.5 && q > rIn * rIn + 0.5) {
                    set(x, y, z, s);
                }
            }
        }
    }

    // ------------------------------------------------------------------ строки состояний

    static String base(String s) {
        if (s == null) {
            return null;
        }
        int b = s.indexOf('[');
        String r = b < 0 ? s : s.substring(0, b);
        return r.startsWith("minecraft:") ? r.substring(10) : r;
    }

    static Map<String, String> props(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        int b = s.indexOf('[');
        if (b >= 0) {
            for (String kv : s.substring(b + 1, s.length() - 1).split(",")) {
                int e = kv.indexOf('=');
                if (e > 0) {
                    m.put(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
                }
            }
        }
        return m;
    }

    static String with(String base, Map<String, String> p) {
        if (p.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    private static final String[] NON_FULL = {
        "stairs", "slab", "fence", "pane", "iron_bars", "_wall", "lantern", "chain", "torch", "carpet", "door", "sign", "button",
        "pressure_plate", "water", "lava", "campfire", "_bed", "cauldron", "hopper", "anvil", "bell", "rail", "pot", "candle", "sapling",
        "vine", "ladder", "banner", "head", "skull", "chest", "lectern", "grindstone", "end_rod", "lightning_rod", "azalea", "fern",
        "bush", "roots", "dripleaf", "rose", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet", "daisy", "cornflower",
        "lily", "sunflower", "lilac", "peony", "berry", "sugar_cane", "bamboo", "cactus", "scaffolding", "cobweb", "snow",
        "pointed_dripstone", "cluster", "pickle", "coral", "kelp", "seagrass", "short_grass", "tall_grass", "brewing_stand",
        "enchanting_table", "stonecutter", "composter", "flower", "petals", "wheat", "carrots", "potatoes", "beetroots", "melon_stem",
        "pumpkin_stem", "dirt_path", "farmland", "tripwire", "lever", "repeater", "comparator", "conduit", "heavy_core", "decorated_pot"
    };

    static boolean full(String s) {
        if (s == null) {
            return false;
        }
        if (s.startsWith("pal:")) {
            return true;
        }
        String b = base(s);
        if (b.endsWith("leaves") || b.equals("sea_lantern") || b.equals("jack_o_lantern") || b.equals("snow_block")) {
            return true;
        }
        for (String n : NON_FULL) {
            if (b.contains(n)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFence(String b) {
        return b != null && b.endsWith("_fence");
    }

    private static boolean isGate(String b) {
        return b != null && b.endsWith("_fence_gate");
    }

    private static boolean isPane(String b) {
        return b != null && (b.endsWith("_pane") || b.equals("iron_bars"));
    }

    private static boolean isWall(String b) {
        return b != null && b.endsWith("_wall") && !b.contains("sign") && !b.contains("torch") && !b.contains("banner")
            && !b.contains("head") && !b.contains("skull") && !b.contains("fan");
    }

    private static boolean isStairs(String b) {
        return b != null && b.endsWith("_stairs");
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    private static int dirIndex(String f) {
        for (int k = 0; k < 4; k++) {
            if (DIRS[k].equals(f)) {
                return k;
            }
        }
        return 0;
    }

    /** Проставляет соединения и формы ступенек по соседям. */
    void finish() {
        String[] baseOf = new String[ids.size()];
        boolean[] fullOf = new boolean[ids.size()];
        for (int k = 1; k < ids.size(); k++) {
            baseOf[k] = ids.get(k).startsWith("pal:") ? "pal" : base(ids.get(k));
            fullOf[k] = full(ids.get(k));
        }
        int n0 = ids.size();
        for (int x = x0; x < x0 + w; x++) {
            for (int y = y0; y < y0 + h; y++) {
                for (int z = z0; z < z0 + d; z++) {
                    short v = g[i(x, y, z)];
                    if (v == 0 || v >= n0) {
                        continue;
                    }
                    String b = baseOf[v];
                    boolean fence = isFence(b), pane = isPane(b), wall = isWall(b), stairs = isStairs(b);
                    if (!fence && !pane && !wall && !stairs) {
                        continue;
                    }
                    String s = ids.get(v);
                    Map<String, String> p = props(s);
                    if (stairs) {
                        p.put("shape", stairShape(x, y, z, p.getOrDefault("facing", "north"), p.getOrDefault("half", "bottom"), baseOf, n0));
                        g[i(x, y, z)] = id(with(b, p));
                        continue;
                    }
                    boolean[] c = new boolean[4];
                    for (int k = 0; k < 4; k++) {
                        short nv = in(x + DX[k], y, z + DZ[k]) ? g[i(x + DX[k], y, z + DZ[k])] : 0;
                        if (nv == 0) {
                            continue;
                        }
                        String nb = nv < n0 ? baseOf[nv] : base(ids.get(nv));
                        boolean nf = nv < n0 ? fullOf[nv] : full(ids.get(nv));
                        if (fence) {
                            c[k] = nf || isFence(nb) || isGate(nb);
                        } else if (pane) {
                            c[k] = nf || isPane(nb) || isWall(nb);
                        } else {
                            c[k] = nf || isWall(nb) || isGate(nb) || isPane(nb);
                        }
                    }
                    p.clear();
                    if (wall) {
                        String above = get(x, y + 1, z);
                        boolean tall = full(above);
                        boolean straight = (c[0] && c[2] && !c[1] && !c[3]) || (c[1] && c[3] && !c[0] && !c[2]);
                        for (int k = 0; k < 4; k++) {
                            p.put(DIRS[k], c[k] ? (tall ? "tall" : "low") : "none");
                        }
                        p.put("up", String.valueOf(!straight || (above != null && !tall)));
                    } else {
                        for (int k = 0; k < 4; k++) {
                            p.put(DIRS[k], String.valueOf(c[k]));
                        }
                    }
                    g[i(x, y, z)] = id(with(b, p));
                }
            }
        }
    }

    private String stairAt(int x, int y, int z) {
        String s = get(x, y, z);
        return s != null && isStairs(base(s)) ? s : null;
    }

    private String stairShape(int x, int y, int z, String facing, String half, String[] baseOf, int n0) {
        int f = dirIndex(facing);
        int ccw = (f + 3) % 4;
        String behind = stairAt(x + DX[f], y, z + DZ[f]);
        if (behind != null) {
            Map<String, String> bp = props(behind);
            if (half.equals(bp.getOrDefault("half", "bottom"))) {
                int d2 = dirIndex(bp.getOrDefault("facing", "north"));
                if (d2 % 2 != f % 2 && canTake(x, y, z, (d2 + 2) % 4, f, half)) {
                    return d2 == ccw ? "outer_left" : "outer_right";
                }
            }
        }
        int o = (f + 2) % 4;
        String front = stairAt(x + DX[o], y, z + DZ[o]);
        if (front != null) {
            Map<String, String> fp = props(front);
            if (half.equals(fp.getOrDefault("half", "bottom"))) {
                int d3 = dirIndex(fp.getOrDefault("facing", "north"));
                if (d3 % 2 != f % 2 && canTake(x, y, z, d3, f, half)) {
                    return d3 == ccw ? "inner_left" : "inner_right";
                }
            }
        }
        return "straight";
    }

    private boolean canTake(int x, int y, int z, int dir, int f, String half) {
        String s = stairAt(x + DX[dir], y, z + DZ[dir]);
        if (s == null) {
            return true;
        }
        Map<String, String> p = props(s);
        return dirIndex(p.getOrDefault("facing", "north")) != f || !half.equals(p.getOrDefault("half", "bottom"));
    }

    // ------------------------------------------------------------------ выгрузка

    interface Sink {
        void run(String data, int x, int y, int z1, int z2);
    }

    /** Выгружает непустые блоки отрезками вдоль Z. */
    void emit(Sink sink) {
        for (int x = x0; x < x0 + w; x++) {
            for (int y = y0; y < y0 + h; y++) {
                int base = ((x - x0) * h + (y - y0)) * d;
                int z = 0;
                while (z < d) {
                    short v = g[base + z];
                    if (v == 0) {
                        z++;
                        continue;
                    }
                    int s = z;
                    while (z + 1 < d && g[base + z + 1] == v) {
                        z++;
                    }
                    sink.run(ids.get(v), x, y, z0 + s, z0 + z);
                    z++;
                }
            }
        }
    }

    // ------------------------------------------------------------------ шум

    static int hash(int x, int y, int z, int seed) {
        int hsh = x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ seed * 668265263;
        hsh ^= hsh >>> 13;
        hsh *= 0x5bd1e995;
        return (hsh ^ hsh >>> 15) & 0x7fffffff;
    }

    static double rnd(int x, int y, int z, int seed) {
        return hash(x, y, z, seed) / (double) 0x7fffffff;
    }

    static double noise(double x, double z, int seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        double u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
        double a = rnd(xi, 0, zi, seed), b = rnd(xi + 1, 0, zi, seed), c = rnd(xi, 0, zi + 1, seed), e = rnd(xi + 1, 0, zi + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + e) * u * v;
    }

    static double fbm(double x, double z, int seed, int oct) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int k = 0; k < oct; k++) {
            sum += noise(x * f, z * f, seed + k * 31) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }
}
