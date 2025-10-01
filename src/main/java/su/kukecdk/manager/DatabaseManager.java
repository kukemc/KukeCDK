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
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
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
                createCDKTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "cdk (" +
                        "id VARCHAR(255) NOT NULL, " +
                        "name VARCHAR(255) NOT NULL PRIMARY KEY, " +
                        "quantity INTEGER NOT NULL, " +
                        "single_use BOOLEAN NOT NULL, " +
                        "commands TEXT NOT NULL, " +
                        "expiration_date VARCHAR(255)" +
                        ");";
                
                createRedeemedPlayersTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "redeemed_players (" +
                        "cdk_name VARCHAR(255) NOT NULL, " +
                        "player_name VARCHAR(255) NOT NULL, " +
                        "PRIMARY KEY (cdk_name, player_name)" +
                        ");";
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
        try {
            String tablePrefix = "mysql".equalsIgnoreCase(storageMode) ? config.getString("mysql.table_prefix", "kukecdk_") : "";
            
            // 开始事务
            connection.setAutoCommit(false);
            
            // 清空现有数据
            try (Statement statement = connection.createStatement()) {
                statement.execute("DELETE FROM " + tablePrefix + "cdk");
                statement.execute("DELETE FROM " + tablePrefix + "redeemed_players");
            }
            
            // 插入新的CDK数据
            String insertCDKSQL = "INSERT INTO " + tablePrefix + "cdk (id, name, quantity, single_use, commands, expiration_date) VALUES (?, ?, ?, ?, ?, ?)";
            String insertPlayerSQL = "INSERT INTO " + tablePrefix + "redeemed_players (cdk_name, player_name) VALUES (?, ?)";
            
            try (PreparedStatement cdkStatement = connection.prepareStatement(insertCDKSQL);
                 PreparedStatement playerStatement = connection.prepareStatement(insertPlayerSQL)) {
                
                for (Map.Entry<String, Map<String, CDK>> entry : cdkMap.entrySet()) {
                    for (CDK cdk : entry.getValue().values()) {
                        cdkStatement.setString(1, cdk.getId());
                        cdkStatement.setString(2, cdk.getName());
                        cdkStatement.setInt(3, cdk.getQuantity());
                        cdkStatement.setBoolean(4, cdk.isSingleUse());
                        cdkStatement.setString(5, cdk.getCommands());
                        cdkStatement.setString(6, cdk.getExpirationDate() != null ? dateFormat.format(cdk.getExpirationDate()) : null);
                        cdkStatement.executeUpdate();
                        
                        // 保存已兑换玩家列表
                        if (cdk.getRedeemedPlayers() != null) {
                            for (String playerName : cdk.getRedeemedPlayers()) {
                                playerStatement.setString(1, cdk.getName());
                                playerStatement.setString(2, playerName);
                                playerStatement.executeUpdate();
                            }
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