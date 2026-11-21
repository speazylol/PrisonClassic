package dev.prisonclassic;

import dev.prisonclassic.auction.AuctionService;
import dev.prisonclassic.awaken.AwakenService;
import dev.prisonclassic.booster.BoosterService;
import dev.prisonclassic.boss.BossManager;
import dev.prisonclassic.build.WorldBuilder;
import dev.prisonclassic.cell.CellManager;
import dev.prisonclassic.basement.BasementService;
import dev.prisonclassic.merchant.MerchantService;
import dev.prisonclassic.game.TicTacToeService;
import dev.prisonclassic.family.FamilyWarService;
import dev.prisonclassic.dungeon.DungeonService;
import dev.prisonclassic.boss.BossFightService;
import dev.prisonclassic.gypsy.GypsyService;
import dev.prisonclassic.cell.CellService;
import dev.prisonclassic.collection.CollectionService;
import dev.prisonclassic.items.SpecialItems;
import dev.prisonclassic.church.ChurchService;
import dev.prisonclassic.command.AdminCommand;
import dev.prisonclassic.command.ExtraCommands;
import dev.prisonclassic.command.PlayerCommands;
import dev.prisonclassic.command.VimeCommands;
import dev.prisonclassic.crate.CrateService;
import dev.prisonclassic.data.DataManager;
import dev.prisonclassic.data.PlayerData;
import dev.prisonclassic.donate.DonateService;
import dev.prisonclassic.enchant.EnchantListener;
import dev.prisonclassic.enchant.EnchantService;
import dev.prisonclassic.faction.FactionBalanceService;
import dev.prisonclassic.faction.FactionManager;
import dev.prisonclassic.faction.PointService;
import dev.prisonclassic.family.FamilyService;
import dev.prisonclassic.gang.GangService;
import dev.prisonclassic.level.LevelManager;
import dev.prisonclassic.listener.BossListener;
import dev.prisonclassic.listener.GeneralListener;
import dev.prisonclassic.listener.WorldListener;
import dev.prisonclassic.mine.MineManager;
import dev.prisonclassic.mine.MiningService;
import dev.prisonclassic.needs.NeedsService;
import dev.prisonclassic.npc.NpcService;
import dev.prisonclassic.pmine.PrivateMineService;
import dev.prisonclassic.quest.QuestManager;
import dev.prisonclassic.sell.SellService;
import dev.prisonclassic.shop.ShopService;
import dev.prisonclassic.six.SixService;
import dev.prisonclassic.trainer.TrainerService;
import dev.prisonclassic.treasure.TreasureService;
import dev.prisonclassic.ui.Gui;
import dev.prisonclassic.ui.MenuService;
import dev.prisonclassic.ui.ScoreboardService;
import dev.prisonclassic.upgrade.UpgradeService;
import dev.prisonclassic.util.LocationStore;
import dev.prisonclassic.util.Msg;
import dev.prisonclassic.util.Selection;
import java.io.File;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrisonClassic extends JavaPlugin {
    private LocationStore locations;
    private Selection selection;
    private DataManager data;
    private LevelManager levels;
    private FactionManager factions;
    private SellService sell;
    private QuestManager quests;
    private MineManager mines;
    private CellManager cells;
    private BossManager bosses;
    private ScoreboardService board;
    private EnchantService enchants;
    private MiningService mining;
    private TrainerService trainer;
    private DonateService donate;
    private NpcService npc;
    private MenuService menus;
    private UpgradeService upgrades;
    private NeedsService needs;
    private ChurchService church;
    private ShopService shop;
    private TreasureService treasures;
    private CrateService crates;
    private BoosterService boosters;
    private PointService points;
    private AuctionService auction;
    private PrivateMineService privateMines;
    private GangService gangs;
    private FamilyService families;
    private SixService sixes;
    private AwakenService awaken;
    private FactionBalanceService factionBalance;
    private BasementService basement;
    private MerchantService merchants;
    private TicTacToeService ttt;
    private FamilyWarService familyWar;
    private DungeonService dungeon;
    private BossFightService bossFights;
    private GypsyService gypsy;
    private CellService cellService;
    private CollectionService collections;
    private SpecialItems specials;

    @Override
    public void onEnable() {
        this.migrateConfig();
        this.saveDefaultConfig();
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
        Msg.init(this.getConfig());
        this.loadBuildWorld();
        this.locations = new LocationStore(this);
        this.selection = new Selection(this);
        this.data = new DataManager(this);
        this.donate = new DonateService(this);
        this.specials = new SpecialItems(this);
        this.enchants = new EnchantService(this);
        this.gangs = new GangService(this);
        this.families = new FamilyService(this);
        this.awaken = new AwakenService(this);
        this.upgrades = new UpgradeService(this);
        this.gypsy = new GypsyService(this);
        this.treasures = new TreasureService(this);
        this.boosters = new BoosterService(this);
        this.needs = new NeedsService(this);
        this.church = new ChurchService(this);
        this.shop = new ShopService(this);
        this.crates = new CrateService(this);
        this.auction = new AuctionService(this);
        this.sixes = new SixService(this);
        this.mining = new MiningService(this);
        this.trainer = new TrainerService(this);
        this.levels = new LevelManager(this);
        this.factions = new FactionManager(this);
        this.factionBalance = new FactionBalanceService(this);
        this.sell = new SellService(this);
        this.merchants = new MerchantService(this);
        this.quests = new QuestManager(this);
        this.mines = new MineManager(this);
        this.cells = new CellManager(this);
        this.basement = new BasementService(this);
        this.cellService = new CellService(this);
        this.collections = new CollectionService(this);
        this.bosses = new BossManager(this);
        this.dungeon = new DungeonService(this);
        this.bossFights = new BossFightService(this);
        this.points = new PointService(this);
        this.ttt = new TicTacToeService(this);
        this.familyWar = new FamilyWarService(this);
        this.privateMines = new PrivateMineService(this);
        this.board = new ScoreboardService(this);
        PluginManager var1 = this.getServer().getPluginManager();
        var1.registerEvents(new GeneralListener(this), this);
        var1.registerEvents(this.donate, this);
        var1.registerEvents(new WorldListener(this), this);
        var1.registerEvents(new BossListener(this), this);
        var1.registerEvents(new EnchantListener(this, this.enchants), this);
        var1.registerEvents(this.trainer, this);
        var1.registerEvents(new Gui.GuiListener(), this);

        for (Listener var3 : List.of(
            this.upgrades,
            this.treasures,
            this.boosters,
            this.needs,
            this.church,
            this.crates,
            this.auction,
            this.privateMines,
            this.gangs,
            this.families,
            this.awaken,
            this.collections,
            this.cellService,
            this.bossFights,
            this.dungeon,
            this.ttt,
            this.merchants /*L*/
        )) {
            var1.registerEvents(var3, this);
        }

        this.menus = new MenuService(this);
        var1.registerEvents(this.menus, this);
        this.npc = new NpcService(this);
        var1.registerEvents(this.npc, this);
        ExtraCommands var8 = new ExtraCommands(this);
        var1.registerEvents(var8, this);

        for (String var4 : List.of("trainer", "ec", "top", "keys", "exchange", "active")) {
            this.bind(var4, var8);
        }

        PlayerCommands var10 = new PlayerCommands(this);

        for (String var5 : List.of("spawn", "mine", "level", "rankup", "sell", "autosell", "balance", "pay", "cell", "faction", "fleave", "base", "quest")) {
            this.bind(var5, var10);
        }

        this.bind("prisonadmin", new AdminCommand(this));
        dev.prisonclassic.command.SpecCommands spec = new dev.prisonclassic.command.SpecCommands(this);

        for (String name : List.of("dungeon", "fw", "ttt", "collection", "donate")) {
            this.bind(name, spec);
        }
        VimeCommands var12 = new VimeCommands(this);

        for (String var6 : List.of(
            "up", "shop", "auc", "pmine", "church", "needs", "points", "gang", "g", "family", "six", "sieve", "awaken", "lufonia", "warp"
        )) {
            this.bind(var6, var12);
        }

        this.data.startAutosave();
        this.mines.start();
        this.bosses.start();
        this.board.start();

        for (Player var15 : Bukkit.getOnlinePlayers()) {
            PlayerData var7 = this.data.load(var15.getUniqueId(), var15.getName());
            this.quests.ensureDaily(var7);
            this.levels.applyBonuses(var15, var7);
            this.board.add(var15);
        }

        this.autoBuild();
        Bukkit.getScheduler().runTaskLater(this, this.npc::refresh, 80L);
        this.getLogger().info("PrisonClassic включён.");
    }

    @Override
    public void onDisable() {
        if (this.dungeon != null) {
            this.dungeon.shutdown();
        }

        if (this.ttt != null) {
            this.ttt.shutdown();
        }

        if (this.familyWar != null) {
            this.familyWar.shutdown();
        }

        if (this.privateMines != null) {
            this.privateMines.shutdown();
        }

        if (this.points != null) {
            this.points.shutdown();
        }

        if (this.factionBalance != null) {
            this.factionBalance.save();
        }

        if (this.auction != null) {
            this.auction.save();
        }

        if (this.gangs != null) {
            this.gangs.save();
        }

        if (this.bosses != null) {
            this.bosses.removeAll();
        }

        if (this.board != null) {
            this.board.shutdown();
        }

        if (this.mines != null) {
            this.mines.save();
        }

        if (this.cells != null) {
            this.cells.save();
        }

        if (this.data != null) {
            this.data.saveAll(false);
            this.data.close();
        }
    }

    /** Старый конфиг (3.x) не совместим с реестрами ТЗ: сохраняем копию и ставим новый. */
    private void migrateConfig() {
        File cfg = new File(this.getDataFolder(), "config.yml");
        if (!cfg.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration old = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfg);
        if (old.getInt("config-version", 0) >= 4) {
            return;
        }
        File backup = new File(this.getDataFolder(), "config-old.yml");
        if (cfg.renameTo(backup)) {
            this.getLogger().warning("Найден конфиг старой версии — сохранён как config-old.yml, создан новый config.yml (ТЗ 4.0).");
            this.reloadConfig();
        }
    }

    private void loadBuildWorld() {
        String var1 = this.getConfig().getString("build.world", "prison_classic");
        if (Bukkit.getWorld(var1) == null && new File(Bukkit.getWorldContainer(), var1).isDirectory()) {
            org.bukkit.World nw = new WorldCreator(var1).generator(new ChunkGenerator() {}).createWorld();
            if (nw != null) nw.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            this.getLogger().info("Загружен мир " + var1);
        }
    }

    private void autoBuild() {
        if (this.getConfig().getBoolean("build.auto", true) && this.spawn() == null) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (this.spawn() == null && !WorldBuilder.isBusy()) {
                    String var1 = this.getConfig().getString("build.world", "prison_classic");
                    World var2 = Bukkit.getWorld(var1);
                    if (var2 == null) {
                        var2 = new WorldCreator(var1).generator(new ChunkGenerator() {}).createWorld();
                        if (var2 != null) var2.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                    }

                    if (var2 == null) {
                        this.getLogger().severe("Не удалось создать мир " + var1);
                    } else {
                        Location var3 = new Location(var2, 0.0, this.getConfig().getInt("build.y", 64), 0.0);
                        WorldBuilder var4 = new WorldBuilder(this, var3);
                        if (!var4.heightOk()) {
                            this.getLogger().severe("Автостройка: не хватает высоты мира (build.y).");
                        } else {
                            this.getLogger().info("Автостройка: строю мир " + var1 + " ...");
                            var4.start(Bukkit.getConsoleSender());
                        }
                    }
                }
            }, 40L);
        }
    }

    private void bind(String var1, TabExecutor var2) {
        PluginCommand var3 = this.getCommand(var1);
        if (var3 == null) {
            this.getLogger().warning("Команда не найдена в plugin.yml: " + var1);
        } else {
            var3.setExecutor(var2);
            var3.setTabCompleter(var2);
        }
    }

    public void reloadAll() {
        this.reloadConfig();
        Msg.init(this.getConfig());
        this.levels.reload();
        this.factions.reload();
        this.sell.reload();
        this.quests.reload();
        this.bosses.reloadTypes();
        this.board.reload();
        this.upgrades.reload();
        this.needs.reload();
        this.points.reload();
        this.points.refreshHolograms();
        this.familyWar.reload();
        this.familyWar.refreshHolograms();
    }

    public boolean canPvp(Player var1, Player var2) {
        if (var1 != null && !var1.equals(var2)) {
            Location var3 = this.spawn();
            double var4 = this.getConfig().getDouble("spawn.protect-radius", 30.0);
            if (var3 != null && var3.getWorld() == var2.getWorld() && var3.distanceSquared(var2.getLocation()) <= var4 * var4) {
                return false;
            } else {
                PlayerData var6 = this.data.get(var1);
                PlayerData var7 = this.data.get(var2);
                if (var6 == null || var7 == null) {
                    return true;
                } else {
                    return !this.gangs.sameGang(var6, var7) && !this.families.married(var6, var7)
                        ? var6.faction == null || !var6.faction.equals(var7.faction)
                        : false;
                }
            }
        } else {
            return true;
        }
    }

    public Location spawn() {
        return this.locations.get("spawn");
    }

    public LocationStore locations() {
        return this.locations;
    }

    public Selection selection() {
        return this.selection;
    }

    public DataManager data() {
        return this.data;
    }

    public LevelManager levels() {
        return this.levels;
    }

    public FactionManager factions() {
        return this.factions;
    }

    public SellService sell() {
        return this.sell;
    }

    public QuestManager quests() {
        return this.quests;
    }

    public MineManager mines() {
        return this.mines;
    }

    public CellManager cells() {
        return this.cells;
    }

    public BossManager bosses() {
        return this.bosses;
    }

    public ScoreboardService board() {
        return this.board;
    }

    public EnchantService enchants() {
        return this.enchants;
    }

    public MiningService mining() {
        return this.mining;
    }

    public TrainerService trainer() {
        return this.trainer;
    }

    public DonateService donate() {
        return this.donate;
    }

    public NpcService npc() {
        return this.npc;
    }

    public MenuService menus() {
        return this.menus;
    }

    public UpgradeService upgrades() {
        return this.upgrades;
    }

    public NeedsService needs() {
        return this.needs;
    }

    public ChurchService church() {
        return this.church;
    }

    public ShopService shop() {
        return this.shop;
    }

    public TreasureService treasures() {
        return this.treasures;
    }

    public CrateService crates() {
        return this.crates;
    }

    public BoosterService boosters() {
        return this.boosters;
    }

    public PointService points() {
        return this.points;
    }

    public AuctionService auction() {
        return this.auction;
    }

    public PrivateMineService privateMines() {
        return this.privateMines;
    }

    public GangService gangs() {
        return this.gangs;
    }

    public FamilyService families() {
        return this.families;
    }

    public SixService sixes() {
        return this.sixes;
    }

    public AwakenService awaken() {
        return this.awaken;
    }

    public SpecialItems specials() {
        return this.specials;
    }

    public CollectionService collections() {
        return this.collections;
    }

    public CellService cellService() {
        return this.cellService;
    }

    public GypsyService gypsy() {
        return this.gypsy;
    }

    public BossFightService bossFights() {
        return this.bossFights;
    }

    public DungeonService dungeon() {
        return this.dungeon;
    }

    public FamilyWarService familyWar() {
        return this.familyWar;
    }

    public TicTacToeService ttt() {
        return this.ttt;
    }

    public MerchantService merchants() {
        return this.merchants;
    }

    public BasementService basement() {
        return this.basement;
    }

    public FactionBalanceService factionBalance() {
        return this.factionBalance;
    }
}
