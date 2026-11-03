package br.com.perfil8.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuessRequest(
        @NotBlank(message = "Digite um palpite.")
        @Size(max = 120, message = "O palpite deve ter no máximo 120 caracteres.")
        String guess
) {
}
