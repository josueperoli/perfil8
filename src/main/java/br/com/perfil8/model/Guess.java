package br.com.perfil8.model;

import java.time.Instant;

public record Guess(String answer, boolean correct, int clueNumber, int pointsAwarded, Instant submittedAt) {
}
