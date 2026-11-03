package br.com.perfil8.dto;

import br.com.perfil8.model.Difficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGameRequest(
        @NotNull Difficulty difficulty,
        @Size(max = 8) List<@NotBlank @Size(max = 30) String> players,
        @Size(max = 8) List<@NotBlank @Size(max = 20) String> playerAvatars
) {
    public CreateGameRequest(Difficulty difficulty) {
        this(difficulty, null, null);
    }

    public CreateGameRequest(Difficulty difficulty, List<String> players) {
        this(difficulty, players, null);
    }
}
