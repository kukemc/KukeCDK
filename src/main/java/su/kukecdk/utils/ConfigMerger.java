package su.kukecdk.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 配置文件合并工具类，用于自动补全缺失的配置项
 */
public class ConfigMerger {
    private final JavaPlugin plugin;

    public ConfigMerger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 合并配置文件，将默认配置中缺失的项添加到当前配置中
     *
     * @param currentConfig 当前配置
     * @param defaultConfigPath 默认配置文件路径（在resources目录中）
     * @param configFile 配置文件
     * @return 是否发生了更改
     */
    public boolean mergeConfig(FileConfiguration currentConfig, String defaultConfigPath, File configFile) {
        boolean changed = false;

        try (InputStream defaultConfigStream = plugin.getResource(defaultConfigPath)) {
            if (defaultConfigStream == null) {
                plugin.getLogger().warning("无法加载默认配置文件: " + defaultConfigPath);
                return false;
            }

            // 加载默认配置
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));

            // 递归合并配置项
            changed = mergeConfigSection(currentConfig, defaultConfig, "");

            // 如果有更改，保存配置
            if (changed) {
                currentConfig.save(configFile);
                plugin.getLogger().info("已自动补全配置文件中缺失的配置项");
            }

        } catch (IOException e) {
            plugin.getLogger().severe("合并配置文件时出错: " + e.getMessage());
            e.printStackTrace();
        }

        return changed;
    }

    /**
     * 递归合并配置节点
     *
     * @param current 当前配置节点
     * @param defaults 默认配置节点
     * @param path 当前路径
     * @return 是否发生了更改
     */
    private boolean mergeConfigSection(ConfigurationSection current, ConfigurationSection defaults, String path) {
        boolean changed = false;

        // 获取默认配置中的所有键
        Set<String> keys = defaults.getKeys(false);

        for (String key : keys) {
            String fullPath = path.isEmpty() ? key : path + "." + key;

            if (!current.contains(key)) {
                // 如果当前配置中不存在该键，则添加
                current.set(key, defaults.get(key));
                changed = true;
                plugin.getLogger().info("添加缺失的配置项: " + fullPath);
            } else if (defaults.isConfigurationSection(key)) {
                // 如果是配置节点，则递归处理
                ConfigurationSection currentSection = current.getConfigurationSection(key);
                ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
                if (currentSection != null && defaultSection != null) {
                    changed |= mergeConfigSection(currentSection, defaultSection, fullPath);
                }
            }
        }

        return changed;
    }
}