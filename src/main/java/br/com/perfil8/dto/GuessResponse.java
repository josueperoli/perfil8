package br.com.perfil8.dto;

public record GuessResponse(
        boolean correct,
        int pointsAwarded,
        String message,
        String revealedAnswer,
        GameResponse game
) {
}
