package su.kukecdk.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import su.kukecdk.manager.CDKManager;
import su.kukecdk.manager.LogManager;
import su.kukecdk.model.CDK;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * CDK命令处理器类，负责处理所有CDK相关命令
 */
public class CDKCommandHandler {
    private final CDKManager cdkManager;
    private final LogManager logManager;
    private final File dataFolder;

    /**
     * 创建一个新的CDK命令处理器
     *
     * @param cdkManager CDK管理器
     * @param logManager 日志管理器
     * @param dataFolder 插件数据文件夹
     */
    public CDKCommandHandler(CDKManager cdkManager, LogManager logManager, File dataFolder) {
        this.cdkManager = cdkManager;
        this.logManager = logManager;
        this.dataFolder = dataFolder;
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
            sendMessageToSender(sender, "用法: /cdk create single <id> <数量> \"<命令1|命令2|...>\" [有效时间]");
            sendMessageToSender(sender, "用法: /cdk create multiple <name|random> <id> <数量> \"<命令1|命令2|...>\" [有效时间]");
            sendMessageToSender(sender, "");
            sendMessageToSender(sender, "示例: /cdk create single 兑换1钻石 5 \"give %player% diamond 1\" 2024-12-01 10:00");
            sendMessageToSender(sender, "示例: /cdk create multiple vip666 兑换10钻石 999 \"give %player% diamond 10\" 2024-12-01 10:00");
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
                sendMessageToSender(sender, "数量必须是一个有效的数字！");
                return true;
            }
        } else if (type.equals("multiple")) {
            name = args[2]; // 对于 multiple 类型，第二个参数是名称
            id = args[3]; // 第三个参数是 id
            try {
                quantity = Integer.parseInt(args[4]); // 第四个参数是数量
            } catch (NumberFormatException e) {
                sendMessageToSender(sender, "数量必须是一个有效的数字！");
                return true;
            }
        } else {
            sendMessageToSender(sender, "无效的 CDK 类型！请使用 'single' 或 'multiple'。");
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
            sendMessageToSender(sender, "命令参数不能为空！");
            return true;
        }

        // 处理有效时间
        Date expirationDate = null;
        if (expirationDateString != null) {
            expirationDate = parseDate(expirationDateString);
            if (expirationDate == null) {
                sendMessageToSender(sender, "无效的时间格式，请使用 yyyy-MM-dd HH:mm 格式。");
                return true;
            }
        }

        // 创建 CDK
        if (type.equals("single")) {
            for (int i = 0; i < quantity; i++) {
                String cdkName = cdkManager.generateUniqueRandomCDKName();
                cdkManager.createCDK(id, cdkName, 1, true, commands, expirationDate);
            }
            sendMessageToSender(sender, "成功创建 " + quantity + " 个一次性 CDK。");
        } else if (type.equals("multiple")) {
            String cdkName = name.equalsIgnoreCase("random") ? cdkManager.generateUniqueRandomCDKName() : name;
            cdkManager.createCDK(id, cdkName, quantity, false, commands, expirationDate);
            sendMessageToSender(sender, "多次使用 CDK 创建成功: " + cdkName);
        } else {
            sendMessageToSender(sender, "无效的 CDK 类型！请使用 'single' 或 'multiple'。");
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
            sendMessageToSender(sender, "用法: /cdk add <id> <数量>");
            return true;
        }

        String id = args[1];
        int quantity;

        try {
            quantity = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendMessageToSender(sender, "数量必须是一个有效的数字！");
            return true;
        }

        Map<String, CDK> cdkGroup = cdkManager.findCDKGroupById(id);
        if (cdkGroup == null || cdkGroup.isEmpty()) {
            sendMessageToSender(sender, "未找到对应的 CDK。");
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
        sendMessageToSender(sender, "成功添加 " + quantity + " 次使用次数到 CDK: " + cdk.getName());

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
            sender.sendMessage("此命令只能由玩家执行！");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage("用法: /cdk use <CDK>");
            return true;
        }

        String cdkName = args[1];
        CDK usedCDK = cdkManager.findCDKByName(cdkName);

        if (usedCDK == null) {
            player.sendMessage("无效的 CDK。");
            return true;
        }

        if (usedCDK.isExpired()) {
            player.sendMessage("此 CDK 已过期。");
            return true;
        }

        if (usedCDK.hasPlayerRedeemed(player.getName())) {
            player.sendMessage("您已经兑换过此 CDK。");
            return true;
        }

        // 执行命令并替换 %player% 占位符
        String[] commands = usedCDK.getCommands().split("\\|");
        for (String command : commands) {
            String parsedCommand = command.replace("%player%", player.getName());
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

        // 记录使用日志
        logManager.logCDKUsage(player.getName(), usedCDK);
        player.sendMessage("成功使用 CDK: " + cdkName);

        return true;
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
            sendMessageToSender(sender, "用法: /cdk delete id <ID> 或 /cdk delete cdk <CDK名称>");
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
                    sendMessageToSender(sender, "成功删除 ID: " + target + " 及其所有 CDK");
                } else {
                    sendMessageToSender(sender, "未找到 ID: " + target);
                }
                break;

            case "cdk":
                // 删除具体的CDK
                found = cdkManager.deleteByCDKName(target);
                if (found) {
                    sendMessageToSender(sender, "成功删除 CDK: " + target);
                } else {
                    sendMessageToSender(sender, "未找到 CDK: " + target);
                }
                break;

            default:
                sendMessageToSender(sender, "未知的删除类型: " + deleteType);
                break;
        }

        return true;
    }

    /**
     * 处理列出CDK命令
     *
     * @param sender 命令发送者
     * @return 命令执行结果
     */
    public boolean handleListCommand(CommandSender sender) {
        Map<String, Map<String, CDK>> allCDKs = cdkManager.getAllCDKs();
        if (allCDKs.isEmpty()) {
            sendMessageToSender(sender, "当前没有可用的 CDK。");
            return true;
        }

        sendMessageToSender(sender, "当前可用的 CDK 列表:");
        for (Map<String, CDK> cdkGroup : allCDKs.values()) {
            for (CDK cdk : cdkGroup.values()) {
                String type = cdk.isSingleUse() ? "一次性" : "多次兑换";
                String quantityInfo = cdk.isSingleUse() ? "" : " 剩余兑换次数: " + cdk.getQuantity();
                sendMessageToSender(sender, "- " + cdk.getName() + " (ID: " + cdk.getId() + ", 类型: " + type + ")" + quantityInfo);
            }
        }

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
        sendMessageToSender(sender, "CDK 配置已重新加载！");
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
            sendMessageToSender(sender, "导出 CDK 列表时出错！请检查权限或文件系统。");
            e.printStackTrace();
            return true;
        }

        sendMessageToSender(sender, "CDK 列表已成功导出到 " + exportFile.getName() + "。");
        return true;
    }

    /**
     * 显示帮助信息
     *
     * @param sender 命令发送者
     * @return 命令执行结果
     */
    public boolean displayHelp(CommandSender sender) {
        // 定义帮助信息内容
        List<String> helpMessages = Arrays.asList(
                "/cdk create single <id> <数量> \"<命令1|命令2|...>\" [有效时间]",
                "/cdk create multiple <name|random> <id> <数量> \"<命令1|命令2|...>\" [有效时间]",
                "/cdk add <id> <数量> - 批量生成/添加使用次数",
                "/cdk delete cdk <CDK名称> - 删除 CDK",
                "/cdk delete id <id> - 删除此 id 下的所有 CDK",
                "/cdk list - 查看所有 CDK",
                "/cdk use <CDK名称> - 使用 CDK",
                "/cdk reload - 重新加载 CDK 配置",
                "/cdk export - 导出 CDK 配置和日志",
                "/cdk help - 显示此帮助信息"
        );

        // 发送帮助信息给 sender
        sendMessageToSender(sender, "§aKukeCDK 插件帮助:");
        for (String message : helpMessages) {
            sendMessageToSender(sender, message);
        }

        return true;
    }

    /**
     * 解析日期字符串
     *
     * @param dateString 日期字符串
     * @return 解析后的日期对象
     */
    private Date parseDate(String dateString) {
        System.out.println("解析的时间字符串: " + dateString);
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
        if (sender instanceof Player) {
            // 如果是玩家，发送带颜色的消息
            Player player = (Player) sender;
            player.sendMessage(message);
        } else {
            // 如果是控制台，发送普通文本消息
            sender.sendMessage(message);
        }
    }
}