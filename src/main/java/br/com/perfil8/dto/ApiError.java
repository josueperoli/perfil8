package br.com.perfil8.dto;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String message) {
}
