package su.kukecdk.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 配置合并器，用于检查和补全配置文件中缺失的配置项
 */
public class ConfigMerger {
    private final JavaPlugin plugin;

    /**
     * 创建一个新的配置合并器
     *
     * @param plugin 插件实例
     */
    public ConfigMerger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 合并配置文件，检查并补全缺失的配置项
     *
     * @param currentConfig 当前配置
     * @param defaultResourcePath 默认配置文件的资源路径
     * @param configFile 配置文件
     */
    public void mergeConfig(FileConfiguration currentConfig, String defaultResourcePath, File configFile) {
        // 从资源文件中加载默认配置
        InputStream defaultConfigStream = plugin.getResource(defaultResourcePath);
        if (defaultConfigStream == null) {
            plugin.getLogger().warning("无法找到默认配置文件: " + defaultResourcePath);
            return;
        }

        // 使用UTF-8编码读取默认配置
        FileConfiguration defaultConfig = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));

        boolean hasChanges = false;

        // 检查并补全缺失的配置项
        for (String key : defaultConfig.getKeys(true)) {
            if (!currentConfig.contains(key)) {
                currentConfig.set(key, defaultConfig.get(key));
                hasChanges = true;
                plugin.getLogger().info("已添加缺失的配置项: " + key);
            }
        }

        // 如果有变更，保存配置文件
        if (hasChanges) {
            try {
                currentConfig.save(configFile);
                plugin.getLogger().info("已更新配置文件: " + configFile.getName());
            } catch (Exception e) {
                plugin.getLogger().severe("保存配置文件时出错: " + e.getMessage());
            }
        }
    }
}