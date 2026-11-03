package br.com.perfil8.service;

import java.text.Normalizer;
import java.util.Locale;

public final class AnswerNormalizer {
    private AnswerNormalizer() {
    }

    public static String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]+", " ")
                .trim();
    }
}
