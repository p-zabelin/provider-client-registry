package dev.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ProviderRegistryTest {

    private static final ProviderConfig A = new ProviderConfig("a", URI.create("https://a.example"), "test-a");
    private static final ProviderConfig B = new ProviderConfig("b", URI.create("https://b.example/api/"), "test-b");
    private static final String OK = "{\"status\":\"ok\"}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ProviderRegistry registry =
            new ProviderRegistry(builder, new ProviderRegistryConfiguration().objectMapper());

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    @DisplayName("Ответ 200 со статусом ok возвращает true при обращении к выбранному поставщику с его URL, ключом и закодированным номером")
    void execute1() {
        registry.replaceAll(List.of(A, B));
        server.expect(requestTo("https://b.example/api/subscribers/%2B00123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-b"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://a.example/subscribers/00123"))
                .andExpect(header("X-API-Key", "test-a"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertTrue(registry.isAvailable("b", "+00123"));
        assertTrue(registry.isAvailable("a", "00123"));
    }

    @Test
    @DisplayName("Ответ 404 с пустым телом возвращает false")
    void execute2() {
        registry.replaceAll(List.of(A));
        server.expect(requestTo("https://a.example/subscribers/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(registry.isAvailable("a", "1"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 503})
    @DisplayName("HTTP-ошибка поставщика передаётся вызывающему коду с исходным статусом и телом ответа")
    void execute3(int code) {
        registry.replaceAll(List.of(A));
        server.expect(requestTo("https://a.example/subscribers/1"))
                .andRespond(withStatus(HttpStatus.valueOf(code)).body("provider error"));

        var error = assertThrows(RestClientResponseException.class, () -> registry.isAvailable("a", "1"));
        assertEquals(code, error.getStatusCode().value());
        assertEquals("provider error", error.getResponseBodyAsString());
    }
}
