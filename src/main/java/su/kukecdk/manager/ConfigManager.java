package su.kukecdk.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * 配置管理器类，负责处理插件的配置文件
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    /**
     * 创建一个新的配置管理器
     *
     * @param plugin 插件实例
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        createConfig();
    }

    /**
     * 创建配置文件
     */
    private void createConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 重新加载配置文件
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    /**
     * 获取配置文件
     *
     * @return 配置文件
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * 保存配置文件
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存配置文件时出错！");
            e.printStackTrace();
        }
    }

    /**
     * 获取配置项的整数值
     *
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置项的整数值
     */
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    /**
     * 获取配置项的字符串值
     *
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置项的字符串值
     */
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    /**
     * 获取配置项的布尔值
     *
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置项的布尔值
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }
}