package dev.prisonclassic.data;

import dev.prisonclassic.PrisonClassic;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * MySQL-хранилище. Драйвер com.mysql.cj.jdbc.Driver входит в Paper/Spigot.
 * Таблица prison_players: индексные колонки (для топов/аналитики) + полный снимок в data (YAML-текст).
 * Таблица prison_dungeon_records: глобальные рекорды тайм-аттака катакомб.
 */
public final class MySqlStorage implements Storage {
    private final PrisonClassic plugin;
    private final String url;
    private final String user;
    private final String password;
    private final String table;
    private Connection connection;

    public MySqlStorage(PrisonClassic plugin, ConfigurationSection c) throws SQLException {
        this.plugin = plugin;
        this.url = "jdbc:mysql://" + c.getString("host", "localhost") + ":" + c.getInt("port", 3306) + "/" + c.getString("database", "prison")
            + "?useSSL=" + c.getBoolean("ssl", false) + "&autoReconnect=true&characterEncoding=utf8&useUnicode=true";
        this.user = c.getString("user", "root");
        this.password = c.getString("password", "");
        this.table = c.getString("table-prefix", "prison_");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL-драйвер не найден на сервере");
        }
        try (Statement st = this.conn().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + this.table + "players ("
                + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                + "name VARCHAR(16) NOT NULL,"
                + "level INT NOT NULL,"
                + "balance DOUBLE NOT NULL,"
                + "stars BIGINT NOT NULL,"
                + "blocks BIGINT NOT NULL,"
                + "kills INT NOT NULL,"
                + "faction VARCHAR(32) NULL,"
                + "gang VARCHAR(32) NULL,"
                + "data MEDIUMTEXT NOT NULL,"
                + "updated BIGINT NOT NULL,"
                + "INDEX idx_level (level), INDEX idx_blocks (blocks)) CHARACTER SET utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + this.table + "dungeon_records ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "party VARCHAR(128) NOT NULL,"
                + "ms BIGINT NOT NULL,"
                + "at BIGINT NOT NULL,"
                + "INDEX idx_ms (ms)) CHARACTER SET utf8mb4");
        }
    }

    private synchronized Connection conn() throws SQLException {
        if (this.connection == null || this.connection.isClosed() || !this.connection.isValid(2)) {
            this.connection = DriverManager.getConnection(this.url, this.user, this.password);
        }
        return this.connection;
    }

    @Override
    public synchronized YamlConfiguration load(UUID uuid) {
        try (PreparedStatement ps = this.conn().prepareStatement("SELECT data FROM " + this.table + "players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    YamlConfiguration y = new YamlConfiguration();
                    y.loadFromString(rs.getString(1));
                    return y;
                }
            }
        } catch (SQLException | InvalidConfigurationException e) {
            this.plugin.getLogger().warning("MySQL load " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public synchronized void save(UUID uuid, YamlConfiguration snapshot, PlayerData d) {
        String sql = "INSERT INTO " + this.table + "players (uuid,name,level,balance,stars,blocks,kills,faction,gang,data,updated) VALUES (?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE name=VALUES(name),level=VALUES(level),balance=VALUES(balance),stars=VALUES(stars),blocks=VALUES(blocks),"
            + "kills=VALUES(kills),faction=VALUES(faction),gang=VALUES(gang),data=VALUES(data),updated=VALUES(updated)";
        try (PreparedStatement ps = this.conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, snapshot.getString("name", "?"));
            ps.setInt(3, snapshot.getInt("level", 1));
            ps.setDouble(4, snapshot.getDouble("balance", 0.0));
            ps.setLong(5, snapshot.getLong("stars", 0L));
            ps.setLong(6, snapshot.getLong("blocks", 0L));
            ps.setInt(7, snapshot.getInt("kills", 0));
            ps.setString(8, snapshot.getString("faction"));
            ps.setString(9, snapshot.getString("gang"));
            ps.setString(10, snapshot.saveToString());
            ps.setLong(11, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().warning("MySQL save " + uuid + ": " + e.getMessage());
        }
    }

    @Override
    public synchronized List<PlayerData> loadAll() {
        List<PlayerData> out = new ArrayList<>();
        try (Statement st = this.conn().createStatement(); ResultSet rs = st.executeQuery("SELECT uuid,data FROM " + this.table + "players")) {
            while (rs.next()) {
                try {
                    YamlConfiguration y = new YamlConfiguration();
                    y.loadFromString(rs.getString(2));
                    out.add(PlayerData.read(UUID.fromString(rs.getString(1)), y));
                } catch (InvalidConfigurationException | IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("MySQL loadAll: " + e.getMessage());
        }
        return out;
    }

    @Override
    public synchronized void saveDungeonRecord(String party, long millis, long when) {
        try (PreparedStatement ps = this.conn().prepareStatement("INSERT INTO " + this.table + "dungeon_records (party,ms,at) VALUES (?,?,?)")) {
            ps.setString(1, party.length() > 128 ? party.substring(0, 128) : party);
            ps.setLong(2, millis);
            ps.setLong(3, when);
            ps.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().warning("MySQL dungeon record: " + e.getMessage());
        }
    }

    @Override
    public synchronized List<String> topDungeon(int limit) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = this.conn().prepareStatement("SELECT party,ms,at FROM " + this.table + "dungeon_records ORDER BY ms ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1) + "|" + rs.getLong(2) + "|" + rs.getLong(3));
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("MySQL dungeon top: " + e.getMessage());
        }
        return out;
    }

    @Override
    public String name() {
        return "MySQL";
    }

    @Override
    public synchronized void close() {
        try {
            if (this.connection != null) {
                this.connection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
