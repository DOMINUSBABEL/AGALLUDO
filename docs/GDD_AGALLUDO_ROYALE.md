# DOCUMENTO DE DISEÑO EJECUTABLE (GDD): AGALLUDO ROYALE (AGAYUL)
**Plataforma Primaria:** WebGL / Three.js / Cannon.js (100% Navegador, Cero Dependencias Pesadas)
**Modo:** Battle Royale de Dados 8P Simultáneo · Múltiples Tableros · Físicas de Combate & Poderes
**Autor & Estudio:** BABYLON.IA / Antigravity

---

## 1. Pilares Fundamentales No Negociables

1. **Caos Físico de 8 Dados Concurrentes:**
   En cada ronda de combate, la mesa alberga hasta 8 dados en pugna simultánea. Los dados chocan entre sí transfiriendo momento lineal y angular, alterando las caras de reposo de los contrincantes y pudiendo expulsarlos al abismo.
2. **La Tensión Clásica del Agalludo (Riesgo vs. Codicia):**
   Cualquier tirada que arroje un **1 Mortal** pulveriza el acumulado de ese turno ($TurnScore = 0$) e impone una sanción de estasis (*Lockout*) de 3 segundos, salvo que el jugador haya protegido su tirada o asegurado con **BANK**.
3. **Cero Dependencia de Modelos Externos (100% Procedural):**
   Todas las texturas de dados (9 acabados PBR: marfil, ámbar, oro, carbono, zafiro neón, rubí, cromo, esmeralda y amatista), las superficies de mesa y los sonidos se sintetizan mediante código nativo (`CanvasRenderingContext2D` y `Web Audio API`). Cero descargas pesadas, arranque instantáneo en cualquier dispositivo.
4. **Arsenal de Combate Táctico:**
   Habilidades activables en tiempo real que permiten alterar las leyes de la física para sabotear a los rivales o salvar el puntaje en el último segundo.
5. **Observabilidad Agéntica Absoluta:**
   Cada tarea del desarrollo está respaldada por un Ledger ejecutable (`roadmap.json`), un Dashboard Gantt interactivo (`roadmap.html`) y una batería de verificación automatizada e2e en headless.

---

## 2. Especificación de los 5 Tableros / Biomas de Fosa

Cada tablero modifica de forma determinante el comportamiento balístico de los 8 dados:

| Tablero | Nombre Temático | Fricción | Restitución (Rebote) | Gravedad | Características Ambientales y Tácticas |
|---|---|---|---|---|---|
| **M-01** | **Salón de Roble (Burdeos)** | $0.65$ | $0.48$ | $-17.0 \text{ m/s}^2$ | Tapete de fieltro burdeos con marco de caoba clásica. Comportamiento equilibrado estándar. |
| **M-02** | **Verde Casino (Montecarlo)** | $0.85$ | $0.35$ | $-17.0 \text{ m/s}^2$ | Fieltro pesado de paño inglés. Frena rápidamente los dados, ideal para tiradores de alta precisión. |
| **M-03** | **Búnker Industrial (Acero)** | $0.50$ | $0.65$ | $-17.0 \text{ m/s}^2$ | Planchas de acero blindado con líneas láser de contención. Los choques entre dados despiden chispas intensas y producen rebotes violentos. |
| **M-04** | **Tribunal de Mármol (Carrara)**| $0.12$ | $0.45$ | $-17.0 \text{ m/s}^2$ | Superficie pulida de mármol y filigrana dorada. Fricción casi nula; los dados se deslizan largas distancias, facilitando empujes y colisiones masivas. |
| **M-05** | **Casino Virtual (Plasma)** | $0.55$ | $0.55$ | $-8.0 \text{ m/s}^2$ | Arena holográfica en el ciberespacio. Micro-gravedad que prolonga el tiempo de vuelo de los dados y permite maniobras aéreas y vórtices energéticos. |

---

## 3. Geometría de la Arena y Distribución de los 8 Jugadores

La arena rectangular ampliada ($5.2\text{m} \times 6.8\text{m}$) dispone de **8 bahías de lanzamiento perimétricas** orientadas hacia el epicentro del combate:

```
                  [BUNKER WALL - NORTE]
      (Bot 6)                               (Bot 7)
    [-1.8, -2.6]       (Bot 1)           [ 1.8, -2.6]
                     [ 0.0, -2.7]
           \              |              /
            \             |             /
             v            v            v
(Bot 2)  --->      EPICENTRO DE      <---  (Bot 3)
[-2.2, 0.0]         COLISIONES            [ 2.2, 0.0]
             ^     (Área de Caos)      ^
            /             ^             \
           /              |              \
      (Bot 4)             |                 (Bot 5)
    [-1.8,  2.2]       (JUGADOR)          [ 1.8,  2.2]
                     [ 0.0,  2.7]
                  [BUNKER WALL - SUR]
```

### Coordenadas Iniciales de Reposo (`readyPos`):
1. **Jugador Humano:** $(0.0, 0.40, 2.70)$ · Ángulo de tiro frontal $+Z \to -Z$.
2. **Bot 1 (Norte):** $(0.0, 0.40, -2.70)$ · Ángulo de tiro frontal $-Z \to +Z$.
3. **Bot 2 (Oeste):** $(-2.20, 0.40, 0.0)$ · Ángulo de tiro lateral $-X \to +X$.
4. **Bot 3 (Este):** $(2.20, 0.40, 0.0)$ · Ángulo de tiro lateral $+X \to -X$.
5. **Bot 4 (Suroeste):** $(-1.80, 0.40, 2.20)$ · Ángulo de tiro diagonal.
6. **Bot 5 (Sureste):** $(1.80, 0.40, 2.20)$ · Ángulo de tiro diagonal.
7. **Bot 6 (Noroeste):** $(-1.80, 0.40, -2.20)$ · Ángulo de tiro diagonal.
8. **Bot 7 (Noreste):** $(1.80, 0.40, -2.20)$ · Ángulo de tiro diagonal.

---

## 4. Mecánica de Combate y Agalludo Battle Royale

### 4.1. Bucle de Ronda
1. **Duración:** Cada ronda dura $30.0$ segundos exactos.
2. **Lanzamiento:**
   - **Tirachinas Táctico (Slingshot):** El jugador pulsa sobre su dado, arrastra en dirección opuesta al objetivo para acumular tensión/fuerza (indicada por una estela predictiva luminiscente) y suelta para disparar con velocidad lineal y torsión angular variable.
   - **Lanzamiento Rápido:** Botón directo en el panel de control para tiro estándar instantáneo.
   - **Comportamiento de la IA (Bots):** Los 7 bots evalúan continuamente su puntaje asegurado frente al líder y la amenaza de eliminación. Si están en zona de peligro (últimos lugares), aumentan su agresividad de tiro y lanzan con mayor cadencia.
3. **Evaluación de Caras:**
   - La cara superior del dado en reposo ($\vec{v} \approx 0, \vec{\omega} \approx 0$) se determina mediante el producto escalar entre las normales locales del dado y el vector vertical del mundo $\hat{u}_y = (0, 1, 0)$.
   - Valores $2, 3, 4, 5, 6$: Se añaden al `turnScore`. El jugador puede volver a tirar o pulsar `BANK`.
   - Valor $1$: Dispara **1 MORTAL**. `turnScore` se anula y el jugador entra en 3 segundos de `lockout`.
4. **Fosa Abisal (Perimeter Ejection):**
   Si el impacto de otro dado expulsa un proyectil fuera de los límites de la arena ($|x| > 2.85$ o $|z| > 3.65$ o $y < -0.15$), el cuerpo rígido se precipita a la fosa. Se penaliza con la pérdida del turno y un bloqueo de stasis de $4.0$ segundos antes de reaparecer en su bahía de lanzamiento.
5. **Drenaje por Radiación Crítica ($t \le 7.0\text{s}$):**
   Al aproximarse el final de la ronda, la consola entra en modo de alerta roja parpadeante y comienza a drenar $1$ punto por segundo del puntaje asegurado de los jugadores.
6. **Ejecución y Shock de Eliminación ($t = 0.0\text{s}$):**
   El jugador vivo en la mesa con el menor puntaje asegurado es declarado sentenciado y ejecutado por el Tribunal de la Fosa con pantalla de shock. En torneos de 16 jugadores, los peores de las mesas son purgados ronda a ronda hasta la gran final de supervivencia.

---

## 5. Arsenal de Habilidades Tácticas (Poderes)

| Habilidad | Tecla / Control | Efecto Operativo | Recarga |
|---|---|---|---|
| 💥 **Freno Magnético** | `[ESPACIO]` | Frena instantáneamente la velocidad lineal y angular del dado en movimiento, congelándolo en su cara actual. | 1 uso por ronda |
| 🛡️ **Escudo de Fosa** | `[C]` | Crea un domo protector de plasma. Si la tirada resulta en un 1 Mortal, el escudo absorbe el golpe, guarda automáticamente el acumulado en el casillero permanente y evita la stasis. | 1 uso por ronda |
| ⚡ **Onda de Choque (Repulsor)** | `[X]` | Emite un pulso cinético radial de alta intensidad centrado en el dado del jugador que repele con violencia a todos los dados rivales en un radio de $2.5\text{m}$. | 1 uso por ronda |
| 🧲 **Vórtice Táctico** | `[V]` | Activa un punto de singularidad gravitatoria en el centro de la mesa durante $2.0$ segundos que atrae a los dados hacia el foso o altera sus trayectorias. | Nivel $\ge 15$ |

---

## 6. Progresión y Metajuego (Niveles 1 a 150)

- **Cálculo de XP:** $\text{XP Ganada} = \text{Puntos Asegurados} \times 10 + (\text{Supervivencia} \times 150) + (\text{Victoria} \times 1000)$.
- **Curva de Nivel:** $\text{XP Requerida para Nivel } N = N \times 300\text{ XP}$.
- **Desbloqueos:**
  - **Skins de Dados:** Marfil (Lvl 1), Ámbar (Lvl 5), Oro (Lvl 15), Carbono (Lvl 30), Zafiro Neón (Lvl 45), Rubí (Lvl 60), Cromo (Lvl 80), Esmeralda (Lvl 110), Materia Amatista (Lvl 150).
  - **Tableros:** Roble Burdeos (Lvl 1), Verde Casino (Lvl 20), Búnker Acero (Lvl 40), Mármol Tribunal (Lvl 70), Casino Virtual Plasma (Lvl 120).
  - **Temas UI:** Cian Criogénico (Lvl 1), Ámbar Seguridad (Lvl 15), Rojo Industrial (Lvl 35), Montecarlo Verde/Oro (Lvl 90).

---

## 7. Prompt de Arranque Determinista para Agentes Autónomos

```markdown
Eres un Agente Autónomo de Desarrollo asignado al proyecto Agalludo Royale (Agayul).
Tu espacio de trabajo es `C:\Users\jegom\.gemini\antigravity\scratch\agalludo_royale`.

INSTRUCCIONES DE EJECUCIÓN OBLIGATORIAS:
1. Lee `docs/roadmap.json`. Localiza la tarea prioritaria con estado "pending" cuyas dependencias estén en "done".
2. Ejecuta `python scripts/update_task.py --task <ID> --status in_progress`.
3. Desarrolla el código cumpliendo de forma estricta los requerimientos de `QUÉ INCLUYE` y el criterio unívoco de `HECHO CUANDO`.
4. Ejecuta la verificación estipulada en `VERIFICACIÓN` (e.g. `python scripts/verify_web_e2e.py`). Asegúrate de que las capturas de evidencia se guarden en `docs/gdd/evidencia/`.
5. Registra el diff de líneas por disciplina técnica y ejecuta `python scripts/update_task.py --task <ID> --status done --lines "disciplina=N"`.
6. Verifica que `docs/roadmap.html` se compile limpiamente y continúa con la siguiente tarea.
```
