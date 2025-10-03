package su.kukecdk;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import su.kukecdk.command.CDKCommandHandler;
import su.kukecdk.manager.CDKManager;
import su.kukecdk.manager.ConfigManager;
import su.kukecdk.manager.LanguageManager;
import su.kukecdk.manager.LogManager;
import su.kukecdk.manager.FailedAttemptsManager;
import su.kukecdk.metrics.Metrics;
// import com.tcoded.folialib.FoliaLib;  // 暂时注释，网络问题

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * KukeCDK插件主类
 */
public final class KukeCDK extends JavaPlugin implements CommandExecutor {

    private ConfigManager configManager;
    private CDKManager cdkManager;
    private LogManager logManager;
    private LanguageManager languageManager;
    private FailedAttemptsManager failedAttemptsManager;
    private CDKCommandHandler commandHandler;
    // private FoliaLib foliaLib;  // 暂时注释，网络问题

    @Override
    public void onEnable() {
        // 初始化Metrics
        int pluginId = 23812;
        Metrics metrics = new Metrics(this, pluginId);

        // 打印插件信息
        getLogger().info("██╗  ██╗██╗   ██╗██╗  ██╗███████╗ ██████╗██████╗ ██╗  ██╗");
        getLogger().info("██║ ██╔╝██║   ██║██║ ██╔╝██╔════╝██╔════╝██╔══██╗██║ ██╔╝");
        getLogger().info("█████╔╝ ██║   ██║█████╔╝ █████╗  ██║     ██║  ██║█████╔╝ ");
        getLogger().info("██╔═██╗ ██║   ██║██╔═██╗ ██╔══╝  ██║     ██║  ██║██╔═██╗ ");
        getLogger().info("██║  ██╗╚██████╔╝██║  ██╗███████╗╚██████╗██████╔╝██║  ██╗");
        getLogger().info("╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝ ╚═════╝╚═════╝ ╚═╝  ╚═╝");
        getLogger().info("KukeCDK v" + getDescription().getVersion() + " by KukeMC");
        getLogger().info("欢迎使用 KukeCDK");

        // 初始化FoliaLib
        // foliaLib = new FoliaLib(this);  // 暂时注释，网络问题

        // 初始化管理器
        configManager = new ConfigManager(this);
        cdkManager = new CDKManager(this, configManager.getConfig());
        logManager = new LogManager(this);
        languageManager = new LanguageManager(this, configManager);
        failedAttemptsManager = new FailedAttemptsManager(this);
        commandHandler = new CDKCommandHandler(cdkManager, logManager, languageManager, failedAttemptsManager, getDataFolder());

        // 注册命令和Tab补全
        getCommand("cdk").setExecutor(this);
        CDKTabCompleter tabCompleter = new CDKTabCompleter();
        tabCompleter.setCDKManager(cdkManager);
        getCommand("cdk").setTabCompleter(tabCompleter);

        // 注意：定期检查过期的CDK功能已暂时禁用以兼容Folia服务器
        // Folia不支持传统的Bukkit调度器API，需要使用FoliaLib或手动触发清理
        // 过期CDK将在玩家使用CDK时进行检查和清理

        // 注意：定期清理失败尝试记录功能已暂时禁用以兼容Folia服务器
        // Folia不支持传统的Bukkit调度器API，需要使用FoliaLib或手动触发清理
        // 失败尝试记录将在插件重启时自动清理
    }
    
    /**
     * 清理过期的失败尝试记录
     */
    private void cleanupFailedAttempts() {
        // 这里只是触发检查，实际清理在recordFailedAttempt方法中进行
        if (failedAttemptsManager != null) {
            // 可以添加日志记录或其他操作
        }
    }
    


    @Override
    public void onDisable() {
        // 保存数据
        if (cdkManager != null) {
            cdkManager.saveCDKs();
        }
        if (logManager != null) {
            logManager.saveLog();
        }
        if (failedAttemptsManager != null) {
            failedAttemptsManager.saveConfig();
        }
        getLogger().info("KukeCDK 已卸载");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 如果没有参数，显示帮助信息
        if (args.length == 0) {
            return commandHandler.displayHelp(sender);
        }

        // 根据命令类型执行相应操作
        switch (args[0].toLowerCase()) {
            case "create":
            case "add":
            case "delete":
            case "list":
            case "reload":
            case "export":
            case "migrate":
                // 检查管理员权限
                if (!sender.hasPermission("kukecdk.admin." + args[0].toLowerCase())) {
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("no_permission"));
                    return true;
                }
                break;
            case "use":
            case "verify":
                // 检查使用权限
                if (!sender.hasPermission("kukecdk.use")) {
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("no_permission_use"));
                    return true;
                }
                break;
            case "help":
                // help命令对所有人可用，无需权限检查
                break;
            default:
                sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("unknown_command"));
                return true;
        }

        // 委托命令处理器处理具体命令
        switch (args[0].toLowerCase()) {
            case "create":
                return commandHandler.handleCreateCommand(sender, args);
            case "add":
                return commandHandler.handleAddCommand(sender, args);
            case "delete":
                return commandHandler.handleDeleteCommand(sender, args);
            case "list":
                return commandHandler.handleListCommand(sender, args);
            case "reload":
                return commandHandler.handleReloadCommand(sender);
            case "export":
                return commandHandler.handleExportCommand(sender);
            case "use":
                return commandHandler.handleUseCommand(sender, args);
            case "verify":
                return commandHandler.handleVerifyCommand(sender, args);
            case "help":
                return commandHandler.displayHelp(sender);
            case "migrate":
                return handleMigrateCommand(sender, args);
            default:
                sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("unknown_command"));
                return true;
        }
    }
    
    /**
     * 处理数据迁移命令
     * 
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 是否处理成功
     */
    private boolean handleMigrateCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("migrate_usage"));
            return true;
        }
        
        String sourceMode = args[1].toLowerCase();
        String targetMode = args[2].toLowerCase();
        
        // 检查源和目标模式是否有效
        List<String> validModes = Arrays.asList("yaml", "sqlite", "mysql");
        if (!validModes.contains(sourceMode) || !validModes.contains(targetMode)) {
            sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("migrate_invalid_mode"));
            return true;
        }
        
        // 检查是否需要确认
        boolean confirmed = args.length > 3 && "confirm".equalsIgnoreCase(args[3]);
        
        if (!confirmed) {
            // 显示确认信息
            sender.sendMessage(languageManager.getMessage("prefix") + 
                languageManager.getMessage("migrate_confirm_warning", "%source%", sourceMode, "%target%", targetMode));
            sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("migrate_confirm_data_loss"));
            sender.sendMessage(languageManager.getMessage("prefix") + 
                languageManager.getMessage("migrate_confirm_instruction", "%source%", sourceMode, "%target%", targetMode));
            return true;
        }
        
        // 执行迁移
        try {
            int count = migrateBetweenModes(sender, sourceMode, targetMode);
            
            if (count >= 0) {
                sender.sendMessage(languageManager.getMessage("prefix") + 
                    languageManager.getMessage("migrate_to_database_success"));
            }
        } catch (Exception e) {
            sender.sendMessage(languageManager.getMessage("prefix") + 
                languageManager.getMessage("migrate_error", "%error%", e.getMessage()));
            e.printStackTrace();
        }
        
        return true;
    }
    
    /**
     * 在不同存储模式之间迁移数据
     * 
     * @param sender 命令发送者
     * @param sourceMode 源存储模式
     * @param targetMode 目标存储模式
     * @return 迁移的CDK数量
     */
    private int migrateBetweenModes(CommandSender sender, String sourceMode, String targetMode) {
        // 如果源和目标相同
        if (sourceMode.equals(targetMode)) {
            sender.sendMessage(languageManager.getMessage("prefix") + 
                languageManager.getMessage("migrate_already_target_mode", "%mode%", targetMode));
            return -1;
        }
        
        // 从YAML迁移到数据库
        if ("yaml".equals(sourceMode)) {
            if ("sqlite".equals(targetMode) || "mysql".equals(targetMode)) {
                // 更新配置中的存储模式
                configManager.getConfig().set("storage_mode", targetMode);
                configManager.saveConfig();
                
                try {
                    int count = cdkManager.migrateYamlToDatabaseDirect(targetMode);
                    sender.sendMessage(languageManager.getMessage("prefix") + 
                        languageManager.getMessage("migrate_to_database_success"));
                    return count;
                } catch (Exception e) {
                    // 恢复配置
                    configManager.getConfig().set("storage_mode", cdkManager.getStorageMode());
                    configManager.saveConfig();
                    throw new RuntimeException("从YAML迁移到数据库失败: " + e.getMessage(), e);
                }
            }
        } 
        // 从数据库迁移到YAML
        else if ("sqlite".equals(sourceMode) || "mysql".equals(sourceMode)) {
            if ("yaml".equals(targetMode)) {
                try {
                    int count = cdkManager.exportDatabaseToYamlDirect(sourceMode);
                    sender.sendMessage(languageManager.getMessage("prefix") + 
                        languageManager.getMessage("migrate_to_yaml_success"));
                    return count;
                } catch (Exception e) {
                    throw new RuntimeException("从数据库导出到YAML失败: " + e.getMessage(), e);
                }
            } 
            // 数据库之间迁移
            else if ("sqlite".equals(sourceMode) && "mysql".equals(targetMode) || 
                     "mysql".equals(sourceMode) && "sqlite".equals(targetMode)) {
                sender.sendMessage(languageManager.getMessage("prefix") + 
                    languageManager.getMessage("migrate_unsupported_db_to_db"));
                return -1;
            }
        }
        
        sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("migrate_wrong_mode"));
        return -1;
    }
}