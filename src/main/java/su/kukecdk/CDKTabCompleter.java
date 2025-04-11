package su.kukecdk;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CDKTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        boolean inQuotes = false;

        // 引号状态检测
        for (String arg : args) {
            boolean hasStart = arg.startsWith("\"");
            boolean hasEnd = arg.endsWith("\"");

            if (hasStart && !hasEnd) inQuotes = true;
            else if (!hasStart && hasEnd) inQuotes = false;
            else if (hasStart && hasEnd && arg.length() > 1) inQuotes = false;
        }

        if (inQuotes) return suggestions;

        // 计算有效参数位置
        int effectivePos = calculateEffectivePosition(args);

        // 生成建议
        generateSuggestions(args, effectivePos, suggestions);

        // 过滤当前输入
        return filterSuggestions(suggestions, args.length > 0 ? args[args.length-1] : "");
    }

    private int calculateEffectivePosition(String[] args) {
        int pos = 0;
        boolean inQuote = false;
        for (String arg : args) {
            boolean start = arg.startsWith("\"");
            boolean end = arg.endsWith("\"");

            if (!inQuote && start) {
                pos++;
                inQuote = !end;
            } else if (inQuote && end) {
                inQuote = false;
            } else if (!inQuote) {
                pos++;
            }
        }
        return pos;
    }

    private void generateSuggestions(String[] args, int effectivePos, List<String> suggestions) {
        if (effectivePos == 1) {
            addRootCommands(suggestions);
        } else if (effectivePos >= 2 && args.length > 0) {
            String rootCmd = args[0].toLowerCase();
            switch (rootCmd) {
                case "create":
                    handleCreate(args, effectivePos, suggestions);
                    break;
                case "add":
                    handleAdd(effectivePos, suggestions);
                    break;
                case "delete":
                    handleDelete(effectivePos, suggestions);
                    break;
                case "use":
                    handleUse(effectivePos, suggestions);
                    break;
            }
        }
    }

    private void addRootCommands(List<String> list) {
        list.add("create");
        list.add("add");
        list.add("delete");
        list.add("list");
        list.add("use");
        list.add("reload");
        list.add("export");
        list.add("help");
    }

    private void handleCreate(String[] args, int pos, List<String> list) {
        if (pos == 2) {
            list.add("single");
            list.add("multiple");
        } else if (pos >= 3 && args.length > 1) {
            String sub = args[1].toLowerCase();
            if (sub.equals("single")) {
                handleSingle(pos, list);
            } else if (sub.equals("multiple")) {
                handleMultiple(pos, list);
            }
        }
    }

    private void handleSingle(int pos, List<String> list) {
        switch (pos) {
            case 3: list.add("<唯一ID>"); break;
            case 4: list.add("<生成数量>"); break;
            case 5: list.add("\"命令1|命令2|...\""); break;
            case 6: list.add("[有效时间 yyyy-MM-dd HH:mm]"); break;
        }
    }

    private void handleMultiple(int pos, List<String> list) {
        switch (pos) {
            case 3:
                list.add("<名称>");
                list.add("random");
                break;
            case 4: list.add("<起始ID>"); break;
            case 5: list.add("<生成数量>"); break;
            case 6: list.add("\"命令1|命令2|...\""); break;
            case 7: list.add("[有效时间 yyyy-MM-dd HH:mm]"); break;
        }
    }

    private void handleAdd(int pos, List<String> list) {
        switch (pos) {
            case 2: list.add("<id>"); break;
            case 3: list.add("<数量>"); break;
        }
    }

    private void handleDelete(int pos, List<String> list) {
        switch (pos) {
            case 2:
                list.add("cdk");
                list.add("id");
                break;
            case 3:
                list.add("<具体内容>");
                break;
        }
    }

    private void handleUse(int pos, List<String> list) {
        if (pos == 2) list.add("<CDK>");
    }

    private List<String> filterSuggestions(List<String> suggestions, String input) {
        String lowerInput = input.toLowerCase();
        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }
}