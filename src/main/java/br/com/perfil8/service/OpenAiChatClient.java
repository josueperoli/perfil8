package br.com.perfil8.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class OpenAiChatClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiChatClient(RestClient aiRestClient, ObjectMapper objectMapper,
                            @org.springframework.beans.factory.annotation.Value("${ai.model}") String model) {
        this.restClient = aiRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public JsonNode completeJson(
            String systemPrompt,
            String userPrompt,
            double temperature,
            String schemaName,
            Map<String, Object> schema
    ) {
        try {
            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .body(new Request(
                            model,
                            List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                            temperature,
                            new ResponseFormat("json_schema", new JsonSchema(schemaName, true, schema))
                    ))
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                throw new AiProviderException("A API de IA retornou uma resposta vazia.");
            }

            JsonNode completion = objectMapper.readTree(responseBody);
            JsonNode content = completion.at("/choices/0/message/content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new AiProviderException("A API de IA não retornou conteúdo estruturado.");
            }

            JsonNode result = objectMapper.readTree(content.asText());
            if (!result.isObject()) {
                throw new AiProviderException("A API de IA retornou um JSON inválido.");
            }
            return result;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String providerMessage = extractProviderMessage(exception);
            logger.warn("O provedor de IA rejeitou a solicitação com HTTP {}: {}", status, providerMessage);
            throw new AiProviderException(messageForStatus(status, providerMessage), exception);
        } catch (RestClientException exception) {
            logger.warn("Falha de comunicação com o provedor de IA: {}", exception.getClass().getSimpleName());
            throw new AiProviderException("Não foi possível conectar ao provedor de IA. Tente novamente.", exception);
        } catch (JsonProcessingException exception) {
            logger.warn("O provedor de IA retornou conteúdo que não é JSON válido.");
            throw new AiProviderException("O provedor de IA retornou uma resposta em formato inválido.", exception);
        }
    }

    private String extractProviderMessage(RestClientResponseException exception) {
        try {
            JsonNode errorMessage = objectMapper.readTree(exception.getResponseBodyAsString()).at("/error/message");
            if (errorMessage.isTextual() && !errorMessage.asText().isBlank()) {
                String sanitizedMessage = errorMessage.asText()
                        .replaceAll("(?i)gsk_[A-Za-z0-9_-]+", "[chave ocultada]")
                        .replaceAll("(?i)sk-[A-Za-z0-9_-]+", "[chave ocultada]")
                        .replaceAll("\\s+", " ");
                return sanitizedMessage.substring(0, Math.min(sanitizedMessage.length(), 300));
            }
        } catch (JsonProcessingException ignored) {
            return "resposta de erro sem detalhes JSON";
        }
        return "a API não informou detalhes adicionais";
    }

    private static String messageForStatus(int status, String providerMessage) {
        return switch (status) {
            case 400 -> "A Groq rejeitou a solicitação (HTTP 400): " + providerMessage;
            case 401, 403 -> "A Groq recusou o acesso (HTTP " + status
                    + "). Confira a chave e se ela tem acesso à API.";
            case 429 -> "O limite de uso da Groq foi atingido (HTTP 429). Aguarde a renovação do limite gratuito.";
            default -> "A Groq respondeu com HTTP " + status + ". Tente novamente em instantes.";
        };
    }

    private record Request(
            String model,
            List<Message> messages,
            double temperature,
            ResponseFormat response_format
    ) {
    }

    private record Message(String role, String content) {
    }

    private record ResponseFormat(String type, JsonSchema json_schema) {
    }

    private record JsonSchema(String name, boolean strict, Map<String, Object> schema) {
    }
}
