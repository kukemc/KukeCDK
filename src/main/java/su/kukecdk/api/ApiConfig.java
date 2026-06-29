package su.kukecdk.api;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ApiConfig {
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String basePath;
    private final String docsPath;
    private final boolean authEnabled;
    private final List<ApiToken> tokens;
    private final List<String> ipAllowlist;
    private final boolean rateLimitEnabled;
    private final int requestsPerMinute;
    private final boolean corsEnabled;
    private final List<String> corsAllowedOrigins;
    private final int maxBodyBytes;
    private final boolean auditEnabled;
    private final boolean auditSuccess;
    private final boolean auditFailedAuth;

    private ApiConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("api.enabled", false);
        this.host = config.getString("api.host", "127.0.0.1");
        this.port = config.getInt("api.port", 8765);
        this.basePath = normalizePath(config.getString("api.base_path", "/api/v1"));
        this.docsPath = normalizePath(config.getString("api.docs_path", "/docs"));
        this.authEnabled = config.getBoolean("api.auth.enabled", true);
        this.tokens = loadTokens(config);
        this.ipAllowlist = config.getStringList("api.ip_allowlist");
        this.rateLimitEnabled = config.getBoolean("api.rate_limit.enabled", true);
        this.requestsPerMinute = Math.max(1, config.getInt("api.rate_limit.requests_per_minute", 120));
        this.corsEnabled = config.getBoolean("api.cors.enabled", false);
        List<String> origins = config.getStringList("api.cors.allowed_origins");
        this.corsAllowedOrigins = origins.isEmpty() ? Collections.singletonList("*") : origins;
        this.maxBodyBytes = Math.max(1024, config.getInt("api.max_body_bytes", 1048576));
        this.auditEnabled = config.getBoolean("api.audit.enabled", true);
        this.auditSuccess = config.getBoolean("api.audit.log_success", true);
        this.auditFailedAuth = config.getBoolean("api.audit.log_failed_auth", true);
    }

    public static ApiConfig from(FileConfiguration config) {
        return new ApiConfig(config);
    }

    private static String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) return "/";
        String normalized = path.trim();
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private List<ApiToken> loadTokens(FileConfiguration config) {
        List<ApiToken> result = new ArrayList<>();
        List<?> list = config.getList("api.auth.tokens");
        if (list != null) {
            for (Object item : list) {
                if (item instanceof ConfigurationSection) {
                    ConfigurationSection section = (ConfigurationSection) item;
                    result.add(new ApiToken(section.getString("name"), section.getString("token"), section.getStringList("scopes")));
                } else if (item instanceof java.util.Map) {
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) item;
                    Object scopesObject = map.get("scopes");
                    List<String> scopes = new ArrayList<>();
                    if (scopesObject instanceof List) {
                        for (Object scope : (List<?>) scopesObject) scopes.add(String.valueOf(scope));
                    }
                    Object name = map.get("name");
                    Object token = map.get("token");
                    result.add(new ApiToken(name == null ? null : String.valueOf(name), token == null ? null : String.valueOf(token), scopes));
                }
            }
        }
        if (result.isEmpty()) {
            result.add(new ApiToken("admin", "change-me-admin-token", Arrays.asList("*")));
        }
        return result;
    }

    public boolean isEnabled() { return enabled; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getBasePath() { return basePath; }
    public String getDocsPath() { return docsPath; }
    public boolean isAuthEnabled() { return authEnabled; }
    public List<ApiToken> getTokens() { return tokens; }
    public List<String> getIpAllowlist() { return ipAllowlist; }
    public boolean isRateLimitEnabled() { return rateLimitEnabled; }
    public int getRequestsPerMinute() { return requestsPerMinute; }
    public boolean isCorsEnabled() { return corsEnabled; }
    public List<String> getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public int getMaxBodyBytes() { return maxBodyBytes; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public boolean isAuditSuccess() { return auditSuccess; }
    public boolean isAuditFailedAuth() { return auditFailedAuth; }
}
