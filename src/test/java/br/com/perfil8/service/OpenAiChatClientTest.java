package br.com.perfil8.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OpenAiChatClientTest {
    @Test
    void reportsProviderRejectionWithoutExposingApiKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"Invalid key gsk_secret-value"}}
                                """));
        OpenAiChatClient client = new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model");

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> completeJson(client)
        );

        assertTrue(exception.getMessage().contains("HTTP 400"));
        assertTrue(exception.getMessage().contains("[chave ocultada]"));
        assertTrue(!exception.getMessage().contains("gsk_secret-value"));
        server.verify();
    }

    @Test
    void translatesRateLimitResponsesToActionableMessage() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"Rate limit reached"}}
                                """));
        OpenAiChatClient client = new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model");

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> completeJson(client)
        );

        assertEquals(
                "O limite de uso da Groq foi atingido (HTTP 429). Aguarde a renovação do limite gratuito.",
                exception.getMessage()
        );
        server.verify();
    }

    private static void completeJson(OpenAiChatClient client) {
        client.completeJson(
                "JSON",
                "{}",
                0.8,
                "test_schema",
                Map.of(
                        "type", "object",
                        "properties", Map.of("ok", Map.of("type", "boolean")),
                        "required", List.of("ok"),
                        "additionalProperties", false
                )
        );
    }
}
