package br.com.perfil8.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AIConfig {
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    RestClient aiRestClient(
            RestClient.Builder builder,
            @Value("${ai.api.base-url}") String baseUrl,
            @Value("${ai.api.key:}") String apiKey
    ) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("AI_ENABLED=true exige a configuração de AI_API_KEY.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
}
