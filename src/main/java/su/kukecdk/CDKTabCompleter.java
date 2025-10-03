package su.kukecdk;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import su.kukecdk.manager.CDKManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CDKTabCompleter implements TabCompleter {
    private CDKManager cdkManager;
    
    public void setCDKManager(CDKManager cdkManager) {
        this.cdkManager = cdkManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 第一个参数 - 主命令
            for (String cmd : ADMIN_COMMANDS) {
                if (sender.hasPermission("kukecdk.admin." + cmd) && 
                    (args[0].isEmpty() || cmd.startsWith(args[0].toLowerCase()))) {
                    completions.add(cmd);
                }
            }
            
            if (sender.hasPermission("kukecdk.use")) {
                if (args[0].isEmpty() || "use".startsWith(args[0].toLowerCase())) {
                    completions.add("use");
                }
                if (args[0].isEmpty() || "verify".startsWith(args[0].toLowerCase())) {
                    completions.add("verify");
                }
            }
            
            // help命令对所有人可用
            if (args[0].isEmpty() || "help".startsWith(args[0].toLowerCase())) {
                completions.add("help");
            }
        } else if (args.length >= 2) {
            // 第二个及后续参数 - 子命令参数
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "create":
                    handleCreateCompletion(args, completions);
                    break;
                case "add":
                    handleAddCompletion(args, completions);
                    break;
                case "delete":
                    handleDeleteCompletion(args, completions);
                    break;
                case "migrate":
                    handleMigrateCompletion(args, completions);
                    break;
                case "list":
                    handleListCompletion(args, completions);
                    break;
                case "use":
                case "verify":
                    handleUseVerifyCompletion(args, completions);
                    break;
                case "help":
                    // help命令不需要额外参数
                    break;
            }
        }

        // 过滤结果
        if (!args[args.length - 1].isEmpty()) {
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return completions;
    }

    private void handleCreateCompletion(String[] args, List<String> completions) {
        if (args.length == 2) {
            if ("single".startsWith(args[1].toLowerCase()) || args[1].isEmpty()) {
                completions.add("single");
            }
            if ("multiple".startsWith(args[1].toLowerCase()) || args[1].isEmpty()) {
                completions.add("multiple");
            }
        } else if (args.length >= 3) {
            String type = args[1].toLowerCase();
            if ("single".equals(type)) {
                handleSingleCreateCompletion(args, completions);
            } else if ("multiple".equals(type)) {
                handleMultipleCreateCompletion(args, completions);
            }
        }
    }

    private void handleListCompletion(String[] args, List<String> completions) {
        if (args.length == 2 && cdkManager != null) {
            // 添加已存在的CDK ID列表
            cdkManager.getAllCDKs().keySet().stream()
                .filter(id -> args[1].isEmpty() || id.startsWith(args[1]))
                .forEach(completions::add);
            // 如果没有匹配的ID，添加占位符
            if (completions.isEmpty()) {
                completions.add("<id>");
            }
        } else if (args.length == 2 && cdkManager == null) {
            completions.add("<id>");
        }
    }

    /**
     * 智能解析命令参数，正确处理引号内的内容
     * @param args 原始参数数组
     * @return 解析后的参数位置
     */
    private int getActualParameterPosition(String[] args) {
        int position = 0;
        boolean inQuotes = false;
        
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            
            // 检查是否开始引号
            if (!inQuotes && arg.startsWith("\"")) {
                inQuotes = true;
                position++;
                // 如果引号在同一个参数中结束
                if (arg.endsWith("\"") && arg.length() > 1) {
                    inQuotes = false;
                }
            } else if (inQuotes) {
                // 在引号内，检查是否结束
                if (arg.endsWith("\"")) {
                    inQuotes = false;
                }
                // 引号内的参数不增加位置计数
            } else {
                // 普通参数
                position++;
            }
        }
        
        return position;
    }

    private void handleSingleCreateCompletion(String[] args, List<String> completions) {
        int position = getActualParameterPosition(args);
        switch (position) {
            case 1:
                completions.add("<id>");
                break;
            case 2:
                completions.add("<数量>");
                break;
            case 3:
                completions.add("\"<命令1|命令2|...>\"");
                break;
            case 4:
            case 5:
                completions.add("[有效时间 yyyy-MM-dd HH:mm]");
                break;
        }
    }

    private void handleMultipleCreateCompletion(String[] args, List<String> completions) {
        int position = getActualParameterPosition(args);
        switch (position) {
            case 1:
                completions.add("<name>");
                completions.add("random");
                break;
            case 2:
                completions.add("<id>");
                break;
            case 3:
                completions.add("<数量>");
                break;
            case 4:
                completions.add("\"<命令1|命令2|...>\"");
                break;
            case 5:
            case 6:
                completions.add("[有效时间 yyyy-MM-dd HH:mm]");
                break;
        }
    }

    private void handleAddCompletion(String[] args, List<String> completions) {
        if (args.length == 2 && cdkManager != null) {
            // 添加已存在的CDK ID列表
            cdkManager.getAllCDKs().keySet().stream()
                .filter(id -> args[1].isEmpty() || id.startsWith(args[1]))
                .forEach(completions::add);
            // 如果没有匹配的ID，添加占位符
            if (completions.isEmpty()) {
                completions.add("<id>");
            }
        } else if (args.length == 2 && cdkManager == null) {
            completions.add("<id>");
        } else if (args.length == 3) {
            completions.add("<数量>");
        }
    }

    private void handleDeleteCompletion(String[] args, List<String> completions) {
        if (args.length == 2) {
            if ("cdk".startsWith(args[1].toLowerCase()) || args[1].isEmpty()) {
                completions.add("cdk");
            }
            if ("id".startsWith(args[1].toLowerCase()) || args[1].isEmpty()) {
                completions.add("id");
            }
            // 如果没有匹配的类型，添加占位符
            if (completions.isEmpty()) {
                completions.add("<cdk|id>");
            }
        } else if (args.length == 3) {
            String deleteType = args[1].toLowerCase();
            if ("id".equals(deleteType) && cdkManager != null) {
                // 添加已存在的CDK ID列表
                cdkManager.getAllCDKs().keySet().stream()
                    .filter(id -> args[2].isEmpty() || id.startsWith(args[2]))
                    .forEach(completions::add);
                // 如果没有匹配的ID，添加占位符
                if (completions.isEmpty()) {
                    completions.add("<id>");
                }
            } else if ("cdk".equals(deleteType) && cdkManager != null) {
                // 添加已存在的CDK名称列表
                cdkManager.getAllCDKs().values().stream()
                    .flatMap(map -> map.keySet().stream())
                    .filter(name -> args[2].isEmpty() || name.startsWith(args[2]))
                    .forEach(completions::add);
                // 如果没有匹配的名称，添加占位符
                if (completions.isEmpty()) {
                    completions.add("<cdk名称>");
                }
            } else {
                // 如果无法获取实际数据，添加占位符
                if ("id".equals(deleteType)) {
                    completions.add("<id>");
                } else if ("cdk".equals(deleteType)) {
                    completions.add("<cdk名称>");
                } else {
                    completions.add("<具体内容>");
                }
            }
        }
    }

    private void handleMigrateCompletion(String[] args, List<String> completions) {
        if (args.length == 2 || args.length == 3) {
            int position = args.length - 1; // 位置索引 (1或2)
            for (String mode : MIGRATE_OPTIONS) {
                if (args[position].isEmpty() || mode.startsWith(args[position].toLowerCase())) {
                    completions.add(mode);
                }
            }
            // 如果没有匹配的模式，添加占位符
            if (completions.isEmpty()) {
                completions.add("<yaml|sqlite|mysql>");
            }
        }
    }

    private void handleUseVerifyCompletion(String[] args, List<String> completions) {
        if (args.length == 2 && cdkManager != null) {
            // 添加已存在的CDK名称列表
            cdkManager.getAllCDKs().values().stream()
                .flatMap(map -> map.keySet().stream())
                .filter(name -> args[1].isEmpty() || name.startsWith(args[1]))
                .forEach(completions::add);
            // 如果没有匹配的名称，添加占位符
            if (completions.isEmpty()) {
                completions.add("<cdk名称>");
            }
        } else if (args.length == 2 && cdkManager == null) {
            completions.add("<cdk名称>");
        }
    }

    private final List<String> ADMIN_COMMANDS = Arrays.asList(
            "create", "add", "delete", "list", "reload", "export", "migrate"
    );
    
    private final List<String> MIGRATE_OPTIONS = Arrays.asList(
            "yaml", "sqlite", "mysql"
    );
}