package br.com.perfil8.service;

import org.springframework.stereotype.Service;

@Service
public class ScoreService {
    private static final int[] POINTS_BY_CLUE = {20, 18, 16, 14, 12, 10, 8, 6, 4, 2};

    public int pointsForClue(int clueNumber) {
        if (clueNumber < 1 || clueNumber > POINTS_BY_CLUE.length) {
            throw new IllegalArgumentException("O número da pista deve estar entre 1 e 10.");
        }
        return POINTS_BY_CLUE[clueNumber - 1];
    }
}
