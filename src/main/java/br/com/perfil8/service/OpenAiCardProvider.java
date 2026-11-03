package br.com.perfil8.service;

import br.com.perfil8.model.Card;
import br.com.perfil8.model.Category;
import br.com.perfil8.model.Difficulty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class OpenAiCardProvider implements CardProvider {
    private static final int MAX_ALIASES = 20;
    private static final int MAX_ANSWER_LENGTH = 120;
    private static final int MAX_CLUE_LENGTH = 500;

    private final OpenAiChatClient chatClient;
    private final ObjectMapper objectMapper;

    public OpenAiCardProvider(OpenAiChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Card createCard(Difficulty difficulty, Set<String> excludedAnswers) {
        JsonNode response = chatClient.completeJson(
                "Você é a apresentadora de um jogo de adivinhação. Siga estritamente o formato JSON solicitado.",
                createPrompt(difficulty, excludedAnswers),
                0.8,
                "perfil8_card",
                cardSchema()
        );
        GeneratedCard generated;
        try {
            generated = objectMapper.treeToValue(response, GeneratedCard.class);
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("O provedor de IA retornou uma carta em formato inválido.", exception);
        }
        return validateAndBuildCard(generated, difficulty, excludedAnswers);
    }

    private String createPrompt(Difficulty difficulty, Set<String> excludedAnswers) {
        String availableCategories = Arrays.toString(Category.values());
        String exclusions = excludedAnswers.isEmpty() ? "Nenhuma" : String.join(", ", excludedAnswers);
        return """
                Crie uma carta original em português para um jogo de adivinhação.
                Dificuldade selecionada: %s.
                Categorias permitidas: %s.
                Respostas já usadas nesta partida e que não podem se repetir: %s.
                Gere exatamente dez pistas, em ordem progressiva: a primeira deve ser a mais
                indireta e a décima a mais específica. A dificuldade deve alterar o quão óbvias
                são as pistas: FACIL usa fatos mais conhecidos; MEDIO exige conhecimento geral;
                DIFICIL usa fatos específicos; EXTREMO usa fatos raros e menos óbvios.
                As pistas devem ser factuais, distintas e coerentes. Não inclua a resposta secreta
                literalmente em nenhuma pista. Não use pistas genéricas que sirvam para muitas respostas.
                Inclua somente apelidos ou variações realmente reconhecidas em acceptedAnswers.
                Responda somente com um objeto JSON com as propriedades:
                answer (string), category (um dos valores permitidos), clues (array de dez strings)
                e acceptedAnswers (array de strings).
                """.formatted(difficulty, availableCategories, exclusions);
    }

    private Card validateAndBuildCard(
            GeneratedCard generated,
            Difficulty difficulty,
            Set<String> excludedAnswers
    ) {
        if (generated == null || generated.answer() == null || generated.answer().isBlank()
                || generated.answer().length() > MAX_ANSWER_LENGTH) {
            throw new AiProviderException("A API de IA retornou uma resposta secreta inválida.");
        }
        String normalizedAnswer = AnswerNormalizer.normalize(generated.answer());
        if (excludedAnswers.stream().map(AnswerNormalizer::normalize).anyMatch(normalizedAnswer::equals)) {
            throw new AiProviderException("A API de IA repetiu uma resposta já usada nesta partida.");
        }
        if (generated.category() == null || generated.clues() == null || generated.clues().size() != 10
                || generated.acceptedAnswers() == null || generated.acceptedAnswers().size() > MAX_ALIASES) {
            throw new AiProviderException("A API de IA retornou uma carta com campos inválidos.");
        }

        Category category = parseCategory(generated.category());

        if (generated.clues().stream().anyMatch(clue -> clue == null || clue.isBlank()
                || clue.length() > MAX_CLUE_LENGTH
                || AnswerNormalizer.normalize(clue).contains(normalizedAnswer))) {
            throw new AiProviderException("A API de IA retornou pistas inválidas ou que revelam a resposta.");
        }
        if (generated.acceptedAnswers().stream().anyMatch(alias -> alias == null || alias.isBlank()
                || alias.length() > MAX_ANSWER_LENGTH)) {
            throw new AiProviderException("A API de IA retornou variações de resposta inválidas.");
        }

        Set<String> acceptedAnswers = new HashSet<>(generated.acceptedAnswers());
        acceptedAnswers.add(generated.answer());
        return new Card(generated.answer(), category, difficulty, generated.clues(), acceptedAnswers);
    }

    private static Category parseCategory(String value) {
        String normalized = AnswerNormalizer.normalize(value).replace(' ', '_').toUpperCase(java.util.Locale.ROOT);
        try {
            return Category.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("A API de IA retornou uma categoria desconhecida.", exception);
        }
    }

    private static Map<String, Object> cardSchema() {
        Map<String, Object> clueArray = Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", 10,
                "maxItems", 10
        );
        Map<String, Object> stringArray = Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "answer", Map.of("type", "string"),
                        "category", Map.of(
                                "type", "string",
                                "enum", Arrays.stream(Category.values()).map(Enum::name).toList()
                        ),
                        "clues", clueArray,
                        "acceptedAnswers", stringArray
                ),
                "required", List.of("answer", "category", "clues", "acceptedAnswers"),
                "additionalProperties", false
        );
    }

    private record GeneratedCard(
            String answer,
            String category,
            List<String> clues,
            List<String> acceptedAnswers
    ) {
    }
}
