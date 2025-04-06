package su.kukecdk.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import su.kukecdk.model.CDK;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 日志管理器类，负责处理插件的日志记录
 */
public class LogManager {
    private final JavaPlugin plugin;
    private File logFile;
    private FileConfiguration logConfig;

    /**
     * 创建一个新的日志管理器
     *
     * @param plugin 插件实例
     */
    public LogManager(JavaPlugin plugin) {
        this.plugin = plugin;
        createLogFile();
    }

    /**
     * 创建日志文件
     */
    private void createLogFile() {
        logFile = new File(plugin.getDataFolder(), "log.yml");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        logConfig = YamlConfiguration.loadConfiguration(logFile);
    }

    /**
     * 保存日志文件
     */
    public void saveLog() {
        try {
            logConfig.save(logFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存日志文件时出错！");
            e.printStackTrace();
        }
    }

    /**
     * 记录CDK使用日志
     *
     * @param playerName 玩家名称
     * @param cdk 使用的CDK
     */
    public void logCDKUsage(String playerName, CDK cdk) {
        String path = playerName + ".used";
        List<String> usedCDKs = logConfig.getStringList(path);

        // 获取当前时间
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());

        // 使用 toString 方法记录 CDK 使用情况和兑换时间
        usedCDKs.add(cdk.toString() + " (兑换时间: " + currentTime + ")");
        logConfig.set(path, usedCDKs);

        saveLog();
    }

    /**
     * 获取玩家使用的CDK列表
     *
     * @param playerName 玩家名称
     * @return 玩家使用的CDK列表
     */
    public List<String> getPlayerUsedCDKs(String playerName) {
        String path = playerName + ".used";
        return logConfig.getStringList(path);
    }

    /**
     * 获取日志配置
     *
     * @return 日志配置
     */
    public FileConfiguration getLogConfig() {
        return logConfig;
    }
}