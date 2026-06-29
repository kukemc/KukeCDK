package su.kukecdk.api;

public final class ApiDocs {
    private ApiDocs() {}

    public static String html(ApiConfig config) {
        String base = escape(config.getBasePath());
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>KukeCDK API Docs</title>" +
                "<style>body{font-family:Arial,Helvetica,sans-serif;line-height:1.55;max-width:1100px;margin:32px auto;padding:0 18px;color:#222}code,pre{background:#f6f8fa;border-radius:6px}code{padding:2px 5px}pre{padding:14px;overflow:auto}h1,h2{border-bottom:1px solid #eee;padding-bottom:6px}.method{font-weight:bold;color:#0969da}.warn{background:#fff8c5;padding:12px;border-radius:6px}</style>" +
                "</head><body><h1>KukeCDK HTTP API</h1>" +
                "<p>Base path: <code>" + base + "</code></p>" +
                "<div class=\"warn\"><strong>Security:</strong> Keep <code>host: 127.0.0.1</code> unless you expose the API through HTTPS reverse proxy. Always replace the default token.</div>" +
                "<h2>Authentication</h2><p>Send a Bearer token:</p><pre>Authorization: Bearer change-me-admin-token</pre>" +
                "<h2>Scopes</h2><pre>*\nserver:read\ncdk:read\ncdk:create\ncdk:update\ncdk:delete\ncdk:verify\ncdk:redeem\nlog:read\nadmin:reload</pre>" +
                "<h2>Response Format</h2><pre>{\"success\":true,\"data\":{},\"requestId\":\"...\"}</pre><pre>{\"success\":false,\"error\":{\"code\":\"CDK_NOT_FOUND\",\"message\":\"CDK not found\"},\"requestId\":\"...\"}</pre>" +
                "<h2>Endpoints</h2>" +
                endpoint("GET", base + "/health", "Health check", "server:read") +
                endpoint("GET", base + "/version", "Plugin and server version", "server:read") +
                endpoint("GET", base + "/stats", "CDK statistics", "server:read") +
                endpoint("GET", base + "/cdks", "List CDKs. Query: id, page, pageSize, includeRedeemedPlayers", "cdk:read") +
                endpoint("GET", base + "/cdks/{name}", "Get one CDK by name", "cdk:read") +
                endpoint("GET", base + "/cdks/by-id/{id}", "List CDKs under an id", "cdk:read") +
                endpoint("POST", base + "/cdks", "Create single/multiple CDK. Body: type,id,name,quantity,commands,expiration,requiredPermission,requiredGroup", "cdk:create") +
                endpoint("PATCH", base + "/cdks/{name}", "Update quantity, commands, expiration or use conditions", "cdk:update") +
                endpoint("DELETE", base + "/cdks/{name}", "Delete one CDK", "cdk:delete") +
                endpoint("DELETE", base + "/cdks/by-id/{id}", "Delete all CDKs under an id", "cdk:delete") +
                endpoint("POST", base + "/cdks/by-id/{id}/add", "Add random CDKs to an id. Body: quantity", "cdk:create") +
                endpoint("POST", base + "/cdks/{name}/verify", "Verify for a player. Body: player", "cdk:verify") +
                endpoint("POST", base + "/cdks/{name}/redeem", "Redeem for an online player. Body: player", "cdk:redeem") +
                endpoint("GET", base + "/logs", "List raw player logs. Query: player", "log:read") +
                endpoint("GET", base + "/logs/players/{player}", "Get one player's logs", "log:read") +
                endpoint("POST", base + "/admin/reload", "Reload plugin config and restart API if needed", "admin:reload") +
                "<h2>Create Example</h2><pre>curl -X POST http://127.0.0.1:" + config.getPort() + base + "/cdks \\\n  -H 'Authorization: Bearer change-me-admin-token' \\\n  -H 'Content-Type: application/json' \\\n  -d '{\"type\":\"multiple\",\"id\":\"vip\",\"name\":\"vip666\",\"quantity\":100,\"commands\":[\"give %player% diamond 10\"],\"expiration\":\"2026-12-31 23:59\",\"requiredGroup\":\"vip\"}'</pre>" +
                "<h2>Redeem Example</h2><pre>curl -X POST http://127.0.0.1:" + config.getPort() + base + "/cdks/vip666/redeem \\\n  -H 'Authorization: Bearer change-me-admin-token' \\\n  -H 'Content-Type: application/json' \\\n  -d '{\"player\":\"Steve\"}'</pre>" +
                "<p>JSON OpenAPI-style summary: <a href=\"" + escape(config.getDocsPath()) + "/openapi.json\">" + escape(config.getDocsPath()) + "/openapi.json</a></p>" +
                "</body></html>";
    }

    public static String openApiJson(ApiConfig config) {
        String base = config.getBasePath();
        return "{\n" +
                "  \"name\": \"KukeCDK API\",\n" +
                "  \"version\": \"v1\",\n" +
                "  \"basePath\": \"" + base + "\",\n" +
                "  \"auth\": \"Authorization: Bearer <token>\",\n" +
                "  \"endpoints\": [\n" +
                "    \"GET " + base + "/health\",\n" +
                "    \"GET " + base + "/version\",\n" +
                "    \"GET " + base + "/stats\",\n" +
                "    \"GET " + base + "/cdks\",\n" +
                "    \"GET " + base + "/cdks/{name}\",\n" +
                "    \"GET " + base + "/cdks/by-id/{id}\",\n" +
                "    \"POST " + base + "/cdks\",\n" +
                "    \"PATCH " + base + "/cdks/{name}\",\n" +
                "    \"DELETE " + base + "/cdks/{name}\",\n" +
                "    \"DELETE " + base + "/cdks/by-id/{id}\",\n" +
                "    \"POST " + base + "/cdks/by-id/{id}/add\",\n" +
                "    \"POST " + base + "/cdks/{name}/verify\",\n" +
                "    \"POST " + base + "/cdks/{name}/redeem\",\n" +
                "    \"GET " + base + "/logs\",\n" +
                "    \"GET " + base + "/logs/players/{player}\",\n" +
                "    \"POST " + base + "/admin/reload\"\n" +
                "  ]\n" +
                "}";
    }

    private static String endpoint(String method, String path, String description, String scope) {
        return "<p><span class=\"method\">" + escape(method) + "</span> <code>" + escape(path) + "</code><br>" + escape(description) + "<br>Scope: <code>" + escape(scope) + "</code></p>";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
