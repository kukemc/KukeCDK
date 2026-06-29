package su.kukecdk.api;

import com.sun.net.httpserver.HttpExchange;

public class ApiAuthManager {
    private final ApiConfig config;

    public ApiAuthManager(ApiConfig config) {
        this.config = config;
    }

    public AuthResult authenticate(HttpExchange exchange, String requiredScope) {
        if (!config.isAuthEnabled()) {
            return AuthResult.allow("disabled");
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return AuthResult.deny("Missing Bearer token", 401);
        }
        String tokenValue = authorization.substring("Bearer ".length()).trim();
        for (ApiToken token : config.getTokens()) {
            if (constantEquals(token.getToken(), tokenValue)) {
                if (!token.isValid()) {
                    return AuthResult.deny("Invalid token configuration", 403);
                }
                if (requiredScope != null && !requiredScope.isEmpty() && !token.hasScope(requiredScope)) {
                    return AuthResult.deny("Missing scope: " + requiredScope, 403);
                }
                return AuthResult.allow(token.getName());
            }
        }
        return AuthResult.deny("Invalid token", 401);
    }

    private boolean constantEquals(String a, String b) {
        if (a == null || b == null) return false;
        int result = a.length() ^ b.length();
        int max = Math.max(a.length(), b.length());
        for (int i = 0; i < max; i++) {
            char ca = i < a.length() ? a.charAt(i) : 0;
            char cb = i < b.length() ? b.charAt(i) : 0;
            result |= ca ^ cb;
        }
        return result == 0;
    }

    public static class AuthResult {
        private final boolean allowed;
        private final String tokenName;
        private final String message;
        private final int status;

        private AuthResult(boolean allowed, String tokenName, String message, int status) {
            this.allowed = allowed;
            this.tokenName = tokenName;
            this.message = message;
            this.status = status;
        }

        public static AuthResult allow(String tokenName) {
            return new AuthResult(true, tokenName, null, 200);
        }

        public static AuthResult deny(String message, int status) {
            return new AuthResult(false, null, message, status);
        }

        public boolean isAllowed() { return allowed; }
        public String getTokenName() { return tokenName; }
        public String getMessage() { return message; }
        public int getStatus() { return status; }
    }
}
