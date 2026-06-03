package com.example.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class GameViewModel : ViewModel() {

    // Sonidos integrados
    private val soundPlayer = GameSoundPlayer()

    // Motor físico
    private val physicsEngine = DicePhysicsEngine()

    // Estado del juego principal
    private val _gameState = MutableStateFlow(com.example.game.GameState.INTRO)
    val gameState: StateFlow<com.example.game.GameState> = _gameState.asStateFlow()

    // Jugadores activos
    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    // Temporizador principal del Lobby (en segundos)
    private val _lobbyTimer = MutableStateFlow(30.0f)
    val lobbyTimer: StateFlow<Float> = _lobbyTimer.asStateFlow()

    // Bitácora retro de eventos de la mesa
    private val _consoleLogs = MutableStateFlow<List<ConsoleLog>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLog>> = _consoleLogs.asStateFlow()

    // Lanzamiento del dado e interactividad física
    private val _diceState = MutableStateFlow(DicePhysicsState())
    val diceState: StateFlow<DicePhysicsState> = _diceState.asStateFlow()

    // Lanzamiento de dados de bots e interactividad física
    private val _botDiceStates = MutableStateFlow<Map<String, DicePhysicsState>>(emptyMap())
    val botDiceStates: StateFlow<Map<String, DicePhysicsState>> = _botDiceStates.asStateFlow()

    // Registro de arrastre táctil para simular "flick"
    private var dragStartTimestamp = 0L
    private var dragStarted = false

    // Control de trabajos de corrutinas para bucles del juego
    private var gameLoopJob: Job? = null
    private var timerLoopJob: Job? = null
    private var botLogicJob: Job? = null

    // Almacenamos el último eliminado temporal para mostrarlo de forma dramática
    private val _lastEliminatedName = MutableStateFlow("")
    val lastEliminatedName: StateFlow<String> = _lastEliminatedName.asStateFlow()

    // Registro de estadísticas locales de juego de la sesión actual
    private val _sessionWins = MutableStateFlow(0)
    val sessionWins: StateFlow<Int> = _sessionWins.asStateFlow()

    private val _sessionLosses = MutableStateFlow(0)
    val sessionLosses: StateFlow<Int> = _sessionLosses.asStateFlow()

    private val timeFormatter = SimpleDateFormat("mm:ss", Locale.getDefault())

    init {
        resetGameToLobby()
    }

    /**
     * Inicializa o reinicia todos los jugadores para una nueva partida del Battle Royale.
     */
    fun resetGameToLobby() {
        _gameState.value = com.example.game.GameState.INTRO
        _lobbyTimer.value = 30.0f
        _lastEliminatedName.value = ""
        _diceState.value = DicePhysicsState()
        _botDiceStates.value = emptyMap()

        val initialPlayers = listOf(
            Player(
                id = "player",
                name = "TU (JUGADOR)",
                isBot = false,
                securedTotalScore = 0,
                currentTurnScore = 0,
                avatarEmoji = "👤"
            ),
            Player(
                id = "bot_clinch",
                name = "CLINCH (BOT)",
                isBot = true,
                personality = BotPersonality.CONSERVATIVE,
                securedTotalScore = 0,
                currentTurnScore = 0,
                avatarEmoji = "🐢"
            ),
            Player(
                id = "bot_gamble",
                name = "GAMBLE (BOT)",
                isBot = true,
                personality = BotPersonality.AGGRESSIVE,
                securedTotalScore = 0,
                currentTurnScore = 0,
                avatarEmoji = "🐺"
            ),
            Player(
                id = "bot_slasher",
                name = "SLASHER (BOT)",
                isBot = true,
                personality = BotPersonality.TACTICAL,
                securedTotalScore = 0,
                currentTurnScore = 0,
                avatarEmoji = "🦈"
            )
        )
        _players.value = initialPlayers
        
        _consoleLogs.value = listOf(
            ConsoleLog(getCurrentTimeString(), "SISTEMA INICIALIZADO: AGALLUDO ROYALE v1.02", LogType.SYSTEM),
            ConsoleLog(getCurrentTimeString(), "HABITACIÓN CÉRRADA. CONTROL MECÁNICO OPERATIVO.", LogType.SYSTEM),
            ConsoleLog(getCurrentTimeString(), "DESLIZA EL DADO HACIA ADELANTE PARA COMPRESIÓN DE FUERZA.", LogType.INFO)
        )

        // Detener bucles activos anteriores
        stopAllLoops()
    }

    /**
     * Inicia los bucles continuos del juego simulando la acción en tiempo real.
     */
    fun startGame() {
        _gameState.value = com.example.game.GameState.PLAYING
        _lobbyTimer.value = 30.0f
        _botDiceStates.value = emptyMap()
        
        // Limpiar estados de turno de todos los sobrevivientes y quitar parálisis eléctricas
        _players.update { list ->
            list.map { it.copy(securedTotalScore = 0, currentTurnScore = 0, isEliminated = false, lastRollResult = null, isLockout = false, lockoutRemainingMs = 0L) }
        }

        addLog("TRIBUNAL DE RECTITUD MECÁNICA INICIADO. 30 SEGUNDOS O EJECUCIÓN.", LogType.WARN)
        soundPlayer.playBank()

        stopAllLoops()
        startTimerLoop()
        startGamePhysicsLoop()
        startBotDecisionLoop()
    }

    private fun stopAllLoops() {
        gameLoopJob?.cancel()
        timerLoopJob?.cancel()
        botLogicJob?.cancel()
    }

    /**
     * Lazo físico ejecutado a ~33ms (30 FPS) para simular el desplazamiento inercial de la física de los dados.
     */
    private fun startGamePhysicsLoop() {
        gameLoopJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val dt = (now - lastTime) / 1000f
                lastTime = now

                // 1. Actualizar dado del Jugador Humano
                val currentState = _diceState.value
                if (currentState.isRolling) {
                    val (newState, playCollideSound) = physicsEngine.updateState(currentState, dt)
                    _diceState.value = newState

                    if (playCollideSound) {
                        soundPlayer.playDiceRattle()
                    }

                    // En el instante justo donde frena el dado, resolvemos el tiro del Jugador
                    if (!newState.isRolling) {
                        resolvePlayerThrow(newState.currentFace)
                    }
                }

                // 2. Actualizar dados físicos de los competidores Bots
                val botsMap = _botDiceStates.value
                if (botsMap.isNotEmpty()) {
                    var mapChanged = false
                    val updatedBotsMap = botsMap.mapValues { (botId, bState) ->
                        if (bState.isRolling) {
                            val (newState, playCollideSound) = physicsEngine.updateState(bState, dt)
                            mapChanged = true

                            if (playCollideSound) {
                                soundPlayer.playDiceRattle()
                            }

                            if (!newState.isRolling) {
                                resolveBotThrow(botId, newState.currentFace)
                            }
                            newState
                        } else {
                            bState
                        }
                    }
                    if (mapChanged) {
                        _botDiceStates.value = updatedBotsMap
                    }
                }

                delay(30)
            }
        }
    }

    /**
     * Lazo en tiempo real que descuenta centésimas al temporizador global (Lobby de eliminación).
     */
    private fun startTimerLoop() {
        timerLoopJob = viewModelScope.launch {
            var lastPulseSecond = 30
            while (_gameState.value == com.example.game.GameState.PLAYING) {
                delay(100)
                val current = _lobbyTimer.value - 0.1f
                if (current <= 0.0f) {
                    _lobbyTimer.value = 0.0f
                    triggerRoundElimination()
                    break
                } else {
                    _lobbyTimer.value = current
                    val currentSec = current.toInt()
                    
                    // Latido cardíaco en zona de riesgo crítica (< 8 segundos)
                    if (currentSec <= 8) {
                        if (currentSec != lastPulseSecond) {
                            soundPlayer.playHeartbeat()
                            lastPulseSecond = currentSec
                        }
                    } else if (currentSec != lastPulseSecond) {
                        // Ticks normales para la tensión del temporizador metálico
                        if (currentSec % 5 == 0) {
                            soundPlayer.playTick()
                        }
                        lastPulseSecond = currentSec
                    }
                }
            }
        }
    }

    /**
     * Inteligencia artificial de Bot Competidores: Toman decisiones asíncronas cada x segundos.
     */
    private fun startBotDecisionLoop() {
        botLogicJob = viewModelScope.launch {
            while (_gameState.value == com.example.game.GameState.PLAYING) {
                // Intervalo aleatorio imitando el tiempo de reacción humana de los bots
                delay((1200..2200).random().toLong())
                
                // Obtener jugadores bots válidos que no estén eliminados ni paralizados
                val activeBots = _players.value.filter { it.isBot && !it.isEliminated && !it.isLockout }
                if (activeBots.isEmpty()) continue

                // Hacer mover a un bot aleatorio de la mesa
                val selectedBot = activeBots.random()
                executeBotAction(selectedBot)
            }
        }
    }

    /**
     * Algoritmo de Inteligencia Artificial para el dado Agalludo de un Bot de acuerdo a su perfil de riesgo.
     */
    private fun executeBotAction(bot: Player) {
        val personality = bot.personality ?: BotPersonality.CONSERVATIVE
        val timer = _lobbyTimer.value
        val currentScore = bot.currentTurnScore
        val secured = bot.securedTotalScore
        
        // Clasificamos las posiciones de la tabla de menor a mayor
        val sortedAlive = _players.value.filter { !it.isEliminated }.sortedBy { it.securedTotalScore }
        val lowestSecured = sortedAlive.firstOrNull()?.securedTotalScore ?: 0
        val isBotInDangerZone = sortedAlive.firstOrNull()?.id == bot.id

        // Determinar probabilidad de plantarse ("Bank")
        var bankProbability = 0.0f

        if (currentScore == 0) {
            // No ha acumulado nada esta ronda -> DEBE tirar
            rollBotDice(bot)
            return
        }

        when (personality) {
            BotPersonality.CONSERVATIVE -> {
                // Se planta rápido si ya superó los 8 puntos
                bankProbability = when {
                    currentScore >= 12 -> 0.95f
                    currentScore >= 8 -> 0.70f
                    else -> 0.15f
                }
                // Si el tiempo es muy bajo (< 5s), prefiere asegurar de inmediato
                if (timer < 6.0f) bankProbability += 0.3f
            }
            BotPersonality.AGGRESSIVE -> {
                // Arriesga mucho, buscando grandes lanzamientos de 16+ puntos
                bankProbability = when {
                    currentScore >= 18 -> 0.90f
                    currentScore >= 14 -> 0.60f
                    currentScore >= 10 -> 0.20f
                    else -> 0.01f
                }
                // Si el bot está en peligro absoluto y la fosa timer es < 3s, asegura
                if (isBotInDangerZone && timer < 3.0f) {
                    bankProbability = 0.99f
                }
            }
            BotPersonality.TACTICAL -> {
                // Evalúa estratégicamente las distancias de puntos de eliminación
                if (isBotInDangerZone) {
                    // Si está en el fondo, tiene pánico. Sigue tirando a menos de que obtenga suficiente para salvarse
                    val deficit = (sortedAlive.getOrNull(1)?.securedTotalScore ?: 10) - secured
                    if (currentScore >= deficit && currentScore >= 8) {
                        bankProbability = 0.85f // Satisfecho porque escapa del fondo
                    } else {
                        // Sigue tirando porque necesita escalar
                        bankProbability = 0.10f
                    }
                } else {
                    // Si está a salvo, asegura puntos más moderadamente (e.g. 10 pts)
                    bankProbability = when {
                        currentScore >= 12 -> 0.75f
                        currentScore >= 10 -> 0.50f
                        else -> 0.05f
                    }
                }

                // Ajuste extremo por tiempo de fosa
                if (timer < 5.0f && currentScore > 4) {
                    bankProbability = 0.95f
                }
            }
        }

        // Ejecutar decisión aleatoria según probabilidad calculada
        if (Math.random().toFloat() < bankProbability) {
            bankBotPoints(bot)
        } else {
            rollBotDice(bot)
        }
    }

    private fun rollBotDice(bot: Player) {
        viewModelScope.launch {
            // Tocar sonido rápido de agitar vaso de dados del bot
            soundPlayer.playDiceRattle()
            delay(400)

            // Crear un ángulo y empuje físico aleatorio dinámico imitando lanzamiento bot
            val angle = (30..150).random().toFloat()
            val rad = Math.toRadians(angle.toDouble())
            val force = 1.6f + (Math.random().toFloat() * 0.8f) // fuerza en rango 1.6f a 2.4f
            val dragDx = (cos(rad) * force * 300).toFloat()
            val dragDy = (-sin(rad) * force * 300).toFloat()

            // Lanzar el dado del bot desde la zona superior de la pantalla
            val launchState = physicsEngine.launchDice(dragDx, dragDy, 80L).copy(
                x = 0.25f + (Math.random().toFloat() * 0.5f), // rango 0.25f..0.75f
                y = 0.15f + (Math.random().toFloat() * 0.2f)  // rango 0.15f..0.35f
            )

            _botDiceStates.update { it + (bot.id to launchState) }
        }
    }

    private fun resolveBotThrow(botId: String, face: Int) {
        _players.update { list ->
            list.map { p ->
                if (p.id == botId) {
                    if (face == 1) {
                        addLog("${p.avatarEmoji} ${p.name} falló sacando un 1.", LogType.DANGER)
                        soundPlayer.playBust()
                        p.copy(
                            currentTurnScore = 0,
                            lastRollResult = 1,
                            isLockout = true,
                            lockoutRemainingMs = 2000L
                        )
                    } else {
                        val newScore = p.currentTurnScore + face
                        addLog("${p.avatarEmoji} ${p.name} sacó un $face (Acumula: $newScore).", LogType.INFO)
                        p.copy(currentTurnScore = newScore, lastRollResult = face)
                    }
                } else p
            }
        }

        val botClient = _players.value.firstOrNull { it.id == botId }
        if (botClient?.isLockout == true) {
            viewModelScope.launch {
                delay(2500)
                liberarLockout(botId)
            }
        }
    }

    private fun bankBotPoints(bot: Player) {
        val ptsBanked = bot.currentTurnScore
        if (ptsBanked == 0) return

        _players.update { list ->
            list.map { p ->
                if (p.id == bot.id) {
                    val finalSecured = p.securedTotalScore + ptsBanked
                    addLog("${bot.avatarEmoji} ${bot.name} aseguró (BANK) $ptsBanked pts (Total: $finalSecured).", LogType.SUCCESS)
                    p.copy(securedTotalScore = finalSecured, currentTurnScore = 0)
                } else p
            }
        }
        soundPlayer.playBank()
    }

    private fun liberarLockout(playerId: String) {
        _players.update { list ->
            list.map { p ->
                if (p.id == playerId) p.copy(isLockout = false, lockoutRemainingMs = 0L) else p
            }
        }
    }

    // --- INTERACTIVOS DEL JUGADOR PRINCIPAL ---

    /**
     * El jugador arrastra/suelta el dado. Registramos cuando empieza el toque.
     */
    fun onDiceDragStart() {
        if (_gameState.value != com.example.game.GameState.PLAYING) return
        val myself = _players.value.firstOrNull { it.id == "player" }
        if (myself?.isLockout == true || myself?.isEliminated == true) return

        dragStartTimestamp = SystemClock.elapsedRealtime()
        dragStarted = true
    }

    /**
     * El jugador completa el gesto de estirar y soltar el dado con cierta fuerza táctil.
     */
    fun onDiceDragRelease(dragDx: Float, dragDy: Float) {
        if (!dragStarted) return
        dragStarted = false

        val myself = _players.value.firstOrNull { it.id == "player" }
        if (myself?.isLockout == true || myself?.isEliminated == true) return
        if (_diceState.value.isRolling) return // Ya está rodando en físico

        val duration = SystemClock.elapsedRealtime() - dragStartTimestamp
        val forceState = physicsEngine.launchDice(dragDx, dragDy, duration)
        
        _diceState.value = forceState
        addLog("👤 Has lanzado el dado físico con torque inercial...", LogType.SYSTEM)
        soundPlayer.playDiceRattle()
    }

    /**
     * El jugador realiza el lanzamiento presionando el botón rápido "Lanzar Dado".
     */
    fun executePlayerRollAction() {
        if (_gameState.value != com.example.game.GameState.PLAYING) return
        val myself = _players.value.firstOrNull { it.id == "player" }
        if (myself?.isLockout == true || myself?.isEliminated == true) return
        if (_diceState.value.isRolling) return

        // Crear una física acelerada aleatoria de rebotes rápidos
        val angle = (30..150).random().toFloat()
        val rad = Math.toRadians(angle.toDouble())
        val force = 2.4f
        val dragDx = (cos(rad) * force * 300).toFloat()
        val dragDy = (-sin(rad) * force * 300).toFloat()

        onDiceDragStart()
        _diceState.value = physicsEngine.launchDice(dragDx, dragDy, 80L)
        addLog("👤 Lanzando el dado metálico...", LogType.SYSTEM)
        soundPlayer.playDiceRattle()
    }

    /**
     * El dado físico detuvo su velocidad por completo. Evaluamos la puntuación del jugador.
     */
    private fun resolvePlayerThrow(face: Int) {
        _players.update { list ->
            list.map { p ->
                if (p.id == "player") {
                    if (face == 1) {
                        addLog("👤 Sacaste un 1 Mortal! Puntos acumulados de la ronda evaporados.", LogType.DANGER)
                        soundPlayer.playBust()
                        p.copy(
                            currentTurnScore = 0,
                            lastRollResult = 1,
                            isLockout = true,
                            lockoutRemainingMs = 3000L
                        )
                    } else {
                        val newScore = p.currentTurnScore + face
                        addLog("👤 Sacaste un $face (Acumulado temporal: $newScore).", LogType.INFO)
                        p.copy(currentTurnScore = newScore, lastRollResult = face)
                    }
                } else p
            }
        }

        // Si sacó un 1, iniciamos parálisis por susto de 3 segundos
        val p = _players.value.first { it.id == "player" }
        if (p.isLockout) {
            viewModelScope.launch {
                delay(3000)
                liberarLockout("player")
            }
        }
    }

    /**
     * El jugador decide de forma consciente asegurar sus puntos acumulados en la ronda (Bank).
     */
    fun executePlayerBankAction() {
        if (_gameState.value != com.example.game.GameState.PLAYING) return
        val myself = _players.value.firstOrNull { it.id == "player" } ?: return
        if (myself.isLockout || myself.isEliminated || myself.currentTurnScore == 0) return

        val ptsBanked = myself.currentTurnScore
        _players.update { list ->
            list.map { p ->
                if (p.id == "player") {
                    val newSecured = p.securedTotalScore + ptsBanked
                    addLog("👤 Aseguraste (BANK) $ptsBanked pts! (Tu total guardado: $newSecured).", LogType.SUCCESS)
                    p.copy(securedTotalScore = newSecured, currentTurnScore = 0)
                } else p
            }
        }
        soundPlayer.playBank()
    }

    // --- FASES DE LA MAQUINA DE ESTADOS (ELIMINACIÓN BATTLE ROYALE) ---

    /**
     * Ejecuta el temido tribunal de la rueda de ejecución cuando la cuenta regresiva llega a 0.
     */
    private fun triggerRoundElimination() {
        stopAllLoops()
        _gameState.value = com.example.game.GameState.ELIMINATION_SHOCK

        viewModelScope.launch {
            addLog("⌛ TIEMPO AGOTADO! DETENIENDO MECANISMOS DE LANZAMIENTO.", LogType.SYSTEM)
            // Pequeña pausa dramática con parpadeo o alarma
            delay(1000)
            
            // Buscar los sobrevivientes vigentes antes de este cierre
            val survivors = _players.value.filter { !it.isEliminated }
            if (survivors.isEmpty()) return@launch

            // Ordenar por puntaje asegurado de menor a mayor
            val minScore = survivors.minOf { it.securedTotalScore }
            
            // Los candidatos a eliminación son aquellos que empatan en el peor puntaje
            val candidates = survivors.filter { it.securedTotalScore == minScore }
            
            // Si el jugador está entre ellos, y hay bot también, podemos lanzar una probabilidad o desempatar al azar.
            // Para ser más implacable, eliminamos a cualquiera con la puntuación mínima.
            // Escogemos a uno de los peores del fondo para eliminar quirúrgicamente.
            val toEliminate = candidates.random()

            _lastEliminatedName.value = toEliminate.name
            addLog("⛓️ EJECUCIÓN INMEDIATA: ${toEliminate.name} con un puntaje asegurado de ${toEliminate.securedTotalScore} es DESCARGADO.", LogType.DANGER)
            soundPlayer.playElimination()

            // Marcar al desventurado como eliminado en nuestra lista de estado
            _players.update { list ->
                list.map { p ->
                    if (p.id == toEliminate.id) p.copy(isEliminated = true, currentTurnScore = 0) else p
                }
            }

            delay(3500) // Pantallazo de shock de fosa y desenfoque cinematográfico

            // Chequear si el juego terminó (Winner) o si pasamos al resumen
            val remainingSurvivors = _players.value.filter { !it.isEliminated }
            
            val isUserAlive = remainingSurvivors.any { it.id == "player" }

            if (!isUserAlive) {
                // El jugador falleció en la ruleta rusa
                _gameState.value = com.example.game.GameState.GAME_OVER
                _sessionLosses.update { it + 1 }
                addLog("💀 PROTOCOLO TERMINADO: Has sido eliminado del Agalludo Royale.", LogType.DANGER)
            } else if (remainingSurvivors.size == 1) {
                // Quedas solo tú! Eres el sobreviviente
                _players.update { list ->
                    list.map { p -> if (p.id == "player") p.copy(isWinner = true) else p }
                }
                _gameState.value = com.example.game.GameState.VICTORY
                _sessionWins.update { it + 1 }
                addLog("👑 AGALLUDO SUPREMO: ¡Has sobrevivido a la ruleta y vencido a la fosa!", LogType.SUCCESS)
            } else {
                // Pasamos al resumen de sobrevivientes antes del siguiente bloque de 30 segundos
                _gameState.value = com.example.game.GameState.ROUND_SUMMARY
            }
        }
    }

    /**
     * El usuario vio el resumen de sobrevivientes y decide pasar a la siguiente ronda de supervivencia.
     */
    fun startNextRound() {
        _gameState.value = com.example.game.GameState.PLAYING
        _lobbyTimer.value = 30.0f
        _botDiceStates.value = emptyMap()
        
        // Limpiamos los puntos temporales (currentTurnScore) de todos los sobrevivientes para la equidad
        // ¡Pero persisten los puntos sumados acumulados de la anterior ronda para mantener la tensión in crescendo!
        _players.update { list ->
            list.map { p ->
                if (!p.isEliminated) {
                    p.copy(currentTurnScore = 0, lastRollResult = null, isLockout = false)
                } else p
            }
        }

        addLog("⛓️ SIGUIENTE RONDA ELECTROCUTADA. 30 SEGUNDOS ACELERADOS.", LogType.WARN)
        soundPlayer.playBank()

        stopAllLoops()
        startTimerLoop()
        startGamePhysicsLoop()
        startBotDecisionLoop()
    }

    // --- BITÁCORAL DE REGISTRO RETRO INDUSTRIAL ---

    private fun addLog(text: String, type: LogType = LogType.INFO) {
        val newLog = ConsoleLog(
            timestamp = getCurrentTimeString(),
            text = text,
            type = type
        )
        // Guardamos máximo 40 logs para no ralentizar el búfer de texto
        val currentList = _consoleLogs.value
        val limitedList = if (currentList.size > 45) currentList.takeLast(40) else currentList
        _consoleLogs.value = limitedList + newLog
    }

    private fun getCurrentTimeString(): String {
        return timeFormatter.format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        stopAllLoops()
    }
}
