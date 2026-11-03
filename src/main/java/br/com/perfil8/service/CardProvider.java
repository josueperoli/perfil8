package br.com.perfil8.service;

import br.com.perfil8.model.Card;
import br.com.perfil8.model.Difficulty;

import java.util.Set;

public interface CardProvider {
    Card createCard(Difficulty difficulty, Set<String> excludedAnswers);
}
