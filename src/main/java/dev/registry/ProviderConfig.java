package dev.registry;

import java.net.URI;
import java.util.regex.Pattern;

public record ProviderConfig(String id, URI baseUrl, String apiKey) {

    private static final Pattern HEADER_VALUE = Pattern.compile("[\\t\\x20-\\x7e\\x80-\\xff]+");

    public ProviderConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Provider id is required");
        }
        if (baseUrl == null || baseUrl.getHost() == null || baseUrl.getPort() > 65535
                || !("https".equalsIgnoreCase(baseUrl.getScheme()) || "http".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null
                || baseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("Expected an HTTP(S) base URL with an optional path for " + id);
        }
        if (apiKey == null || apiKey.isBlank() || !HEADER_VALUE.matcher(apiKey).matches()) {
            throw new IllegalArgumentException("Invalid API key for " + id);
        }
    }

    @Override
    public String toString() {
        return "ProviderConfig[id=" + id + ", baseUrl=" + baseUrl + "]";
    }
}
