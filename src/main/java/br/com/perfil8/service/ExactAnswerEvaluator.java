package br.com.perfil8.service;

import br.com.perfil8.dto.AnswerEvaluation;
import br.com.perfil8.model.Card;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "false", matchIfMissing = true)
public class ExactAnswerEvaluator implements AnswerEvaluator {
    @Override
    public AnswerEvaluation evaluate(Card card, String guess, int clueNumber) {
        String normalizedGuess = AnswerNormalizer.normalize(guess);
        boolean correct = Stream.concat(Stream.of(card.answer()), card.acceptedAnswers().stream())
                .map(AnswerNormalizer::normalize)
                .anyMatch(normalizedGuess::equals);
        return new AnswerEvaluation(correct);
    }
}
