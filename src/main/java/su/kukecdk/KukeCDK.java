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
import su.kukecdk.metrics.Metrics;

/**
 * KukeCDK插件主类
 */
public final class KukeCDK extends JavaPlugin implements CommandExecutor {

    private ConfigManager configManager;
    private CDKManager cdkManager;
    private LogManager logManager;
    private LanguageManager languageManager;
    private CDKCommandHandler commandHandler;

    @Override
    public void onEnable() {
        // 初始化Metrics
        int pluginId = 23812;
        Metrics metrics = new Metrics(this, pluginId);

        // 打印插件信息
        System.out.println("██╗  ██╗██╗   ██╗██╗  ██╗███████╗ ██████╗██████╗ ██╗  ██╗");
        System.out.println("██║ ██╔╝██║   ██║██║ ██╔╝██╔════╝██╔════╝██╔══██╗██║ ██╔╝");
        System.out.println("█████╔╝ ██║   ██║█████╔╝ █████╗  ██║     ██║  ██║█████╔╝ ");
        System.out.println("██╔═██╗ ██║   ██║██╔═██╗ ██╔══╝  ██║     ██║  ██║██╔═██╗ ");
        System.out.println("██║  ██╗╚██████╔╝██║  ██╗███████╗╚██████╗██████╔╝██║  ██╗");
        System.out.println("╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝ ╚═════╝╚═════╝ ╚═╝  ╚═╝");
        System.out.println("KukeCDK v" + getDescription().getVersion() + " by KukeMC");
        System.out.println("欢迎使用 KukeCDK");

        // 初始化管理器
        configManager = new ConfigManager(this);
        cdkManager = new CDKManager(this, configManager.getConfig());
        logManager = new LogManager(this);
        languageManager = new LanguageManager(this, configManager);
        commandHandler = new CDKCommandHandler(cdkManager, logManager, languageManager, getDataFolder());

        // 注册命令和Tab补全
        getCommand("cdk").setExecutor(this);
        getCommand("cdk").setTabCompleter(new CDKTabCompleter());

        // 定期检查过期的CDK
        Bukkit.getScheduler().runTaskTimer(this, cdkManager::removeExpiredCDKs, 6000, 6000); // 每五分钟检查一次
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
                // 检查管理员权限
                if (!sender.hasPermission("kukecdk.admin." + args[0].toLowerCase())) {
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("no_permission"));
                    return true;
                }
                break;
            case "use":
                // 检查使用权限
                if (!sender.hasPermission("kukecdk.use")) {
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("no_permission_use"));
                    return true;
                }
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
                return commandHandler.handleListCommand(sender);
            case "reload":
                return commandHandler.handleReloadCommand(sender);
            case "export":
                return commandHandler.handleExportCommand(sender);
            case "use":
                return commandHandler.handleUseCommand(sender, args);
            case "help":
                return commandHandler.displayHelp(sender);
            default:
                sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("unknown_command"));
                return true;
        }
    }
}
