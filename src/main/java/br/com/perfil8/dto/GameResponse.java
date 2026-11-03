package br.com.perfil8.dto;

import br.com.perfil8.model.Category;
import br.com.perfil8.model.Difficulty;
import br.com.perfil8.model.RoundStatus;

import java.util.List;
import java.util.UUID;

public record GameResponse(
        UUID gameId,
        Difficulty difficulty,
        int roundNumber,
        int maxRounds,
        int playerRoundNumber,
        int score,
        Category category,
        int clueNumber,
        int totalClues,
        String clue,
        boolean hasNextClue,
        RoundStatus roundStatus,
        String presenterMessage,
        String currentPlayer,
        List<PlayerResultResponse> players,
        boolean hasNextTurn,
        boolean competitionFinished,
        List<String> winners
) {
}
