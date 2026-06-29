package su.kukecdk.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApiToken {
    private final String name;
    private final String token;
    private final Set<String> scopes;

    public ApiToken(String name, String token, List<String> scopes) {
        this.name = name == null || name.trim().isEmpty() ? "unnamed" : name.trim();
        this.token = token == null ? "" : token.trim();
        this.scopes = new HashSet<>(scopes == null ? Collections.emptyList() : scopes);
    }

    public String getName() {
        return name;
    }

    public String getToken() {
        return token;
    }

    public boolean isValid() {
        return !token.isEmpty();
    }

    public boolean hasScope(String scope) {
        return scopes.contains("*") || scopes.contains(scope);
    }
}
