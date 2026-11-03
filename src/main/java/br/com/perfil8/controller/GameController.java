package br.com.perfil8.controller;

import br.com.perfil8.dto.CreateGameRequest;
import br.com.perfil8.dto.GameResponse;
import br.com.perfil8.dto.GuessRequest;
import br.com.perfil8.dto.GuessResponse;
import br.com.perfil8.service.GameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    public GameResponse createGame(@Valid @RequestBody CreateGameRequest request) {
        return gameService.createGame(request);
    }

    @GetMapping("/{id}")
    public GameResponse getGame(@PathVariable UUID id) {
        return gameService.getGame(id);
    }

    @PostMapping("/{id}/guess")
    public GuessResponse submitGuess(@PathVariable UUID id, @Valid @RequestBody GuessRequest request) {
        return gameService.submitGuess(id, request);
    }

    @PostMapping("/{id}/clue")
    public GameResponse showNextClue(@PathVariable UUID id) {
        return gameService.showNextClue(id);
    }

    @PostMapping("/{id}/pass-turn")
    public GameResponse passTurn(@PathVariable UUID id) {
        return gameService.passTurn(id);
    }

    @PostMapping("/{id}/next-round")
    public GameResponse startNextRound(@PathVariable UUID id) {
        return gameService.startNextRound(id);
    }

    @PostMapping("/{id}/give-up")
    public GuessResponse giveUp(@PathVariable UUID id) {
        return gameService.giveUp(id);
    }
}
