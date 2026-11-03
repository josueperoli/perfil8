package br.com.perfil8.model;

import java.util.List;
import java.util.Locale;

public enum PlayerAvatar {
    FOX,
    OWL,
    TIGER,
    DRAGON,
    AXOLOTL,
    CAT,
    RABBIT,
    OCTOPUS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlayerAvatar fromId(String id, int playerIndex) {
        if (id == null || id.isBlank()) {
            return values()[playerIndex % values().length];
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Avatar de jogador inválido.", exception);
        }
    }

    public static List<String> ids() {
        return java.util.Arrays.stream(values()).map(PlayerAvatar::id).toList();
    }
}
