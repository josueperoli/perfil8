package br.com.perfil8.model;

import br.com.perfil8.dto.PlayerResultResponse;

public class GamePlayer {
    private final String name;
    private final PlayerAvatar avatar;
    private int score;
    private int pointsAwarded;
    private int roundsPlayed;
    private boolean played;
    private boolean correct;

    public GamePlayer(String name, PlayerAvatar avatar) {
        this.name = name;
        this.avatar = avatar;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public int getRoundsPlayed() {
        return roundsPlayed;
    }

    public void finishTurn(RoundStatus status, int points) {
        score += points;
        pointsAwarded = points;
        roundsPlayed++;
        played = true;
        correct = status == RoundStatus.WON;
    }

    public PlayerResultResponse toResponse() {
        return new PlayerResultResponse(name, avatar.id(), score, score, pointsAwarded, roundsPlayed, played, correct);
    }
}
