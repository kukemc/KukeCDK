package su.kukecdk.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import su.kukecdk.model.CDK;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * CDK管理器类，负责CDK的加载、保存和管理
 */
public class CDKManager {
    private final JavaPlugin plugin;
    private FileConfiguration cdkConfig;
    private File cdkFile;
    private final Map<String, Map<String, CDK>> cdkMap = new HashMap<>();
    private FileConfiguration config;
    private DatabaseManager databaseManager;
    private String storageMode;

    /**
     * 创建一个新的CDK管理器
     *
     * @param plugin 插件实例
     * @param config 配置文件
     */
    public CDKManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.storageMode = config.getString("storage_mode", "yaml");
        
        // 如果使用数据库模式，初始化数据库管理器
        if ("sqlite".equalsIgnoreCase(storageMode) || "mysql".equalsIgnoreCase(storageMode)) {
            this.databaseManager = new DatabaseManager(plugin, config, storageMode);
        }
        
        loadCDKs();
    }

    /**
     * 加载CDK配置
     */
    public void loadCDKs() {
        if ("sqlite".equalsIgnoreCase(storageMode) || "mysql".equalsIgnoreCase(storageMode)) {
            // 从数据库加载
            cdkMap.clear();
            cdkMap.putAll(databaseManager.loadCDKs());
        } else {
            // 从YAML文件加载
            cdkFile = new File(plugin.getDataFolder(), "cdk.yml");
            if (!cdkFile.exists()) {
                try {
                    cdkFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            cdkConfig = YamlConfiguration.loadConfiguration(cdkFile);

            cdkMap.clear();
            for (String id : cdkConfig.getKeys(false)) {
                cdkConfig.getConfigurationSection(id).getKeys(false).forEach(cdkName -> {
                    String name = cdkConfig.getString(id + "." + cdkName + ".name");
                    int quantity = cdkConfig.getInt(id + "." + cdkName + ".quantity");
                    boolean isSingleUse = cdkConfig.getBoolean(id + "." + cdkName + ".single");
                    String commands = cdkConfig.getString(id + "." + cdkName + ".commands");
                    Date expirationDate = null;

                    String dateStr = cdkConfig.getString(id + "." + cdkName + ".expiration");
                    if (dateStr != null) {
                        try {
                            expirationDate = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(dateStr);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }

                    CDK cdk = new CDK(id, name, quantity, isSingleUse, commands, expirationDate);
                    
                    // 加载已兑换玩家列表
                    List<String> redeemedPlayersList = cdkConfig.getStringList(id + "." + cdkName + ".redeemedPlayers");
                    Set<String> redeemedPlayers = new HashSet<>(redeemedPlayersList);
                    cdk.setRedeemedPlayers(redeemedPlayers);
                    
                    // 确保redeemedPlayers不为null
                    if (cdk.getRedeemedPlayers() == null) {
                        cdk.setRedeemedPlayers(new HashSet<>());
                    }
                    
                    cdkMap.computeIfAbsent(id, k -> new HashMap<>()).put(name, cdk);
                });
            }
        }
    }

    /**
     * 保存CDK配置
     */
    public void saveCDKs() {
        if ("sqlite".equalsIgnoreCase(storageMode) || "mysql".equalsIgnoreCase(storageMode)) {
            // 保存到数据库
            databaseManager.saveCDKs(cdkMap);
        } else {
            // 保存到YAML文件
            cdkConfig.getKeys(false).forEach(key -> cdkConfig.set(key, null)); // 清空现有内容，避免累积
            for (Map.Entry<String, Map<String, CDK>> entry : cdkMap.entrySet()) {
                String id = entry.getKey();
                for (CDK cdk : entry.getValue().values()) {
                    String basePath = id + "." + cdk.getName();
                    cdkConfig.set(basePath + ".name", cdk.getName());
                    cdkConfig.set(basePath + ".quantity", cdk.getQuantity());
                    cdkConfig.set(basePath + ".single", cdk.isSingleUse());
                    cdkConfig.set(basePath + ".commands", cdk.getCommands());
                    if (cdk.getExpirationDate() != null) {
                        cdkConfig.set(basePath + ".expiration", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(cdk.getExpirationDate()));
                    }
                    // 保存已兑换玩家列表
                    if (cdk.getRedeemedPlayers() != null) {
                        cdkConfig.set(basePath + ".redeemedPlayers", new ArrayList<>(cdk.getRedeemedPlayers())); // 将 Set 转换为 List
                    } else {
                        cdkConfig.set(basePath + ".redeemedPlayers", new ArrayList<>());
                    }
                }
            }
            try {
                cdkConfig.save(cdkFile);
            } catch (IOException e) {
                plugin.getLogger().severe("保存 CDK 文件时出错！");
                e.printStackTrace();
            }
        }
    }

    /**
     * 移除过期的CDK
     */
    public void removeExpiredCDKs() {
        for (Map<String, CDK> cdkGroup : cdkMap.values()) {
            cdkGroup.values().removeIf(CDK::isExpired);
        }
        saveCDKs();
    }

    /**
     * 生成唯一的随机CDK名称
     *
     * @return 随机生成的CDK名称
     */
    public String generateUniqueRandomCDKName() {
        String cdkName;
        do {
            cdkName = generateRandomCDKName();
        } while (cdkMap.containsKey(cdkName)); // 避免冲突
        return cdkName;
    }

    /**
     * 生成随机CDK名称
     *
     * @return 随机生成的CDK名称
     */
    public String generateRandomCDKName() {
        int length = config.getInt("default_cdk_name_length", 8);
        String characters = config.getString("default_cdk_characters", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        StringBuilder result = new StringBuilder(length);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            result.append(characters.charAt(random.nextInt(characters.length())));
        }
        return result.toString();
    }

    /**
     * 创建一个新的CDK
     *
     * @param id CDK的ID
     * @param name CDK的名称
     * @param quantity CDK的数量
     * @param isSingleUse 是否为一次性使用
     * @param commands 兑换时执行的命令
     * @param expirationDate 过期时间
     * @return 创建的CDK对象
     */
    public CDK createCDK(String id, String name, int quantity, boolean isSingleUse, String commands, Date expirationDate) {
        CDK cdk = new CDK(id, name, quantity, isSingleUse, commands, expirationDate);
        cdkMap.computeIfAbsent(id, k -> new HashMap<>()).put(name, cdk);
        saveCDKs();
        return cdk;
    }

    /**
     * 删除指定ID的所有CDK
     *
     * @param id CDK的ID
     * @return 如果删除成功则返回true，否则返回false
     */
    public boolean deleteById(String id) {
        if (cdkMap.containsKey(id)) {
            cdkMap.remove(id);
            saveCDKs();
            return true;
        }
        return false;
    }

    /**
     * 删除指定名称的CDK
     *
     * @param cdkName CDK的名称
     * @return 如果删除成功则返回true，否则返回false
     */
    public boolean deleteByCDKName(String cdkName) {
        for (Map.Entry<String, Map<String, CDK>> entry : cdkMap.entrySet()) {
            if (entry.getValue().containsKey(cdkName)) {
                entry.getValue().remove(cdkName);
                saveCDKs();
                return true;
            }
        }
        return false;
    }

    /**
     * 根据CDK名称查找CDK
     *
     * @param cdkName CDK的名称
     * @return 找到的CDK对象，如果未找到则返回null
     */
    public CDK findCDKByName(String cdkName) {
        for (Map<String, CDK> cdkGroup : cdkMap.values()) {
            if (cdkGroup.containsKey(cdkName)) {
                return cdkGroup.get(cdkName);
            }
        }
        return null;
    }

    /**
     * 根据ID查找CDK组
     *
     * @param id CDK的ID
     * @return 找到的CDK组，如果未找到则返回null
     */
    public Map<String, CDK> findCDKGroupById(String id) {
        return cdkMap.get(id);
    }

    /**
     * 获取所有CDK
     *
     * @return 所有CDK的映射
     */
    public Map<String, Map<String, CDK>> getAllCDKs() {
        return cdkMap;
    }

    /**
     * 更新配置
     *
     * @param config 新的配置
     */
    public void updateConfig(FileConfiguration config) {
        this.config = config;
        this.storageMode = config.getString("storage_mode", "yaml");
        
        // 如果切换到数据库模式，初始化数据库管理器
        if (("sqlite".equalsIgnoreCase(storageMode) || "mysql".equalsIgnoreCase(storageMode)) && databaseManager == null) {
            this.databaseManager = new DatabaseManager(plugin, config, storageMode);
            // 重新加载数据
            loadCDKs();
        } else if (!("sqlite".equalsIgnoreCase(storageMode) || "mysql".equalsIgnoreCase(storageMode))) {
            // 关闭数据库连接
            if (databaseManager != null) {
                databaseManager.close();
                databaseManager = null;
            }
        }
    }
    
    /**
     * 获取当前存储模式
     * 
     * @return 当前存储模式
     */
    public String getStorageMode() {
        return storageMode;
    }
    
    /**
     * 从YAML迁移到数据库
     * 
     * @return 迁移的CDK数量
     */
    public int migrateYamlToDatabase() {
        if (!"yaml".equalsIgnoreCase(storageMode)) {
            throw new IllegalStateException("当前不是YAML模式，无法执行迁移");
        }
        
        // 保存当前模式
        String oldMode = storageMode;
        
        try {
            // 切换到数据库模式
            storageMode = config.getString("storage_mode", "sqlite"); // 获取配置中指定的数据库类型
            if (databaseManager == null) {
                databaseManager = new DatabaseManager(plugin, config, storageMode);
            }
            
            // 保存当前数据到数据库
            saveCDKs();
            
            return cdkMap.values().stream().mapToInt(Map::size).sum();
        } finally {
            // 恢复原来的模式
            storageMode = oldMode;
        }
    }
    
    /**
     * 从数据库导出到YAML
     * 
     * @return 导出的CDK数量
     */
    public int exportDatabaseToYaml() {
        if (!("sqlite".equalsIgnoreCase(storageMode) || "mysql".equalsIgnoreCase(storageMode))) {
            throw new IllegalStateException("当前不是数据库模式，无法执行导出");
        }
        
        // 保存当前模式
        String oldMode = storageMode;
        
        try {
            // 切换到YAML模式
            storageMode = "yaml";
            
            // 确保YAML文件存在
            cdkFile = new File(plugin.getDataFolder(), "cdk.yml");
            if (!cdkFile.exists()) {
                try {
                    cdkFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            cdkConfig = YamlConfiguration.loadConfiguration(cdkFile);
            
            // 保存当前数据到YAML
            saveCDKs();
            
            return cdkMap.values().stream().mapToInt(Map::size).sum();
        } catch (Exception e) {
            plugin.getLogger().severe("导出数据时出错: " + e.getMessage());
            e.printStackTrace();
            return 0;
        } finally {
            // 恢复原来的模式
            storageMode = oldMode;
        }
    }
    
    /**
     * 从YAML直接迁移到指定的数据库类型，不考虑当前的storageMode
     * 
     * @param targetDatabaseType 目标数据库类型 (sqlite 或 mysql)
     * @return 迁移的CDK数量
     */
    public int migrateYamlToDatabaseDirect(String targetDatabaseType) {
        // 保存当前模式
        String oldMode = storageMode;
        DatabaseManager oldDatabaseManager = databaseManager;
        
        try {
            // 切换到数据库模式
            storageMode = targetDatabaseType;
            databaseManager = new DatabaseManager(plugin, config, targetDatabaseType);
            
            // 先从YAML加载数据
            cdkMap.clear();
            File yamlFile = new File(plugin.getDataFolder(), "cdk.yml");
            if (yamlFile.exists()) {
                FileConfiguration yamlConfig = YamlConfiguration.loadConfiguration(yamlFile);
                for (String id : yamlConfig.getKeys(false)) {
                    yamlConfig.getConfigurationSection(id).getKeys(false).forEach(cdkName -> {
                        String name = yamlConfig.getString(id + "." + cdkName + ".name");
                        int quantity = yamlConfig.getInt(id + "." + cdkName + ".quantity");
                        boolean isSingleUse = yamlConfig.getBoolean(id + "." + cdkName + ".single");
                        String commands = yamlConfig.getString(id + "." + cdkName + ".commands");
                        Date expirationDate = null;

                        String dateStr = yamlConfig.getString(id + "." + cdkName + ".expiration");
                        if (dateStr != null) {
                            try {
                                expirationDate = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(dateStr);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }

                        CDK cdk = new CDK(id, name, quantity, isSingleUse, commands, expirationDate);
                        
                        // 加载已兑换玩家列表
                        List<String> redeemedPlayersList = yamlConfig.getStringList(id + "." + cdkName + ".redeemedPlayers");
                        Set<String> redeemedPlayers = new HashSet<>(redeemedPlayersList);
                        cdk.setRedeemedPlayers(redeemedPlayers);
                        
                        // 确保redeemedPlayers不为null
                        if (cdk.getRedeemedPlayers() == null) {
                            cdk.setRedeemedPlayers(new HashSet<>());
                        }
                        
                        cdkMap.computeIfAbsent(id, k -> new HashMap<>()).put(name, cdk);
                    });
                }
            }
            
            // 保存当前数据到数据库
            saveCDKs();
            
            return cdkMap.values().stream().mapToInt(Map::size).sum();
        } finally {
            // 恢复原来的模式
            storageMode = oldMode;
            databaseManager = oldDatabaseManager;
        }
    }
    
    /**
     * 从数据库直接导出到YAML，不考虑当前的storageMode
     * 
     * @param sourceDatabaseType 源数据库类型 (sqlite 或 mysql)
     * @return 导出的CDK数量
     */
    public int exportDatabaseToYamlDirect(String sourceDatabaseType) {
        // 保存当前模式
        String oldMode = storageMode;
        DatabaseManager oldDatabaseManager = databaseManager;
        
        try {
            // 切换到YAML模式
            storageMode = "yaml";
            
            // 确保YAML文件存在
            cdkFile = new File(plugin.getDataFolder(), "cdk.yml");
            if (!cdkFile.exists()) {
                try {
                    cdkFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            cdkConfig = YamlConfiguration.loadConfiguration(cdkFile);
            
            // 从数据库加载数据
            databaseManager = new DatabaseManager(plugin, config, sourceDatabaseType);
            cdkMap.clear();
            cdkMap.putAll(databaseManager.loadCDKs());
            
            // 保存当前数据到YAML
            saveCDKs();
            
            return cdkMap.values().stream().mapToInt(Map::size).sum();
        } catch (Exception e) {
            plugin.getLogger().severe("导出数据时出错: " + e.getMessage());
            e.printStackTrace();
            return 0;
        } finally {
            // 恢复原来的模式
            storageMode = oldMode;
            databaseManager = oldDatabaseManager;
        }
    }
}