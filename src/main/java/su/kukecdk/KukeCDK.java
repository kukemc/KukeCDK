package su.kukecdk;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import su.kukecdk.metrics.Metrics;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public final class KukeCDK extends JavaPlugin implements CommandExecutor {
    private FileConfiguration cdkConfig;
    private File cdkFile;
    private final Map<String, Map<String, CDK>> cdkMap = new HashMap<>();
    private File logFile;
    private FileConfiguration logConfig;
    private FileConfiguration config;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        int pluginId = 23812; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        System.out.println("██╗  ██╗██╗   ██╗██╗  ██╗███████╗ ██████╗██████╗ ██╗  ██╗");
        System.out.println("██║ ██╔╝██║   ██║██║ ██╔╝██╔════╝██╔════╝██╔══██╗██║ ██╔╝");
        System.out.println("█████╔╝ ██║   ██║█████╔╝ █████╗  ██║     ██║  ██║█████╔╝ ");
        System.out.println("██╔═██╗ ██║   ██║██╔═██╗ ██╔══╝  ██║     ██║  ██║██╔═██╗ ");
        System.out.println("██║  ██╗╚██████╔╝██║  ██╗███████╗╚██████╗██████╔╝██║  ██╗");
        System.out.println("╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝ ╚═════╝╚═════╝ ╚═╝  ╚═╝");
        System.out.println("KukeCDK v" + getDescription().getVersion() + " by KukeMC");
        System.out.println("欢迎使用 KukeCDK");

        createConfig();
        loadCDKs();
        createLogFile();
        createMessages();

        getCommand("cdk").setExecutor(this);
        getCommand("cdk").setTabCompleter(new CDKTabCompleter());

        // 定期检查过期的 CDK
        Bukkit.getScheduler().runTaskTimer(this, this::removeExpiredCDKs, 6000, 6000); // 每五分钟检查一次

    }

    @Override
    public void onDisable() {
        saveCDKs();
        saveLog();
    }

    private void createConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void createMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void loadCDKs() {
        cdkFile = new File(getDataFolder(), "cdk.yml");
        if (!cdkFile.exists()) {
            try {
                cdkFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        cdkConfig = YamlConfiguration.loadConfiguration(cdkFile);

        cdkMap.clear();
        for (String id : cdkConfig.getKeys(false)) {
            cdkConfig.getConfigurationSection(id).getKeys(false).forEach(cdkName -> {
                String name = cdkConfig.getString(id + "." + cdkName + ".name");
                int quantity = cdkConfig.getInt(id + "." + cdkName + ".quantity");
                boolean isSingleUse = cdkConfig.getBoolean(id + "." + cdkName + ".single");
                String commands = cdkConfig.getString(id + "." + cdkName + ".commands");
                Date expirationDate = null;

                String dateStr = cdkConfig.getString(id + "." + cdkName + ".expiration");
                if (dateStr != null) {
                    try {
                        expirationDate = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(dateStr);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }

                CDK cdk = new CDK(id, name, quantity, isSingleUse, commands, expirationDate);
                cdkMap.computeIfAbsent(id, k -> new HashMap<>()).put(name, cdk);
            });
        }
    }

    private void saveCDKs() {
        cdkConfig.getKeys(false).forEach(key -> cdkConfig.set(key, null)); // 清空现有内容，避免累积
        for (Map.Entry<String, Map<String, CDK>> entry : cdkMap.entrySet()) {
            String id = entry.getKey();
            for (CDK cdk : entry.getValue().values()) {
                String basePath = id + "." + cdk.name;
                cdkConfig.set(basePath + ".name", cdk.name);
                cdkConfig.set(basePath + ".quantity", cdk.quantity);
                cdkConfig.set(basePath + ".single", cdk.isSingleUse);
                cdkConfig.set(basePath + ".commands", cdk.commands);
                if (cdk.expirationDate != null) {
                    cdkConfig.set(basePath + ".expiration", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(cdk.expirationDate));
                }
                // 保存已兑换玩家列表
                cdkConfig.set(basePath + ".redeemedPlayers", new ArrayList<>(cdk.redeemedPlayers)); // 将 Set 转换为 List
            }
        }
        try {
            cdkConfig.save(cdkFile);
        } catch (IOException e) {
            getLogger().severe("保存 CDK 文件时出错！");
            e.printStackTrace();
        }
    }

    private void createLogFile() {
        logFile = new File(getDataFolder(), "log.yml");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        logConfig = YamlConfiguration.loadConfiguration(logFile);
    }

    private void saveLog() {
        try {
            logConfig.save(logFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // 检查 sender 是否是玩家
        Player player = null;
        if (sender instanceof Player) {
            player = (Player) sender;
        }

        if (args.length == 0) {
            return displayHelp(sender);
        }

        // 权限检查部分
        switch (args[0].toLowerCase()) {
            case "create":
            case "add":
            case "delete":
            case "list":
            case "reload":
            case "export":
                // 判断是否具有 admin 权限，控制台也需要有相应权限
                if (!sender.hasPermission("kukecdk.admin." + args[0].toLowerCase())) {
                    sendMessageToSender(sender, "permission-denied");
                    return true;
                }
                break;
            case "use":
                // /cdk use 需要 kukecdk.use 权限
                if (player != null && !player.hasPermission("kukecdk.use")) {
                    sendMessageToSender(sender, "permission-denied-use");
                    return true;
                } else if (player == null && !sender.hasPermission("kukecdk.use")) {
                    sendMessageToSender(sender, "permission-denied-use");
                    return true;
                }
                break;
            default:
                sendMessageToSender(sender, "unknown-command");
                return true;
        }

        // 其他命令处理代码
        switch (args[0].toLowerCase()) {
            case "create":
                // 如果是玩家，传递 player；如果是控制台，传递 sender
                return handleCreateCommand(player != null ? player : sender, args);
            case "add":
                return handleAddCommand(player != null ? player : sender, args);
            case "delete":
                return handleDeleteCommand(player != null ? player : sender, args);
            case "list":
                return handleListCommand(player != null ? player : sender);
            case "reload":
                return handleReloadCommand(player != null ? player : sender);
            case "export":
                return handleExportCommand(player != null ? player : sender);
            case "use":
                if (!(sender instanceof Player)) {
                    // 如果不是玩家，返回提示消息并终止执行
                    sendMessageToSender(sender, "player-only");
                    return true;
                }
                return handleUseCommand(player, args);
            case "help":
                return displayHelp(player != null ? player : sender);
            default:
                sendMessageToSender(sender, "unknown-command");
                return true;
        }
    }

    private void removeExpiredCDKs() {
        for (Map<String, CDK> cdkGroup : cdkMap.values()) {
            cdkGroup.values().removeIf(cdk -> cdk.expirationDate != null && new Date().after(cdk.expirationDate));
        }
        saveCDKs();
    }

    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sendMessageToSender(sender, "create-command-usage-single");
            sendMessageToSender(sender, "create-command-usage-multiple");
            sendMessageToSender(sender, "");
            sendMessageToSender(sender, "create-command-example-single");
            sendMessageToSender(sender, "create-command-example-multiple");
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
            sendMessageToSender(sender, "invalid-cdk-type");
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
                String cdkName = generateUniqueRandomCDKName();
                CDK cdk = new CDK(id, cdkName, 1, true, commands, expirationDate);
                cdkMap.computeIfAbsent(id, k -> new HashMap<>()).put(cdkName, cdk);
            }
            saveCDKs();
            sendMessageToSender(sender, "create-single-success", quantity);
        } else if (type.equals("multiple")) {
            String cdkName = name.equalsIgnoreCase("random") ? generateUniqueRandomCDKName() : name;
            CDK cdk = new CDK(id, cdkName, quantity, false, commands, expirationDate);
            cdkMap.computeIfAbsent(id, k -> new HashMap<>()).put(cdkName, cdk);
            saveCDKs();
            sendMessageToSender(sender, "create-multiple-success", cdkName);
        } else {
            sendMessageToSender(sender, "invalid-cdk-type");
            return true;
        }

        return true;
    }


    private String generateUniqueRandomCDKName() {
        String cdkName;
        do {
            cdkName = generateRandomCDKName();
        } while (cdkMap.containsKey(cdkName)); // 避免冲突
        return cdkName;
    }

    private String generateRandomCDKName() {
        int length = config.getInt("default_cdk_name_length", 8);
        String characters = config.getString("default_cdk_characters", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        StringBuilder result = new StringBuilder(length);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            result.append(characters.charAt(random.nextInt(characters.length())));
        }
        return result.toString();
    }

    private Date parseDate(String dateString) {
        System.out.println("解析的时间字符串: " + dateString);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // 根据需要更改时区
        dateFormat.setLenient(false); // 确保严格解析
        try {
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            return null; // 返回 null 表示解析失败
        }
    }


    private void logCDKUsage(String playerName, CDK cdk) {
        String path = playerName + ".used";
        List<String> usedCDKs = logConfig.getStringList(path);

        // 获取当前时间
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());

        // 使用 toString 方法记录 CDK 使用情况和兑换时间
        usedCDKs.add(cdk.toString() + " (兑换时间: " + currentTime + ")");
        logConfig.set(path, usedCDKs);

        try {
            logConfig.save(logFile);
        } catch (IOException e) {
            getLogger().severe("保存日志文件时出错！");
            e.printStackTrace();
        }
    }

    private boolean handleAddCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessageToSender(sender, "add-usage");
            return true;
        }

        String id = args[1];
        int quantity;

        try {
            quantity = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendMessageToSender(sender, "invalid-number");
            return true;
        }

        Map<String, CDK> cdkGroup = cdkMap.get(id);
        if (cdkGroup == null || cdkGroup.isEmpty()) {
            sendMessageToSender(sender, "add-not-found");
            return true;
        }

        CDK cdk = cdkGroup.values().iterator().next(); // 自动读取第一个 CDK

        if (cdk.isSingleUse) {
            // 如果是一次性CDK，批量生成新的CDK
            for (int i = 0; i < quantity; i++) {
                String newCdkName = generateUniqueRandomCDKName();
                CDK newCdk = new CDK(id, newCdkName, 1, true, cdk.commands, cdk.expirationDate);
                cdkMap.computeIfAbsent(id, k -> new HashMap<>()).put(newCdkName, newCdk);
            }
        } else {
            // 如果是多次使用的CDK，直接增加数量
            cdk.quantity += quantity;
        }

        saveCDKs();
        sendMessageToSender(sender, "add-success", quantity, cdk.name);

        return true;
    }

    private boolean handleUseCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendMessageToSender(player, "use-usage");
            return true;
        }

        String cdkName = args[1];
        CDK usedCDK = null;
        for (Map<String, CDK> cdkGroup : cdkMap.values()) {
            if (cdkGroup.containsKey(cdkName)) {
                usedCDK = cdkGroup.get(cdkName);
                break;
            }
        }

        if (usedCDK == null) {
            sendMessageToSender(player, "use-invalid");
            return true;
        }

        if (usedCDK.expirationDate != null && new Date().after(usedCDK.expirationDate)) {
            sendMessageToSender(player, "use-expired");
            return true;
        }

        if (usedCDK.redeemedPlayers.contains(player.getName())) {
            sendMessageToSender(player, "use-already-redeemed");
            return true;
        }

        // 执行命令并替换 %player% 占位符
        String[] commands = usedCDK.commands.split("\\|");
        for (String command : commands) {
            String parsedCommand = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
        }

        // 更新 CDK 使用信息
        usedCDK.quantity--;
        if (usedCDK.isSingleUse || usedCDK.quantity <= 0) {
            cdkMap.get(usedCDK.id).remove(usedCDK.name);
        }

        // 添加已兑换玩家
        usedCDK.redeemedPlayers.add(player.getName());
        saveCDKs();

        // 记录使用日志
        logCDKUsage(player.getName(), usedCDK);
        sendMessageToSender(player, "use-success", cdkName);

        return true;
    }


    private boolean handleDeleteCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessageToSender(sender, "delete-usage");
            return true;
        }

        String deleteType = args[1];
        String target = args[2];
        boolean found = false;

        switch (deleteType.toLowerCase()) {
            case "id":
                // 删除整个ID及其下的所有CDK
                if (cdkMap.containsKey(target)) {
                    cdkMap.remove(target);
                    saveCDKs();
                    sendMessageToSender(sender, "delete-id-success", target);
                    found = true;
                } else {
                    sendMessageToSender(sender, "delete-id-not-found", target);
                }
                break;

            case "cdk":
                // 遍历寻找并删除具体的CDK
                for (Map.Entry<String, Map<String, CDK>> entry : cdkMap.entrySet()) {
                    if (entry.getValue().containsKey(target)) {
                        entry.getValue().remove(target);
                        saveCDKs();
                        sendMessageToSender(sender, "delete-cdk-success", target);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    sendMessageToSender(sender, "delete-cdk-not-found", target);
                }
                break;

            default:
                sendMessageToSender(sender, "delete-unknown-type", deleteType);
                break;
        }

        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        if (cdkMap.isEmpty()) {
            sendMessageToSender(sender, "list-empty");
            return true;
        }

        sendMessageToSender(sender, "list-header");
        for (Map<String, CDK> cdkGroup : cdkMap.values()) {
            for (CDK cdk : cdkGroup.values()) {
                String type = cdk.isSingleUse ? "一次性" : "多次兑换";
                String quantityInfo = cdk.isSingleUse ? "" : " 剩余兑换次数: " + cdk.quantity;
                sendMessageToSender(sender, "- " + cdk.name + " (ID: " + cdk.id + ", 类型: " + type + ")" + quantityInfo);
            }
        }

        return true;
    }


    private boolean handleReloadCommand(CommandSender sender) {
        // 重新加载配置文件
        reloadConfig();
        config = getConfig(); // 更新 config 变量的引用

        // 重新加载 messages.yml 文件
        File messagesFile = new File(getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        loadCDKs();
        createLogFile();
        sendMessageToSender(sender, "reload-success");
        return true;
    }


    private boolean handleExportCommand(CommandSender sender) {
        // 创建导出文件
        File exportFile = new File(getDataFolder(), "export.yml");
        YamlConfiguration exportConfig = YamlConfiguration.loadConfiguration(exportFile);

        for (Map.Entry<String, Map<String, CDK>> entry : cdkMap.entrySet()) {
            String id = entry.getKey();
            Map<String, CDK> cdkGroup = entry.getValue();

            // 创建一个列表来存储 CDK 名称
            List<String> cdkList = new ArrayList<>();
            for (CDK cdk : cdkGroup.values()) {
                cdkList.add(cdk.name);
            }

            // 将 CDK 列表保存到 exportConfig 中
            exportConfig.set(id, cdkList);
        }

        // 保存到 export.yml 文件
        try {
            exportConfig.save(exportFile);
        } catch (IOException e) {
            sendMessageToSender(sender, "export-error");
            e.printStackTrace();
            return true;
        }

        sendMessageToSender(sender, "export-success", exportFile.getName());
        return true;
    }

    // 修改 displayHelp 方法，使用统一的处理逻辑
    private boolean displayHelp(CommandSender sender) {
        // 发送帮助信息给 sender
        sendMessageToSender(sender, "help-header");
        sendMessageToSender(sender, "help-create-single");
        sendMessageToSender(sender, "help-create-multiple");
        sendMessageToSender(sender, "help-add");
        sendMessageToSender(sender, "help-delete-cdk");
        sendMessageToSender(sender, "help-delete-id");
        sendMessageToSender(sender, "help-list");
        sendMessageToSender(sender, "help-use");
        sendMessageToSender(sender, "help-reload");
        sendMessageToSender(sender, "help-export");
        sendMessageToSender(sender, "help-help");

        return true;
    }

    // 自动判断 sender 是玩家还是控制台，并发送消息
    private void sendMessageToSender(CommandSender sender, String key, Object... placeholders) {
        if (Objects.equals(key, "")) {
            sender.sendMessage("");
            return;
        }

        String message = messages.getString(key);
        if (message != null) {
            for (int i = 0; i < placeholders.length; i++) {
                message = message.replace("{" + i + "}", placeholders[i].toString());
            }
            sender.sendMessage(message);
        } else {
            sender.sendMessage("Message key not found: " + key);
        }
    }
}
