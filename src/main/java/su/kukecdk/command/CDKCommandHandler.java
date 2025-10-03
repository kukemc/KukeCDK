package su.kukecdk.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import su.kukecdk.manager.CDKManager;
import su.kukecdk.manager.LanguageManager;
import su.kukecdk.manager.LogManager;
import su.kukecdk.manager.FailedAttemptsManager;
import su.kukecdk.model.CDK;
// import com.tcoded.folialib.FoliaLib;  // 暂时注释，网络问题

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CDK命令处理器类，负责处理所有CDK相关命令
 */
public class CDKCommandHandler {
    private final CDKManager cdkManager;
    private final LogManager logManager;
    private final LanguageManager languageManager;
    private final FailedAttemptsManager failedAttemptsManager;
    private final File dataFolder;
    // private final FoliaLib foliaLib;  // 暂时注释，网络问题

    /**
     * 创建一个新的CDK命令处理器
     *
     * @param cdkManager CDK管理器
     * @param logManager 日志管理器
     * @param languageManager 语言管理器
     * @param failedAttemptsManager 失败尝试管理器
     * @param dataFolder 插件数据文件夹
     */
    public CDKCommandHandler(CDKManager cdkManager, LogManager logManager, LanguageManager languageManager, FailedAttemptsManager failedAttemptsManager, File dataFolder) {
        this.cdkManager = cdkManager;
        this.logManager = logManager;
        this.languageManager = languageManager;
        this.failedAttemptsManager = failedAttemptsManager;
        this.dataFolder = dataFolder;
        // this.foliaLib = foliaLib;  // 暂时注释，网络问题
    }

    /**
     * 处理创建CDK命令
     *
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 命令执行结果
     */
    public boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sendMessageToSender(sender, languageManager.getMessage("create_usage_single"));
            sendMessageToSender(sender, languageManager.getMessage("create_usage_multiple"));
            sendMessageToSender(sender, "");
            sendMessageToSender(sender, languageManager.getMessage("create_example_single"));
            sendMessageToSender(sender, languageManager.getMessage("create_example_multiple"));
            return true;
        }

        String type = args[1].toLowerCase();
        String name = null; // 用于 multiple 类型的 CDK 名称
        String id = null; // 用于 CDK 的唯一标识
        int quantity;

        // 解析数量
        if (type.equals("single")) {
            id = args[2]; // 对于 single 类型，第二个参数是 id
            try {
                quantity = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("invalid_quantity"));
                return true;
            }
        } else if (type.equals("multiple")) {
            name = args[2]; // 对于 multiple 类型，第二个参数是名称
            id = args[3]; // 第三个参数是 id
            try {
                quantity = Integer.parseInt(args[4]); // 第四个参数是数量
            } catch (NumberFormatException e) {
                sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("invalid_quantity"));
                return true;
            }
        } else {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("invalid_cdk_type"));
            return true;
        }

        StringBuilder commandBuilder = new StringBuilder();
        boolean inQuotes = false;
        String expirationDateString = null;

        for (int i = (type.equals("single") ? 4 : 5); i < args.length; i++) { // 从正确的位置开始构建命令
            if (args[i].startsWith("\"") && !inQuotes) {
                inQuotes = true;
                commandBuilder.append(args[i].substring(1)).append(" ");
            } else if (args[i].endsWith("\"") && inQuotes) {
                inQuotes = false;
                commandBuilder.append(args[i], 0, args[i].length() - 1);
                // 只在命令提取完成后检查有效时间
                if (i + 1 < args.length) {
                    expirationDateString = args[i + 1] + " " + args[i + 2]; // 加上下一项作为时间
                }
                break;
            } else if (inQuotes) {
                commandBuilder.append(args[i]).append(" ");
            }
        }

        // 如果命令中有引号但没有结束，则继续拼接
        if (inQuotes) {
            for (int i = (type.equals("single") ? 4 : 5); i < args.length; i++) {
                commandBuilder.append(args[i]).append(" ");
            }
        }

        String commands = commandBuilder.toString().trim();

        if (commands.isEmpty()) {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("command_empty"));
            return true;
        }

        // 处理有效时间
        Date expirationDate = null;
        if (expirationDateString != null) {
            expirationDate = parseDate(expirationDateString);
            if (expirationDate == null) {
                sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("invalid_expiration"));
                return true;
            }
        }

        // 创建 CDK
        if (type.equals("single")) {
            for (int i = 0; i < quantity; i++) {
                String cdkName = cdkManager.generateUniqueRandomCDKName();
                cdkManager.createCDK(id, cdkName, 1, true, commands, expirationDate);
            }
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("create_success_single", "%quantity%", String.valueOf(quantity), "%id%", id));
        } else if (type.equals("multiple")) {
            String cdkName = name.equalsIgnoreCase("random") ? cdkManager.generateUniqueRandomCDKName() : name;
            cdkManager.createCDK(id, cdkName, quantity, false, commands, expirationDate);
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("create_success_multiple", "%cdk%", cdkName, "%quantity%", String.valueOf(quantity), "%id%", id));
        } else {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("invalid_cdk_type"));
            return true;
        }

        return true;
    }

    /**
     * 处理添加CDK命令
     *
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 命令执行结果
     */
    public boolean handleAddCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("add_usage"));
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("add_example"));
            return true;
        }

        String id = args[1];
        int quantity;

        try {
            quantity = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("invalid_quantity"));
            return true;
        }

        Map<String, CDK> cdkGroup = cdkManager.findCDKGroupById(id);
        if (cdkGroup == null || cdkGroup.isEmpty()) {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("cdk_not_found"));
            return true;
        }

        CDK cdk = cdkGroup.values().iterator().next(); // 自动读取第一个 CDK

        if (cdk.isSingleUse()) {
            // 如果是一次性CDK，批量生成新的CDK
            for (int i = 0; i < quantity; i++) {
                String newCdkName = cdkManager.generateUniqueRandomCDKName();
                cdkManager.createCDK(id, newCdkName, 1, true, cdk.getCommands(), cdk.getExpirationDate());
            }
        } else {
            // 如果是多次使用的CDK，直接增加数量
            cdk.increaseQuantity(quantity);
        }

        cdkManager.saveCDKs();
        sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("add_success", "%id%", id, "%quantity%", String.valueOf(quantity)));

        return true;
    }

    /**
     * 处理使用CDK命令
     *
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 命令执行结果
     */
    public boolean handleUseCommand(CommandSender sender, String[] args) {
        // 检查发送者是否为玩家
        if (!(sender instanceof Player)) {
            sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("use_player_only"));
            return true;
        }
        
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();
        
        // 检查玩家是否被封禁
        if (failedAttemptsManager.isPlayerBanned(playerUUID)) {
            Long unbanTime = failedAttemptsManager.getUnbanTime(playerUUID);
            if (unbanTime != null) {
                long currentTime = System.currentTimeMillis();
                long remainingTime = unbanTime - currentTime;
                if (remainingTime > 0) {
                    long minutes = remainingTime / 60000;
                    long seconds = (remainingTime % 60000) / 1000;
                    
                    String timeMessage;
                    if (minutes > 0) {
                        timeMessage = languageManager.getMessage("ban_time_left", "%time%", String.valueOf(minutes), "%seconds%", String.valueOf(seconds));
                    } else {
                        timeMessage = languageManager.getMessage("ban_time_left_seconds", "%seconds%", String.valueOf(seconds));
                    }
                    
                    player.sendMessage(languageManager.getMessage("prefix") + timeMessage);
                    return true;
                }
            }
        }
        
        if (args.length < 2) {
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("use_usage"));
            return true;
        }

        String cdkName = args[1];
        CDK usedCDK = cdkManager.findCDKByName(cdkName);

        if (usedCDK == null) {
            // CDK不存在，记录失败尝试
            handleFailedAttempt(player, cdkName);
            return true;
        }

        if (usedCDK.isExpired()) {
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("cdk_expired", "%cdk%", cdkName));
            return true;
        }

        if (usedCDK.hasPlayerRedeemed(player.getName())) {
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("cdk_already_used"));
            return true;
        }

        // 执行命令并替换 %player% 占位符
        String[] commands = usedCDK.getCommands().split("\\|");
        for (String command : commands) {
            String parsedCommand = command.replace("%player%", player.getName());
            
            // 暂时使用传统方式执行命令
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
        }

        // 更新 CDK 使用信息
        usedCDK.decreaseQuantity();
        if (usedCDK.isSingleUse() || usedCDK.getQuantity() <= 0) {
            cdkManager.deleteByCDKName(usedCDK.getName());
        }

        // 添加已兑换玩家
        usedCDK.addRedeemedPlayer(player.getName());
        cdkManager.saveCDKs();

        // 清除失败尝试记录
        failedAttemptsManager.clearFailedAttempts(playerUUID);
        
        // 记录使用日志
        logManager.logCDKUsage(player.getName(), usedCDK);
        player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("use_success", "%cdk%", cdkName));

        return true;
    }
    
    /**
     * 处理验证命令
     * 
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 是否处理成功
     */
    public boolean handleVerifyCommand(CommandSender sender, String[] args) {
        // 检查发送者是否为玩家
        if (!(sender instanceof Player)) {
            sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("use_player_only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("verify_usage"));
            return true;
        }

        String cdkName = args[1];
        CDK cdk = cdkManager.findCDKByName(cdkName);

        if (cdk == null) {
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("verify_not_exist", "%cdk%", cdkName));
            return true;
        }

        if (cdk.isExpired()) {
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("verify_expired", "%cdk%", cdkName));
            return true;
        }

        if (cdk.hasPlayerRedeemed(player.getName())) {
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("verify_already_used", "%cdk%", cdkName));
            return true;
        }

        // CDK可用
        String expiryInfo = "";
        if (cdk.getExpirationDate() != null) {
            expiryInfo = languageManager.getMessage("verify_expiry_info", "%date%", cdk.getExpirationDate().toString());
        } else {
            expiryInfo = languageManager.getMessage("verify_no_expiry");
        }
        
        String quantityInfo = "";
        if (cdk.isSingleUse()) {
            quantityInfo = languageManager.getMessage("verify_single_use");
        } else {
            quantityInfo = languageManager.getMessage("verify_quantity_info", "%quantity%", String.valueOf(cdk.getQuantity()));
        }
        
        player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("verify_success", "%cdk%", cdkName));
        player.sendMessage(languageManager.getMessage("prefix") + expiryInfo);
        player.sendMessage(languageManager.getMessage("prefix") + quantityInfo);
        
        return true;
    }
    
    /**
     * 处理失败尝试
     * 
     * @param player 玩家
     * @param cdkName CDK名称
     */
    private void handleFailedAttempt(Player player, String cdkName) {
        UUID playerUUID = player.getUniqueId();
        boolean enabled = failedAttemptsManager.getConfigValue("failed_attempts.enabled", true);
        
        if (!enabled) {
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("cdk_not_found", "%cdk%", cdkName));
            return;
        }
        
        int maxAttempts = failedAttemptsManager.getConfigValue("failed_attempts.max_attempts", 3);
        int attempts = failedAttemptsManager.recordFailedAttempt(playerUUID);
        
        if (attempts >= maxAttempts) {
            // 达到最大尝试次数，封禁玩家
            int banDurationMinutes = failedAttemptsManager.getConfigValue("failed_attempts.ban_duration", 10);
            long banDurationMillis = banDurationMinutes * 60 * 1000L;
            
            failedAttemptsManager.banPlayer(playerUUID, banDurationMillis);
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("player_banned_permanently"));
        } else {
            // 未达到最大尝试次数，提示剩余次数
            int remainingAttempts = maxAttempts - attempts;
            player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("cdk_not_found_with_attempts", "%cdk%", cdkName, "%attempts%", String.valueOf(attempts), "%remaining%", String.valueOf(remainingAttempts)));
        }
    }

    /**
     * 处理删除CDK命令
     *
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 命令执行结果
     */
    public boolean handleDeleteCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("delete_usage"));
            return true;
        }

        String deleteType = args[1];
        String target = args[2];
        boolean found = false;

        switch (deleteType.toLowerCase()) {
            case "id":
                // 删除整个ID及其下的所有CDK
                found = cdkManager.deleteById(target);
                if (found) {
                    sendMessageToSender(sender, languageManager.getMessage("prefix") + "成功删除 ID: " + target + " 及其所有 CDK");
                } else {
                    sendMessageToSender(sender, languageManager.getMessage("prefix") + "未找到 ID: " + target);
                }
                break;

            case "cdk":
                // 删除具体的CDK
                found = cdkManager.deleteByCDKName(target);
                if (found) {
                    sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("delete_success", "%cdk%", target));
                } else {
                    sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("cdk_not_found", "%cdk%", target));
                }
                break;

            default:
                sendMessageToSender(sender, languageManager.getMessage("prefix") + "未知的删除类型: " + deleteType);
                break;
        }

        return true;
    }

    /**
     * 处理列出CDK命令
     *
     * @param sender 命令发送者
     * @param args 命令参数
     * @return 命令执行结果
     */
    public boolean handleListCommand(CommandSender sender, String[] args) {
        Map<String, Map<String, CDK>> allCDKs = cdkManager.getAllCDKs();
        if (allCDKs.isEmpty()) {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("list_empty"));
            return true;
        }

        // 检查是否有id参数
        String filterId = null;
        if (args.length > 1) {
            filterId = args[1];
        }

        sendMessageToSender(sender, languageManager.getMessage("list_header"));
        boolean foundAny = false;
        
        for (Map<String, CDK> cdkGroup : allCDKs.values()) {
            for (CDK cdk : cdkGroup.values()) {
                // 如果指定了id过滤，只显示匹配的CDK
                if (filterId != null && !cdk.getId().equals(filterId)) {
                    continue;
                }
                
                foundAny = true;
                String expiration = cdk.getExpirationDate() != null ? 
                    new SimpleDateFormat("yyyy-MM-dd HH:mm").format(cdk.getExpirationDate()) : 
                    languageManager.getMessage("no_expiration");
                sendMessageToSender(sender, languageManager.getMessage("list_item", 
                    "%cdk%", cdk.getName(), 
                    "%id%", cdk.getId(), 
                    "%commands%", cdk.getCommands(),
                    "%expiration%", expiration));
            }
        }
        
        // 如果指定了id但没有找到匹配的CDK
        if (filterId != null && !foundAny) {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + "未找到ID为 " + filterId + " 的CDK");
            return true;
        }
        
        sendMessageToSender(sender, languageManager.getMessage("list_footer"));

        return true;
    }

    /**
     * 处理重载命令
     *
     * @param sender 命令发送者
     * @return 命令执行结果
     */
    public boolean handleReloadCommand(CommandSender sender) {
        cdkManager.loadCDKs();
        // 重新加载语言文件
        languageManager.loadLanguage();
        sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("reload_success"));
        return true;
    }

    /**
     * 处理导出命令
     *
     * @param sender 命令发送者
     * @return 命令执行结果
     */
    public boolean handleExportCommand(CommandSender sender) {
        // 创建导出文件
        File exportFile = new File(dataFolder, "export.yml");
        YamlConfiguration exportConfig = YamlConfiguration.loadConfiguration(exportFile);

        Map<String, Map<String, CDK>> allCDKs = cdkManager.getAllCDKs();
        for (Map.Entry<String, Map<String, CDK>> entry : allCDKs.entrySet()) {
            String id = entry.getKey();
            Map<String, CDK> cdkGroup = entry.getValue();

            // 创建一个列表来存储 CDK 名称
            List<String> cdkList = new ArrayList<>();
            for (CDK cdk : cdkGroup.values()) {
                cdkList.add(cdk.getName());
            }

            // 将 CDK 列表保存到 exportConfig 中
            exportConfig.set(id, cdkList);
        }

        // 保存到 export.yml 文件
        try {
            exportConfig.save(exportFile);
        } catch (IOException e) {
            sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("export_failed"));
            e.printStackTrace();
            return true;
        }

        sendMessageToSender(sender, languageManager.getMessage("prefix") + languageManager.getMessage("export_success", "%file%", exportFile.getName()));
        return true;
    }

    /**
     * 显示帮助信息
     *
     * @param sender 命令发送者
     * @return 命令执行结果
     */
    public boolean displayHelp(CommandSender sender) {
        // 发送帮助信息给 sender
        sendMessageToSender(sender, languageManager.getMessage("help_header"));
        sendMessageToSender(sender, languageManager.getMessage("help_create"));
        sendMessageToSender(sender, languageManager.getMessage("help_create_multiple"));
        sendMessageToSender(sender, languageManager.getMessage("help_add"));
        sendMessageToSender(sender, languageManager.getMessage("help_delete"));
        sendMessageToSender(sender, languageManager.getMessage("help_list"));
        sendMessageToSender(sender, languageManager.getMessage("help_use"));
        sendMessageToSender(sender, languageManager.getMessage("help_verify"));
        sendMessageToSender(sender, languageManager.getMessage("help_reload"));
        sendMessageToSender(sender, languageManager.getMessage("help_export"));
        sendMessageToSender(sender, languageManager.getMessage("help_migrate"));
        sendMessageToSender(sender, languageManager.getMessage("help_footer"));

        return true;
    }

    /**
     * 解析日期字符串
     *
     * @param dateString 日期字符串
     * @return 解析后的日期对象
     */
    private Date parseDate(String dateString) {
        Bukkit.getLogger().info("解析的时间字符串: " + dateString);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); // 根据需要更改时区
        dateFormat.setLenient(false); // 确保严格解析
        try {
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            return null; // 返回 null 表示解析失败
        }
    }

    /**
     * 向命令发送者发送消息
     *
     * @param sender 命令发送者
     * @param message 消息内容
     */
    private void sendMessageToSender(CommandSender sender, String message) {
        // 直接发送消息，LanguageManager已经处理了颜色代码
        sender.sendMessage(message);
    }
    

}