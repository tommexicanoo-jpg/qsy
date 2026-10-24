package com.ruletamod;

/**
 * Los 8 sectores de la ruleta, en sentido horario empezando por el de ARRIBA.
 * El orden DEBE coincidir con la textura wheel.png.
 */
public enum RuletaEvent {
    NARANJA("Cambio de dificultad", 0xFF9A1F),
    ROJA("Momento revi", 0xFF3030),
    ROSA("Efecto loco", 0xFF4FD8),
    MORADA("Teletransporte", 0xA040FF),
    AZUL("Cambio de clima", 0x4A6BFF),
    CIAN("Bendición o maldición", 0x20D8FF),
    VERDE("Aviso", 0x3CD23C),
    AMARILLA("Regalo", 0xFFE020);

    public final String titulo;
    public final int color;

    RuletaEvent(String titulo, int color) {
        this.titulo = titulo;
        this.color = color;
    }
}
