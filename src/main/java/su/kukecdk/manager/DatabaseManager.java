package su.kukecdk.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import su.kukecdk.model.CDK;

import java.io.File;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private Connection connection;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final String storageMode;
    private final FileConfiguration config;

    public DatabaseManager(JavaPlugin plugin, FileConfiguration config, String storageMode) {
        this.plugin = plugin;
        this.config = config;
        this.storageMode = storageMode;
        initDatabase();
    }

    /**
     * 检查并维护数据库连接
     */
    private void checkConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                plugin.getLogger().info("数据库连接已断开，正在重新连接...");
                initDatabase();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("检查数据库连接时出错: " + e.getMessage());
            // 尝试重新初始化
            initDatabase();
        }
    }

    /**
     * 初始化数据库连接
     */
    private void initDatabase() {
        try {
            if ("sqlite".equalsIgnoreCase(storageMode)) {
                // SQLite连接
                File dataFolder = new File(plugin.getDataFolder(), "data.db");
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
            } else if ("mysql".equalsIgnoreCase(storageMode)) {
                // MySQL连接
                Class.forName("com.mysql.cj.jdbc.Driver");
                String host = config.getString("mysql.host", "localhost");
                int port = config.getInt("mysql.port", 3306);
                String database = config.getString("mysql.database", "kukecdk");
                String username = config.getString("mysql.username", "root");
                String password = config.getString("mysql.password", "password");
                // 添加 autoReconnect=true 这是一个旧参数，但在某些驱动版本有效。
                // 最重要的是代码层面的重连。
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&autoReconnect=true";
                connection = DriverManager.getConnection(url, username, password);
            }
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("初始化数据库时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建数据库表
     */
    private void createTables() {
        try {
            String tablePrefix = "mysql".equalsIgnoreCase(storageMode) ? config.getString("mysql.table_prefix", "kukecdk_") : "";
            
            // 根据数据库类型创建不同的SQL
            String createCDKTableSQL;
            String createRedeemedPlayersTableSQL;
            
            if ("mysql".equalsIgnoreCase(storageMode)) {
                // MySQL使用VARCHAR类型以支持PRIMARY KEY
                // 缩短VARCHAR长度以兼容utf8mb4编码下的索引限制 (767 bytes)
                createCDKTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "cdk (" +
                        "id VARCHAR(128) NOT NULL, " +
                        "name VARCHAR(128) NOT NULL, " +
                        "quantity INTEGER NOT NULL, " +
                        "single_use BOOLEAN NOT NULL, " +
                        "commands TEXT NOT NULL, " +
                        "expiration_date VARCHAR(64), " +
                        "PRIMARY KEY (name)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
                
                createRedeemedPlayersTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "redeemed_players (" +
                        "cdk_name VARCHAR(128) NOT NULL, " +
                        "player_name VARCHAR(60) NOT NULL, " +
                        "PRIMARY KEY (cdk_name, player_name)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            } else {
                // SQLite使用TEXT类型
                createCDKTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "cdk (" +
                        "id TEXT NOT NULL, " +
                        "name TEXT NOT NULL PRIMARY KEY, " +
                        "quantity INTEGER NOT NULL, " +
                        "single_use BOOLEAN NOT NULL, " +
                        "commands TEXT NOT NULL, " +
                        "expiration_date TEXT" +
                        ");";
                
                createRedeemedPlayersTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "redeemed_players (" +
                        "cdk_name TEXT NOT NULL, " +
                        "player_name TEXT NOT NULL, " +
                        "PRIMARY KEY (cdk_name, player_name)" +
                        ");";
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute(createCDKTableSQL);
                statement.execute(createRedeemedPlayersTableSQL);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据库表时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从数据库加载所有CDK
     *
     * @return 包含所有CDK的映射
     */
    public Map<String, Map<String, CDK>> loadCDKs() {
        checkConnection();
        Map<String, Map<String, CDK>> cdkMap = new HashMap<>();
        
        try {
            String tablePrefix = "mysql".equalsIgnoreCase(storageMode) ? config.getString("mysql.table_prefix", "kukecdk_") : "";
            
            // 加载所有CDK
            String selectCDKSQL = "SELECT * FROM " + tablePrefix + "cdk";
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(selectCDKSQL)) {
                
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    int quantity = rs.getInt("quantity");
                    boolean isSingleUse = rs.getBoolean("single_use");
                    String commands = rs.getString("commands");
                    String expirationStr = rs.getString("expiration_date");
                    
                    java.util.Date expirationDate = null;
                    if (expirationStr != null && !expirationStr.isEmpty()) {
                        try {
                            expirationDate = dateFormat.parse(expirationStr);
                        } catch (ParseException e) {
                            plugin.getLogger().warning("解析CDK " + name + " 的过期时间时出错: " + e.getMessage());
                        }
                    }
                    
                    CDK cdk = new CDK(id, name, quantity, isSingleUse, commands, expirationDate);
                    cdkMap.computeIfAbsent(id, k -> new HashMap<>()).put(name, cdk);
                }
            }
            
            // 加载所有已兑换玩家信息
            String selectPlayersSQL = "SELECT * FROM " + tablePrefix + "redeemed_players";
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(selectPlayersSQL)) {
                
                while (rs.next()) {
                    String cdkName = rs.getString("cdk_name");
                    String playerName = rs.getString("player_name");
                    
                    // 查找对应的CDK并添加玩家
                    for (Map<String, CDK> cdkGroup : cdkMap.values()) {
                        CDK cdk = cdkGroup.get(cdkName);
                        if (cdk != null) {
                            cdk.addRedeemedPlayer(playerName);
                            break;
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("从数据库加载CDK时出错: " + e.getMessage());
            e.printStackTrace();
        }
        
        return cdkMap;
    }

    /**
     * 将CDK保存到数据库
     *
     * @param cdkMap 要保存的CDK映射
     */
    public void saveCDKs(Map<String, Map<String, CDK>> cdkMap) {
        checkConnection();
        try {
            String tablePrefix = "mysql".equalsIgnoreCase(storageMode) ? config.getString("mysql.table_prefix", "kukecdk_") : "";
            boolean isMysql = "mysql".equalsIgnoreCase(storageMode);

            // 1. 获取数据库中现有的所有CDK名称
            Set<String> dbCDKNames = new HashSet<>();
            // 检查表是否存在，防止首次运行报错（虽然initDatabase已创建，但防万一）
            try {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT name FROM " + tablePrefix + "cdk")) {
                    while (rs.next()) {
                        dbCDKNames.add(rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                // 如果查询失败，可能是表不存在，尝试重新创建
                createTables();
                // 再次尝试查询，如果还失败则抛出异常
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT name FROM " + tablePrefix + "cdk")) {
                    while (rs.next()) {
                        dbCDKNames.add(rs.getString("name"));
                    }
                }
            }

            // 2. 收集内存中所有的CDK名称
            Set<String> memoryCDKNames = new HashSet<>();
            for (Map<String, CDK> map : cdkMap.values()) {
                memoryCDKNames.addAll(map.keySet());
            }

            // 3. 计算需要删除的CDK (数据库中有但内存中没有的)
            Set<String> toDelete = new HashSet<>(dbCDKNames);
            toDelete.removeAll(memoryCDKNames);

            // 开始事务
            connection.setAutoCommit(false);

            // 4. 删除多余的CDK
            if (!toDelete.isEmpty()) {
                String deleteCdkSQL = "DELETE FROM " + tablePrefix + "cdk WHERE name = ?";
                String deletePlayerSQL = "DELETE FROM " + tablePrefix + "redeemed_players WHERE cdk_name = ?";
                try (PreparedStatement psCdk = connection.prepareStatement(deleteCdkSQL);
                     PreparedStatement psPlayer = connection.prepareStatement(deletePlayerSQL)) {
                    for (String name : toDelete) {
                        psCdk.setString(1, name);
                        psCdk.addBatch();
                        psPlayer.setString(1, name);
                        psPlayer.addBatch();
                    }
                    psCdk.executeBatch();
                    psPlayer.executeBatch();
                }
            }
            
            // 5. 更新或插入CDK
            String upsertCDKSQL;
            if (isMysql) {
                upsertCDKSQL = "INSERT INTO " + tablePrefix + "cdk (id, name, quantity, single_use, commands, expiration_date) VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE quantity=VALUES(quantity), single_use=VALUES(single_use), commands=VALUES(commands), expiration_date=VALUES(expiration_date)";
            } else {
                upsertCDKSQL = "INSERT OR REPLACE INTO " + tablePrefix + "cdk (id, name, quantity, single_use, commands, expiration_date) VALUES (?, ?, ?, ?, ?, ?)";
            }

            // 对于 redeemed_players，简单起见，先删除该CDK的记录再插入新的
            String deleteCDKPlayersSQL = "DELETE FROM " + tablePrefix + "redeemed_players WHERE cdk_name = ?";
            String insertPlayerSQL = "INSERT INTO " + tablePrefix + "redeemed_players (cdk_name, player_name) VALUES (?, ?)";

            try (PreparedStatement cdkStatement = connection.prepareStatement(upsertCDKSQL);
                 PreparedStatement delPlayersStmt = connection.prepareStatement(deleteCDKPlayersSQL);
                 PreparedStatement insPlayersStmt = connection.prepareStatement(insertPlayerSQL)) {
                
                for (Map.Entry<String, Map<String, CDK>> entry : cdkMap.entrySet()) {
                    for (CDK cdk : entry.getValue().values()) {
                        // Upsert CDK
                        cdkStatement.setString(1, cdk.getId());
                        cdkStatement.setString(2, cdk.getName());
                        cdkStatement.setInt(3, cdk.getQuantity());
                        cdkStatement.setBoolean(4, cdk.isSingleUse());
                        cdkStatement.setString(5, cdk.getCommands());
                        cdkStatement.setString(6, cdk.getExpirationDate() != null ? dateFormat.format(cdk.getExpirationDate()) : null);
                        cdkStatement.executeUpdate();
                        
                        // 更新玩家列表
                        // 先删除旧的
                        delPlayersStmt.setString(1, cdk.getName());
                        delPlayersStmt.executeUpdate();

                        // 插入新的
                        if (cdk.getRedeemedPlayers() != null && !cdk.getRedeemedPlayers().isEmpty()) {
                            for (String playerName : cdk.getRedeemedPlayers()) {
                                insPlayersStmt.setString(1, cdk.getName());
                                insPlayersStmt.setString(2, playerName);
                                insPlayersStmt.addBatch();
                            }
                            insPlayersStmt.executeBatch();
                        }
                    }
                }
            }
            
            // 提交事务
            connection.commit();
            connection.setAutoCommit(true);
            
        } catch (SQLException e) {
            plugin.getLogger().severe("保存CDK到数据库时出错: " + e.getMessage());
            e.printStackTrace();
            
            // 回滚事务
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException rollbackEx) {
                plugin.getLogger().severe("回滚事务时出错: " + rollbackEx.getMessage());
                rollbackEx.printStackTrace();
            }
        }
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭数据库连接时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}