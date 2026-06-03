package com.example.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estado físico del dado en 2.5D (Posición X, Y, altura simulada Z, rotación y torque).
 */
data class DicePhysicsState(
    val x: Float = 0.5f,        // 0.0f a 1.0f (Fracción de la mesa)
    val y: Float = 0.6f,        // 0.0f a 1.0f
    val vx: Float = 0.0f,       // Velocidad horizontal
    val vy: Float = 0.0f,       // Velocidad vertical
    val z: Float = 0.0f,        // Altura 3D simulada (Z-Axis)
    val vz: Float = 0.0f,       // Velocidad de rebote en Z
    val rotation: Float = 0f,   // Ángulo de giro en grados
    val omega: Float = 0f,      // Velocidad angular (torque)
    val isRolling: Boolean = false,
    val currentFace: Int = 4,   // Cara visible actual
    val lastCollisionOccurred: Boolean = false,
    val rollTimeAccumulator: Float = 0f // Tiempo transcurrido de giro para frenado de seguridad
) {
    // Escala del dado basada en la altura Z (da un efecto 3D magnífico)
    val scale: Float
        get() = 1.0f + (z * 1.5f)
}

/**
 * Simulador físico en Kotlin para el lanzamiento táctil del dado.
 */
class DicePhysicsEngine {

    companion object {
        private const val GRAVITY_Z = -14.0f    // Fuerza que jala el dado hacia abajo
        private const val FRICTION = 0.94f       // Fricción de la mesa (coeficiente aerodinámico)
        private const val REBOTE_RESTITUCION = 0.65f // Cuánto rebota el dado contra los bordes
        private const val PISO_REBOTE = 0.55f   // Cuánto rebota en el eje Z al chocar con la mesa
        private const val PARADA_THRESHOLD = 0.005f // Cuándo consideramos que ya se detuvo
    }

    /**
     * Calcula el siguiente estado del dado transcurrido un delta de tiempo [dt].
     * Retorna el nuevo estado y un booleano indicando si ocurrió un choque acústico con el piso.
     */
    fun updateState(state: DicePhysicsState, dt: Float): Pair<DicePhysicsState, Boolean> {
        if (!state.isRolling) return Pair(state, false)

        // 1. Aplicar movimiento horizontal
        var newX = state.x + state.vx * dt
        var newY = state.y + state.vy * dt
        var newVx = state.vx * FRICTION
        var newVy = state.vy * FRICTION

        // 2. Aplicar movimiento vertical (Eje Z - Gravedad tridimensional)
        var newZ = state.z + state.vz * dt
        var newVz = state.vz + GRAVITY_Z * dt
        var bouncerSound = false

        // Si el dado colisiona con el piso de la mesa (Z <= 0)
        if (newZ <= 0f) {
            newZ = 0f
            // Si tiene suficiente velocidad vertical de caída, rebota
            if (abs(newVz) > 1.2f) {
                newVz = -newVz * PISO_REBOTE
                bouncerSound = true
                // El impacto contra el suelo altera la velocidad angular ligeramente
                newVx += (Math.random().toFloat() - 0.5f) * 0.1f
                newVy += (Math.random().toFloat() - 0.5f) * 0.1f
            } else {
                newVz = 0f
            }
        }

        // 3. Colisiones con los bordes de la mesa (Rebotadores magnéticos de la fosa)
        val limitXMin = 0.05f
        val limitXMax = 0.95f
        val limitYMin = 0.12f
        val limitYMax = 0.88f

        if (newX < limitXMin) {
            newX = limitXMin
            newVx = -newVx * REBOTE_RESTITUCION
            bouncerSound = true
        } else if (newX > limitXMax) {
            newX = limitXMax
            newVx = -newVx * REBOTE_RESTITUCION
            bouncerSound = true
        }

        if (newY < limitYMin) {
            newY = limitYMin
            newVy = -newVy * REBOTE_RESTITUCION
            bouncerSound = true
        } else if (newY > limitYMax) {
            newY = limitYMax
            newVy = -newVy * REBOTE_RESTITUCION
            bouncerSound = true
        }

        // 4. Rotación y Torque
        val newRotation = (state.rotation + state.omega * dt * 50f) % 360f
        val newOmega = state.omega * FRICTION

        // Cambiar la cara aleatoriamente mientras gira a gran velocidad para dar efecto dinámico
        var newFace = state.currentFace
        if (abs(state.vx) + abs(state.vy) + abs(state.vz) > 0.8f) {
            // El dado rota caóticamente
            if (Math.random() < 0.2) {
                newFace = (1..6).random()
            }
        }

        // 5. Evaluar si se detuvo por completo o superó el tiempo máximo de seguridad
        val elapsed = state.rollTimeAccumulator + dt
        val timeoutOccurred = elapsed > 2.5f

        val isMoving = !timeoutOccurred && (abs(newVx) > PARADA_THRESHOLD ||
                abs(newVy) > PARADA_THRESHOLD ||
                abs(newZ) > 0.001f ||
                abs(newVz) > 0.1f)

        val finalVelocityX = if (isMoving) newVx else 0f
        val finalVelocityY = if (isMoving) newVy else 0f
        val finalOmega = if (isMoving) newOmega else 0f
        val finalRolling = isMoving

        // Si se acaba de detener, aseguramos que la cara final sea una decisión firme
        if (state.isRolling && !finalRolling) {
            // Decisión final: cara determinada por el giro o un entero aleatorio ponderado
            // (Si era 1, de acuerdo con el Agalludo Royale, dará un susto tremendo)
        }

        return Pair(
            DicePhysicsState(
                x = newX,
                y = newY,
                vx = finalVelocityX,
                vy = finalVelocityY,
                z = newZ,
                vz = newVz,
                rotation = newRotation,
                omega = finalOmega,
                isRolling = finalRolling,
                currentFace = newFace,
                rollTimeAccumulator = if (finalRolling) elapsed else 0f
            ),
            bouncerSound
        )
    }

    /**
     * Aplica un empuje basado en un gesto de arrastre de pantalla (Flick).
     */
    fun launchDice(dragDx: Float, dragDy: Float, dragDurationMs: Long): DicePhysicsState {
        // Velocidad = Distancia / Tiempo
        val timeSec = Math.max(dragDurationMs, 50L) / 1000f
        
        // Escalar velocidad horizontal y vertical
        var vx = (dragDx / 450f) / timeSec
        var vy = (dragDy / 450f) / timeSec

        // Limitar velocidades máximas para evitar que el dado escape de la física por completo
        val maxSpeed = 3.5f
        val currentSpeed = Math.sqrt((vx * vx + vy * vy).toDouble()).toFloat()
        if (currentSpeed > maxSpeed) {
            vx = (vx / currentSpeed) * maxSpeed
            vy = (vy / currentSpeed) * maxSpeed
        }

        // Si el tiro es insignificante, darle un empujón por defecto para evitar atascos
        if (currentSpeed < 0.2f) {
            vx = (Math.random().toFloat() - 0.5f) * 1.5f
            vy = -1.2f - (Math.random().toFloat() * 0.8f)
        }

        // Torque aleatorio proporcional a la velocidad de tiro (giro inicial fuerte)
        val speedMagnitude = Math.sqrt((vx * vx + vy * vy).toDouble()).toFloat()
        val sideFactor = if (Math.random() > 0.5) 1.0f else -1.0f
        val omega = (speedMagnitude * 12.0f + 6.0f) * sideFactor

        // Altura Z inicial basada en la fuerza de empuje para el lanzamiento de arco 3D
        val vz = 3.5f + speedMagnitude * 1.8f

        return DicePhysicsState(
            x = 0.5f,
            y = 0.85f, // Comienza cerca del vaso del jugador en la parte inferior
            vx = vx,
            vy = vy,
            z = 0.05f,
            vz = vz,
            rotation = (0..359).random().toFloat(),
            omega = omega,
            isRolling = true,
            currentFace = (1..6).random()
        )
    }
}
