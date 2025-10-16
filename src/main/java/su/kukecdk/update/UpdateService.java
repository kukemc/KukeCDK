package su.kukecdk.update;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 启动后检查 GitHub 最新版本，并在管理员加入时提示更新。
 */
public class UpdateService implements Listener {
    private final JavaPlugin plugin;
    private volatile boolean updateAvailable = false;
    private volatile String latestTag = null;

    public UpdateService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 启动异步检查最新版本 */
    public void init() {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String tag = fetchLatestTag();
                    if (tag == null || tag.trim().isEmpty()) return;
                    latestTag = tag.trim();
                    String local = normalizeVersion(plugin.getDescription().getVersion());
                    String remote = normalizeVersion(latestTag);
                    if (!safeEquals(local, remote)) {
                        updateAvailable = true;
                        String url = "https://github.com/kukemc/KukeCDK/releases/tag/" + latestTag;
                        plugin.getLogger().warning("检测到新版本: " + latestTag + "，当前版本: v" + local + "，请前往更新: " + url);
                    } else {
                        updateAvailable = false;
                    }
                } catch (Throwable t) {
                    // 静默错误，不影响插件启动
                    plugin.getLogger().fine("版本检查失败: " + t.getMessage());
                }
            });
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        try {
            if (!updateAvailable) return;
            Player p = event.getPlayer();
            if (p == null) return;
            // 管理员判断：OP 或拥有权限 kukecdk.admin
            if (p.isOp() || p.hasPermission("kukecdk.admin")) {
                String url = "https://github.com/kukemc/KukeCDK/releases/tag/" + (latestTag == null ? "" : latestTag);
                String localNorm = normalizeVersion(plugin.getDescription().getVersion());
                p.sendMessage(color("&e[KukeCDK] 检测到新版本: &b" + latestTag));
                p.sendMessage(color("&e[KukeCDK] 当前版本: &bv" + localNorm + "，请前往更新下载:"));
                p.sendMessage(color("&9" + url));
            }
        } catch (Throwable ignored) {}
    }

    private String fetchLatestTag() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.github.com/repos/kukemc/KukeCDK/releases/latest");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "KukeCDK-VersionCheck");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String json = sb.toString();
                // 简单解析 tag_name 字段
                String key = "\"tag_name\"";
                int i = json.indexOf(key);
                if (i >= 0) {
                    int colon = json.indexOf(':', i);
                    int quote1 = json.indexOf('"', colon + 1);
                    int quote2 = json.indexOf('"', quote1 + 1);
                    if (quote1 > 0 && quote2 > quote1) {
                        return json.substring(quote1 + 1, quote2);
                    }
                }
                return null;
            }
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (conn != null) conn.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private String normalizeVersion(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.startsWith("v")) s = s.substring(1);
        // 去除 -snapshot/-beta 等后缀
        int dash = s.indexOf('-');
        if (dash > 0) s = s.substring(0, dash);
        return s;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equalsIgnoreCase(b);
    }

    private String color(String s) {
        try { return org.bukkit.ChatColor.translateAlternateColorCodes('&', s); } catch (Throwable t) { return s; }
    }
}