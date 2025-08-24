package su.kukecdk.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FailedAttemptsManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    
    // 玎家失败尝试次数记录
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();
    // 玩家失败尝试时间记录
    private final Map<UUID, Long> failedAttemptTimes = new HashMap<>();
    
    // 被封禁玩家及其解封时间
    private final Map<UUID, Long> bannedPlayers = new HashMap<>();
    
    public FailedAttemptsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "failed_attempts.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // 加载被封禁的玩家
        if (config.contains("banned_players")) {
            for (String uuidStr : config.getConfigurationSection("banned_players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                long unbanTime = config.getLong("banned_players." + uuidStr);
                bannedPlayers.put(uuid, unbanTime);
            }
        }
    }
    
    /**
     * 保存配置文件
     */
    public void saveConfig() {
        // 清除已过期的封禁
        long currentTime = System.currentTimeMillis();
        bannedPlayers.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        
        // 保存被封禁的玩家
        for (Map.Entry<UUID, Long> entry : bannedPlayers.entrySet()) {
            config.set("banned_players." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存失败尝试配置文件时出错！");
            e.printStackTrace();
        }
    }
    
    /**
     * 记录玩家失败尝试
     * 
     * @param uuid 玩家UUID
     * @return 当前失败次数
     */
    public int recordFailedAttempt(UUID uuid) {
        // 检查是否需要重置失败尝试记录
        checkAndResetFailedAttempts();
        
        int attempts = failedAttempts.getOrDefault(uuid, 0) + 1;
        failedAttempts.put(uuid, attempts);
        failedAttemptTimes.put(uuid, System.currentTimeMillis());
        return attempts;
    }
    
    /**
     * 清除玩家失败尝试记录
     * 
     * @param uuid 玩家UUID
     */
    public void clearFailedAttempts(UUID uuid) {
        failedAttempts.remove(uuid);
        failedAttemptTimes.remove(uuid);
    }
    
    /**
     * 检查并重置过期的失败尝试记录
     */
    private void checkAndResetFailedAttempts() {
        long currentTime = System.currentTimeMillis();
        int resetDurationMinutes = getConfigValue("failed_attempts.reset_duration", 10);
        long resetDurationMillis = resetDurationMinutes * 60 * 1000L;
        
        // 移除过期的失败尝试记录
        failedAttemptTimes.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() >= resetDurationMillis) {
                failedAttempts.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 检查玩家是否被封禁
     * 
     * @param uuid 玩家UUID
     * @return 是否被封禁
     */
    public boolean isPlayerBanned(UUID uuid) {
        // 检查是否有封禁记录
        if (!bannedPlayers.containsKey(uuid)) {
            return false;
        }
        
        // 检查封禁是否已过期
        long unbanTime = bannedPlayers.get(uuid);
        long currentTime = System.currentTimeMillis();
        
        if (currentTime >= unbanTime) {
            // 封禁已过期，移除记录
            bannedPlayers.remove(uuid);
            return false;
        }
        
        return true;
    }
    
    /**
     * 封禁玩家
     * 
     * @param uuid 玩家UUID
     * @param banDurationMillis 封禁时长（毫秒）
     */
    public void banPlayer(UUID uuid, long banDurationMillis) {
        long unbanTime = System.currentTimeMillis() + banDurationMillis;
        bannedPlayers.put(uuid, unbanTime);
        saveConfig();
    }
    
    /**
     * 获取玩家解封时间
     * 
     * @param uuid 玩家UUID
     * @return 解封时间（毫秒时间戳）
     */
    public Long getUnbanTime(UUID uuid) {
        return bannedPlayers.get(uuid);
    }
    
    /**
     * 获取配置值
     * 
     * @param path 配置路径
     * @param def 默认值
     * @return 配置值
     */
    public <T> T getConfigValue(String path, T def) {
        return (T) config.get(path, def);
    }
}