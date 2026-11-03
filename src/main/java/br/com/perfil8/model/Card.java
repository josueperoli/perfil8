package br.com.perfil8.model;

import java.util.List;
import java.util.Set;

public record Card(
        String answer,
        Category category,
        Difficulty difficulty,
        List<String> clues,
        Set<String> acceptedAnswers
) {
    public Card {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("A resposta da carta é obrigatória.");
        }
        if (category == null || difficulty == null) {
            throw new IllegalArgumentException("Categoria e dificuldade são obrigatórias.");
        }
        if (clues == null || clues.size() != 10 || clues.stream().anyMatch(clue -> clue == null || clue.isBlank())) {
            throw new IllegalArgumentException("A carta deve conter exatamente dez pistas válidas.");
        }
        if (acceptedAnswers == null) {
            throw new IllegalArgumentException("As respostas aceitas são obrigatórias.");
        }

        clues = List.copyOf(clues);
        acceptedAnswers = Set.copyOf(acceptedAnswers);
    }
}
