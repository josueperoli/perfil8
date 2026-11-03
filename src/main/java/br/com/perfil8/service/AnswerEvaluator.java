package br.com.perfil8.service;

import br.com.perfil8.dto.AnswerEvaluation;
import br.com.perfil8.model.Card;

public interface AnswerEvaluator {
    AnswerEvaluation evaluate(Card card, String guess, int clueNumber);
}
