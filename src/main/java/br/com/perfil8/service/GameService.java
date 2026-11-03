package br.com.perfil8.service;

import br.com.perfil8.dto.CreateGameRequest;
import br.com.perfil8.dto.AnswerEvaluation;
import br.com.perfil8.dto.GameResponse;
import br.com.perfil8.dto.GuessRequest;
import br.com.perfil8.dto.GuessResponse;
import br.com.perfil8.model.Card;
import br.com.perfil8.model.Game;
import br.com.perfil8.model.Guess;
import br.com.perfil8.model.RoundStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class GameService {
    private final ConcurrentMap<UUID, Game> games = new ConcurrentHashMap<>();
    private final CardProvider cardProvider;
    private final AnswerEvaluator answerEvaluator;
    private final ScoreService scoreService;

    public GameService(CardProvider cardProvider, AnswerEvaluator answerEvaluator, ScoreService scoreService) {
        this.cardProvider = cardProvider;
        this.answerEvaluator = answerEvaluator;
        this.scoreService = scoreService;
    }

    public GameResponse createGame(CreateGameRequest request) {
        List<String> playerNames = normalizePlayerNames(request.players());
        List<String> avatarIds = normalizeAvatarIds(request.playerAvatars(), playerNames.size());
        UUID id = UUID.randomUUID();
        Game game = new Game(id, request.difficulty(), playerNames, avatarIds);
        game.startRound(cardProvider.createCard(game.getDifficulty(), game.getUsedAnswers()));
        games.put(id, game);
        return toResponse(game, "A apresentadora preparou uma nova carta. Boa sorte!");
    }

    public GameResponse getGame(UUID id) {
        Game game = findGame(id);
        synchronized (game) {
            return toResponse(game, "Aqui está o estado atual da partida.");
        }
    }

    public GuessResponse submitGuess(UUID id, GuessRequest request) {
        Game game = findGame(id);
        synchronized (game) {
            requireActiveRound(game);
            Card card = game.getCurrentCard();
            AnswerEvaluation evaluation = answerEvaluator.evaluate(card, request.guess(), game.getClueNumber());
            boolean correct = evaluation.correct();
            int points = correct ? scoreService.pointsForClue(game.getClueNumber()) : 0;
            game.recordGuess(new Guess(request.guess(), correct, game.getClueNumber(), points, Instant.now()));

            String message;
            String revealedAnswer = null;
            if (correct) {
                game.finishRound(RoundStatus.WON, points);
                message = "Acertou! Você ganhou " + points + " pontos.";
                revealedAnswer = card.answer();
            } else if (game.getClueNumber() == card.clues().size()) {
                game.finishRound(RoundStatus.LOST, 0);
                message = "As pistas acabaram. A resposta era: " + card.answer() + ".";
                revealedAnswer = card.answer();
            } else {
                message = "Ainda não foi dessa vez. Você pode tentar novamente ou pedir outra pista.";
            }

            return new GuessResponse(correct, points, message, revealedAnswer, toResponse(game, message));
        }
    }

    public GameResponse showNextClue(UUID id) {
        Game game = findGame(id);
        synchronized (game) {
            requireActiveRound(game);
            if (game.getPlayers().size() > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cada jogador pode receber apenas uma pista.");
            }
            try {
                game.showNextClue();
            } catch (IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
            }
            return toResponse(game, "Vamos à próxima pista.");
        }
    }

    public GameResponse passTurn(UUID id) {
        Game game = findGame(id);
        synchronized (game) {
            requireActiveRound(game);
            if (game.getPlayers().size() == 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Só é possível passar a vez em uma competição.");
            }
            game.finishRound(RoundStatus.LOST, 0);
            return toResponse(game, game.getCurrentPlayer() + " passou a vez.");
        }
    }

    public GameResponse startNextRound(UUID id) {
        Game game = findGame(id);
        synchronized (game) {
            if (game.getRoundStatus() == RoundStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Encerre a rodada atual antes de iniciar outra.");
            }
            if (game.getPlayers().size() > 1 && !game.hasNextTurn()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Todos os jogadores já completaram sua vez.");
            }
            if (game.getRoundNumber() >= game.getMaxRounds()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A partida já atingiu o limite de " + game.getMaxRounds() + " rodadas.");
            }
            try {
                Card nextCard = cardProvider.createCard(game.getDifficulty(), game.getUsedAnswers());
                if (game.getPlayers().size() > 1) {
                    game.prepareNextTurn();
                }
                game.startRound(nextCard);
            } catch (IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
            }
            return toResponse(game, "Nova rodada! Uma carta diferente está na mesa.");
        }
    }

    public GuessResponse giveUp(UUID id) {
        Game game = findGame(id);
        synchronized (game) {
            requireActiveRound(game);
            Card card = game.getCurrentCard();
            game.finishRound(RoundStatus.LOST, 0);
            String message = "A resposta era: " + card.answer() + ".";
            return new GuessResponse(false, 0, message, card.answer(), toResponse(game, message));
        }
    }

    private Game findGame(UUID id) {
        Game game = games.get(id);
        if (game == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida não encontrada.");
        }
        return game;
    }

    private static void requireActiveRound(Game game) {
        if (game.getRoundStatus() != RoundStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A rodada já foi encerrada.");
        }
    }

    private static List<String> normalizePlayerNames(List<String> requestedNames) {
        if (requestedNames == null || requestedNames.isEmpty()) {
            return List.of("Jogador 1");
        }
        if (requestedNames.size() > Game.MAX_PLAYERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A partida aceita no máximo 8 jogadores.");
        }
        List<String> names = requestedNames.stream()
                .map(name -> name == null ? "" : name.trim())
                .toList();
        if (names.stream().anyMatch(String::isBlank)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome de todos os jogadores.");
        }
        if (names.stream().map(name -> name.toLowerCase(Locale.ROOT)).distinct().count() != names.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada jogador precisa ter um nome diferente.");
        }
        return names;
    }

    private static List<String> normalizeAvatarIds(List<String> requestedAvatars, int playerCount) {
        if (requestedAvatars == null || requestedAvatars.isEmpty()) {
            return List.of();
        }
        if (requestedAvatars.size() != playerCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha um avatar para cada jogador.");
        }
        try {
            return java.util.stream.IntStream.range(0, playerCount)
                    .mapToObj(index -> br.com.perfil8.model.PlayerAvatar
                            .fromId(requestedAvatars.get(index), index).id())
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private static GameResponse toResponse(Game game, String presenterMessage) {
        Card card = game.getCurrentCard();
        int clueNumber = game.getClueNumber();
        if (game.isCompetitionFinished()) {
            presenterMessage = "A competição terminou! Confira o resultado final.";
        } else if (game.getRoundNumber() >= game.getMaxRounds() && game.getRoundStatus() != RoundStatus.ACTIVE) {
            presenterMessage = "Partida encerrada: você completou as " + game.getMaxRounds() + " rodadas.";
        }
        return new GameResponse(
                game.getId(),
                game.getDifficulty(),
                game.getRoundNumber(),
                game.getMaxRounds(),
                game.getPlayerRoundNumber(),
                game.getScore(),
                card.category(),
                clueNumber,
                card.clues().size(),
                card.clues().get(clueNumber - 1),
                game.getPlayers().size() == 1
                        && game.getRoundStatus() == RoundStatus.ACTIVE
                        && clueNumber < card.clues().size(),
                game.getRoundStatus(),
                presenterMessage,
                game.getCurrentPlayer(),
                game.getPlayers(),
                game.hasNextTurn(),
                game.isCompetitionFinished(),
                game.getWinners()
        );
    }
}
