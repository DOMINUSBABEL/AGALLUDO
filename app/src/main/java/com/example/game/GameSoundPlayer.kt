package com.example.game

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Sintetizador de audio en tiempo real estilo industrial retro.
 * Utiliza AudioTrack para generar tonos sin necesidad de cargar pesados archivos MP3.
 */
class GameSoundPlayer {

    private val sampleRate = 22050
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Toca un click de temporizador mecánico metálico.
     */
    fun playTick() {
        scope.launch {
            val duration = 0.04f    // 40ms
            val numSamples = (duration * sampleRate).toInt()
            val sample = ShortArray(numSamples)
            val freq = 1200.0 // Frecuencia alta metálica

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Decaimiento exponencial agresivo
                val amplitude = 32767.0 * Math.exp(-t * 90.0)
                sample[i] = (amplitude * sin(2 * Math.PI * freq * t)).toInt().toShort()
            }
            playRaw(sample)
        }
    }

    /**
     * Toca un latido de corazón de alta tensión (bajo profundo).
     */
    fun playHeartbeat() {
        scope.launch {
            val duration = 0.18f    // 180ms
            val numSamples = (duration * sampleRate).toInt()
            val sample = ShortArray(numSamples)
            val freq = 55.0 // Sub-bass

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val amplitude = 32767.0 * Math.exp(-t * 18.0)
                // Modulación para simular la sístole
                val wave = sin(2 * Math.PI * freq * t) * (1.0 - t / duration)
                sample[i] = (amplitude * wave).toInt().toShort()
            }
            playRaw(sample)
        }
    }

    /**
     * Sonido crujiente de dados chocando sobre madera gastada.
     */
    fun playDiceRattle() {
        scope.launch {
            val duration = 0.12f
            val numSamples = (duration * sampleRate).toInt()
            val sample = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val amplitude = 18000.0 * Math.exp(-t * 30.0)
                // Ruido blanco filtrado o ráfagas aleatorias para simular el plástico golpeando
                val noise = if (Math.random() > 0.4) 1.0 else -1.0
                val freq = 440.0 + Math.sin(t * 150.0) * 200.0
                val wave = sin(2 * Math.PI * freq * t) * 0.5 + noise * 0.5
                sample[i] = (amplitude * wave).toInt().toShort()
            }
            playRaw(sample)
        }
    }

    /**
     * Tono descendente para cuando se saca un 1 perdedor (Bust).
     */
    fun playBust() {
        scope.launch {
            val duration = 0.6f
            val numSamples = (duration * sampleRate).toInt()
            val sample = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Caída de frecuencia dramática
                val freq = 280.0 - (t / duration) * 200.0
                val amplitude = 28000.0 * Math.exp(-t * 3.0)
                // Forma de onda agresiva (saturación manual para onda pseudo-cuadrada tensa)
                val rawSine = sin(2 * Math.PI * freq * t)
                val wave = if (rawSine > 0) 0.6 else -0.6
                sample[i] = (amplitude * wave).toInt().toShort()
            }
            playRaw(sample)
        }
    }

    /**
     * Chime ascendente y metálico para el Bank de puntos.
     */
    fun playBank() {
        scope.launch {
            val duration = 0.4f
            val numSamples = (duration * sampleRate).toInt()
            val sample = ShortArray(numSamples)

            // Primera nota (Mi) y Segunda nota (Si) combinadas
            val freq1 = 659.25 // E5
            val freq2 = 987.77 // B5

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val amplitude = 24000.0 * Math.exp(-t * 6.0)
                // Transición del primer tono al segundo tono
                val mixRatio = (t / duration)
                val wave = sin(2 * Math.PI * freq1 * t) * (1.0 - mixRatio) + sin(2 * Math.PI * freq2 * t) * mixRatio
                sample[i] = (amplitude * wave).toInt().toShort()
            }
            playRaw(sample)
        }
    }

    /**
     * Ruido de descarga hidráulica o ejecución metálica cuando se elimina un jugador.
     */
    fun playElimination() {
        scope.launch {
            val duration = 1.8f
            val numSamples = (duration * sampleRate).toInt()
            val sample = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val tPct = t / duration
                
                // Un zumbido mecánico que decae muy lento
                val rumbleFreq = 90.0 - tPct * 60.0
                val rumble = sin(2 * Math.PI * rumbleFreq * t)
                
                // Ruido industrial sibilante (simula vapor o impacto metálico)
                val noise = (Math.random() * 2.0 - 1.0) * Math.exp(-t * 5.0)
                
                val amplitude = 32767.0 * (1.0 - tPct) * Math.exp(-t * 0.8)
                val wave = (rumble * 0.6 + noise * 0.4)
                
                sample[i] = (amplitude * wave).toInt().toShort()
            }
            playRaw(sample)
        }
    }

    private fun playRaw(samples: ShortArray) {
        try {
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                samples.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            // Monitorear para liberar
            scope.launch {
                val playDurationMs = (samples.size.toFloat() / sampleRate * 1000).toLong() + 100
                kotlinx.coroutines.delay(playDurationMs)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    // Ignorar errores menores de liberación
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
