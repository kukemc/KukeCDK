package su.kukecdk;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class CDKTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("create");
            suggestions.add("add");
            suggestions.add("delete");
            suggestions.add("list");
            suggestions.add("use");
            suggestions.add("reload");
            suggestions.add("export");
            suggestions.add("help");
        }
        return suggestions;
    }
}
