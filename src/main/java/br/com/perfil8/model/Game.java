package br.com.perfil8.model;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Game {
    public static final int MAX_ROUNDS = 10;
    public static final int MAX_PLAYERS = 8;

    private final UUID id;
    private final Difficulty difficulty;
    private final List<GamePlayer> players;
    private final Set<String> usedAnswers = new HashSet<>();
    private final List<Guess> guesses = new ArrayList<>();
    private int score;
    private int roundNumber;
    private int currentPlayerIndex;
    private Card currentCard;
    private int clueNumber;
    private RoundStatus roundStatus = RoundStatus.ACTIVE;

    public Game(UUID id, Difficulty difficulty) {
        this(id, difficulty, List.of("Jogador 1"));
    }

    public Game(UUID id, Difficulty difficulty, List<String> playerNames) {
        this(id, difficulty, playerNames, List.of());
    }

    public Game(UUID id, Difficulty difficulty, List<String> playerNames, List<String> avatarIds) {
        if (id == null || difficulty == null) {
            throw new IllegalArgumentException("Identificador e dificuldade são obrigatórios.");
        }
        if (playerNames == null || playerNames.isEmpty() || playerNames.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("A partida deve ter entre 1 e 8 jogadores.");
        }
        this.id = id;
        this.difficulty = difficulty;
        if (avatarIds == null || !avatarIds.isEmpty() && avatarIds.size() != playerNames.size()) {
            throw new IllegalArgumentException("Cada jogador deve ter um avatar válido.");
        }
        this.players = java.util.stream.IntStream.range(0, playerNames.size())
                .mapToObj(index -> new GamePlayer(playerNames.get(index),
                        PlayerAvatar.fromId(avatarIds.isEmpty() ? null : avatarIds.get(index), index)))
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public synchronized Set<String> getUsedAnswers() {
        return Set.copyOf(usedAnswers);
    }

    public synchronized int getScore() {
        return players.size() > 1 ? players.get(currentPlayerIndex).getScore() : score;
    }

    public synchronized String getCurrentPlayer() {
        return players.get(currentPlayerIndex).getName();
    }

    public synchronized List<br.com.perfil8.dto.PlayerResultResponse> getPlayers() {
        return players.stream().map(GamePlayer::toResponse).toList();
    }

    public synchronized boolean hasNextTurn() {
        return players.size() > 1 && currentPlayerIndex + 1 < players.size();
    }

    public synchronized boolean isCompetitionFinished() {
        return players.size() > 1 && roundStatus != RoundStatus.ACTIVE && currentPlayerIndex == players.size() - 1;
    }

    public synchronized List<String> getWinners() {
        if (!isCompetitionFinished()) {
            return List.of();
        }
        int highestScore = players.stream().mapToInt(GamePlayer::getScore).max().orElse(0);
        return players.stream()
                .filter(player -> player.getScore() == highestScore)
                .map(GamePlayer::getName)
                .toList();
    }

    public synchronized int getMaxRounds() {
        return players.size() > 1 ? players.size() : MAX_ROUNDS;
    }

    public synchronized int getRoundNumber() {
        return roundNumber;
    }

    public synchronized int getPlayerRoundNumber() {
        return players.get(currentPlayerIndex).getRoundsPlayed() + (roundStatus == RoundStatus.ACTIVE ? 1 : 0);
    }

    public synchronized Card getCurrentCard() {
        return currentCard;
    }

    public synchronized int getClueNumber() {
        return clueNumber;
    }

    public synchronized RoundStatus getRoundStatus() {
        return roundStatus;
    }

    public synchronized List<Guess> getGuesses() {
        return List.copyOf(guesses);
    }

    public synchronized void startRound(Card card) {
        if (card == null || card.difficulty() != difficulty) {
            throw new IllegalArgumentException("A carta deve existir e respeitar a dificuldade da partida.");
        }
        if (roundNumber >= getMaxRounds()) {
            throw new IllegalStateException("A partida já atingiu o limite de rodadas.");
        }
        if (usedAnswers.contains(card.answer())) {
            throw new IllegalArgumentException("A resposta já foi usada nesta partida.");
        }
        currentCard = card;
        roundNumber++;
        clueNumber = 1;
        roundStatus = RoundStatus.ACTIVE;
        usedAnswers.add(card.answer());
    }

    public synchronized void showNextClue() {
        if (roundStatus != RoundStatus.ACTIVE || currentCard == null || clueNumber >= currentCard.clues().size()) {
            throw new IllegalStateException("Não há outra pista disponível nesta rodada.");
        }
        clueNumber++;
    }

    public synchronized void recordGuess(Guess guess) {
        if (roundStatus != RoundStatus.ACTIVE || guess == null) {
            throw new IllegalStateException("Não é possível registrar um palpite nesta rodada.");
        }
        guesses.add(guess);
    }

    public synchronized void finishRound(RoundStatus status, int pointsAwarded) {
        if (roundStatus != RoundStatus.ACTIVE || status == null || status == RoundStatus.ACTIVE || pointsAwarded < 0) {
            throw new IllegalStateException("A rodada não pode ser encerrada com esses valores.");
        }
        roundStatus = status;
        score += pointsAwarded;
        players.get(currentPlayerIndex).finishTurn(status, pointsAwarded);
    }

    public synchronized void prepareNextTurn() {
        if (!hasNextTurn() || roundStatus == RoundStatus.ACTIVE) {
            throw new IllegalStateException("Não há outro jogador aguardando a vez.");
        }
        currentPlayerIndex++;
    }
}
