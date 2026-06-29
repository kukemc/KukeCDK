package su.kukecdk.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import su.kukecdk.manager.CDKManager;
import su.kukecdk.manager.ConfigManager;
import su.kukecdk.manager.LogManager;
import su.kukecdk.model.CDK;
import su.kukecdk.util.FoliaSupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ApiServerManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CDKManager cdkManager;
    private final LogManager logManager;
    private final ReloadCallback reloadCallback;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final ApiRateLimiter rateLimiter = new ApiRateLimiter();
    private HttpServer server;
    private ExecutorService executorService;
    private ApiConfig apiConfig;
    private ApiAuthManager authManager;
    private volatile boolean active;

    public ApiServerManager(JavaPlugin plugin, ConfigManager configManager, CDKManager cdkManager, LogManager logManager, ReloadCallback reloadCallback) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cdkManager = cdkManager;
        this.logManager = logManager;
        this.reloadCallback = reloadCallback;
    }

    public synchronized void startIfEnabled() {
        apiConfig = ApiConfig.from(configManager.getConfig());
        if (!apiConfig.isEnabled()) return;
        try {
            authManager = new ApiAuthManager(apiConfig);
            if (hasUnsafeDefaultToken()) {
                plugin.getLogger().severe("KukeCDK API refused to start because a default token is configured. Change api.auth.tokens first.");
                return;
            }
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(apiConfig.getHost()), apiConfig.getPort()), 0);
            server.createContext("/", this::handle);
            executorService = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), r -> {
                Thread thread = new Thread(r, "KukeCDK-API");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executorService);
            server.start();
            active = true;
            plugin.getLogger().info("KukeCDK API server started at http://" + apiConfig.getHost() + ":" + apiConfig.getPort() + apiConfig.getBasePath());
            plugin.getLogger().info("KukeCDK API docs: http://" + apiConfig.getHost() + ":" + apiConfig.getPort() + apiConfig.getDocsPath());
            warnUnsafeConfig();
        } catch (Exception e) {
            plugin.getLogger().severe("KukeCDK API server failed to start: " + e.getMessage());
        }
    }

    public synchronized void restart() {
        stop();
        startIfEnabled();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.getLogger().info("KukeCDK API server stopped.");
        }
        active = false;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private boolean hasUnsafeDefaultToken() {
        if (!apiConfig.isAuthEnabled()) return false;
        for (ApiToken token : apiConfig.getTokens()) {
            if ("change-me-admin-token".equals(token.getToken()) || "change-me-readonly-token".equals(token.getToken())) return true;
        }
        return false;
    }

    private void warnUnsafeConfig() {
        if ("0.0.0.0".equals(apiConfig.getHost()) || "::".equals(apiConfig.getHost())) {
            plugin.getLogger().warning("KukeCDK API is bound to all interfaces. Use HTTPS reverse proxy, firewall and strong tokens.");
        }
        for (ApiToken token : apiConfig.getTokens()) {
            if ("change-me-admin-token".equals(token.getToken())) {
                plugin.getLogger().warning("KukeCDK API default token is still configured. The API will not start until it is changed.");
            }
        }
        if (("0.0.0.0".equals(apiConfig.getHost()) || "::".equals(apiConfig.getHost())) && (apiConfig.getIpAllowlist() == null || apiConfig.getIpAllowlist().isEmpty())) {
            plugin.getLogger().warning("KukeCDK API is public and ip_allowlist is empty. This exposes the API to every reachable address.");
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();
        String remote = exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
        try {
            addCorsHeaders(exchange);
            if ("OPTIONS".equals(method)) {
                sendText(exchange, 204, "", "text/plain; charset=utf-8");
                return;
            }
            if (!isIpAllowed(remote)) {
                sendJson(exchange, 403, ApiResponse.error("IP_FORBIDDEN", "IP is not allowed", requestId), requestId);
                audit(false, remote, method, path, 403, requestId, start, "ip forbidden");
                return;
            }
            if (apiConfig.isRateLimitEnabled() && !rateLimiter.allow(remote, apiConfig.getRequestsPerMinute())) {
                sendJson(exchange, 429, ApiResponse.error("RATE_LIMITED", "Too many requests", requestId), requestId);
                audit(false, remote, method, path, 429, requestId, start, "rate limited");
                return;
            }
            if (path.equals(apiConfig.getDocsPath()) || path.equals(apiConfig.getDocsPath() + "/")) {
                sendText(exchange, 200, ApiDocs.html(apiConfig), "text/html; charset=utf-8");
                return;
            }
            if (path.equals(apiConfig.getDocsPath() + "/openapi.json")) {
                sendText(exchange, 200, ApiDocs.openApiJson(apiConfig), "application/json; charset=utf-8");
                return;
            }
            if (!path.startsWith(apiConfig.getBasePath())) {
                sendJson(exchange, 404, ApiResponse.error("NOT_FOUND", "Endpoint not found", requestId), requestId);
                return;
            }
            Route route = Route.parse(method, trimBase(path));
            if (route == null) {
                sendJson(exchange, 404, ApiResponse.error("NOT_FOUND", "Endpoint not found", requestId), requestId);
                return;
            }
            ApiAuthManager.AuthResult auth = authManager.authenticate(exchange, route.scope);
            if (!auth.isAllowed()) {
                sendJson(exchange, auth.getStatus(), ApiResponse.error(auth.getStatus() == 401 ? "UNAUTHORIZED" : "FORBIDDEN", auth.getMessage(), requestId), requestId);
                audit(false, remote, method, path, auth.getStatus(), requestId, start, auth.getMessage());
                return;
            }
            Object data = dispatch(exchange, route);
            sendJson(exchange, route.status, ApiResponse.success(data, requestId), requestId);
            audit(true, remote, method, path, route.status, requestId, start, auth.getTokenName());
        } catch (BadRequest e) {
            sendJson(exchange, e.status, ApiResponse.error(e.code, e.getMessage(), requestId), requestId);
            audit(false, remote, method, path, e.status, requestId, start, e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("KukeCDK API error " + requestId + ": " + e.getMessage());
            sendJson(exchange, 500, ApiResponse.error("INTERNAL_ERROR", "Internal server error", requestId), requestId);
            audit(false, remote, method, path, 500, requestId, start, e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private Object dispatch(HttpExchange exchange, Route route) throws Exception {
        switch (route.name) {
            case "health": return health();
            case "version": return version();
            case "stats": return stats();
            case "listCdks": return listCdks(query(exchange));
            case "getCdk": return cdkData(requiredCdk(route.value), true);
            case "getById": return cdksById(route.value);
            case "createCdk": return createCdk(readJson(exchange));
            case "updateCdk": return updateCdk(route.value, readJson(exchange));
            case "deleteCdk": return deleteCdk(route.value);
            case "deleteById": return deleteById(route.value);
            case "addById": return addById(route.value, readJson(exchange));
            case "verify": return verify(route.value, readJson(exchange));
            case "redeem": return redeem(route.value, readJson(exchange));
            case "logs": return logs(query(exchange));
            case "playerLogs": return playerLogs(route.value);
            case "reload": return reload();
            default: throw new BadRequest(404, "NOT_FOUND", "Endpoint not found");
        }
    }

    private Map<String, Object> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("plugin", plugin.getName());
        data.put("version", plugin.getDescription().getVersion());
        data.put("storageMode", cdkManager.getStorageMode());
        return data;
    }

    private Map<String, Object> version() {
        Map<String, Object> data = health();
        data.put("serverVersion", Bukkit.getVersion());
        data.put("bukkitVersion", Bukkit.getBukkitVersion());
        return data;
    }

    private Map<String, Object> stats() {
        Map<String, Map<String, CDK>> all = cdkManager.getAllCDKs();
        int total = 0, expired = 0, single = 0, multiple = 0;
        for (Map<String, CDK> group : all.values()) {
            for (CDK cdk : group.values()) {
                total++;
                if (cdk.isExpired()) expired++;
                if (cdk.isSingleUse()) single++; else multiple++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalCdks", total);
        data.put("totalGroups", all.size());
        data.put("expiredCdks", expired);
        data.put("singleUseCdks", single);
        data.put("multipleUseCdks", multiple);
        data.put("storageMode", cdkManager.getStorageMode());
        return data;
    }

    private Map<String, Object> listCdks(Map<String, String> query) {
        String id = query.get("id");
        int page = parseInt(query.get("page"), 1);
        int pageSize = Math.min(200, parseInt(query.get("pageSize"), 50));
        boolean includeRedeemed = Boolean.parseBoolean(query.getOrDefault("includeRedeemedPlayers", "false"));
        List<Object> allItems = new ArrayList<>();
        Map<String, Map<String, CDK>> all = cdkManager.getAllCDKs();
        for (Map.Entry<String, Map<String, CDK>> group : all.entrySet()) {
            if (id != null && !id.equals(group.getKey())) continue;
            for (CDK cdk : group.getValue().values()) allItems.add(cdkData(cdk, includeRedeemed));
        }
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(allItems.size(), from + pageSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("total", allItems.size());
        data.put("items", from >= allItems.size() ? new ArrayList<>() : allItems.subList(from, to));
        return data;
    }

    private Object cdksById(String id) throws BadRequest {
        Map<String, CDK> group = cdkManager.findCDKGroupById(id);
        if (group == null) throw new BadRequest(404, "CDK_ID_NOT_FOUND", "CDK id not found");
        List<Object> items = new ArrayList<>();
        for (CDK cdk : group.values()) items.add(cdkData(cdk, true));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("total", items.size());
        data.put("items", items);
        return data;
    }

    private Map<String, Object> createCdk(JsonObject body) throws BadRequest {
        String type = string(body, "type", "multiple");
        String id = requiredString(body, "id");
        int quantity = Math.max(1, intValue(body, "quantity", 1));
        String commands = commands(body);
        Date expiration = dateOrNull(string(body, "expiration", null));
        String permission = string(body, "requiredPermission", null);
        String group = string(body, "requiredGroup", null);
        List<String> names = new ArrayList<>();
        if ("single".equalsIgnoreCase(type)) {
            for (int i = 0; i < quantity; i++) {
                String name = cdkManager.generateUniqueRandomCDKName();
                cdkManager.createCDK(id, name, 1, true, commands, expiration, permission, group);
                names.add(name);
            }
        } else {
            String name = requiredString(body, "name");
            try {
                cdkManager.createCDK(id, name, quantity, false, commands, expiration, permission, group);
            } catch (IllegalArgumentException e) {
                throw new BadRequest(409, "CDK_ALREADY_EXISTS", e.getMessage());
            }
            names.add(name);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", names.size());
        data.put("cdks", names);
        return data;
    }

    private Object updateCdk(String name, JsonObject body) throws BadRequest {
        Integer quantity = has(body, "quantity") ? intValue(body, "quantity", 0) : null;
        String commands = has(body, "commands") ? commands(body) : null;
        boolean updateExpiration = has(body, "expiration");
        Date expiration = updateExpiration ? dateOrNull(string(body, "expiration", null)) : null;
        boolean updatePermission = has(body, "requiredPermission");
        String permission = updatePermission ? string(body, "requiredPermission", null) : null;
        boolean updateGroup = has(body, "requiredGroup");
        String group = updateGroup ? string(body, "requiredGroup", null) : null;
        if (!cdkManager.updateCDK(name, quantity, commands, expiration, updateExpiration, permission, updatePermission, group, updateGroup)) {
            throw new BadRequest(404, "CDK_NOT_FOUND", "CDK not found");
        }
        return cdkData(requiredCdk(name), true);
    }

    private Object deleteCdk(String name) throws BadRequest {
        if (!cdkManager.deleteByCDKName(name)) throw new BadRequest(404, "CDK_NOT_FOUND", "CDK not found");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        data.put("name", name);
        return data;
    }

    private Object deleteById(String id) throws BadRequest {
        if (!cdkManager.deleteById(id)) throw new BadRequest(404, "CDK_ID_NOT_FOUND", "CDK id not found");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        data.put("id", id);
        return data;
    }

    private Object addById(String id, JsonObject body) throws BadRequest {
        Map<String, CDK> group = cdkManager.findCDKGroupById(id);
        if (group == null || group.isEmpty()) throw new BadRequest(404, "CDK_ID_NOT_FOUND", "CDK id not found");
        CDK template = group.values().iterator().next();
        int quantity = Math.max(1, intValue(body, "quantity", 1));
        List<String> names = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            String name = cdkManager.generateUniqueRandomCDKName();
            cdkManager.createCDK(id, name, 1, true, template.getCommands(), template.getExpirationDate(), template.getRequiredPermission(), template.getRequiredGroup());
            names.add(name);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", names.size());
        data.put("cdks", names);
        return data;
    }

    private Object verify(String name, JsonObject body) throws BadRequest {
        CDK cdk = requiredCdk(name);
        String playerName = requiredString(body, "player");
        PlayerCheck playerCheck = checkPlayer(playerName, cdk);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", true);
        data.put("reason", "OK");
        if (cdk.isExpired()) setInvalid(data, "EXPIRED");
        else if (cdk.hasPlayerRedeemed(playerName)) setInvalid(data, "ALREADY_REDEEMED");
        else if (cdk.hasUseConditions() && !playerCheck.online) setInvalid(data, "PLAYER_OFFLINE");
        else if (playerCheck.online && !playerCheck.conditionMet) setInvalid(data, "CONDITION_NOT_MET");
        data.put("cdk", name);
        data.put("expired", cdk.isExpired());
        data.put("alreadyRedeemed", cdk.hasPlayerRedeemed(playerName));
        data.put("conditionMet", playerCheck.online ? playerCheck.conditionMet : !cdk.hasUseConditions());
        data.put("requiredPermission", cdk.getRequiredPermission());
        data.put("requiredGroup", cdk.getRequiredGroup());
        return data;
    }

    private Object redeem(String name, JsonObject body) throws BadRequest {
        CDK cdk = requiredCdk(name);
        String playerName = requiredString(body, "player");
        PlayerCheck playerCheck = checkPlayer(playerName, cdk);
        if (!playerCheck.online) throw new BadRequest(422, "PLAYER_OFFLINE", "Player must be online to redeem CDK");
        if (cdk.isExpired()) throw new BadRequest(422, "CDK_EXPIRED", "CDK is expired");
        if (cdk.hasPlayerRedeemed(playerCheck.playerName)) throw new BadRequest(409, "ALREADY_REDEEMED", "Player already redeemed this CDK");
        if (!playerCheck.conditionMet) throw new BadRequest(403, "CONDITION_NOT_MET", "Player does not meet CDK use conditions");
        CDKManager.RedemptionResult result = cdkManager.redeemForApi(name, playerCheck.playerName, playerCheck.conditionMet);
        if (!result.isSuccess()) {
            int status = "ALREADY_REDEEMED".equals(result.getCode()) ? 409 : ("CONDITION_NOT_MET".equals(result.getCode()) ? 403 : 422);
            throw new BadRequest(status, result.getCode(), result.getMessage());
        }
        for (String command : result.getCommands().split("\\|")) {
            runCommandForPlayer(playerCheck.playerName, command);
        }
        logManager.logCDKUsage(playerCheck.playerName, result.getCdk());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("redeemed", true);
        data.put("cdk", name);
        data.put("player", playerCheck.playerName);
        data.put("remainingQuantity", result.getRemainingQuantity());
        return data;
    }

    private Object logs(Map<String, String> query) {
        String player = query.get("player");
        if (player != null) return playerLogs(player);
        return logManager.getAllPlayerLogs();
    }

    private Object playerLogs(String player) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", player);
        data.put("used", logManager.getPlayerUsedCDKs(player));
        return data;
    }

    private Object reload() {
        new Thread(() -> {
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ignored) {}
            if (active && plugin.isEnabled()) {
                FoliaSupport.runGlobal(plugin, () -> {
                    if (active && plugin.isEnabled()) reloadCallback.reloadFromApi();
                });
            }
        }, "KukeCDK-API-Reload").start();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reloadScheduled", true);
        return data;
    }

    private CDK requiredCdk(String name) throws BadRequest {
        CDK cdk = cdkManager.findCDKByName(name);
        if (cdk == null) throw new BadRequest(404, "CDK_NOT_FOUND", "CDK not found");
        return cdk;
    }

    private Map<String, Object> cdkData(CDK cdk, boolean includeRedeemed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", cdk.getId());
        data.put("name", cdk.getName());
        data.put("quantity", cdk.getQuantity());
        data.put("singleUse", cdk.isSingleUse());
        data.put("commands", splitCommands(cdk.getCommands()));
        data.put("expiration", cdk.getExpirationDate() == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(cdk.getExpirationDate()));
        data.put("expired", cdk.isExpired());
        data.put("requiredPermission", cdk.getRequiredPermission());
        data.put("requiredGroup", cdk.getRequiredGroup());
        if (includeRedeemed) data.put("redeemedPlayers", new ArrayList<>(cdk.getRedeemedPlayers()));
        return data;
    }

    private boolean canPlayerUse(Player player, CDK cdk) {
        String permission = cdk.getRequiredPermission();
        if (permission != null && !player.hasPermission(permission)) return false;
        String group = cdk.getRequiredGroup();
        return group == null || player.hasPermission("group." + group);
    }

    private PlayerCheck checkPlayer(String playerName, CDK cdk) throws BadRequest {
        try {
            return FoliaSupport.callGlobal(plugin, () -> {
                Player player = Bukkit.getPlayerExact(playerName);
                if (player == null) return new PlayerCheck(false, playerName, !cdk.hasUseConditions());
                return new PlayerCheck(true, player.getName(), canPlayerUse(player, cdk));
            }, 5000L);
        } catch (Exception e) {
            throw new BadRequest(500, "SERVER_THREAD_TIMEOUT", "Failed to query player state on server thread");
        }
    }

    private void runCommandForPlayer(String playerName, String command) throws BadRequest {
        try {
            FoliaSupport.callGlobal(plugin, () -> {
                Player player = Bukkit.getPlayerExact(playerName);
                String parsed = command == null ? "" : command.replace("%player%", playerName);
                if (player != null) parsed = applyPlaceholders(player, command);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                return null;
            }, 5000L);
        } catch (Exception e) {
            throw new BadRequest(500, "COMMAND_EXECUTION_FAILED", "Failed to execute reward command");
        }
    }

    private String applyPlaceholders(Player player, String text) {
        if (text == null) return "";
        String withBuiltin = text.replace("%player%", player.getName());
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                Class<?> cls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method method = cls.getMethod("setPlaceholders", Player.class, String.class);
                Object value = method.invoke(null, player, withBuiltin);
                return value instanceof String ? (String) value : withBuiltin;
            }
        } catch (Throwable ignored) {}
        return withBuiltin;
    }

    private static class PlayerCheck {
        private final boolean online;
        private final String playerName;
        private final boolean conditionMet;

        private PlayerCheck(boolean online, String playerName, boolean conditionMet) {
            this.online = online;
            this.playerName = playerName;
            this.conditionMet = conditionMet;
        }
    }

    private void setInvalid(Map<String, Object> data, String reason) {
        data.put("valid", false);
        data.put("reason", reason);
    }

    private JsonObject readJson(HttpExchange exchange) throws IOException, BadRequest {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        while ((read = exchange.getRequestBody().read(buffer)) != -1) {
            total += read;
            if (total > apiConfig.getMaxBodyBytes()) throw new BadRequest(413, "BODY_TOO_LARGE", "Request body is too large");
            out.write(buffer, 0, read);
        }
        String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (body.trim().isEmpty()) return new JsonObject();
        try {
            JsonElement element = new JsonParser().parse(body);
            if (!element.isJsonObject()) throw new BadRequest(400, "INVALID_JSON", "JSON body must be an object");
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new BadRequest(400, "INVALID_JSON", "Invalid JSON body");
        }
    }

    private String commands(JsonObject body) throws BadRequest {
        if (!has(body, "commands")) throw new BadRequest(400, "MISSING_FIELD", "Missing field: commands");
        JsonElement element = body.get("commands");
        if (element.isJsonArray()) {
            List<String> commands = new ArrayList<>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) commands.add(item.getAsString());
            return String.join("|", commands);
        }
        return element.getAsString();
    }

    private List<String> splitCommands(String commands) {
        List<String> result = new ArrayList<>();
        if (commands == null || commands.isEmpty()) return result;
        for (String command : commands.split("\\|")) result.add(command);
        return result;
    }

    private Date dateOrNull(String value) throws BadRequest {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(value.trim());
        } catch (ParseException e) {
            throw new BadRequest(400, "INVALID_DATE", "Date format must be yyyy-MM-dd HH:mm");
        }
    }

    private boolean has(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull();
    }

    private String requiredString(JsonObject body, String key) throws BadRequest {
        String value = string(body, key, null);
        if (value == null || value.trim().isEmpty()) throw new BadRequest(400, "MISSING_FIELD", "Missing field: " + key);
        return value.trim();
    }

    private String string(JsonObject body, String key, String def) {
        return has(body, key) ? body.get(key).getAsString() : def;
    }

    private int intValue(JsonObject body, String key, int def) {
        return has(body, key) ? body.get(key).getAsInt() : def;
    }

    private int parseInt(String value, int def) {
        try { return value == null ? def : Integer.parseInt(value); } catch (Exception ignored) { return def; }
    }

    private Map<String, String> query(HttpExchange exchange) throws IOException {
        Map<String, String> result = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) return result;
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            result.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
        }
        return result;
    }

    private String trimBase(String path) {
        String rest = path.substring(apiConfig.getBasePath().length());
        if (rest.isEmpty()) rest = "/";
        return rest;
    }

    private boolean isIpAllowed(String remote) {
        List<String> allow = apiConfig.getIpAllowlist();
        return allow == null || allow.isEmpty() || allow.contains(remote);
    }

    private void addCorsHeaders(HttpExchange exchange) {
        if (!apiConfig.isCorsEnabled()) return;
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String allowed = apiConfig.getCorsAllowedOrigins().contains("*") ? "*" : (apiConfig.getCorsAllowedOrigins().contains(origin) ? origin : null);
        if (allowed != null) exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowed);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization,Content-Type");
    }

    private void sendJson(HttpExchange exchange, int status, Object body, String requestId) throws IOException {
        exchange.getResponseHeaders().set("X-Request-Id", requestId);
        sendText(exchange, status, gson.toJson(body), "application/json; charset=utf-8");
    }

    private void sendText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void audit(boolean success, String remote, String method, String path, int status, String requestId, long start, String detail) {
        if (!apiConfig.isAuditEnabled()) return;
        if (success && !apiConfig.isAuditSuccess()) return;
        if (!success && status == 401 && !apiConfig.isAuditFailedAuth()) return;
        plugin.getLogger().info("API " + status + " " + method + " " + path + " remote=" + remote + " requestId=" + requestId + " cost=" + (System.currentTimeMillis() - start) + "ms detail=" + detail);
    }

    public interface ReloadCallback {
        void reloadFromApi();
    }

    private static class BadRequest extends Exception {
        private final int status;
        private final String code;

        private BadRequest(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    private static class Route {
        private final String name;
        private final String scope;
        private final String value;
        private final int status;

        private Route(String name, String scope, String value, int status) {
            this.name = name;
            this.scope = scope;
            this.value = value;
            this.status = status;
        }

        private static Route parse(String method, String path) {
            String[] parts = path.equals("/") ? new String[0] : path.substring(1).split("/");
            if ("GET".equals(method) && eq(parts, "health")) return r("health", "server:read");
            if ("GET".equals(method) && eq(parts, "version")) return r("version", "server:read");
            if ("GET".equals(method) && eq(parts, "stats")) return r("stats", "server:read");
            if ("GET".equals(method) && eq(parts, "cdks")) return r("listCdks", "cdk:read");
            if (parts.length == 2 && "GET".equals(method) && "cdks".equals(parts[0])) return r("getCdk", "cdk:read", decode(parts[1]));
            if (parts.length == 3 && "GET".equals(method) && "cdks".equals(parts[0]) && "by-id".equals(parts[1])) return r("getById", "cdk:read", decode(parts[2]));
            if ("POST".equals(method) && eq(parts, "cdks")) return r("createCdk", "cdk:create", null, 201);
            if (parts.length == 2 && "PATCH".equals(method) && "cdks".equals(parts[0])) return r("updateCdk", "cdk:update", decode(parts[1]));
            if (parts.length == 2 && "DELETE".equals(method) && "cdks".equals(parts[0])) return r("deleteCdk", "cdk:delete", decode(parts[1]));
            if (parts.length == 3 && "DELETE".equals(method) && "cdks".equals(parts[0]) && "by-id".equals(parts[1])) return r("deleteById", "cdk:delete", decode(parts[2]));
            if (parts.length == 4 && "POST".equals(method) && "cdks".equals(parts[0]) && "by-id".equals(parts[1]) && "add".equals(parts[3])) return r("addById", "cdk:create", decode(parts[2]), 201);
            if (parts.length == 3 && "POST".equals(method) && "cdks".equals(parts[0]) && "verify".equals(parts[2])) return r("verify", "cdk:verify", decode(parts[1]));
            if (parts.length == 3 && "POST".equals(method) && "cdks".equals(parts[0]) && "redeem".equals(parts[2])) return r("redeem", "cdk:redeem", decode(parts[1]));
            if ("GET".equals(method) && eq(parts, "logs")) return r("logs", "log:read");
            if (parts.length == 3 && "GET".equals(method) && "logs".equals(parts[0]) && "players".equals(parts[1])) return r("playerLogs", "log:read", decode(parts[2]));
            if ("POST".equals(method) && eq(parts, "admin", "reload")) return r("reload", "admin:reload");
            return null;
        }

        private static boolean eq(String[] parts, String... expected) {
            if (parts.length != expected.length) return false;
            for (int i = 0; i < parts.length; i++) if (!expected[i].equals(parts[i])) return false;
            return true;
        }

        private static Route r(String name, String scope) { return new Route(name, scope, null, 200); }
        private static Route r(String name, String scope, String value) { return new Route(name, scope, value, 200); }
        private static Route r(String name, String scope, String value, int status) { return new Route(name, scope, value, status); }
        private static String decode(String value) {
            try { return URLDecoder.decode(value, "UTF-8"); } catch (Exception ignored) { return value; }
        }
    }
}
