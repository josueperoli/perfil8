package br.com.perfil8.service;

import br.com.perfil8.model.Category;
import br.com.perfil8.model.Difficulty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.POST;

class OpenAiCardProviderTest {
    @Test
    void createsValidatedCardFromStructuredAiResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"response_format":{"type":"json_schema","json_schema":{"name":"perfil8_card","strict":true,"schema":{"properties":{"clues":{"minItems":10,"maxItems":10},"acceptedAnswers":{"type":"array"}}}}}}
                        """, false))
                .andExpect(content().string(containsString("clues (array de dez strings)")))
                .andExpect(content().string(containsString("todas as dez pistas em português brasileiro")))
                .andExpect(content().string(containsString("nunca em inglês")))
                .andRespond(withSuccess(completion(cardJson("Segredo", "CIENCIA")), MediaType.APPLICATION_JSON));
        OpenAiCardProvider provider = new OpenAiCardProvider(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        var card = provider.createCard(Difficulty.DIFICIL, Set.of());

        assertEquals("Segredo", card.answer());
        assertEquals(Category.CIENCIA, card.category());
        assertEquals(Difficulty.DIFICIL, card.difficulty());
        assertEquals(10, card.clues().size());
        server.verify();
    }

    @Test
    void rejectsCardThatRevealsAnswerInAClue() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess(completion("""
                        {"answer":"Segredo","category":"CIENCIA","clues":["A resposta é Segredo.","Pista 2","Pista 3","Pista 4","Pista 5","Pista 6","Pista 7","Pista 8","Pista 9","Pista 10"],"acceptedAnswers":[]}
                        """), MediaType.APPLICATION_JSON));
        OpenAiCardProvider provider = new OpenAiCardProvider(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        assertThrows(AiProviderException.class, () -> provider.createCard(Difficulty.MEDIO, Set.of()));
        server.verify();
    }

    @Test
    void rejectsCluesWrittenInEnglish() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 2; attempt++) {
            server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                    .andRespond(withSuccess(completion(cardJson("Segredo", "CIENCIA")
                            .replace("Primeira pista", "He was born in Portugal.")), MediaType.APPLICATION_JSON));
        }
        OpenAiCardProvider provider = new OpenAiCardProvider(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        assertThrows(AiProviderException.class, () -> provider.createCard(Difficulty.MEDIO, Set.of()));
        server.verify();
    }

    @Test
    void retriesCardGenerationWhenCluesAreInEnglish() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess(completion(cardJson("Segredo", "CIENCIA")
                        .replace("Primeira pista", "He was born in Portugal.")), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess(completion(cardJson("Segredo", "CIENCIA")), MediaType.APPLICATION_JSON));
        OpenAiCardProvider provider = new OpenAiCardProvider(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        var card = provider.createCard(Difficulty.MEDIO, Set.of());

        assertEquals(10, card.clues().size());
        server.verify();
    }

    @Test
    void rejectsCluesThatRevealAnAcceptedAnswer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess(completion(cardJson("Segredo", "CIENCIA")
                        .replace("Primeira pista", "Também é chamado Nome alternativo.")),
                        MediaType.APPLICATION_JSON));
        OpenAiCardProvider provider = new OpenAiCardProvider(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        assertThrows(AiProviderException.class, () -> provider.createCard(Difficulty.MEDIO, Set.of()));
        server.verify();
    }

    @Test
    void doesNotTreatAnswerAsRevealedWhenItAppearsInsideAnotherWord() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess(completion(cardJson("Roma", "CIENCIA")
                        .replace("Primeira pista", "O aroma da fruta é marcante.")),
                        MediaType.APPLICATION_JSON));
        OpenAiCardProvider provider = new OpenAiCardProvider(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        var card = provider.createCard(Difficulty.MEDIO, Set.of());

        assertEquals("Roma", card.answer());
        server.verify();
    }

    @Test
    void acceptsPortugueseCategoryWithAccentsAndSpaces() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess(completion(cardJson("Segredo", "Personalidades Históricas")),
                        MediaType.APPLICATION_JSON));
        OpenAiCardProvider provider = new OpenAiCardProvider(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        var card = provider.createCard(Difficulty.DIFICIL, Set.of());

        assertEquals(Category.PERSONALIDADES_HISTORICAS, card.category());
        server.verify();
    }

    private static String cardJson(String answer, String category) {
        return """
                {"answer":"%s","category":"%s","clues":["Primeira pista","Segunda pista","Terceira pista","Quarta pista","Quinta pista","Sexta pista","Sétima pista","Oitava pista","Nona pista","Décima pista"],"acceptedAnswers":["Nome alternativo"]}
                """.formatted(answer, category);
    }

    private static String completion(String cardJson) {
        String escapedCardJson = cardJson.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "");
        return """
                {"choices":[{"message":{"content":"%s"}}]}
                """.formatted(escapedCardJson);
    }
}
