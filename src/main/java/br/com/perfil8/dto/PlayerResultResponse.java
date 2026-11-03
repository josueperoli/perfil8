package br.com.perfil8.dto;

public record PlayerResultResponse(
        String name,
        String avatarId,
        int score,
        int boardPosition,
        int pointsAwarded,
        int roundsPlayed,
        boolean played,
        boolean correct
) {
}
