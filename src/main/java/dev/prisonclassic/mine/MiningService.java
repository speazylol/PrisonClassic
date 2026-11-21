package dev.prisonclassic.mine;

import dev.prisonclassic.PrisonClassic;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.enchant.Ench;
import dev.prisonclassic.enchant.EnchantService;
import dev.prisonclassic.quest.QuestType;
import dev.prisonclassic.util.Msg;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Добыча в шахтах. Каждый блок:
 *  - дроп: руда без «Шёлка» → переработанный предмет, с «Шёлком» → руда целиком (дороже);
 *  - навык «Удача» (тренер + душ): шанс x2 дропа редких руд;
 *  - чары кирки: Бустер монет/блоков, Спешка, Бум (сферический подрыв, монеты и блоки зачисляются мгновенно);
 *  - потиповой пул блоков PlayerData.blockTypes (требования тренера), паутина для луков;
 *  - сокровища шахты, осколки коллекций, пропуска (подземелье на Стройке, боевой пропуск на шахте 20), ключи.
 */
public final class MiningService {
    private final PrisonClassic plugin;

    public MiningService(PrisonClassic plugin) {
        this.plugin = plugin;
    }

    public static boolean silk(ItemStack tool) {
        return tool != null && tool.getType().name().endsWith("_PICKAXE") && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
    }

    public void onMine(BlockBreakEvent event, Player p, PlayerData d, Mine mine) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        FileConfiguration c = this.plugin.getConfig();
        EnchantService ench = this.plugin.enchants();
        Block block = event.getBlock();
        ItemStack tool = p.getInventory().getItemInMainHand();
        event.setCancelled(true);
        Material type = block.getType();
        boolean silk = silk(tool);
        Material drop = MineDefs.dropFor(type, silk);
        block.setType(Material.AIR, false);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 0.6F, 1.0F);

        int amount = drop == null ? 0 : 1;
        long blocks = 1L;
        double procChance = c.getDouble("enchants.proc-chance", 0.12);
        double procFactor = c.getDouble("enchants.proc-factor", 8.0);
        double[] boost = {0.0, 0.25, 0.45, 1.0};

        int coin = ench.level(tool, Ench.COIN);
        if (coin > 0 && amount > 0 && r.nextDouble() < procChance) {
            amount += (int)Math.round(boost[coin] * procFactor);
        }
        int blk = ench.level(tool, Ench.BLOCKS);
        if (blk > 0 && r.nextDouble() < procChance) {
            blocks += Math.round(boost[blk] * procFactor);
        }
        if (amount > 0 && r.nextDouble() < this.plugin.awaken().toolExtra(tool)) {
            amount++;
        }
        if (ench.level(tool, Ench.GRAVE) > 0 && amount > 0 && r.nextDouble() < 0.05) {
            amount += 4;
        }
        // Навык «Удача»: шанс повышенного (x2) дропа редких руд
        if (amount > 0 && MineDefs.isRare(type) && r.nextDouble() < this.plugin.trainer().luckChance(d)) {
            amount *= 2;
            p.sendActionBar(Msg.parse("<green>Удача! <gray>Двойной дроп: <lang:" + drop.translationKey() + ">"));
        }
        int haste = ench.level(tool, Ench.HASTE);
        if (haste > 0 && r.nextDouble() < 0.04) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, (haste == 1 ? 14 : 23) * 20, 1, true, false, true));
        }

        EnumMap<Material, Integer> drops = new EnumMap<>(Material.class);
        int broken = 1;
        d.blockTypes.merge(type.name(), 1L, Long::sum);

        int boom = ench.level(tool, Ench.BOOM);
        if (boom > 0) {
            double[] chance = {0.0, c.getDouble("enchants.boom.chance1", 0.015), c.getDouble("enchants.boom.chance2", 0.01), c.getDouble("enchants.boom.chance3", 0.008)};
            if (r.nextDouble() < chance[Math.min(3, boom)]) {
                Boom result = this.sphere(p, d, mine, block, boom, silk);
                blocks += result.blocks;
                broken += result.blocks;
                double earned = result.coins;
                if (earned > 0.0) {
                    d.balance += earned;
                    this.plugin.quests().progress(p, d, QuestType.SELL, earned);
                }
                block.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, block.getLocation().add(0.5, 0.5, 0.5), 1);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.3F);
                p.sendActionBar(Msg.parse("<gold>Бум " + EnchantService.roman(boom) + "! <white>+" + result.blocks + " блоков <green>+$" + Msg.money(earned)));
            }
        }

        if (drop != null && amount > 0) {
            drops.merge(drop, amount, Integer::sum);
        }
        if (type == Material.GRAVEL && r.nextDouble() < 0.1) {
            drops.merge(Material.FLINT, 1, Integer::sum);
        }
        if (type == Material.SNOW_BLOCK && r.nextDouble() < 0.25) {
            drops.merge(Material.SNOWBALL, 1, Integer::sum);
        }

        this.give(p, d, drops);
        this.rollKeys(p, d, ench.level(tool, Ench.KEYMASTER));
        this.plugin.treasures().roll(p, mine);
        this.plugin.collections().onMine(p, d, mine, type);
        this.rollPasses(p, mine);
        if (type == Material.COBWEB) {
            d.stats.merge("cobweb", 1L, Long::sum);
        }
        this.plugin.upgrades().addBlocks(tool, type, broken);
        long credited = this.stochastic(blocks * this.plugin.sell().blockMultiplier(d) * this.plugin.points().blockMultiplier(d, mine), r.nextDouble());
        this.addBlocks(p, d, credited);
        this.plugin.mines().registerBreak(mine, broken);
    }

    private record Boom(int blocks, double coins) {
    }

    /** «Бум»: сферический подрыв радиуса = уровень чара; дроп сразу продаётся, блоки — в статистику. */
    private Boom sphere(Player p, PlayerData d, Mine mine, Block center, int radius, boolean silk) {
        World w = center.getWorld();
        int count = 0;
        double coins = 0.0;
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > r2 || x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    Block b = w.getBlockAt(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!mine.region.contains(b)) {
                        continue;
                    }
                    Material t = b.getType();
                    if (t.isAir() || t == Material.BEDROCK) {
                        continue;
                    }
                    Material drop = MineDefs.dropFor(t, silk);
                    b.setType(Material.AIR, false);
                    count++;
                    d.blockTypes.merge(t.name(), 1L, Long::sum);
                    if (t == Material.COBWEB) {
                        d.stats.merge("cobweb", 1L, Long::sum);
                    }
                    if (drop != null) {
                        coins += this.plugin.sell().valueOf(new ItemStack(drop), d);
                    }
                }
            }
        }
        return new Boom(count, coins);
    }

    private void rollPasses(Player p, Mine mine) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        FileConfiguration c = this.plugin.getConfig();
        if (mine.name.equals(c.getString("dungeon.pass-mine", "17")) && r.nextDouble() < c.getDouble("dungeon.pass-chance", 0.00025)) {
            this.plugin.specials().give(p, "dungeon_pass", 1);
            p.sendMessage(Msg.parse("<dark_red><bold>Редкая находка!</bold> <gray>Пропуск в подземелье (Вампирские катакомбы)."));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.6F);
        }
        if (mine.name.equals(c.getString("bosses.evil-pumpkin.pass-mine", "20")) && r.nextDouble() < c.getDouble("bosses.evil-pumpkin.pass-chance-mine", 0.0002)) {
            this.plugin.specials().give(p, "battle_pass", 1);
            p.sendMessage(Msg.parse("<gold><bold>Редкая находка!</bold> <gray>Боевой пропуск на арену Злого Тыкова."));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.6F);
        }
    }

    private long stochastic(double v, double roll) {
        long base = (long)Math.floor(v);
        return base + (roll < v - base ? 1 : 0);
    }

    /** Выдать дроп: с флагом AutoSell_Premium (и включённым /autosell) — сразу в монеты, иначе в инвентарь. */
    public void give(Player p, PlayerData d, Map<Material, Integer> drops) {
        boolean auto = d.autosell && this.plugin.sell().premium(p, d);
        double earned = 0.0;
        boolean full = false;
        for (Entry<Material, Integer> e : drops.entrySet()) {
            ItemStack it = new ItemStack(e.getKey(), e.getValue());
            if (auto) {
                double v = this.plugin.sell().valueOf(it, d);
                if (v > 0.0) {
                    earned += v;
                    continue;
                }
            }
            for (ItemStack left : p.getInventory().addItem(it).values()) {
                full = true;
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
        if (earned > 0.0) {
            d.balance += earned;
            this.plugin.quests().progress(p, d, QuestType.SELL, earned);
        }
        if (full) {
            p.sendActionBar(Msg.msg("inventory-full"));
        }
    }

    private void rollKeys(Player p, PlayerData d, int keymaster) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        FileConfiguration c = this.plugin.getConfig();
        int perk = d.perk("keydrop");
        if (perk > 0 && r.nextDouble() < perk * c.getDouble("keys.perk-common-per-level", 4.0E-4)) {
            this.key(p, d, "common");
        }
        if (keymaster >= 1 && r.nextDouble() < c.getDouble("keys.keymaster-dk-" + Math.min(keymaster, 2), 4.0E-5)) {
            this.key(p, d, "dk");
        }
        if (keymaster >= 3 && r.nextDouble() < c.getDouble("keys.keymaster-legendary", 1.25E-5)) {
            this.key(p, d, "legendary");
        }
    }

    public void key(Player p, PlayerData d, String id) {
        d.addKey(id, 1);
        p.sendMessage(Msg.parse("<gold>Вы нашли ключ: <white>" + keyName(id) + "<gray> (/keys)"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.5F);
    }

    public static String keyName(String id) {
        return switch (id) {
            case "dk" -> "Древний ключ (ДК)";
            case "legendary" -> "Легендарный ключ";
            default -> "Обычный ключ";
        };
    }

    public void addBlocks(Player p, PlayerData d, long n) {
        long today = LocalDate.now().toEpochDay();
        if (d.blocksDay != today) {
            d.blocksDay = today;
            d.blocksToday = 0L;
        }
        d.blocksMined += n;
        d.blocksToday += n;
        this.plugin.quests().progress(p, d, QuestType.MINE, n);
        this.checkMilestones(p, d);
    }

    private void checkMilestones(Player p, PlayerData d) {
        ConfigurationSection ms = this.plugin.getConfig().getConfigurationSection("milestones");
        if (ms == null) {
            return;
        }
        for (String k : ms.getKeys(false)) {
            ConfigurationSection m = ms.getConfigurationSection(k);
            if (m == null) {
                continue;
            }
            long need = m.getLong("blocks");
            if (d.blocksMined >= need && !d.milestones.contains(k)) {
                d.milestones.add(k);
                double coins = m.getDouble("coins", 0.0);
                d.balance += coins;
                String key = m.getString("key", "");
                int keys = m.getInt("keys", 1);
                if (!key.isEmpty()) {
                    d.addKey(key, keys);
                }
                p.sendMessage(Msg.parse("<green>Квестовик: <white>" + need + " блоков добыто! <gray>Награда: <green>$" + Msg.money(coins)
                    + (key.isEmpty() ? "" : " <gold>+" + keys + " " + keyName(key))));
            }
        }
    }

    public String nextMilestone(PlayerData d) {
        ConfigurationSection ms = this.plugin.getConfig().getConfigurationSection("milestones");
        if (ms == null) {
            return null;
        }
        for (String k : ms.getKeys(false)) {
            if (d.milestones.contains(k)) {
                continue;
            }
            ConfigurationSection m = ms.getConfigurationSection(k);
            if (m != null) {
                return d.blocksMined + "/" + m.getLong("blocks") + " блоков → $" + Msg.money(m.getDouble("coins", 0.0))
                    + (m.getString("key", "").isEmpty() ? "" : " + " + keyName(m.getString("key")));
            }
        }
        return null;
    }
}
