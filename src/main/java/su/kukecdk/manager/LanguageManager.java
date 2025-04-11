package su.kukecdk.manager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 语言管理器，负责加载和管理多语言支持
 */
public class LanguageManager {
    private final JavaPlugin plugin;
    private final File languageFolder;
    private final ConfigManager configManager;
    private String currentLanguage;
    private FileConfiguration languageConfig;
    private final Map<String, String> messageCache = new HashMap<>();
    private final ConfigMerger configMerger;

    /**
     * 创建一个新的语言管理器
     *
     * @param plugin 插件实例
     * @param configManager 配置管理器
     */
    public LanguageManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.languageFolder = new File(plugin.getDataFolder(), "language");
        this.configMerger = new ConfigMerger(plugin);
        createLanguageFolder();
        loadLanguage();
    }

    /**
     * 创建语言文件夹和默认语言文件
     */
    private void createLanguageFolder() {
        if (!languageFolder.exists() && !languageFolder.mkdirs()) {
            plugin.getLogger().severe("无法创建语言文件夹！");
            return;
        }

        // 检查并创建默认语言文件
        saveDefaultLanguageFile("zh_CN.yml");
        saveDefaultLanguageFile("en_US.yml");
    }

    /**
     * 保存默认语言文件
     *
     * @param fileName 文件名
     */
    private void saveDefaultLanguageFile(String fileName) {
        File languageFile = new File(languageFolder, fileName);
        if (!languageFile.exists()) {
            try (InputStream in = plugin.getResource("language/" + fileName)) {
                if (in != null) {
                    java.nio.file.Files.copy(in, languageFile.toPath());
                    plugin.getLogger().info("已创建语言文件: " + fileName);
                } else {
                    // 如果资源不存在，创建一个空文件
                    languageFile.createNewFile();
                    plugin.getLogger().warning("未找到默认语言文件，已创建空文件: " + fileName);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("创建语言文件时出错: " + e.getMessage());
            }
        }
    }

    /**
     * 加载当前设置的语言
     */
    public void loadLanguage() {
        // 从配置文件中获取当前语言设置
        currentLanguage = configManager.getString("language", "zh_CN");
        File languageFile = new File(languageFolder, currentLanguage + ".yml");

        // 如果语言文件不存在，使用默认语言
        if (!languageFile.exists()) {
            plugin.getLogger().warning("找不到语言文件: " + currentLanguage + ".yml，使用默认语言 zh_CN.yml");
            currentLanguage = "zh_CN";
            languageFile = new File(languageFolder, currentLanguage + ".yml");
            
            // 如果默认语言文件也不存在，创建它
            if (!languageFile.exists()) {
                saveDefaultLanguageFile("zh_CN.yml");
            }
        }

        // 加载语言文件
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
        
        // 检查并自动补全缺失的配置项
        configMerger.mergeConfig(languageConfig, "language/" + currentLanguage + ".yml", languageFile);
        
        // 清除消息缓存
        messageCache.clear();
        
        plugin.getLogger().info("已加载语言: " + currentLanguage);

    }

    /**
     * 获取翻译消息
     *
     * @param key 消息键
     * @return 翻译后的消息
     */
    public String getMessage(String key) {
        // 先从缓存中查找
        if (messageCache.containsKey(key)) {
            return messageCache.get(key);
        }

        // 从语言文件中获取消息
        String message = languageConfig.getString(key);
        if (message == null) {
            // 如果找不到消息，返回键名
            plugin.getLogger().warning("缺失翻译: " + key + "，在语言文件: " + currentLanguage + ".yml 中，请检查语言文件是否为最新版，可尝试删除语言文件并重新加载");
            return key;
        }

        // 替换颜色代码
        message = ChatColor.translateAlternateColorCodes('&', message);
        
        // 缓存消息
        messageCache.put(key, message);
        
        return message;
    }

    /**
     * 获取带有占位符替换的翻译消息
     *
     * @param key 消息键
     * @param placeholders 占位符和替换值的数组，格式为 [占位符1, 替换值1, 占位符2, 替换值2, ...]
     * @return 翻译后的消息
     */
    public String getMessage(String key, String... placeholders) {
        String message = getMessage(key);
        
        // 替换占位符
        if (placeholders != null && placeholders.length >= 2) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    message = message.replace(placeholders[i], placeholders[i + 1]);
                }
            }
        }
        
        return message;
    }

    /**
     * 获取当前语言
     *
     * @return 当前语言代码
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * 设置当前语言
     *
     * @param language 语言代码
     */
    public void setCurrentLanguage(String language) {
        // 检查语言文件是否存在
        File languageFile = new File(languageFolder, language + ".yml");
        if (!languageFile.exists()) {
            plugin.getLogger().warning("找不到语言文件: " + language + ".yml，无法切换语言");
            return;
        }

        // 更新配置文件中的语言设置
        configManager.getConfig().set("language", language);
        configManager.saveConfig();
        
        // 重新加载语言
        currentLanguage = language;
        loadLanguage();
        
        plugin.getLogger().info("已切换语言为: " + language);
    }
}