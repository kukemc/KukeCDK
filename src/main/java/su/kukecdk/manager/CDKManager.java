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

    /**
     * 创建一个新的CDK管理器
     *
     * @param plugin 插件实例
     * @param config 配置文件
     */
    public CDKManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        loadCDKs();
    }

    /**
     * 加载CDK配置
     */
    public void loadCDKs() {
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

    /**
     * 保存CDK配置
     */
    public void saveCDKs() {
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
    }
}