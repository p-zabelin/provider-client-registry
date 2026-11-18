package dev.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProviderRegistry {

    private final ObjectMapper mapper;
    private final RestClient.Builder template;
    private final DefaultResponseErrorHandler httpErrors = new DefaultResponseErrorHandler();
    private volatile Map<String, ProviderClient> clients = Map.of();

    private record ProviderClient(RestClient http, String subscribersUrl) {
        RestClient.RequestHeadersSpec<?> getSubscriber(String phone) {
            String encodedPhone = UriUtils.encode(phone, StandardCharsets.UTF_8);
            return http.get().uri(URI.create(subscribersUrl + encodedPhone));
        }
    }

    public ProviderRegistry(RestClient.Builder template, ObjectMapper mapper) {
        this.template = template.clone();
        this.mapper = mapper;
    }

    public void replaceAll(List<ProviderConfig> providers) {
        if (providers == null) {
            throw new IllegalArgumentException("Expected a list of providers");
        }
        Map<String, ProviderClient> next = new HashMap<>();
        for (ProviderConfig provider : providers) {
            if (provider == null) {
                throw new IllegalArgumentException("Provider list contains null");
            }
            if (next.containsKey(provider.id())) {
                throw new IllegalArgumentException("Duplicate provider id: " + provider.id());
            }
            next.put(provider.id(), createClient(provider));
        }
        clients = Map.copyOf(next);
    }

    public void replaceAllFromJson(Path path) throws IOException {
        List<ProviderConfig> providers;
        try (InputStream input = Files.newInputStream(path)) {
            providers = mapper.readValue(input, new TypeReference<>() {});
        }
        replaceAll(providers);
    }

    private ProviderClient createClient(ProviderConfig provider) {
        String base = provider.baseUrl().toASCIIString();
        String subscribersUrl = base + (base.endsWith("/") ? "" : "/") + "subscribers/";
        RestClient http = template.clone()
                .defaultHeader("X-API-Key", provider.apiKey())
                .build();
        return new ProviderClient(http, subscribersUrl);
    }

    public boolean isAvailable(String providerId, String phone) {
        ProviderClient client = providerId == null ? null : clients.get(providerId);
        if (client == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerId);
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        return client.getSubscriber(phone).exchange((request, response) -> {
            var status = response.getStatusCode();
            if (status.value() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            if (status.isError()) {
                httpErrors.handleError(request.getURI(), request.getMethod(), response);
            }
            if (status.value() != HttpStatus.OK.value()) {
                throw new RestClientException("Unexpected HTTP " + status.value() + " from provider " + providerId);
            }
            JsonNode body;
            try {
                body = mapper.readTree(response.getBody());
            } catch (JsonProcessingException e) {
                throw new RestClientException("Invalid JSON from provider " + providerId, e);
            }
            if (body == null || !"ok".equals(body.path("status").textValue())) {
                throw new RestClientException("Expected status 'ok' from provider " + providerId);
            }
            return true;
        });
    }
}
