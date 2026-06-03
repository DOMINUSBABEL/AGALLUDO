package com.example.game

/**
 * Representa a un jugador (humano o bot) en el juego Agalludo Royale.
 */
data class Player(
    val id: String,
    val name: String,
    val isBot: Boolean,
    val personality: BotPersonality? = null,
    val currentTurnScore: Int = 0, // Puntos acumulados en el turno actual (no asegurados)
    val securedTotalScore: Int = 0, // Puntos totales guardados
    val isEliminated: Boolean = false,
    val isWinner: Boolean = false,
    val isLockout: Boolean = false, // Penalización temporal si saca un 1
    val lockoutRemainingMs: Long = 0L, // Tiempo restante de parálisis
    val lastRollResult: Int? = null,
    val avatarEmoji: String = "💀"
) {
    val totalPossibleScore: Int
        get() = securedTotalScore + currentTurnScore
}

/**
 * Diferentes comportamientos que los bots pueden adoptar en base a su nivel de riesgo.
 */
enum class BotPersonality {
    CONSERVATIVE, // Se planta rápido (e.g. 8-10 pts), cuidadoso
    AGGRESSIVE,   // Arriesga mucho, busca grandes combinaciones (e.g. 15+ pts)
    TACTICAL      // Evalúa el tiempo restante, la distancia con el sótano y su salud
}

/**
 * Estado general del juego.
 */
enum class GameState {
    INTRO,       // Pantalla de título, atmósfera oscura y selección
    PLAYING,     // Ronda activa (30 segundos frenéticos)
    ELIMINATION_SHOCK, // Fase de ejecución fuera de escena, la cámara se desenfoca
    ROUND_SUMMARY, // Resumen de sobrevivientes de la ronda
    GAME_OVER,   // Has muerto o has sido ejecutado
    VICTORY      // Eres el Agalludo Supremo (Único sobreviviente)
}

/**
 * Un mensaje en la bitácora técnica de la consola industrial.
 */
data class ConsoleLog(
    val timestamp: String,
    val text: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO,
    WARN,
    DANGER,
    SUCCESS,
    SYSTEM
}
