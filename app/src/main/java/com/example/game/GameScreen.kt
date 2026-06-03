package com.example.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val players by viewModel.players.collectAsState()
    val lobbyTimer by viewModel.lobbyTimer.collectAsState()
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val diceState by viewModel.diceState.collectAsState()
    val botDiceStates by viewModel.botDiceStates.collectAsState()
    val lastEliminatedName by viewModel.lastEliminatedName.collectAsState()
    val sessionWins by viewModel.sessionWins.collectAsState()
    val sessionLosses by viewModel.sessionLosses.collectAsState()

    // Animación continua para simular el vaivén de la bombilla colgada del techo
    val infiniteTransition = rememberInfiniteTransition(label = "HangingLamp")
    val lampOffsetAngle by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LampAngle"
    )

    val lampIntensityPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LampPulse"
    )

    // Temblor de pantalla si el cronómetro está en peligro (< 6s) o durante impactos
    val shakeModifier = if (gameState == GameState.PLAYING && lobbyTimer <= 6f) {
        val randomOffsetX = ((-3..3).random() * (1f - (lobbyTimer / 6f))).dp
        val randomOffsetY = ((-3..3).random() * (1f - (lobbyTimer / 6f))).dp
        Modifier.offset(x = randomOffsetX, y = randomOffsetY)
    } else if (gameState == GameState.ELIMINATION_SHOCK) {
        val randomOffsetX = ((-6..6).random()).dp
        val randomOffsetY = ((-6..6).random()).dp
        Modifier.offset(x = randomOffsetX, y = randomOffsetY)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0B09)) // Paredes y fosa industrial profunda
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        when (gameState) {
            GameState.INTRO -> {
                IntroScreen(
                    wins = sessionWins,
                    losses = sessionLosses,
                    onStart = { viewModel.startGame() }
                )
            }
            GameState.PLAYING, GameState.ELIMINATION_SHOCK, GameState.ROUND_SUMMARY -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(shakeModifier)
                ) {
                    // 1. Temporizador mecánico / Cabecera industrial
                    HeaderConsole(
                        timer = lobbyTimer,
                        state = gameState,
                        onReset = { viewModel.resetGameToLobby() }
                    )

                    // 2. Tablero de supervivencia y mesa de dados en la mitad superior
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.3f)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Tabla de posiciones de sobrevivientes (Lateral)
                        SurvivalLeaderboard(
                            players = players,
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                        )

                        // Mesa central de madera gastada con foco oscilante
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF303030), RoundedCornerShape(16.dp))
                        ) {
                            WoodenTableArea(
                                playerDiceState = diceState,
                                botDiceStates = botDiceStates,
                                angleOffset = lampOffsetAngle,
                                intensity = lampIntensityPulse,
                                isShockPeriod = gameState == GameState.ELIMINATION_SHOCK,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 3. Bitácora retro y Controles interactivos en la mitad inferior
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF1C1B1B), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .border(
                                BorderStroke(1.dp, Color(0xFF303030)),
                                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Bitácora retro CRT
                            CrtConsoleLog(
                                logs = consoleLogs,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxHeight()
                            )

                            // Controles rápidos y estado de lockout del Jugador
                            PlayerActionCenter(
                                player = players.firstOrNull { it.id == "player" },
                                diceState = diceState,
                                onRoll = { viewModel.executePlayerRollAction() },
                                onBank = { viewModel.executePlayerBankAction() },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxHeight()
                            )
                        }

                        // Resumen de ronda si está activo (Overlay o control inferior)
                        if (gameState == GameState.ROUND_SUMMARY) {
                            RoundSummaryOverlay(
                                players = players,
                                lastEliminatedName = lastEliminatedName,
                                onNextRound = { viewModel.startNextRound() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF3C0001)) // Fondo rojo oscuro acorde con el tema
                                    .drawBehind {
                                        drawLine(
                                            color = Color(0xFFD32F2F),
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }

                // Shock dramático de foso en caso de eliminación in-situ
                if (gameState == GameState.ELIMINATION_SHOCK) {
                    PowerOutageOverlay(eliminatedName = lastEliminatedName)
                }
            }
            GameState.GAME_OVER -> {
                GameOverScreen(
                    lastEliminated = lastEliminatedName,
                    onRestart = { viewModel.startGame() },
                    onLobby = { viewModel.resetGameToLobby() }
                )
            }
            GameState.VICTORY -> {
                VictoryScreen(
                    onRestart = { viewModel.startGame() },
                    onLobby = { viewModel.resetGameToLobby() }
                )
            }
        }
    }
}

// --- SUB-PANTALLAS ---

@Composable
fun IntroScreen(wins: Int, losses: Int, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(
                text = "AGALLUDO",
                color = Color(0xFFD32F2F), // Rojo carmesí de la paleta Geometric Balance
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "ROYALE",
                color = Color(0xFFE6E1E5), // Color hues off-white
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.  dp, bottom = 8.dp)
            )
            Text(
                text = "GEOMETRIC BALANCE EDITION",
                color = Color(0xFF8E8E8E),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Card de Instrucción con diseño geométrico premium
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .background(Color(0xFF1C1B1B), shape = RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF303030), shape = RoundedCornerShape(16.dp))
                .drawBehind {
                    // Línea acentuada carmesí del lado izquierdo
                    drawLine(
                        color = Color(0xFFD32F2F),
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 4.dp.toPx()
                    )
                }
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "MECÁNICA DE CONTROL DE EJECUCIÓN",
                    color = Color(0xFFFFB4AB), // Color pastel rosado/rojo del tema
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "• Cada 30 segundos se cierra el ciclo mecánico. El del menor puntaje asegurado es eliminado físicamente.\n\n" +
                            "• Arroja el dado metálico. Los valores 2 a 6 se suman a tu reserva de riesgo.\n\n" +
                            "• Saca un \"1\" y tu turno se electrocuta temporalmente, devorando tu riesgo actual.\n\n" +
                            "• Asegura (BANK) para subir puestos y escapar de la fosa de la fiera.",
                    color = Color(0xFFCFC5C4),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Record de sesión
        Text(
            text = "REGISTRO CLANDESTINO",
            color = Color(0xFF8E8E8E),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SOBREVIVIDO: $wins",
                color = Color(0xFF2BEC1C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "EJECUTADO: $losses",
                color = Color(0xFFD32F2F),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Botón entrar a la mesa con estilo Geometric Balance
        Button(
            onClick = onStart,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD32F2F),
                contentColor = Color.White
            ),
            modifier = Modifier
                .width(240.dp)
                .height(56.dp)
                .testTag("iniciar_partida_btn")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ENTRAR A LA MESA",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun HeaderConsole(timer: Float, state: GameState, onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141212)) // Fondo de cabecera Geometric Balance
            .drawBehind {
                // Línea inferior geométrica delgada
                drawLine(
                    color = Color(0xFF2C2C2C),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "MESA-02 : EL ARENA",
                color = Color(0xFF8E8E8E), // Subtítulo apagado
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "PROTOCOLO SURVIVAL",
                    color = Color(0xFFE6E1E5), // Color principal
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        // Timer badge encapsulado estilo píldora Geometric Balance
        val isCritical = timer <= 10f
        val badgeBg = if (isCritical) Color(0xFF2C1614) else Color(0xFF1C1B1B)
        val badgeBorder = if (isCritical) Color(0xFF8C1D18) else Color(0xFF303030)
        val timerColor = if (isCritical) Color(0xFFFFB4AB) else Color(0xFF2BEC1C)

        Row(
            modifier = Modifier
                .background(badgeBg, RoundedCornerShape(50.dp))
                .border(1.dp, badgeBorder, RoundedCornerShape(50.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Icono animado o texto de estado
            Text(
                text = "⏱️",
                fontSize = 11.sp
            )
            Text(
                text = String.format("%04.1fs", timer),
                color = timerColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        IconButton(
            onClick = onReset,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Salir al lobby",
                tint = Color(0xFF8E8E8E),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SurvivalLeaderboard(players: List<Player>, modifier: Modifier = Modifier) {
    // Clasificar jugadores de mayor a menor puntuación obtenida
    val sortedList = players.sortedByDescending { it.securedTotalScore }
    
    // Encontrar el jugador activo con el menor puntaje para marcarlo para eliminación física
    val activePlayers = sortedList.filter { !it.isEliminated }
    val lowestActivePlayerId = activePlayers.lastOrNull()?.id

    Column(
        modifier = modifier
            .background(Color(0xFF1C1B1B), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF303030), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        horizontalAlignment = Alignment.Start
    ) {
        // Cabecera superior de la Tabla al estilo Rankings en Geometric Balance
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252423))
                .drawBehind {
                    drawLine(
                        color = Color(0xFF303030),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RANKINGS",
                color = Color(0xFFCFC5C4),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "SECURED",
                color = Color(0xFF8E8E8E),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Listado de sobrevivientes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            sortedList.forEachIndexed { index, player ->
                val isBottom = player.id == lowestActivePlayerId && !player.isEliminated
                val isYou = player.id == "player"

                val rowBg = when {
                    player.isEliminated -> Color(0xFF181515).copy(alpha = 0.5f)
                    isBottom -> Color(0xFF3C0001).copy(alpha = 0.4f)
                    isYou -> Color(0xFF2D2D2D).copy(alpha = 0.35f)
                    else -> Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .drawBehind {
                            // Línea de división inferior tenue
                            drawLine(
                                color = Color(0xFF303030).copy(alpha = 0.4f),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Si es el del peor puntaje, dibujar borde izquierdo rojo grueso
                            if (isBottom) {
                                drawLine(
                                    color = Color(0xFFD32F2F),
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = String.format("%02d", index + 1),
                                    color = if (isYou) Color(0xFFD32F2F) else Color(0xFF8E8E8E),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isYou) "YOU" else player.name.take(9),
                                    color = when {
                                        player.isEliminated -> Color(0xFF8E8E8E).copy(alpha = 0.5f)
                                        isYou -> Color(0xFFFFB4AB)
                                        else -> Color(0xFFE6E1E5)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (isYou) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (isBottom) {
                                Text(
                                    text = "TARGET",
                                    color = Color(0xFFD32F2F),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier
                                        .background(Color(0xFF3C0001), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${player.securedTotalScore} pts",
                                    color = if (player.isEliminated) Color(0xFF8E8E8E).copy(alpha = 0.4f) else Color(0xFFE6E1E5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (!player.isEliminated && player.currentTurnScore > 0) {
                                    Text(
                                        text = "+${player.currentTurnScore} risky",
                                        color = Color(0xFFFFB4AB),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Estados de sobrevivencia
                            when {
                                player.isEliminated -> {
                                    Text(
                                        text = "⚡ DEAD",
                                        color = Color(0xFFD32F2F).copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                player.isLockout -> {
                                    Text(
                                        text = "⚡ SHOCK",
                                        color = Color(0xFFFFB4AB),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "VIVO",
                                        color = Color(0xFF2BEC1C).copy(alpha = 0.8f),
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WoodenTableArea(
    playerDiceState: DicePhysicsState,
    botDiceStates: Map<String, DicePhysicsState>,
    angleOffset: Float,
    intensity: Float,
    isShockPeriod: Boolean,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    // Escuchar gestos de arrastre físico o clicks simples en la mesa para lanzar el dado
    Box(
        modifier = modifier
            .background(Color(0xFF1E1410)) // Marrón madera profundo
            .clickable { viewModel.executePlayerRollAction() } // ¡Tocar la mesa lanza los dados de inmediato!
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { viewModel.onDiceDragStart() },
                    onDragEnd = {
                        // El cálculo real del fling se hace por estimación de drag inercial
                        viewModel.onDiceDragRelease(10f, -600f) // Empuje rápido simulación por defecto
                    },
                    onDragCancel = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Movimiento del dedo capturado
                    }
                )
            }
            .drawBehind {
                val centerLight = Offset(size.width / 2f + sin(Math.toRadians(angleOffset.toDouble())).toFloat() * 100f, size.height / 4f)
                
                // Fondo texturizado de madera con vetas horizontales gastadas
                drawWoodTexture(size)

                // Lámpara con cono de luz radial degradada que oscila
                drawRadialLampGlow(centerLight, size, intensity, isShockPeriod)
            }
    ) {
        // 1. Enlazar dado interactivo del JUGADOR HUMANO
        DicePainter(
            diceState = playerDiceState,
            label = "TÚ",
            colorTheme = Color(0xFFE2DCD8),
            modifier = Modifier.fillMaxSize()
        )

        // 2. Enlazar dados de competidores BOTS (si se lanzaron en la mesa ronda actual)
        botDiceStates.forEach { (botId, bState) ->
            val tintColor = when (botId) {
                "bot_clinch" -> Color(0xFF81C784) // Verde tortuga suave
                "bot_gamble" -> Color(0xFFFFB74D) // Naranja lobo agresivo
                "bot_slasher" -> Color(0xFF4DD0E1) // Celeste tiburón táctico
                else -> Color(0xFFDED9D5)
            }
            val displayName = when (botId) {
                "bot_clinch" -> "CLINCH 🐢"
                "bot_gamble" -> "GAMBLE 🐺"
                "bot_slasher" -> "SLASHER 🦈"
                else -> "MESA"
            }
            DicePainter(
                diceState = bState,
                label = displayName,
                colorTheme = tintColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Pequeño texto instructivo flotando en el aire de la habitación
        if (!playerDiceState.isRolling) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color(0xD0140E0C), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "DESLIZA, TOCA LA MESA O PULSA LANZAR",
                    color = Color(0xFF7F8C8D),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun DrawScope.drawWoodTexture(size: Size) {
    // Dibujar fondo base sólido
    drawRect(color = Color(0xFF281912))
    
    // Vetas lineales simuladas
    val numVetas = 6
    val spacing = size.height / numVetas
    for (i in 0..numVetas) {
        val y = i * spacing
        drawLine(
            color = Color(0x3B150A05),
            start = Offset(0f, y),
            end = Offset(size.width, y + 25f),
            strokeWidth = 14f
        )
    }

    // Dibujar mesa de dados límites mecánicos
    drawRect(
        color = Color(0x40FF0000),
        size = Size(size.width * 0.9f, size.height * 0.8f),
        topLeft = Offset(size.width * 0.05f, size.height * 0.12f),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))
    )
}

private fun DrawScope.drawRadialLampGlow(center: Offset, size: Size, intensity: Float, isBlackout: Boolean) {
    if (isBlackout) {
        // Ejecución blackout: Luz intermitente roja parpadeante
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xBBFF0000), Color.Transparent),
                center = center,
                radius = size.width * 0.7f
            )
        )
        return
    }

    // Luz incandescente de tubo industrial
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xDEFFE89E).copy(alpha = 0.55f * intensity),
                Color(0x3BFFE89E).copy(alpha = 0.15f * intensity),
                Color.Transparent
            ),
            center = center,
            radius = size.width * 0.65f
        )
    )

    // Un foco de calor pequeño
    drawCircle(
        color = Color(0xFFFFFABC).copy(alpha = 0.8f),
        radius = 16f,
        center = center
    )
}

@Composable
fun DicePainter(
    diceState: DicePhysicsState,
    label: String,
    colorTheme: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 1. Dibujar el cuerpo tridimensional del dado en Canvas de manera física
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tableW = size.width
            val tableH = size.height

            val dicePxX = diceState.x * tableW
            val dicePxY = diceState.y * tableH

            // Sombra en el piso
            val shadowDistanceOffset = 18f * diceState.scale
            val shadowX = dicePxX + shadowDistanceOffset
            val shadowY = dicePxY + shadowDistanceOffset
            val shadowRadius = 38f * diceState.scale
            val shadowAlpha = Math.max(0.08f, 0.45f - diceState.z * 0.08f)

            drawCircle(
                color = Color.Black.copy(alpha = shadowAlpha),
                radius = shadowRadius,
                center = Offset(shadowX, shadowY)
            )

            val diceSize = 64f * diceState.scale

            withTransform({
                translate(dicePxX, dicePxY)
                rotate(diceState.rotation)
            }) {
                // Sombra de extrusión inferior 3D del dado para resaltar los bordes
                val extrudeOffset = 5f * diceState.scale
                drawRoundRect(
                    color = Color(0xFF4A0A0A), // Tonalidad de extrusión oscura
                    topLeft = Offset(-diceSize / 2f + extrudeOffset, -diceSize / 2f + extrudeOffset),
                    size = Size(diceSize, diceSize),
                    cornerRadius = CornerRadius(12f * diceState.scale)
                )

                // Cara frontal superior del dado
                val diceBaseColor = if (diceState.currentFace == 1) Color(0xFFFF2E2E) else colorTheme
                drawRoundRect(
                    color = diceBaseColor,
                    topLeft = Offset(-diceSize / 2f, -diceSize / 2f),
                    size = Size(diceSize, diceSize),
                    cornerRadius = CornerRadius(12f * diceState.scale)
                )

                // Filo interior metálico gastado
                drawRoundRect(
                    color = if (diceState.currentFace == 1) Color.Black else Color(0xFF636363),
                    topLeft = Offset(-diceSize / 2f, -diceSize / 2f),
                    size = Size(diceSize, diceSize),
                    cornerRadius = CornerRadius(12f * diceState.scale),
                    style = Stroke(width = 3.5f * diceState.scale)
                )

                // Pips de acuerdo a la cara obtenida
                val dotRadius = 6.5f * diceState.scale
                val dotColor = if (diceState.currentFace == 1) Color.White else Color(0xFF1E272C)

                val offsetLeft = -diceSize * 0.26f
                val offsetCenter = 0f
                val offsetRight = diceSize * 0.26f

                when (diceState.currentFace) {
                    1 -> {
                        drawCircle(color = Color.Black, radius = dotRadius * 1.5f, center = Offset(offsetCenter, offsetCenter))
                        drawCircle(color = Color.White, radius = dotRadius * 0.6f, center = Offset(offsetCenter, offsetCenter))
                    }
                    2 -> {
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetLeft))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetRight))
                    }
                    3 -> {
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetLeft))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetCenter, offsetCenter))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetRight))
                    }
                    4 -> {
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetLeft))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetLeft))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetRight))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetRight))
                    }
                    5 -> {
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetLeft))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetLeft))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetCenter, offsetCenter))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetRight))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetRight))
                    }
                    6 -> {
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetLeft))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetLeft))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetCenter))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetCenter))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetLeft, offsetRight))
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(offsetRight, offsetRight))
                    }
                }
            }
        }

        // 2. Dibujar overlay con etiqueta de identificación del dueño usando coordenadas relativas (100% seguro)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val dWidth = maxWidth
            val dHeight = maxHeight

            val xPos = diceState.x * dWidth.value
            val yPos = diceState.y * dHeight.value
            val textGap = 35f * diceState.scale

            Box(
                modifier = Modifier
                    .offset(
                        x = (xPos - 30).dp,
                        y = (yPos - textGap - 22).dp
                    )
                    .background(Color(0xE6140E0C), RoundedCornerShape(4.dp))
                    .border(0.5.dp, colorTheme.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = label,
                    color = colorTheme,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CrtConsoleLog(logs: List<ConsoleLog>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // Scrollear automáticamente al fondo de la bitácora cuando entra un log nuevo
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF141212), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF303030), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        // Línea del canóptron retro CRT
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Dibujar scanlines
            val scanSpacing = 16f
            for (y in 0..size.height.toInt() step scanSpacing.toInt()) {
                drawLine(
                    color = Color(0x10000000),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 3f
                )
            }
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs) { log ->
                val logColor = when (log.type) {
                    LogType.INFO -> Color(0xFFE6E1E5)     // Blanco hues
                    LogType.WARN -> Color(0xFFFFB4AB)     // Amber warning suave
                    LogType.DANGER -> Color(0xFFD32F2F)   // Rojo Geometric Balance
                    LogType.SUCCESS -> Color(0xFF2BEC1C)  // Verde exitoso
                    LogType.SYSTEM -> Color(0xFF8E8E8E)   // Gris sistema
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "[${log.timestamp}] ",
                        color = Color(0xFF5D6D7E),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = log.text,
                        color = logColor,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerActionCenter(
    player: Player?,
    diceState: DicePhysicsState,
    onRoll: () -> Unit,
    onBank: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (player == null) return

    val hasTurnPoints = player.currentTurnScore > 0
    val isLockout = player.isLockout

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Panel de Estado Local del Jugador al estilo Geometric Balance (Card Secured/Accumulated)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141212), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF303030), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ESTADO LOCAL",
                    color = Color(0xFF8E8E8E),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // SECURED (GUARDS)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${player.securedTotalScore}",
                            color = Color(0xFFE6E1E5), // Color hues off-white
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "SECURED",
                            color = Color(0xFF8E8E8E),
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Divisor interior
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(Color(0xFF303030))
                    )

                    // ACCUMULATED (RISKY)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isLockout) "0" else "+${player.currentTurnScore}",
                            color = if (isLockout) Color(0xFFD32F2F) else Color(0xFFFFB4AB), // Color rosado destacado
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "ACCUMULATING",
                            color = Color(0xFF8E8E8E),
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Si está en Lockout, mostrar advertencia de parálisis eléctrica táctil (Fondo rojo estilo target)
        if (isLockout) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .background(Color(0xFF3C0001).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFD32F2F), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "⚡ ADVERTENCIA ⚡",
                        color = Color(0xFFD32F2F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SABOTAJE DEL DADO: SACASTE UN 1",
                        color = Color(0xFFFFB4AB),
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp
                    )
                }
            }
        } else {
            // Controles estándar extremadamente estilizados de Geometric Balance
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                // Botón LANZAR estilo "Roll Dice" de Geometric Balance (Píldora grande, roja brillante)
                Button(
                    onClick = onRoll,
                    enabled = !diceState.isRolling,
                    shape = RoundedCornerShape(50.dp), // Rounded full
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F), // Rojo carmesí brillante
                        disabledContainerColor = Color(0x3CD32F2F),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("lanzar_dado_btn")
                ) {
                    Text(
                        text = if (diceState.isRolling) "AGITANDO DADO..." else "LANZAR DADO 🎲",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Botón PLANTARSE (BANK) estilo "Bank Points" (Forma redondeada, color oscuro refinado)
                Button(
                    onClick = onBank,
                    enabled = hasTurnPoints,
                    shape = RoundedCornerShape(12.dp), // Rounded-2xl / 12dp estilo Geometric Balance
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF352F2E), // Marrón carbón premium
                        disabledContainerColor = Color(0x1F352F2E),
                        contentColor = Color(0xFFFFB4AB), // Color rosado destacado
                        disabledContentColor = Color(0xFF8E8E8E).copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(
                        1.dp, 
                        if (hasTurnPoints) Color(0xFF4D4544) else Color(0x304D4544)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("plantarse_bank_btn")
                ) {
                    Text(
                        text = "ASEGURAR (BANK) 💼",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun RoundSummaryOverlay(
    players: List<Player>,
    lastEliminatedName: String,
    onNextRound: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.2f)) {
            Text(
                text = "TRIBUNAL COPIADO: CICLO ACABADO",
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "$lastEliminatedName ha sido fusilado.",
                color = Color(0xFFFFF0F0),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Button(
            onClick = onNextRound,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            ),
            modifier = Modifier
                .height(36.dp)
                .testTag("siguiente_ronda_btn")
        ) {
            Text(
                text = "SIGUIENTE RONDA",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PowerOutageOverlay(eliminatedName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60D0505)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "💥 PROTOCOLO DE CONCILIACIÓN MECÁNICA 💥",
                color = Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "EJECUTANDO AL PEOR DE LA MESA:",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = eliminatedName.uppercase(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(color = Color.Red)
        }
    }
}

@Composable
fun GameOverScreen(lastEliminated: String, onRestart: () -> Unit, onLobby: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141212)) // Fondo unificado Geometric Balance
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FUISTE ELIMINADO",
            color = Color(0xFFD32F2F), // Rojo carmesí principal
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "La fosa mecánica se cerró bajo tu silla. El tribunal de Agalludo no perdona los bajos números.",
            color = Color(0xFF8E8E8E), // Subtexto apagado
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 42.dp),
            lineHeight = 18.sp
        )

        Button(
            onClick = onRestart,
            shape = RoundedCornerShape(50.dp), // Píldora
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD32F2F),
                contentColor = Color.White
            ),
            modifier = Modifier
                .width(240.dp)
                .height(48.dp)
                .testTag("reintentar_partida_btn")
        ) {
            Text(
                text = "REINTENTAR 🔄",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onLobby,
            shape = RoundedCornerShape(12.dp), // Rounded-2xl / 12dp estilo Geometric Balance
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF352F2E), // Marrón carbón premium
                contentColor = Color(0xFFFFB4AB) // Color rosado destacado
            ),
            border = BorderStroke(1.dp, Color(0xFF4D4544)),
            modifier = Modifier
                .width(240.dp)
                .height(48.dp)
                .testTag("regresar_lobby_btn")
        ) {
            Text(
                text = "MENÚ PRINCIPAL",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun VictoryScreen(onRestart: () -> Unit, onLobby: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141212)) // Fondo unificado Geometric Balance
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SOBREVIVIENTE",
            color = Color(0xFF2BEC1C), // Verde brillante de éxito
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "AGALLUDO SUPREMO",
            color = Color(0xFFE6E1E5), // Color hues off-white
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 8.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Las tres mesas mecánicas de fusilamiento han sido vaciadas. Eres el único en pie que escapó de la fosa del 1.",
            color = Color(0xFF8E8E8E), // Subtexto apagado
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 42.dp),
            lineHeight = 18.sp
        )

        Button(
            onClick = onRestart,
            shape = RoundedCornerShape(50.dp), // Píldora
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2BEC1C),
                contentColor = Color.Black
            ),
            modifier = Modifier
                .width(240.dp)
                .height(48.dp)
                .testTag("victoria_repartir_btn")
        ) {
            Text(
                text = "VOLVER A JUGAR 🎰",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onLobby,
            shape = RoundedCornerShape(12.dp), // Rounded-2xl / 12dp estilo Geometric Balance
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF352F2E), // Marrón carbón premium
                contentColor = Color(0xFFFFB4AB) // Color rosado destacado
            ),
            border = BorderStroke(1.dp, Color(0xFF4D4544)),
            modifier = Modifier
                .width(240.dp)
                .height(48.dp)
                .testTag("victoria_lobby_btn")
        ) {
            Text(
                text = "MENÚ PRINCIPAL",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
