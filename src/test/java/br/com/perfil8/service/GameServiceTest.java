package br.com.perfil8.service;

import br.com.perfil8.dto.CreateGameRequest;
import br.com.perfil8.dto.GameResponse;
import br.com.perfil8.dto.GuessRequest;
import br.com.perfil8.dto.GuessResponse;
import br.com.perfil8.model.Card;
import br.com.perfil8.model.Category;
import br.com.perfil8.model.Difficulty;
import br.com.perfil8.model.Game;
import br.com.perfil8.model.PlayerAvatar;
import br.com.perfil8.model.RoundStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameServiceTest {
    private static final List<String> CLUES = List.of(
            "Pista um.", "Pista dois.", "Pista três.", "Pista quatro.", "Pista cinco.",
            "Pista seis.", "Pista sete.", "Pista oito.", "Pista nove.", "Pista dez."
    );

    @Test
    void hidesAnswerUntilRoundIsOver() throws JsonProcessingException {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL));

        assertFalse(new ObjectMapper().writeValueAsString(game).contains("Resposta Secreta"));

        GuessResponse result = service.submitGuess(game.gameId(), new GuessRequest("Resposta Secreta"));

        assertTrue(result.correct());
        assertEquals("Resposta Secreta", result.revealedAnswer());
        assertEquals(20, result.pointsAwarded());
        assertEquals(RoundStatus.WON, result.game().roundStatus());
        assertEquals(20, result.game().score());
        assertEquals(20, result.game().players().getFirst().boardPosition());
        assertEquals("fox", result.game().players().getFirst().avatarId());
    }

    @Test
    void awardsPointsBasedOnTheClueUsed() {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL));

        service.showNextClue(game.gameId());
        GuessResponse result = service.submitGuess(game.gameId(), new GuessRequest("resposta secreta"));

        assertTrue(result.correct());
        assertEquals(18, result.pointsAwarded());
        assertEquals(18, result.game().score());
    }

    @Test
    void awardsTwoPointsAndMovesTwoSpacesOnTheLastClue() {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL));
        for (int clueNumber = 1; clueNumber < 10; clueNumber++) {
            game = service.showNextClue(game.gameId());
        }

        GuessResponse result = service.submitGuess(game.gameId(), new GuessRequest("Resposta Secreta"));

        assertEquals(2, result.pointsAwarded());
        assertEquals(2, result.game().players().getFirst().boardPosition());
    }

    @Test
    void revealsAnswerWhenPlayerRunsOutOfClues() {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL));

        for (int clueNumber = 1; clueNumber < 10; clueNumber++) {
            service.showNextClue(game.gameId());
        }
        GuessResponse result = service.submitGuess(game.gameId(), new GuessRequest("palpite errado"));

        assertFalse(result.correct());
        assertEquals(0, result.pointsAwarded());
        assertEquals("Resposta Secreta", result.revealedAnswer());
        assertEquals(RoundStatus.LOST, result.game().roundStatus());
        assertFalse(result.game().hasNextClue());
        assertThrows(ResponseStatusException.class, () -> service.showNextClue(game.gameId()));
    }

    @Test
    void simultaneousRequestsCannotStartTwoRounds() throws Exception {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL));
        service.submitGuess(game.gameId(), new GuessRequest("Resposta Secreta"));

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> tryStartingNextRound(service, game.gameId(), start));
            Future<Boolean> second = executor.submit(() -> tryStartingNextRound(service, game.gameId(), start));
            start.countDown();

            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            assertEquals(2, service.getGame(game.gameId()).roundNumber());
        }
    }

    @Test
    void runsOneTurnPerPlayerAndReturnsTheCompetitionWinner() {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL, List.of("Ana", "Bia")));

        assertEquals("Ana", game.currentPlayer());
        assertEquals(2, game.maxRounds());
        assertFalse(game.hasNextClue());
        java.util.UUID gameId = game.gameId();
        assertThrows(ResponseStatusException.class, () -> service.showNextClue(gameId));
        GuessResponse firstTurn = service.submitGuess(game.gameId(), new GuessRequest("Resposta Secreta"));
        assertTrue(firstTurn.game().hasNextTurn());
        assertFalse(firstTurn.game().competitionFinished());
        assertEquals(20, firstTurn.game().players().getFirst().score());

        game = service.startNextRound(game.gameId());
        assertEquals("Bia", game.currentPlayer());
        assertEquals(0, game.score());
        GuessResponse wrongGuess = service.submitGuess(game.gameId(), new GuessRequest("palpite errado"));
        assertFalse(wrongGuess.game().competitionFinished());
        GameResponse finalTurn = service.passTurn(game.gameId());

        assertTrue(finalTurn.competitionFinished());
        assertEquals(List.of("Ana"), finalTurn.winners());
        assertEquals(20, finalTurn.players().getFirst().score());
        assertFalse(finalTurn.players().get(1).correct());
        assertEquals(2, finalTurn.roundNumber());
        assertThrows(ResponseStatusException.class, () -> service.startNextRound(gameId));
    }

    @Test
    void passingTurnEndsCurrentPlayerTurnAndAllowsNextPlayerToStart() {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL, List.of("Ana", "Bia")));

        GameResponse passedTurn = service.passTurn(game.gameId());

        assertEquals(RoundStatus.LOST, passedTurn.roundStatus());
        assertEquals("Ana", passedTurn.currentPlayer());
        assertTrue(passedTurn.hasNextTurn());
        assertEquals(0, passedTurn.score());

        GameResponse nextTurn = service.startNextRound(game.gameId());
        assertEquals("Bia", nextTurn.currentPlayer());
        assertEquals(1, nextTurn.clueNumber());
        assertFalse(nextTurn.hasNextClue());
    }

    @Test
    void rejectsDuplicatePlayerNames() {
        GameService service = createService();

        assertThrows(ResponseStatusException.class,
                () -> service.createGame(new CreateGameRequest(Difficulty.FACIL, List.of("Ana", " ana "))));
    }

    @Test
    void assignsSelectedAvatarsToPlayers() {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(
                Difficulty.FACIL,
                List.of("Ana", "Bia"),
                List.of("dragon", "axolotl")
        ));

        assertEquals(List.of("dragon", "axolotl"),
                game.players().stream().map(player -> player.avatarId()).toList());
        assertEquals(PlayerAvatar.DRAGON.id(), game.players().getFirst().avatarId());
    }

    @Test
    void rejectsUnknownPlayerAvatar() {
        GameService service = createService();

        assertThrows(ResponseStatusException.class, () -> service.createGame(new CreateGameRequest(
                Difficulty.FACIL, List.of("Ana"), List.of("unknown")
        )));
    }

    @Test
    void localCardsHaveTenCluesForEveryDifficulty() {
        TestCardProvider provider = new TestCardProvider();
        for (Difficulty difficulty : Difficulty.values()) {
            Set<String> usedAnswers = new HashSet<>();
            for (int round = 0; round < Game.MAX_ROUNDS; round++) {
                Card card = provider.createCard(difficulty, usedAnswers);
                assertEquals(10, card.clues().size());
                assertTrue(usedAnswers.add(card.answer()));
            }
        }
    }

    @Test
    void soloGameCanStartTenLocalCardsWithoutRepeatingAnswers() {
        GameService service = new GameService(new TestCardProvider(), new ExactAnswerEvaluator(), new ScoreService());
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL));

        for (int round = 1; round < Game.MAX_ROUNDS; round++) {
            assertEquals(round, game.roundNumber());
            game = service.giveUp(game.gameId()).game();
            game = service.startNextRound(game.gameId());
        }

        assertEquals(Game.MAX_ROUNDS, game.roundNumber());
        assertEquals(10, game.totalClues());
        assertTrue(game.hasNextClue());
    }

    @Test
    void reportsATieWhenPlayersFinishWithTheSameScore() {
        GameService service = createService();
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL, List.of("Ana", "Bia")));
        service.giveUp(game.gameId());

        game = service.startNextRound(game.gameId());
        GuessResponse finalTurn = service.giveUp(game.gameId());

        assertTrue(finalTurn.game().competitionFinished());
        assertEquals(List.of("Ana", "Bia"), finalTurn.game().winners());
    }

    @Test
    void limitsGameToTenRounds() {
        AtomicInteger generatedCards = new AtomicInteger();
        CardProvider provider = (difficulty, excludedAnswers) -> {
            int cardNumber = generatedCards.incrementAndGet();
            return new Card("Resposta " + cardNumber, Category.CURIOSIDADES, difficulty, CLUES,
                    Set.of("Resposta " + cardNumber));
        };
        GameService service = new GameService(provider, new ExactAnswerEvaluator(), new ScoreService());
        GameResponse game = service.createGame(new CreateGameRequest(Difficulty.FACIL));

        assertEquals(10, game.maxRounds());
        for (int round = 1; round <= Game.MAX_ROUNDS; round++) {
            assertEquals(round, game.roundNumber());
            GuessResponse result = service.submitGuess(game.gameId(), new GuessRequest("Resposta " + round));
            game = result.game();
            if (round < Game.MAX_ROUNDS) {
                game = service.startNextRound(game.gameId());
            }
        }

        assertEquals(Game.MAX_ROUNDS, game.roundNumber());
        assertEquals("Partida encerrada: você completou as 10 rodadas.", game.presenterMessage());
        assertEquals(200, game.score());
        assertEquals(200, game.players().getFirst().score());
        assertFalse(game.competitionFinished());
        assertEquals(Game.MAX_ROUNDS, generatedCards.get());
        java.util.UUID gameId = game.gameId();
        assertThrows(ResponseStatusException.class, () -> service.startNextRound(gameId));
        assertEquals(Game.MAX_ROUNDS, service.getGame(gameId).roundNumber());
        assertEquals(Game.MAX_ROUNDS, generatedCards.get());
    }

    private static boolean tryStartingNextRound(GameService service, java.util.UUID gameId, CountDownLatch start)
            throws InterruptedException {
        start.await();
        try {
            service.startNextRound(gameId);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private static GameService createService() {
        List<Card> cards = List.of(
                new Card("Resposta Secreta", Category.CURIOSIDADES, Difficulty.FACIL, CLUES,
                        Set.of("Resposta Secreta")),
                new Card("Outra Resposta", Category.CURIOSIDADES, Difficulty.FACIL, CLUES,
                        Set.of("Outra Resposta"))
        );
        CardProvider provider = (difficulty, excludedAnswers) -> cards.stream()
                .filter(card -> card.difficulty() == difficulty)
                .filter(card -> !excludedAnswers.contains(card.answer()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Sem cartas de teste."));
        return new GameService(provider, new ExactAnswerEvaluator(), new ScoreService());
    }
}
