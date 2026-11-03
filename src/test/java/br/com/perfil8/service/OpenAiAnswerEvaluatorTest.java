package br.com.perfil8.service;

import br.com.perfil8.model.Card;
import br.com.perfil8.model.Category;
import br.com.perfil8.model.Difficulty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiAnswerEvaluatorTest {
    @Test
    void acceptsCanonicalAnswerWithoutCallingAi() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiAnswerEvaluator evaluator = new OpenAiAnswerEvaluator(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        assertTrue(evaluator.evaluate(card(), "Michael Jackson", 1).correct());
        server.verify();
    }

    @Test
    void acceptsMisspellingWhenAiConfirmsEquivalentAnswer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andExpect(content().json("""
                        {"response_format":{"type":"json_schema","json_schema":{"name":"guess_evaluation","strict":true}}}
                        """, false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Michael Jacksom")))
                .andRespond(withSuccess(completion("{\"correct\":true}"), MediaType.APPLICATION_JSON));
        OpenAiAnswerEvaluator evaluator = new OpenAiAnswerEvaluator(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        assertTrue(evaluator.evaluate(card(), "Michael Jacksom", 2).correct());
        server.verify();
    }

    @Test
    void acceptsApproximateFullNameWhenAiConfirmsEquivalentAnswer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Allan Turrin")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alan Turin")))
                .andRespond(withSuccess(completion("{\"correct\":true}"), MediaType.APPLICATION_JSON));
        OpenAiAnswerEvaluator evaluator = new OpenAiAnswerEvaluator(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );
        Card card = new Card("Alan Turin", Category.CELEBRIDADES, Difficulty.MEDIO,
                List.of("Pista 1", "Pista 2", "Pista 3", "Pista 4", "Pista 5",
                        "Pista 6", "Pista 7", "Pista 8", "Pista 9", "Pista 10"),
                Set.of());

        assertTrue(evaluator.evaluate(card, "Allan Turrin", 2).correct());
        server.verify();
    }

    @Test
    void doesNotAcceptRelatedEntityOrMalformedEvaluation() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"correct\":false}"), MediaType.APPLICATION_JSON));
        OpenAiAnswerEvaluator evaluator = new OpenAiAnswerEvaluator(
                new OpenAiChatClient(builder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        assertFalse(evaluator.evaluate(card(), "Elvis Presley", 2).correct());
        server.verify();

        RestClient.Builder malformedBuilder = RestClient.builder().baseUrl("https://ai.example.test/v1");
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();
        malformedServer.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"correct\":\"true\"}"), MediaType.APPLICATION_JSON));
        OpenAiAnswerEvaluator malformedEvaluator = new OpenAiAnswerEvaluator(
                new OpenAiChatClient(malformedBuilder.build(), new ObjectMapper(), "test-model"),
                new ObjectMapper()
        );

        assertThrows(AiProviderException.class,
                () -> malformedEvaluator.evaluate(card(), "Michaell Jacksom", 1));
        malformedServer.verify();
    }

    private static Card card() {
        return new Card("Michael Jackson", Category.CELEBRIDADES, Difficulty.MEDIO,
                List.of("Pista 1", "Pista 2", "Pista 3", "Pista 4", "Pista 5",
                        "Pista 6", "Pista 7", "Pista 8", "Pista 9", "Pista 10"),
                Set.of("Rei do Pop"));
    }

    private static String completion(String jsonContent) {
        String escapedContent = jsonContent.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"choices":[{"message":{"content":"%s"}}]}
                """.formatted(escapedContent);
    }
}
