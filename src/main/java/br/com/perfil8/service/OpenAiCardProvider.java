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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class OpenAiCardProvider implements CardProvider {
    private static final int MAX_ALIASES = 20;
    private static final int MAX_ANSWER_LENGTH = 120;
    private static final int MAX_CLUE_LENGTH = 500;
    private static final Set<String> ENGLISH_CLUE_WORDS = Set.of(
            "the", "is", "are", "was", "were", "been", "being", "has", "have", "had",
            "born", "known", "with", "from", "his", "her", "their", "located", "became",
            "played", "wrote", "written", "lives", "worked", "created", "founded", "called",
            "who", "which", "where", "first", "largest", "city", "country", "river",
            "mountain", "author", "writer", "scientist", "president"
    );

    private final OpenAiChatClient chatClient;
    private final ObjectMapper objectMapper;

    public OpenAiCardProvider(OpenAiChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Card createCard(Difficulty difficulty, Set<String> excludedAnswers) {
        JsonNode response = chatClient.completeJson(
                """
                Você é a apresentadora de um jogo de adivinhação brasileiro.
                Escreva todas as pistas e todos os textos em português brasileiro, nunca em inglês.
                Siga estritamente o formato JSON solicitado.
                """,
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
                Crie uma carta original para um jogo de adivinhação brasileiro.
                REGRA DE IDIOMA: escreva todas as dez pistas em português brasileiro natural.
                Nunca escreva uma pista em inglês. Não traduza nomes próprios nem títulos oficiais;
                mantenha nomes de pessoas, lugares e obras na forma reconhecida em português.
                Os campos answer e acceptedAnswers devem conter apenas a resposta e suas variações
                reconhecidas, não frases explicativas.
                Dificuldade selecionada: %s.
                Categorias permitidas: %s.
                Respostas já usadas nesta partida e que não podem se repetir: %s.
                Gere exatamente dez pistas, em ordem progressiva: a primeira deve ser a mais
                indireta e a décima a mais específica. A dificuldade deve alterar o quão óbvias
                são as pistas: FACIL usa fatos mais conhecidos; MEDIO exige conhecimento geral;
                DIFICIL usa fatos específicos; EXTREMO usa fatos raros e menos óbvios.
                As pistas devem ser factuais, distintas e coerentes. Não inclua a resposta secreta,
                seus apelidos ou qualquer variação aceita em nenhuma pista, nem revele a resposta
                diretamente. Não use pistas genéricas que sirvam para muitas respostas.
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

        if (generated.acceptedAnswers().stream().anyMatch(alias -> alias == null || alias.isBlank()
                || alias.length() > MAX_ANSWER_LENGTH)) {
            throw new AiProviderException("A API de IA retornou variações de resposta inválidas.");
        }

        List<String> secretAnswers = new java.util.ArrayList<>(generated.acceptedAnswers());
        secretAnswers.add(generated.answer());
        if (generated.clues().stream().anyMatch(clue -> clue == null || clue.isBlank()
                || clue.length() > MAX_CLUE_LENGTH
                || containsResponse(clue, secretAnswers)
                || appearsToBeEnglish(clue))) {
            throw new AiProviderException(
                    "A API de IA retornou pistas inválidas, em inglês ou que revelam a resposta."
            );
        }

        Set<String> acceptedAnswers = new HashSet<>(generated.acceptedAnswers());
        acceptedAnswers.add(generated.answer());
        return new Card(generated.answer(), category, difficulty, generated.clues(), acceptedAnswers);
    }

    private static boolean containsResponse(String clue, List<String> secretAnswers) {
        List<String> clueWords = List.of(AnswerNormalizer.normalize(clue).split(" "));
        return secretAnswers.stream()
                .map(AnswerNormalizer::normalize)
                .filter(answer -> !answer.isBlank())
                .map(answer -> List.of(answer.split(" ")))
                .anyMatch(answerWords -> containsWordSequence(clueWords, answerWords));
    }

    private static boolean containsWordSequence(List<String> words, List<String> sequence) {
        if (sequence.size() > words.size()) {
            return false;
        }
        for (int start = 0; start <= words.size() - sequence.size(); start++) {
            if (words.subList(start, start + sequence.size()).equals(sequence)) {
                return true;
            }
        }
        return false;
    }

    private static boolean appearsToBeEnglish(String clue) {
        List<String> words = List.of(AnswerNormalizer.normalize(clue).split(" "));
        long englishWordCount = words.stream().filter(ENGLISH_CLUE_WORDS::contains).count();
        return englishWordCount >= 2
                || words.contains("born") && (words.contains("was") || words.contains("in"))
                || words.contains("known") && words.contains("as")
                || words.contains("located") && words.contains("in");
    }

    private static Category parseCategory(String value) {
        String normalized = AnswerNormalizer.normalize(value).replace(' ', '_').toUpperCase(Locale.ROOT);
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
