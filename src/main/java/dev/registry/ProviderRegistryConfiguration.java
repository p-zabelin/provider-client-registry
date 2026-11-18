package dev.registry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ProviderRegistryConfiguration {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
}
