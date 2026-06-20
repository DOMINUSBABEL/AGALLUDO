// AGALLUDO ROYALE v1.50: MOTOR 3D, PROCEDURAL AUDIO Y SISTEMA DE PROGRESO (NIVELES 1-150)
const THREE = require('three');
const CANNON = require('cannon');
const { ipcRenderer } = require('electron');

// --- CONSTANTES DE PROGRESO Y DESBLOQUEOS (NIVELES 1 A 150) ---
const Unlockables = {
  diceSkins: [
    { id: 'steel', name: 'Acero Templado', level: 1, desc: 'Dado estándar de fosa.' },
    { id: 'copper', name: 'Cobre Rústico', level: 5, desc: 'Dado de bronce patinado.' },
    { id: 'gold', name: 'Oro Pulido', level: 15, desc: 'dado de oro de alta reflectividad.' },
    { id: 'carbon', name: 'Carbono Militar', level: 30, desc: 'Fibra de carbono mate.' },
    { id: 'neon', name: 'Holograma Neón', level: 45, desc: 'Dado de cristal que brilla en rosa/cian.' },
    { id: 'crimson', name: 'Sangre Carmesí', level: 60, desc: 'Rojo metálico profundo.' },
    { id: 'chrome', name: 'Cromo Líquido', level: 80, desc: 'Dado plateado de reflexión pura.' },
    { id: 'obsidian', name: 'Lava de Obsidiana', level: 110, desc: 'Roca volcánica con puntos de magma.' },
    { id: 'darkmatter', name: 'Materia Oscura', level: 150, desc: 'Dado cósmico de color morado estelar.' }
  ],
  tables: [
    { id: 'oak', name: 'Madera de Roble', level: 1, desc: 'Mesa clásica con fricción media.', friction: 0.65, restitution: 0.48, gravity: -17.0 },
    { id: 'felt', name: 'Fieltro de Casino', level: 20, desc: 'Fieltro verde con alta fricción.', friction: 0.85, restitution: 0.35, gravity: -17.0 },
    { id: 'ironplate', name: 'Placa de Acero', level: 40, desc: 'Metal pesado con gran rebote.', friction: 0.50, restitution: 0.65, gravity: -17.0 },
    { id: 'marble', name: 'Mármol del Búnker', level: 70, desc: 'Piedra lisa resbaladiza y caótica.', friction: 0.12, restitution: 0.45, gravity: -17.0 },
    { id: 'plasma', name: 'Red de Plasma', level: 120, desc: 'Gravedad alterada y red luminosa.', friction: 0.55, restitution: 0.55, gravity: -8.0 }
  ],
  uiThemes: [
    { id: 'cyan', name: 'Cian Criogénico', level: 1, desc: 'Tema azul futurista.' },
    { id: 'amber', name: 'Ámbar de Seguridad', level: 15, desc: 'Tema naranja industrial.' },
    { id: 'red', name: 'Industrial Carmesí', level: 35, desc: 'Tema rojo de fosa.' },
    { id: 'green', name: 'Radiactivo', level: 90, desc: 'Tema verde neón fosforescente.' }
  ],
  titles: [
    { id: 'reclus', name: 'Recluso de la Fosa', level: 1 },
    { id: 'roller', name: 'Tirador de Acero', level: 10 },
    { id: 'survivor', name: 'Sobreviviente de Bronce', level: 25 },
    { id: 'tactical', name: 'Estratega Táctico', level: 50 },
    { id: 'copper_lord', name: 'Señor del Cobre', level: 75 },
    { id: 'gold_champ', name: 'Campeón de Oro', level: 100 },
    { id: 'specter', name: 'Espectro de la Fosa', level: 125 },
    { id: 'supreme', name: 'Agalludo Supremo', level: 150 }
  ]
};

// --- ESTRUCTURA DE PERSISTENCIA (LOCALSTORAGE) ---
let playerProfile = {
  level: 1,
  xp: 0,
  wins: 0,
  losses: 0,
  equippedSkin: 'steel',
  equippedTable: 'oak',
  equippedTheme: 'cyan',
  equippedTitle: 'Recluso de la Fosa'
};

function loadProfile() {
  const saved = localStorage.getItem('agalludo_profile_v15');
  if (saved) {
    playerProfile = JSON.parse(saved);
  }
  // Sincronizar datos globales de UI
  sessionWins = playerProfile.wins;
  sessionLosses = playerProfile.losses;
  playerSkin = playerProfile.equippedSkin;
  currentTableStyle = playerProfile.equippedTable;
  playerTitleText = playerProfile.equippedTitle;

  // Cambiar tema de CSS
  document.body.className = `theme-${playerProfile.equippedTheme}`;

  updateProfileUI();
}

function saveProfile() {
  localStorage.setItem('agalludo_profile_v15', JSON.stringify(playerProfile));
}

function updateProfileUI() {
  document.getElementById('player-level').innerText = playerProfile.level;
  document.getElementById('player-title').innerText = playerProfile.equippedTitle;
  
  const xpNeeded = playerProfile.level * 300;
  const pct = Math.min((playerProfile.xp / xpNeeded) * 100, 100);
  document.getElementById('xp-progress').style.width = `${pct}%`;
  document.getElementById('xp-text').innerText = `XP: ${playerProfile.xp} / ${xpNeeded}`;
}

function earnXP(amount) {
  playerProfile.xp += amount;
  let xpNeeded = playerProfile.level * 300;
  let leveledUp = false;

  while (playerProfile.xp >= xpNeeded && playerProfile.level < 150) {
    playerProfile.xp -= xpNeeded;
    playerProfile.level++;
    xpNeeded = playerProfile.level * 300;
    leveledUp = true;
  }

  if (leveledUp) {
    addLog(`🎉 ¡SUBIDA DE NIVEL! Ahora eres Nivel ${playerProfile.level}. Revisa la personalización.`, 'system');
    soundPlayer.playLevelUp();
  }

  saveProfile();
  updateProfileUI();
}

// --- GENERADOR DE MÚSICA PROCEDURAL (WEB AUDIO API) ---
class ProceduralMusicSynth {
  constructor() {
    this.ctx = null;
    this.isPlaying = false;
    this.tempo = 100; // BPM
    this.step = 0;
    this.timerId = null;
    this.musicVolume = 0.08;
    this.mainVolumeNode = null;
  }

  init(audioCtx) {
    this.ctx = audioCtx;
    this.mainVolumeNode = this.ctx.createGain();
    this.mainVolumeNode.gain.setValueAtTime(this.musicVolume, this.ctx.currentTime);
    this.mainVolumeNode.connect(this.ctx.destination);
  }

  start() {
    if (this.isPlaying) return;
    this.isPlaying = true;
    this.step = 0;
    this.scheduler();
  }

  stop() {
    this.isPlaying = false;
    clearTimeout(this.timerId);
  }

  setTempo(bpm) {
    this.tempo = bpm;
  }

  scheduler() {
    if (!this.isPlaying) return;

    // Planificar nota en el paso actual
    this.playStep();

    const stepDuration = 60 / this.tempo / 2; // Cocheas (8th notes)
    this.step = (this.step + 1) % 16;
    this.timerId = setTimeout(() => this.scheduler(), stepDuration * 1000);
  }

  playStep() {
    const t = this.ctx.currentTime;
    
    // Filtro global para amortiguar la música si el jugador está en Lockout (stasis submarina)
    const isPlayerLockout = players.find(p => p.id === 'player')?.lockout > 0;
    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(isPlayerLockout ? 280 : 3000, t);
    filter.connect(this.mainVolumeNode);

    // 1. KICK DRUM (En pulsos 0, 4, 8, 12 del compás de 16 pasos)
    if (this.step % 4 === 0) {
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(140, t);
      osc.frequency.exponentialRampToValueAtTime(30, t + 0.16);
      gain.gain.setValueAtTime(0.6, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + 0.18);
      osc.connect(gain);
      gain.connect(filter);
      osc.start(t);
      osc.stop(t + 0.2);
    }

    // 2. HI-HAT (En contratiempos: pasos 2, 6, 10, 14)
    if (this.step % 4 === 2) {
      // Buffer de ruido blanco
      const bufferSize = this.ctx.sampleRate * 0.03;
      const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
      const data = buffer.getChannelData(0);
      for (let i = 0; i < bufferSize; i++) {
        data[i] = Math.random() * 2 - 1;
      }
      const noise = this.ctx.createBufferSource();
      noise.buffer = buffer;
      const noiseGain = this.ctx.createGain();
      noiseGain.gain.setValueAtTime(0.08, t);
      noiseGain.gain.exponentialRampToValueAtTime(0.001, t + 0.03);
      noise.connect(noiseGain);
      noiseGain.connect(filter);
      noise.start(t);
    }

    // 3. SECUENCIADOR DE BASS DRONE (cyber-industrial oscuro en escala menor pentatónica)
    // Escala: D2 (73.4Hz), F2 (87.3Hz), G2 (98.0Hz), A2 (110.0Hz), C3 (130.8Hz)
    const bassPattern = [73.4, 73.4, 87.3, 73.4, 98.0, 73.4, 110.0, 73.4, 73.4, 73.4, 130.8, 73.4, 98.0, 87.3, 73.4, 73.4];
    const targetFreq = bassPattern[this.step];

    const oscBass = this.ctx.createOscillator();
    const gainBass = this.ctx.createGain();
    
    // Si el tiempo es crítico (< 7s), usamos sierra agresiva, sino un bajo triángulo profundo
    oscBass.type = (roundTimer <= 7.0 && currentGameState === GameState.PLAYING) ? 'sawtooth' : 'triangle';
    oscBass.frequency.setValueAtTime(targetFreq, t);

    // Ajustar volumen del bajo
    const bassVol = oscBass.type === 'sawtooth' ? 0.08 : 0.16;
    gainBass.gain.setValueAtTime(bassVol, t);
    gainBass.gain.exponentialRampToValueAtTime(0.001, t + 0.25);

    // Lowpass resonante específico para el bajo
    const bassFilter = this.ctx.createBiquadFilter();
    bassFilter.type = 'lowpass';
    bassFilter.frequency.setValueAtTime(oscBass.type === 'sawtooth' ? 550 : 250, t);

    oscBass.connect(bassFilter);
    bassFilter.connect(gainBass);
    gainBass.connect(filter);

    oscBass.start(t);
    oscBass.stop(t + 0.25);

    // 4. MELODÍA / EFECTOS DE FOSA CASUALES (Poca probabilidad)
    if (this.step % 8 === 4 && Math.random() < 0.35 && currentGameState === GameState.PLAYING) {
      const notes = [293.66, 349.23, 392.00, 440.00, 523.25]; // D4, F4, G4, A4, C5
      const noteFreq = notes[Math.floor(Math.random() * notes.length)];
      
      const oscMel = this.ctx.createOscillator();
      const gainMel = this.ctx.createGain();
      oscMel.type = 'sine';
      oscMel.frequency.setValueAtTime(noteFreq, t);
      // Vibrato
      const lfo = this.ctx.createOscillator();
      const lfoGain = this.ctx.createGain();
      lfo.frequency.setValueAtTime(8, t);
      lfoGain.gain.setValueAtTime(15, t);
      lfo.connect(lfoGain);
      lfoGain.connect(oscMel.frequency);

      gainMel.gain.setValueAtTime(0.05, t);
      gainMel.gain.exponentialRampToValueAtTime(0.001, t + 0.5);

      oscMel.connect(gainMel);
      gainMel.connect(filter);

      lfo.start(t);
      oscMel.start(t);
      oscMel.stop(t + 0.55);
      lfo.stop(t + 0.55);
    }
  }
}

const musicSynth = new ProceduralMusicSynth();

// --- SISTEMA DE SONIDOS SINTETIZADOS (EFECTOS AÑADIDOS Y UNIFICADOS) ---
class SynthesizedSoundPlayer {
  constructor() {
    this.ctx = null;
  }

  init() {
    if (!this.ctx) {
      this.ctx = new (window.AudioContext || window.webkitAudioContext)();
    }
  }

  playTick() {
    this.init();
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(800, this.ctx.currentTime);
    gain.gain.setValueAtTime(0.04, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.05);
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start();
    osc.stop(this.ctx.currentTime + 0.05);
  }

  playHeartbeat() {
    this.init();
    const t = this.ctx.currentTime;
    const playThump = (delay) => {
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(55, t + delay);
      osc.frequency.exponentialRampToValueAtTime(10, t + delay + 0.15);
      gain.gain.setValueAtTime(0.35, t + delay);
      gain.gain.exponentialRampToValueAtTime(0.001, t + delay + 0.18);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(t + delay);
      osc.stop(t + delay + 0.2);
    };
    playThump(0);
    playThump(0.12);
  }

  playDiceRattle() {
    this.init();
    const bufferSize = this.ctx.sampleRate * 0.12;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = Math.random() * 2 - 1;
    }
    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;
    
    const filter = this.ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(900, this.ctx.currentTime);
    
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.08, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.12);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(this.ctx.destination);
    noise.start();
  }

  playBounce(intensity = 1) {
    this.init();
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(250 + Math.random() * 350, this.ctx.currentTime);
    
    const vol = Math.min(0.18 * intensity, 0.25);
    gain.gain.setValueAtTime(vol, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.14);
    
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start();
    osc.stop(this.ctx.currentTime + 0.15);
  }

  playBust() {
    this.init();
    const osc1 = this.ctx.createOscillator();
    const osc2 = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc1.type = 'sawtooth';
    osc1.frequency.setValueAtTime(110, this.ctx.currentTime);
    osc1.frequency.linearRampToValueAtTime(30, this.ctx.currentTime + 0.55);

    osc2.type = 'sine';
    osc2.frequency.setValueAtTime(113, this.ctx.currentTime);
    osc2.frequency.linearRampToValueAtTime(33, this.ctx.currentTime + 0.55);

    gain.gain.setValueAtTime(0.25, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.6);

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(this.ctx.destination);

    osc1.start();
    osc2.start();
    osc1.stop(this.ctx.currentTime + 0.65);
    osc2.stop(this.ctx.currentTime + 0.65);
  }

  playBank() {
    this.init();
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(450, this.ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(950, this.ctx.currentTime + 0.22);
    
    gain.gain.setValueAtTime(0.1, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.22);

    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start();
    osc.stop(this.ctx.currentTime + 0.25);
  }

  playElimination() {
    this.init();
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'square';
    osc.frequency.setValueAtTime(90, this.ctx.currentTime);
    
    const lfo = this.ctx.createOscillator();
    const lfoGain = this.ctx.createGain();
    lfo.frequency.value = 5.5; // Hz
    lfoGain.gain.value = 25; // Desviación en Hz
    
    lfo.connect(lfoGain);
    lfoGain.connect(osc.frequency);
    
    gain.gain.setValueAtTime(0.15, this.ctx.currentTime);
    gain.gain.linearRampToValueAtTime(0.15, this.ctx.currentTime + 0.8);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 1.2);

    osc.connect(gain);
    gain.connect(this.ctx.destination);
    
    lfo.start();
    osc.start();
    osc.stop(this.ctx.currentTime + 1.2);
    lfo.stop(this.ctx.currentTime + 1.2);
  }

  playLevelUp() {
    this.init();
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(261.63, t); // C4
    osc.frequency.setValueAtTime(329.63, t + 0.1); // E4
    osc.frequency.setValueAtTime(392.00, t + 0.2); // G4
    osc.frequency.setValueAtTime(523.25, t + 0.3); // C5
    osc.frequency.exponentialRampToValueAtTime(1046.50, t + 0.8); // C6
    
    gain.gain.setValueAtTime(0.2, t);
    gain.gain.linearRampToValueAtTime(0.2, t + 0.5);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.9);

    osc.connect(gain);
    gain.connect(this.ctx.destination);
    
    osc.start(t);
    osc.stop(t + 0.95);
  }

  playClick() {
    this.init();
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(1200, this.ctx.currentTime);
    gain.gain.setValueAtTime(0.02, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.03);
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start();
    osc.stop(this.ctx.currentTime + 0.03);
  }

  playShieldActivate() {
    this.init();
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(600, t);
    osc.frequency.exponentialRampToValueAtTime(1500, t + 0.35);
    gain.gain.setValueAtTime(0.12, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.35);
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start(t);
    osc.stop(t + 0.4);
  }

  playShieldAbsorb() {
    this.init();
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(1200, t);
    osc.frequency.exponentialRampToValueAtTime(300, t + 0.5);
    gain.gain.setValueAtTime(0.2, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.5);
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start(t);
    osc.stop(t + 0.55);
  }

  playBrake() {
    this.init();
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(220, t);
    osc.frequency.linearRampToValueAtTime(80, t + 0.3);
    gain.gain.setValueAtTime(0.18, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.3);
    const filter = this.ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(400, t);
    osc.connect(filter);
    filter.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start(t);
    osc.stop(t + 0.35);
  }
}

const soundPlayer = new SynthesizedSoundPlayer();

// --- CONFIGURACIÓN E INSTANCIACIÓN DE 3D Y FÍSICAS ---
let scene, camera, renderer3d;
let tableMesh, lightBulb, lightBulbFixture;
let world, tablePhysicsMaterial, dicePhysicsMaterial;
let tableDice = [];

// Estilos de Mesa
let currentTableStyle = 'oak';
let playerTitleText = 'Recluso de la Fosa';

// Partículas en impacto (WebGL Spark Particles)
let particleSystem = null;
let sparkParticles = [];
const maxSparks = 80;

// Variables de Shake Damping
let cameraShakeTime = 0;
let cameraShakeIntensity = 0;
const originalCamPos = new THREE.Vector3(0, 8.5, 7);

// HUD Visual de Dados (Sprites 3D flotantes)
let billboardSprites = [];

// Configuraciones base de dados
const DiceMesaConfig = [
  { playerId: 'player', skin: 'steel', readyPos: new CANNON.Vec3(0, 0.4, 2.5), name: 'TU' },
  { playerId: 'bot_table_1', skin: 'copper', readyPos: new CANNON.Vec3(0, 0.4, -2.5), name: 'IA 1' },
  { playerId: 'bot_table_2', skin: 'gold', readyPos: new CANNON.Vec3(-1.8, 0.4, 0), name: 'IA 2' },
  { playerId: 'bot_table_3', skin: 'carbon', readyPos: new CANNON.Vec3(1.8, 0.4, 0), name: 'IA 3' }
];

// --- DECLARACIONES DE VARIABLES GLOBALES ADICIONALES ---
const GameState = {
  INTRO: 'INTRO',
  PLAYING: 'PLAYING',
  ELIMINATION_SHOCK: 'ELIMINATION_SHOCK',
  GAME_OVER: 'GAME_OVER',
  VICTORY: 'VICTORY'
};

let currentGameState = GameState.INTRO;
let gameMode = 'tournament';
let aiDifficulty = 'PLATA';
let players = [];
let activeTablePlayers = [];
let eliminationRoundCount = 1;
let roundTimer = 30.0;
let timerInterval = null;
let botAIInterval = null;
let selectedInitialFace = 6;
let sessionWins = 0;
let sessionLosses = 0;
let playerSkin = 'steel';

// Ghost trail / Estela de trayectoria
const maxGhostPoints = 50;
let ghostLine;
let ghostPoints = [];

// Habilidades tácticas activas del jugador
let hasBrakeUsed = false;
let hasShieldUsed = false;
let isShieldActive = false;

// Drag variables
let isDragging = false;
let dragStartPos = { x: 0, y: 0 };
let currentDragPos = { x: 0, y: 0 };
let lastDragTime = 0;

// Skin colors for dynamic canvases
const SkinColors = {
  steel: { primary: '#4f5b66', secondary: '#343d46', dots: '#00bcd4' },
  copper: { primary: '#b87333', secondary: '#8b5a2b', dots: '#ff7f50' },
  gold: { primary: '#ffd700', secondary: '#b8860b', dots: '#ffffff' },
  carbon: { primary: '#2b2b2b', secondary: '#1c1c1c', dots: '#ff3b30' },
  neon: { primary: '#ff007f', secondary: '#00f0ff', dots: '#39ff14' },
  crimson: { primary: '#dc143c', secondary: '#8b0000', dots: '#ffffff' },
  chrome: { primary: '#e6e6e6', secondary: '#808080', dots: '#00e5ff' },
  obsidian: { primary: '#151515', secondary: '#2a1a1a', dots: '#ff3d00' },
  darkmatter: { primary: '#4a154b', secondary: '#0c020c', dots: '#e040fb' }
};

// --- HELPERS Y FUNCIONES FALTANTES ---

function activateShield() {
  if (currentGameState !== GameState.PLAYING) return;
  const myself = players.find(p => p.id === 'player');
  if (!myself || myself.isEliminated || myself.lockout > 0) return;
  if (hasShieldUsed || isShieldActive) return;

  isShieldActive = true;
  hasShieldUsed = true;
  soundPlayer.playShieldActivate();
  
  const shieldBtn = document.getElementById('btn-skill-shield');
  const shieldStatus = document.getElementById('shield-status');
  if (shieldBtn && shieldStatus) {
    shieldBtn.classList.add('cooldown');
    shieldBtn.disabled = true;
    shieldStatus.innerText = 'ACTIVO';
    shieldStatus.style.color = 'var(--accent-color)';
    shieldStatus.style.background = 'rgba(255,0,0,0.15)';
  }
  
  addLog("🛡️ Escudo de Fosa activado. Te protegerá del próximo 1 Mortal.", 'success');
}

function triggerBrake() {
  if (currentGameState !== GameState.PLAYING) return;
  const myself = players.find(p => p.id === 'player');
  if (!myself || myself.isEliminated || myself.lockout > 0) return;
  if (hasBrakeUsed) return;
  
  const playerDice = tableDice.find(d => d.configId === 'player' || d.activePlayerId === 'player');
  if (!playerDice || !playerDice.isRolling) return;
  
  hasBrakeUsed = true;
  soundPlayer.playBrake();
  
  playerDice.body.velocity.set(0, 0, 0);
  playerDice.body.angularVelocity.set(0, 0, 0);
  
  const brakeBtn = document.getElementById('btn-skill-brake');
  const brakeStatus = document.getElementById('brake-status');
  if (brakeBtn && brakeStatus) {
    brakeBtn.classList.add('cooldown');
    brakeBtn.disabled = true;
    brakeStatus.innerText = 'USADO';
    brakeStatus.style.color = 'var(--text-secondary)';
    brakeStatus.style.background = 'rgba(255,255,255,0.05)';
  }
  
  addLog("💥 ¡Freno Magnético activado! Dado detenido de golpe.", 'success');
}

function addLog(message, type) {
  const container = document.getElementById('crt-logs');
  if (!container) return;
  const p = document.createElement('p');
  p.className = `log-${type}`;
  
  const now = new Date();
  const timeStr = `[${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}]`;
  
  p.innerText = `${timeStr} ${message}`;
  container.appendChild(p);
  container.scrollTop = container.scrollHeight;
}

function showScreen(screenId) {
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  document.getElementById(`${screenId}-screen`).classList.add('active');
}

function stopLoops() {
  if (timerInterval) {
    clearInterval(timerInterval);
    timerInterval = null;
  }
  if (botAIInterval) {
    clearInterval(botAIInterval);
    botAIInterval = null;
  }
}

function createDiceFaceTexture(value, skinId) {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  
  const colors = SkinColors[skinId] || SkinColors.steel;
  
  // Fondo
  const grad = ctx.createLinearGradient(0, 0, 128, 128);
  grad.addColorStop(0, colors.primary);
  grad.addColorStop(1, colors.secondary);
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, 128, 128);
  
  // Borde
  ctx.strokeStyle = 'rgba(255,255,255,0.15)';
  ctx.lineWidth = 4;
  ctx.strokeRect(2, 2, 124, 124);
  
  // Patrón especial Carbono
  if (skinId === 'carbon') {
    ctx.fillStyle = 'rgba(0, 0, 0, 0.25)';
    for (let x = 0; x < 128; x += 8) {
      for (let y = 0; y < 128; y += 8) {
        if ((x + y) % 16 === 0) {
          ctx.fillRect(x, y, 4, 4);
        }
      }
    }
  }

  // Magma Obsidian
  if (skinId === 'obsidian') {
    ctx.strokeStyle = '#ff3d00';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(10, 20); ctx.lineTo(40, 50); ctx.lineTo(20, 80); ctx.lineTo(60, 110);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(110, 10); ctx.lineTo(80, 60); ctx.lineTo(95, 90); ctx.lineTo(120, 120);
    ctx.stroke();
  }
  
  // Dibuja puntos (pips)
  ctx.fillStyle = colors.dots;
  ctx.shadowColor = colors.dots;
  ctx.shadowBlur = (skinId === 'neon' || skinId === 'darkmatter' || skinId === 'obsidian') ? 10 : 0;
  
  const drawPip = (cx, cy) => {
    ctx.beginPath();
    ctx.arc(cx, cy, 10, 0, Math.PI * 2);
    ctx.fill();
  };
  
  const pips = {
    1: [[64, 64]],
    2: [[32, 32], [96, 96]],
    3: [[32, 32], [64, 64], [96, 96]],
    4: [[32, 32], [32, 96], [96, 32], [96, 96]],
    5: [[32, 32], [32, 96], [64, 64], [96, 32], [96, 96]],
    6: [[32, 32], [32, 64], [32, 96], [96, 32], [96, 64], [96, 96]]
  };
  
  const coords = pips[value] || [];
  coords.forEach(([cx, cy]) => drawPip(cx, cy));
  
  return new THREE.CanvasTexture(canvas);
}

function applyDiceSkins() {
  tableDice.forEach((d) => {
    let skinId = 'steel';
    if (d.activePlayerId === 'player') {
      skinId = playerSkin;
    } else if (d.activePlayerId) {
      const cfg = DiceMesaConfig.find(c => c.playerId === d.configId);
      skinId = cfg ? cfg.skin : 'copper';
    } else {
      skinId = 'steel';
    }
    
    // Mapeo orden estándar de caras BoxGeometry: PX, NX, PY, NY, PZ, NZ
    const faceValues = [1, 6, 2, 5, 3, 4];
    for (let i = 0; i < 6; i++) {
      if (d.mesh.material[i]) {
        if (d.mesh.material[i].map) {
          d.mesh.material[i].map.dispose();
        }
        d.mesh.material[i].map = createDiceFaceTexture(faceValues[i], skinId);
        d.mesh.material[i].needsUpdate = true;
      }
    }
  });
}

function setInitialFaceRotation(body, face) {
  const q = new CANNON.Quaternion();
  
  switch (face) {
    case 2: // PY (Arriba)
      q.setFromEuler(0, 0, 0);
      break;
    case 5: // NY (Abajo)
      q.setFromEuler(Math.PI, 0, 0);
      break;
    case 1: // PX (Derecha)
      q.setFromEuler(0, 0, -Math.PI / 2);
      break;
    case 6: // NX (Izquierda)
      q.setFromEuler(0, 0, Math.PI / 2);
      break;
    case 3: // PZ (Frente)
      q.setFromEuler(Math.PI / 2, 0, 0);
      break;
    case 4: // NZ (Atrás)
      q.setFromEuler(-Math.PI / 2, 0, 0);
      break;
    default:
      q.setFromEuler(0, 0, 0);
  }
  
  body.quaternion.copy(q);
}

function resetDiceToReady() {
  tableDice.forEach((d) => {
    d.isRolling = false;
    d.body.velocity.set(0, 0, 0);
    d.body.angularVelocity.set(0, 0, 0);
    d.body.position.copy(d.readyPos);
    
    if (d.activePlayerId === 'player' || d.configId === 'player') {
      setInitialFaceRotation(d.body, selectedInitialFace);
    } else {
      setInitialFaceRotation(d.body, Math.floor(Math.random() * 6) + 1);
    }
    
    d.mesh.position.copy(d.body.position);
    d.mesh.quaternion.copy(d.body.quaternion);
  });
}

function getTopFace(body) {
  const localFaces = [
    new CANNON.Vec3(1, 0, 0),
    new CANNON.Vec3(-1, 0, 0),
    new CANNON.Vec3(0, 1, 0),
    new CANNON.Vec3(0, -1, 0),
    new CANNON.Vec3(0, 0, 1),
    new CANNON.Vec3(0, 0, -1)
  ];
  const faceValues = [1, 6, 2, 5, 3, 4];
  
  let maxDot = -Infinity;
  let topFaceValue = 1;
  
  localFaces.forEach((localDir, index) => {
    const worldDir = new CANNON.Vec3();
    body.vectorToWorldFrame(localDir, worldDir);
    
    const dot = worldDir.dot(new CANNON.Vec3(0, 1, 0));
    if (dot > maxDot) {
      maxDot = dot;
      topFaceValue = faceValues[index];
    }
  });
  
  return topFaceValue;
}

function updateLeaderboard() {
  const list = document.getElementById('leaderboard-list');
  if (!list) return;
  list.innerHTML = '';
  
  const sorted = [...players].sort((a, b) => {
    if (a.isEliminated && !b.isEliminated) return 1;
    if (!a.isEliminated && b.isEliminated) return -1;
    return b.secured - a.secured;
  });
  
  sorted.forEach(p => {
    const li = document.createElement('li');
    li.className = `leaderboard-item ${p.isEliminated ? 'eliminated' : ''} ${p.id === 'player' ? 'is-self' : ''}`;
    
    const lockoutText = p.lockout > 0 ? ' ⚡' : '';
    const nameSpan = `<span class="lbl-name">${p.name}${lockoutText}</span>`;
    const securedSpan = `<span class="lbl-secured">${p.secured}</span>`;
    const turnSpan = `<span class="lbl-turn">${p.isEliminated ? '---' : p.turn}</span>`;
    
    li.innerHTML = `${nameSpan}${securedSpan}${turnSpan}`;
    list.appendChild(li);
  });
  
  const myself = players.find(p => p.id === 'player');
  if (myself) {
    document.getElementById('turn-score').innerText = myself.turn;
    document.getElementById('secured-score').innerText = myself.secured;
  }
}

function updateGhostTrail() {
  if (!ghostLine) return;
  const positions = ghostLine.geometry.attributes.position.array;
  
  for (let i = 0; i < maxGhostPoints; i++) {
    const pt = ghostPoints[i];
    if (pt) {
      positions[i * 3] = pt.x;
      positions[i * 3 + 1] = pt.y;
      positions[i * 3 + 2] = pt.z;
    } else {
      const lastPt = ghostPoints[ghostPoints.length - 1];
      if (lastPt) {
        positions[i * 3] = lastPt.x;
        positions[i * 3 + 1] = lastPt.y;
        positions[i * 3 + 2] = lastPt.z;
      } else {
        positions[i * 3] = 0;
        positions[i * 3 + 1] = 0;
        positions[i * 3 + 2] = 0;
      }
    }
  }
  
  ghostLine.geometry.attributes.position.needsUpdate = true;
}

function init3DAndPhysics() {
  const container = document.getElementById('canvas-3d');
  const width = container.clientWidth;
  const height = container.clientHeight;

  // 1. Inicializar Three.js
  scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2('#0c0d0e', 0.08);

  camera = new THREE.PerspectiveCamera(42, width / height, 0.1, 50);
  camera.position.copy(originalCamPos);
  camera.lookAt(0, -0.4, 0);

  renderer3d = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  renderer3d.setSize(width, height);
  renderer3d.shadowMap.enabled = true;
  renderer3d.shadowMap.type = THREE.PCFSoftShadowMap;
  container.innerHTML = '';
  container.appendChild(renderer3d.domElement);

  // 2. Inicializar Cannon.js
  world = new CANNON.World();
  world.gravity.set(0, -17.0, 0);
  world.broadphase = new CANNON.NaiveBroadphase();

  tablePhysicsMaterial = new CANNON.Material('tableMaterial');
  dicePhysicsMaterial = new CANNON.Material('diceMaterial');

  const diceTableContact = new CANNON.ContactMaterial(tablePhysicsMaterial, dicePhysicsMaterial, {
    friction: 0.65,
    restitution: 0.48
  });
  world.addContactMaterial(diceTableContact);

  const diceDiceContact = new CANNON.ContactMaterial(dicePhysicsMaterial, dicePhysicsMaterial, {
    friction: 0.35,
    restitution: 0.58
  });
  world.addContactMaterial(diceDiceContact);

  // 3. Mesa
  const tableGeo = new THREE.BoxGeometry(6, 0.4, 8);
  const tableMat = new THREE.MeshStandardMaterial({
    color: 0x221814,
    roughness: 0.9,
    metalness: 0.05
  });
  tableMesh = new THREE.Mesh(tableGeo, tableMat);
  tableMesh.position.y = -0.2;
  tableMesh.receiveShadow = true;
  scene.add(tableMesh);

  // Cuerpo físico de la mesa
  const tableBody = new CANNON.Body({
    mass: 0,
    shape: new CANNON.Box(new CANNON.Vec3(3, 0.2, 4)),
    material: tablePhysicsMaterial
  });
  tableBody.position.set(0, -0.2, 0);
  world.addBody(tableBody);

  // Bumper walls
  const createWall = (px, py, pz, sx, sy, sz) => {
    const body = new CANNON.Body({
      mass: 0,
      shape: new CANNON.Box(new CANNON.Vec3(sx / 2, sy / 2, sz / 2)),
      material: tablePhysicsMaterial
    });
    body.position.set(px, py, pz);
    world.addBody(body);

    const wallGeo = new THREE.BoxGeometry(sx, sy, sz);
    const wallMat = new THREE.MeshStandardMaterial({ color: 0x181514, roughness: 0.9, metalness: 0.6 });
    const mesh = new THREE.Mesh(wallGeo, wallMat);
    mesh.position.set(px, py, pz);
    mesh.receiveShadow = true;
    mesh.castShadow = true;
    scene.add(mesh);
  };

  createWall(0, 0.6, -4.1, 6.2, 1.2, 0.2);
  createWall(0, 0.6, 4.1, 6.2, 1.2, 0.2);
  createWall(-3.1, 0.6, 0, 0.2, 1.2, 8.2);
  createWall(3.1, 0.6, 0, 0.2, 1.2, 8.2);

  // 4. Luces y Foco colgante
  const ambient = new THREE.AmbientLight(0xffffff, 0.12);
  scene.add(ambient);

  lightBulb = new THREE.PointLight(0xffe2cc, 2.0, 20);
  lightBulb.position.set(0, 4.5, 0);
  lightBulb.castShadow = true;
  lightBulb.shadow.mapSize.width = 1024; // Resolución superior para sombras nítidas
  lightBulb.shadow.mapSize.height = 1024;
  lightBulb.shadow.bias = -0.002;
  lightBulb.shadow.radius = 4.0; // Bordes de sombra suaves (penumbra industrial)
  scene.add(lightBulb);

  const bulbFixtureGeo = new THREE.CylinderGeometry(0.08, 0.08, 0.8, 8);
  const bulbFixtureMat = new THREE.MeshStandardMaterial({ color: 0x111111, metalness: 0.9 });
  lightBulbFixture = new THREE.Mesh(bulbFixtureGeo, bulbFixtureMat);
  lightBulbFixture.position.set(0, 5, 0);
  scene.add(lightBulbFixture);

  // Cable de suspensión metálico acoplado al movimiento pendular
  const cableGeo = new THREE.CylinderGeometry(0.015, 0.015, 3.0, 6);
  const cableMat = new THREE.MeshStandardMaterial({ color: 0x1a1a1a, metalness: 0.8, roughness: 0.6 });
  const cableMesh = new THREE.Mesh(cableGeo, cableMat);
  cableMesh.position.set(0, 1.5, 0);
  lightBulbFixture.add(cableMesh);

  const bulbGeo = new THREE.SphereGeometry(0.16, 16, 16);
  const bulbMat = new THREE.MeshBasicMaterial({ color: 0xffe2cc });
  const bulbVisual = new THREE.Mesh(bulbGeo, bulbMat);
  bulbVisual.position.set(0, -0.4, 0);
  lightBulbFixture.add(bulbVisual);

  // 5. Dados Físicos simultáneos
  const diceGeo = new THREE.BoxGeometry(0.66, 0.66, 0.66);
  tableDice = [];

  DiceMesaConfig.forEach((cfg) => {
    const materialsArray = Array.from({ length: 6 }, () => 
      new THREE.MeshStandardMaterial({ color: 0x444444, roughness: 0.4, metalness: 0.7 })
    );

    const mesh = new THREE.Mesh(diceGeo, materialsArray);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    scene.add(mesh);

    const body = new CANNON.Body({
      mass: 1.5,
      shape: new CANNON.Box(new CANNON.Vec3(0.33, 0.33, 0.33)),
      material: dicePhysicsMaterial
    });
    world.addBody(body);

    body.addEventListener('collide', handleDiceCollision);

    tableDice.push({
      configId: cfg.playerId,
      activePlayerId: null,
      mesh: mesh,
      body: body,
      skin: cfg.skin,
      isRolling: false,
      readyPos: cfg.readyPos,
      lastCollisionTime: 0
    });
  });

  // 6. Línea de estela
  const positions = new Float32Array(maxGhostPoints * 3);
  const ghostLineGeo = new THREE.BufferGeometry();
  ghostLineGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  const ghostLineMat = new THREE.LineBasicMaterial({
    color: 0x00bcd4,
    transparent: true,
    opacity: 0.6,
    linewidth: 2.0
  });
  ghostLine = new THREE.Line(ghostLineGeo, ghostLineMat);
  scene.add(ghostLine);

  // 7. Sistema de Partículas (Chispas)
  const sparkGeo = new THREE.BoxGeometry(0.06, 0.06, 0.06);
  const sparkMat = new THREE.MeshBasicMaterial({ color: 0xffa000 });
  
  sparkParticles = [];
  for (let i = 0; i < maxSparks; i++) {
    const mesh = new THREE.Mesh(sparkGeo, sparkMat);
    mesh.visible = false;
    scene.add(mesh);
    sparkParticles.push({
      mesh: mesh,
      vel: new THREE.Vector3(0,0,0),
      life: 0,
      maxLife: 0
    });
  }

  // 8. Eventos
  container.addEventListener('mousedown', onDragStart);
  container.addEventListener('mousemove', onDragMove);
  window.addEventListener('mouseup', onDragEnd);
}

// Chispas e impacto
function handleDiceCollision(e) {
  const relativeVelocity = e.contact.getImpactVelocityAlongNormal();
  if (relativeVelocity > 1.0) {
    soundPlayer.playBounce(relativeVelocity / 6);
    
    // Spawner de partículas físicas (Chispas en punto de colisión)
    const contacts = e.target.world.contacts;
    const contact = contacts.find(c => c.bi === e.target || c.bj === e.target);
    
    if (contact) {
      // Determinar la skin del dado que colisiona
      const activeDie = tableDice.find(d => d.body === e.target);
      let skinId = 'steel';
      if (activeDie) {
        if (activeDie.activePlayerId === 'player') {
          skinId = playerSkin;
        } else if (activeDie.activePlayerId) {
          const cfg = DiceMesaConfig.find(c => c.playerId === activeDie.configId);
          skinId = cfg ? cfg.skin : 'copper';
        }
      }

      // Estimar punto del contacto
      const pos = e.target.position;
      spawnSparks(new THREE.Vector3(pos.x, pos.y - 0.25, pos.z), relativeVelocity, skinId);
    }

    // Camera shake proporcional a la fuerza
    if (relativeVelocity > 2.5) {
      cameraShakeIntensity = Math.min(relativeVelocity * 0.03, 0.15);
      cameraShakeTime = 0.25; // Duración corta
    }
  }
}

// --- DUST & SPARK SPARTICLES SYSTEM ---
function spawnSparks(position, intensity, skinId) {
  let spawned = 0;
  const count = Math.min(Math.floor(intensity * 3), 15);

  // Buscar partículas inactivas
  for (let i = 0; i < maxSparks; i++) {
    const s = sparkParticles[i];
    if (s.life <= 0) {
      s.mesh.position.copy(position);
      s.mesh.visible = true;
      
      // Velocidad aleatoria dispersa hacia arriba
      s.vel.set(
        (Math.random() - 0.5) * 3,
        2 + Math.random() * 3,
        (Math.random() - 0.5) * 3
      );

      // Color según la skin del dado colisionado
      const colors = SkinColors[skinId] || SkinColors.steel;
      s.mesh.material.color.set(colors.dots);

      s.maxLife = 0.3 + Math.random() * 0.4;
      s.life = s.maxLife;

      spawned++;
      if (spawned >= count) break;
    }
  }
}

function updateParticles(dt) {
  sparkParticles.forEach((s) => {
    if (s.life > 0) {
      s.life -= dt;
      if (s.life <= 0) {
        s.mesh.visible = false;
      } else {
        // Aplicar velocidad y gravedad simple
        s.mesh.position.addScaledVector(s.vel, dt);
        s.vel.y -= 9.8 * dt; // Gravedad a las chispas

        // Escalar según vida restante
        const scale = s.life / s.maxLife;
        s.mesh.scale.set(scale, scale, scale);
      }
    }
  });
}

// --- PERSONALIZACIÓN Y CAMBIO FÍSICO DE MESAS ---
function applyTableStyle() {
  const styleId = currentTableStyle;
  const cfg = Unlockables.tables.find(t => t.id === styleId) || Unlockables.tables[0];
  
  // 1. Cambiar propiedades visuales de la mesa
  switch (styleId) {
    case 'oak':
      tableMesh.material.color.set(0x3e271c);
      tableMesh.material.roughness = 0.9;
      tableMesh.material.metalness = 0.05;
      break;
    case 'felt':
      tableMesh.material.color.set(0x1b5e20); // Verde fieltro
      tableMesh.material.roughness = 0.95;
      tableMesh.material.metalness = 0.0;
      break;
    case 'ironplate':
      tableMesh.material.color.set(0x455a64); // Chapa de hierro
      tableMesh.material.roughness = 0.25;
      tableMesh.material.metalness = 0.85;
      break;
    case 'marble':
      tableMesh.material.color.set(0xe0e0e0); // Mármol blanco
      tableMesh.material.roughness = 0.08;
      tableMesh.material.metalness = 0.1;
      break;
    case 'plasma':
      tableMesh.material.color.set(0x0a0c10); // Base oscura
      tableMesh.material.roughness = 0.5;
      tableMesh.material.metalness = 0.7;
      break;
  }
  tableMesh.material.needsUpdate = true;

  // 2. Modificar físicas de colisión en tiempo real
  // Actualizar la gravedad del mundo de Cannon.js
  world.gravity.set(0, cfg.gravity, 0);

  // Actualizar coeficientes de fricción y rebote
  const contactMat = world.contactmaterials.find(c => 
    (c.materials[0] === tablePhysicsMaterial && c.materials[1] === dicePhysicsMaterial) ||
    (c.materials[1] === tablePhysicsMaterial && c.materials[0] === dicePhysicsMaterial)
  );

  if (contactMat) {
    contactMat.friction = cfg.friction;
    contactMat.restitution = cfg.restitution;
  }

  // Sincronizar HUD de telemetría de mesa
  document.getElementById('mat-label-physics').innerText = cfg.name;
  document.getElementById('friction-label-physics').innerText = 
    cfg.friction > 0.7 ? 'Alta' : (cfg.friction < 0.2 ? 'Baja' : 'Media');
  document.getElementById('gravity-label-physics').innerText = 
    cfg.gravity > -12 ? 'Baja' : 'Estándar';
}

// --- CREACIÓN DE CARTELERA 3D (TEXT BILLBOARD PARA LANDING) ---
function createLandedSprite(value, position) {
  // Limpiar billboards existentes en la mesa
  clearBillboards();

  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');

  // Circulo brillante detrás del número
  ctx.fillStyle = 'rgba(10,12,14,0.9)';
  ctx.strokeStyle = SkinColors[playerSkin]?.dots || '#00bcd4';
  ctx.lineWidth = 6;
  ctx.beginPath();
  ctx.arc(64, 64, 56, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();

  // Texto del valor
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 72px Outfit, sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(value, 64, 64);

  const texture = new THREE.CanvasTexture(canvas);
  const mat = new THREE.SpriteMaterial({ map: texture, transparent: true, opacity: 1 });
  const sprite = new THREE.Sprite(mat);
  sprite.position.set(position.x, position.y + 0.8, position.z);
  sprite.scale.set(0.9, 0.9, 0.9);
  
  scene.add(sprite);
  billboardSprites.push(sprite);
}

function clearBillboards() {
  billboardSprites.forEach(s => scene.remove(s));
  billboardSprites = [];
}

// --- DRAG Y TIROS ---
function onDragStart(e) {
  if (currentGameState !== GameState.PLAYING) return;
  const myself = players.find(p => p.id === 'player');
  if (myself.isEliminated || myself.lockout > 0) return;

  const playerDiceObj = tableDice.find(d => d.configId === 'player');
  const dist = playerDiceObj.body.position.distanceTo(playerDiceObj.readyPos);
  if (dist > 0.45 || playerDiceObj.isRolling) return;

  isDragging = true;
  dragStartPos = { x: e.clientX, y: e.clientY };
  currentDragPos = { x: e.clientX, y: e.clientY };
  lastDragTime = performance.now();
  
  soundPlayer.playDiceRattle();
}

function onDragMove(e) {
  if (!isDragging) return;
  currentDragPos = { x: e.clientX, y: e.clientY };
}

function onDragEnd(e) {
  if (!isDragging) return;
  isDragging = false;

  const dx = currentDragPos.x - dragStartPos.x;
  const dy = currentDragPos.y - dragStartPos.y;
  const dt = Math.max(performance.now() - lastDragTime, 16);

  const speedX = (dx / dt) * 0.45;
  const speedZ = (dy / dt) * 0.45;

  launchDiceFísica('player', speedX, speedZ);
}

function launchDiceFísica(launcherId, fx, fz) {
  const diceObj = tableDice.find(d => d.activePlayerId === launcherId || (launcherId === 'player' && d.configId === 'player'));
  if (!diceObj || diceObj.isRolling) return;

  // Limpiar sprites anteriores al lanzar nuevo tiro
  clearBillboards();

  let velX = fx;
  let velZ = fz;

  if (launcherId === 'player') {
    const forceMag = Math.sqrt(velX * velX + velZ * velZ);
    if (forceMag < 0.3) {
      velX = (Math.random() - 0.5) * 2;
      velZ = -5.5 - Math.random() * 2.5;
    }

    const maxVel = 14;
    const speed = Math.sqrt(velX * velX + velZ * velZ);
    if (speed > maxVel) {
      velX = (velX / speed) * maxVel;
      velZ = (velZ / speed) * maxVel;
    }

    diceObj.body.position.copy(diceObj.readyPos);
    diceObj.body.velocity.set(velX, 5.2 + speed * 0.38, velZ);

    // Dice control (Tiro suave = deslizamiento sin torque caótico)
    const torqueX = speed < 3.2 ? 0 : (Math.random() - 0.5) * speed * 2.8;
    const torqueY = -velX * 4.0;
    const torqueZ = speed < 3.2 ? 0 : (Math.random() - 0.5) * speed * 2.8;

    diceObj.body.angularVelocity.set(torqueX, torqueY, torqueZ);
    diceObj.isRolling = true;
    
    addLog(`👤 Has lanzado tu dado con fuerza (${speed.toFixed(1)} m/s)...`, 'info');
    document.getElementById('btn-player-roll').disabled = true;
    document.getElementById('btn-player-bank').disabled = true;
    
    ghostPoints = [];
  } else {
    const bot = players.find(p => p.id === launcherId);
    if (!bot || bot.isEliminated) return;

    diceObj.body.position.copy(diceObj.readyPos);
    setInitialFaceRotation(diceObj.body, Math.floor(Math.random() * 6) + 1);

    let targetX = (Math.random() - 0.5) * 2;
    let targetZ = (Math.random() - 0.5) * 2;
    
    let velXBot = targetX - diceObj.readyPos.x;
    let velZBot = targetZ - diceObj.readyPos.z;
    
    const dist = Math.sqrt(velXBot * velXBot + velZBot * velZBot);
    const force = 4.8 + Math.random() * 4.2;
    
    velXBot = (velXBot / dist) * force;
    velZBot = (velZBot / dist) * force;

    diceObj.body.velocity.set(velXBot, 5.0 + force * 0.35, velZBot);
    diceObj.body.angularVelocity.set(
      (Math.random() - 0.5) * force * 3,
      (Math.random() - 0.5) * force * 3,
      (Math.random() - 0.5) * force * 3
    );

    diceObj.isRolling = true;
    addLog(`🎲 ${bot.name} lanza su dado en la mesa...`, 'info');
  }
}

// --- BUCLE FÍSICO Y ANIMACIÓN PRINCIPAL (~60 FPS) ---
let lastFrameTime = performance.now();

function updatePhysicsAndRender() {
  requestAnimationFrame(updatePhysicsAndRender);

  const now = performance.now();
  const dt = Math.min((now - lastFrameTime) / 1000, 0.1); // Cap a 100ms max
  lastFrameTime = now;

  // 1. Simulación física Cannon.js
  world.step(1 / 60);

  // 2. Actualizar partículas
  updateParticles(dt);

  // 3. Efecto de cámara oscilante (foco de fosa pendular)
  const time = now * 0.0016;
  const swingAngleX = Math.sin(time) * 0.16;
  const swingAngleZ = Math.cos(time * 0.8) * 0.12;
  lightBulbFixture.rotation.set(swingAngleX, 0, swingAngleZ);
  
  const bulbWorldPos = new THREE.Vector3(0, -0.4, 0);
  bulbWorldPos.applyMatrix4(lightBulbFixture.matrixWorld);
  lightBulb.position.copy(bulbWorldPos);

  // 4. Procesar Camera Shake con atenuación lineal
  if (cameraShakeTime > 0) {
    cameraShakeTime -= dt;
    cameraShakeIntensity = THREE.MathUtils.lerp(cameraShakeIntensity, 0, dt * 8);
    
    const offsetX = (Math.random() - 0.5) * cameraShakeIntensity;
    const offsetY = (Math.random() - 0.5) * cameraShakeIntensity;
    const offsetZ = (Math.random() - 0.5) * cameraShakeIntensity;
    
    camera.position.set(
      originalCamPos.x + offsetX,
      originalCamPos.y + offsetY,
      originalCamPos.z + offsetZ
    );
  } else {
    camera.position.copy(originalCamPos);
  }

  // 5. Animación de sprites flotantes (Subir lentamente)
  billboardSprites.forEach(s => {
    s.position.y += dt * 0.25;
    s.material.opacity = THREE.MathUtils.lerp(s.material.opacity, 0, dt * 1.5);
  });

  // 6. Sincronizar todos los dados
  tableDice.forEach((d) => {
    d.mesh.position.copy(d.body.position);
    d.mesh.quaternion.copy(d.body.quaternion);

    if (d.configId === 'player' && d.isRolling && d.body.position.y > 0.01) {
      if (ghostPoints.length < maxGhostPoints) {
        ghostPoints.push(new THREE.Vector3(d.body.position.x, d.body.position.y, d.body.position.z));
        updateGhostTrail();
      }
    }

    // Táctica: bots de alto nivel usan freno magnético para congelar 5s o 6s
    if (d.isRolling && d.activePlayerId && d.activePlayerId !== 'player') {
      const bot = players.find(p => p.id === d.activePlayerId);
      if (bot && !bot.hasBrakeUsed && (bot.difficulty === 'ORO' || bot.difficulty === 'PLATA')) {
        const speed = d.body.velocity.length();
        if (speed > 1.5 && speed < 5.0) {
          const currentFace = getTopFace(d.body);
          const triggerProb = bot.difficulty === 'ORO' ? 0.04 : 0.02;
          if ((currentFace === 6 || currentFace === 5) && Math.random() < triggerProb) {
            bot.hasBrakeUsed = true;
            soundPlayer.playBrake();
            d.body.velocity.set(0, 0, 0);
            d.body.angularVelocity.set(0, 0, 0);
            addLog(`💥 ¡${bot.name} usó Freno Magnético para asegurar un ${currentFace}!`, 'success');
          }
        }
      }
    }

    if (d.isRolling) {
      const speed = d.body.velocity.length();
      const angSpeed = d.body.angularVelocity.length();
      
      if (speed < 0.04 && angSpeed < 0.04) {
        d.isRolling = false;
        const landedFace = getTopFace(d.body);
        
        // Crear cartelera visual de número flotante
        createLandedSprite(landedFace, d.body.position);

        const actorId = d.activePlayerId || 'player';
        resolveRollResult(actorId, landedFace);
      }
    }
  });

  // 7. Actualizar estado de botones de habilidad táctica del jugador
  const pDice = tableDice.find(d => d.configId === 'player');
  const myself = players.find(p => p.id === 'player');
  if (currentGameState === GameState.PLAYING && myself && !myself.isEliminated && myself.lockout === 0) {
    const brakeBtn = document.getElementById('btn-skill-brake');
    if (brakeBtn) {
      brakeBtn.disabled = hasBrakeUsed || !pDice || !pDice.isRolling;
    }
    const shieldBtn = document.getElementById('btn-skill-shield');
    if (shieldBtn) {
      shieldBtn.disabled = hasShieldUsed || isShieldActive || (pDice && pDice.isRolling);
    }
  } else {
    const brakeBtn = document.getElementById('btn-skill-brake');
    if (brakeBtn) brakeBtn.disabled = true;
    const shieldBtn = document.getElementById('btn-skill-shield');
    if (shieldBtn) shieldBtn.disabled = true;
  }

  renderer3d.render(scene, camera);
}

// --- TORNEO COMPACTO DE 16 JUGADORES ---

function initLobby() {
  currentGameState = GameState.INTRO;
  eliminationRoundCount = 1;
  roundTimer = 30.0;

  loadProfile();
  renderCustomizationTab('dice-skins');
  applyTableStyle();

  showScreen('intro');
  addLog("BIENVENIDO AL TRIBUNAL DE LA FOSA. PROCESO OPERATIVO.", 'system');
}

function startGame() {
  soundPlayer.init();

  // Resetear habilidades tácticas del jugador al inicio de partida / ronda
  hasBrakeUsed = false;
  hasShieldUsed = false;
  isShieldActive = false;
  
  const brakeBtn = document.getElementById('btn-skill-brake');
  const brakeStatus = document.getElementById('brake-status');
  const shieldBtn = document.getElementById('btn-skill-shield');
  const shieldStatus = document.getElementById('shield-status');
  
  if (brakeBtn && brakeStatus) {
    brakeBtn.classList.remove('cooldown');
    brakeBtn.disabled = true;
    brakeStatus.innerText = 'LISTO';
    brakeStatus.style.color = 'var(--terminal-green)';
    brakeStatus.style.background = 'rgba(0,255,0,0.15)';
  }
  
  if (shieldBtn && shieldStatus) {
    shieldBtn.classList.remove('cooldown');
    shieldBtn.disabled = false;
    shieldStatus.innerText = 'LISTO';
    shieldStatus.style.color = 'var(--terminal-green)';
    shieldStatus.style.background = 'rgba(0,255,0,0.15)';
  }
  
  // Activar música procedural
  if (musicSynth.isPlaying) {
    musicSynth.setTempo(100);
  }

  aiDifficulty = document.getElementById('select-difficulty').value;

  if (gameMode === 'sandbox') {
    document.getElementById('sandbox-indicator').classList.remove('hidden');
    document.getElementById('timer-display').innerText = "SANDBOX";
    document.getElementById('leaderboard-title').innerText = "SANDBOX DE PRÁCTICA";
    
    players = [
      { id: 'player', name: 'TU (SANDBOX)', isBot: false, secured: 0, turn: 0, lockout: 0, isEliminated: false, hasBrakeUsed: false, hasShieldUsed: false, isShieldActive: false }
    ];
    activeTablePlayers = [...players];

    tableDice[0].activePlayerId = 'player';
    tableDice[1].activePlayerId = null;
    tableDice[2].activePlayerId = null;
    tableDice[3].activePlayerId = null;

    applyDiceSkins();
    resetDiceToReady();
    updateLeaderboard();

    document.getElementById('btn-player-roll').disabled = false;
    document.getElementById('btn-player-bank').disabled = true;

    currentGameState = GameState.PLAYING;
    showScreen('play');
    stopLoops();
    addLog("SANDBOX INICIADO. GHOST TRAYECTORIA Y FÍSICA PERSONALIZADA ACTIVAS.", 'system');
    return;
  }

  // MODO TORNEO (16 JUGADORES)
  document.getElementById('sandbox-indicator').classList.add('hidden');
  document.getElementById('leaderboard-title').innerText = "TABLA DE SUPERVIVENCIA (16 JUG.)";
  
  // 16 Jugadores
  players = [
    { id: 'player', name: 'TU (JUGADOR)', isBot: false, secured: 0, turn: 0, lockout: 0, isEliminated: false, hasBrakeUsed: false, hasShieldUsed: false, isShieldActive: false }
  ];

  const botPool = [
    { name: 'CLINCH (BOT)', difficulty: 'BRONCE' },
    { name: 'GAMBLE (BOT)', difficulty: 'ORO' },
    { name: 'SLASHER (BOT)', difficulty: 'PLATA' },
    { name: 'RAZOR (BOT)', difficulty: 'PLATA' },
    { name: 'CYBORG (BOT)', difficulty: 'ORO' },
    { name: 'VIPER (BOT)', difficulty: 'BRONCE' },
    { name: 'APEX (BOT)', difficulty: 'PLATA' },
    { name: 'BUMPER (BOT)', difficulty: 'BRONCE' },
    { name: 'COPPER (BOT)', difficulty: 'BRONCE' },
    { name: 'GEAR (BOT)', difficulty: 'PLATA' },
    { name: 'SPIKE (BOT)', difficulty: 'PLATA' },
    { name: 'VALVE (BOT)', difficulty: 'ORO' },
    { name: 'FLANGE (BOT)', difficulty: 'BRONCE' },
    { name: 'DEBRIS (BOT)', difficulty: 'PLATA' },
    { name: 'RAGE (BOT)', difficulty: 'ORO' }
  ];

  for (let i = 0; i < 15; i++) {
    players.push({
      id: `bot_${i}`,
      name: botPool[i].name,
      isBot: true,
      difficulty: botPool[i].difficulty,
      secured: 0,
      turn: 0,
      lockout: 0,
      isEliminated: false,
      hasBrakeUsed: false,
      hasShieldUsed: false,
      isShieldActive: false
    });
  }

  // 4 en mesa visible (Mesa 0)
  activeTablePlayers = [
    players[0],
    players[1],
    players[2],
    players[3]
  ];

  tableDice[0].activePlayerId = activeTablePlayers[0].id;
  tableDice[1].activePlayerId = activeTablePlayers[1].id;
  tableDice[2].activePlayerId = activeTablePlayers[2].id;
  tableDice[3].activePlayerId = activeTablePlayers[3].id;

  applyDiceSkins();
  resetDiceToReady();
  updateLeaderboard();

  currentGameState = GameState.PLAYING;
  roundTimer = 30.0;
  
  addLog(`⛓️ RONDA DEL TORNEO ${eliminationRoundCount} INICIADA. 16 JUGADORES COMPITIENDO EN BRACKETS.`, 'warn');
  showScreen('play');

  document.getElementById('btn-player-roll').disabled = false;
  document.getElementById('btn-player-bank').disabled = true;

  stopLoops();
  startTimerLoop();
  startBotsAILoop();
  
  if (musicSynth.isPlaying) {
    musicSynth.setTempo(130); // Ritmo rápido de combate
  }
}

function startTimerLoop() {
  timerInterval = setInterval(() => {
    if (currentGameState !== GameState.PLAYING) return;
    if (gameMode === 'sandbox') return;

    roundTimer -= 0.1;
    if (roundTimer <= 0) {
      roundTimer = 0;
      stopLoops();
      triggerEliminationPhase();
    }

    document.getElementById('timer-display').innerText = roundTimer.toFixed(1);

    if (roundTimer <= 7.0) {
      document.getElementById('timer-display').style.color = '#d32f2f';
      if (Math.round(roundTimer * 10) % 10 === 0) {
        soundPlayer.playHeartbeat();
      }
      
      // Decaimiento de puntos (Zone)
      players.forEach(p => {
        if (!p.isEliminated && p.secured > 0 && Math.random() < 0.05) {
          p.secured = Math.max(0, p.secured - 1);
          if (p.id === 'player') {
            addLog("⚠️ La radiación de la fosa drena 1 punto de tu total asegurado!", 'danger');
          }
        }
      });
      updateLeaderboard();
    } else {
      document.getElementById('timer-display').style.color = 'var(--terminal-amber)';
      if (Math.round(roundTimer * 10) % 20 === 0) {
        soundPlayer.playTick();
      }
    }

    // Lockouts
    players.forEach(p => {
      if (p.lockout > 0) {
        p.lockout = Math.max(0, p.lockout - 100);
        if (p.id === 'player') {
          const sec = (p.lockout / 1000).toFixed(1);
          document.getElementById('lockout-timer').innerText = `${sec}s`;
          if (p.lockout === 0) {
            document.getElementById('lockout-overlay').classList.add('hidden');
            document.getElementById('btn-player-roll').disabled = false;
            addLog("👤 Has salido del lockout. Sistemas operativos.", 'system');
          }
        }
      }
    });

  }, 100);
}

function startBotsAILoop() {
  botAIInterval = setInterval(() => {
    if (currentGameState !== GameState.PLAYING) return;
    if (gameMode === 'sandbox') return;

    const aliveMesaBots = activeTablePlayers.filter(p => p.isBot && !p.isEliminated && p.lockout === 0);
    const rollingDice = tableDice.some(d => d.isRolling);

    if (aliveMesaBots.length > 0 && !rollingDice && Math.random() < 0.72) {
      const selectedBot = aliveMesaBots[Math.floor(Math.random() * aliveMesaBots.length)];
      executeBotAction(selectedBot);
    }

    simulateExternalTournamentTables();
    updateLeaderboard();
  }, 1300);
}

function executeBotAction(bot) {
  const secured = bot.secured;
  const current = bot.turn;
  
  const sorted = players.filter(p => !p.isEliminated).sort((a,b) => a.secured - b.secured);
  const bottomScore = sorted[0]?.secured || 0;
  const inDanger = (bot.secured <= bottomScore + 2);

  let shouldBank = false;

  if (current > 0) {
    const finalDifficulty = (aiDifficulty === 'ORO') ? 'ORO' : bot.difficulty;

    if (finalDifficulty === 'BRONCE') {
      shouldBank = current >= 8 || (Math.random() < 0.2);
    } else if (finalDifficulty === 'ORO') {
      const deficit = bottomScore + 4 - secured;
      if (inDanger) {
        shouldBank = (current >= deficit && current >= 10);
      } else {
        shouldBank = current >= 12 || (roundTimer < 8.0 && current > 4);
      }
    } else {
      shouldBank = current >= 10 || (roundTimer < 6.0 && current > 5);
    }
  }

  if (shouldBank) {
    bot.secured += bot.turn;
    addLog(`🤖 ${bot.name} aseguró (BANK) ${bot.turn} pts (Total: ${bot.secured}).`, 'success');
    bot.turn = 0;
    soundPlayer.playBank();
  } else {
    // Táctica: si el bot tira y tiene acumulado valioso, protege con escudo si está disponible
    if (!bot.hasShieldUsed && bot.turn >= 8 && (bot.difficulty === 'ORO' || bot.difficulty === 'PLATA')) {
      bot.isShieldActive = true;
      bot.hasShieldUsed = true;
      soundPlayer.playShieldActivate();
      addLog(`🛡️ ${bot.name} activó su Escudo de Fosa para proteger sus ${bot.turn} pts acumulados.`, 'success');
    }
    launchDiceFísica(bot.id, 0, 0);
  }
}

function simulateExternalTournamentTables() {
  players.forEach(p => {
    const isMesaPlayer = activeTablePlayers.some(am => am.id === p.id);
    if (!isMesaPlayer && p.isBot && !p.isEliminated && p.lockout === 0) {
      if (Math.random() < 0.16) {
        const face = Math.floor(Math.random() * 6) + 1;
        if (face === 1) {
          p.turn = 0;
          p.lockout = 2000;
        } else {
          p.turn += face;
          if (p.turn >= 10 + Math.floor(Math.random() * 5)) {
            p.secured += p.turn;
            p.turn = 0;
          }
        }
      }
    }
  });
}

function resolveRollResult(actorId, face) {
  const player = players.find(p => p.id === actorId);
  if (!player) return;

  if (face === 1) {
    if (player.isShieldActive) {
      player.isShieldActive = false;
      player.secured += player.turn;
      const bankedPoints = player.turn;
      player.turn = 0;
      soundPlayer.playShieldAbsorb();
      
      if (actorId === 'player') {
        addLog(`🛡️ ¡El Escudo de Fosa absorbió el 1 Mortal! Puntos asegurados (+${bankedPoints} pts).`, 'success');
        document.getElementById('btn-player-bank').disabled = true;
        resetDiceToReady();
        const shieldStatus = document.getElementById('shield-status');
        if (shieldStatus) {
          shieldStatus.innerText = 'ABSORBIDO';
          shieldStatus.style.color = 'var(--text-secondary)';
        }
      } else {
        addLog(`🛡️ ¡El Escudo de Fosa de ${player.name} absorbió el 1 Mortal! Puntos asegurados (+${bankedPoints} pts).`, 'success');
      }
    } else {
      player.turn = 0;
      player.lockout = 3000;
      soundPlayer.playBust();
      
      if (actorId === 'player') {
        addLog(`👤 Sacaste un 1 Mortal! Puntos evaporados y stasis.`, 'danger');
        document.getElementById('lockout-overlay').classList.remove('hidden');
        document.getElementById('lockout-timer').innerText = `3.0s`;
        document.getElementById('btn-player-roll').disabled = true;
        document.getElementById('btn-player-bank').disabled = true;
      } else {
        addLog(`⚠️ ${player.name} sacó un 1 Mortal.`, 'danger');
      }
    }
  } else {
    player.turn += face;
    
    if (actorId === 'player') {
      addLog(`👤 Sacaste un ${face} (Acumulado: ${player.turn}).`, 'info');
      document.getElementById('btn-player-roll').disabled = false;
      document.getElementById('btn-player-bank').disabled = false;
    } else {
      addLog(`🎲 ${player.name} sacó un ${face} (Acumulado: ${player.turn}).`, 'info');
    }
  }

  updateLeaderboard();
}

// --- FASE ELIMINATORIA BATTLE ROYALE ---
function triggerEliminationPhase() {
  currentGameState = GameState.ELIMINATION_SHOCK;
  showScreen('elimination');
  soundPlayer.playElimination();

  if (musicSynth.isPlaying) {
    musicSynth.setTempo(60); // Desaceleración dramática
  }

  const survivorsMesa = activeTablePlayers.filter(p => !p.isEliminated);
  const minMesaScore = Math.min(...survivorsMesa.map(p => p.secured));
  const candidatesMesa = survivorsMesa.filter(p => p.secured === minMesaScore);
  const tableLoser = candidatesMesa[Math.floor(Math.random() * candidatesMesa.length)];

  tableLoser.isEliminated = true;
  document.getElementById('eliminated-name-display').innerText = tableLoser.name;

  addLog(`⛓️ FOSA TRIBUNAL: ${tableLoser.name} es descargado al foso (${tableLoser.secured} pts).`, 'danger');

  setTimeout(() => {
    const totalSurvivors = players.filter(p => !p.isEliminated);
    
    // Brackets del torneo (R1: 16->12, R2: 12->9, R3: 9->7, R4: 7->5, R5+: Final)
    let targetSurvivorsCount = 12;
    if (eliminationRoundCount === 1) targetSurvivorsCount = 12;
    else if (eliminationRoundCount === 2) targetSurvivorsCount = 9;
    else if (eliminationRoundCount === 3) targetSurvivorsCount = 7;
    else if (eliminationRoundCount === 4) targetSurvivorsCount = 5;
    else targetSurvivorsCount = Math.max(1, totalSurvivors.length - 1);

    let currentAlive = players.filter(p => !p.isEliminated);
    while (currentAlive.length > targetSurvivorsCount) {
      const externalBotsAlive = currentAlive.filter(p => p.isBot && !activeTablePlayers.some(am => am.id === p.id));
      if (externalBotsAlive.length > 0) {
        externalBotsAlive.sort((a,b) => a.secured - b.secured);
        const worstExtBot = externalBotsAlive[0];
        worstExtBot.isEliminated = true;
        addLog(`⛓️ TRIBUNAL EXTERNO: ${worstExtBot.name} es eliminado de otra mesa (${worstExtBot.secured} pts).`, 'danger');
      } else {
        break;
      }
      currentAlive = players.filter(p => !p.isEliminated);
    }

    const userAlive = !players.find(p => p.id === 'player').isEliminated;
    const finalSurvivors = players.filter(p => !p.isEliminated);

    if (!userAlive) {
      // DERROTA: Cálculo de XP
      currentGameState = GameState.GAME_OVER;
      
      const xpEarned = Math.floor((playerProfile.level * 10) + (16 - finalSurvivors.length) * 80);
      document.getElementById('xp-lost-value').innerText = xpEarned;
      earnXP(xpEarned);

      playerProfile.losses++;
      saveProfile();

      document.getElementById('eliminated-by-name').innerText = tableLoser.name;
      showScreen('gameover');
      
      if (musicSynth.isPlaying) musicSynth.stop();
    } else if (finalSurvivors.length === 1) {
      // VICTORIA SUPREMA: Cálculo de XP
      currentGameState = GameState.VICTORY;
      
      const xpEarned = 1000 + Math.floor(playerProfile.level * 20);
      document.getElementById('xp-win-value').innerText = xpEarned;
      earnXP(xpEarned);

      playerProfile.wins++;
      saveProfile();

      showScreen('victory');
      if (musicSynth.isPlaying) musicSynth.stop();
    } else {
      // Siguiente bracket del torneo
      eliminationRoundCount++;
      
      const sortedSurvivors = finalSurvivors.filter(p => p.id !== 'player').sort((a,b) => b.secured - a.secured);
      activeTablePlayers = [players.find(p => p.id === 'player')];
      for (let i = 0; i < 3; i++) {
        if (sortedSurvivors[i]) activeTablePlayers.push(sortedSurvivors[i]);
      }

      tableDice[0].activePlayerId = activeTablePlayers[0]?.id || null;
      tableDice[1].activePlayerId = activeTablePlayers[1]?.id || null;
      tableDice[2].activePlayerId = activeTablePlayers[2]?.id || null;
      tableDice[3].activePlayerId = activeTablePlayers[3]?.id || null;

      startGame();
    }
  }, 4000);
}

// --- GESTIÓN DE PESTAÑAS DE PERSONALIZACIÓN Y RENDER ---
document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', (e) => {
    soundPlayer.playClick();
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.custom-tab-content').forEach(c => c.classList.remove('active'));

    e.target.classList.add('active');
    const tabId = e.target.getAttribute('data-tab');
    document.getElementById(`tab-${tabId}`).classList.add('active');

    renderCustomizationTab(tabId);
  });
});

function renderCustomizationTab(tabId) {
  const container = document.getElementById(`tab-${tabId}`);
  const grid = container.querySelector('.custom-grid');
  grid.innerHTML = '';

  const userLevel = playerProfile.level;

  if (tabId === 'dice-skins') {
    Unlockables.diceSkins.forEach(skin => {
      const isLocked = userLevel < skin.level;
      const isActive = playerProfile.equippedSkin === skin.id;

      const item = document.createElement('div');
      item.className = `unlock-item ${isLocked ? 'locked' : ''} ${isActive ? 'active' : ''}`;
      
      const colorPreview = SkinColors[skin.id] || SkinColors.steel;

      item.innerHTML = `
        <span class="color-preview" style="background: linear-gradient(135deg, ${colorPreview.primary}, ${colorPreview.secondary});"></span>
        <div class="unlock-name">
          <span>${skin.name}</span>
          <span class="unlock-desc">${skin.desc}</span>
        </div>
        ${isLocked ? `<span class="unlock-badge-lock">Lvl ${skin.level}</span>` : ''}
      `;

      if (!isLocked) {
        item.addEventListener('click', () => {
          soundPlayer.playClick();
          playerProfile.equippedSkin = skin.id;
          playerSkin = skin.id;
          saveProfile();
          renderCustomizationTab(tabId);
        });
      }
      grid.appendChild(item);
    });
  }

  else if (tabId === 'tables') {
    Unlockables.tables.forEach(table => {
      const isLocked = userLevel < table.level;
      const isActive = playerProfile.equippedTable === table.id;

      const item = document.createElement('div');
      item.className = `unlock-item ${isLocked ? 'locked' : ''} ${isActive ? 'active' : ''}`;

      item.innerHTML = `
        <div class="unlock-name">
          <span>${table.name}</span>
          <span class="unlock-desc">${table.desc}</span>
        </div>
        ${isLocked ? `<span class="unlock-badge-lock">Lvl ${table.level}</span>` : ''}
      `;

      if (!isLocked) {
        item.addEventListener('click', () => {
          soundPlayer.playClick();
          playerProfile.equippedTable = table.id;
          currentTableStyle = table.id;
          saveProfile();
          applyTableStyle();
          renderCustomizationTab(tabId);
        });
      }
      grid.appendChild(item);
    });
  }

  else if (tabId === 'ui-themes') {
    Unlockables.uiThemes.forEach(theme => {
      const isLocked = userLevel < theme.level;
      const isActive = playerProfile.equippedTheme === theme.id;

      const item = document.createElement('div');
      item.className = `unlock-item ${isLocked ? 'locked' : ''} ${isActive ? 'active' : ''}`;

      item.innerHTML = `
        <div class="unlock-name">
          <span>${theme.name}</span>
          <span class="unlock-desc">${theme.desc}</span>
        </div>
        ${isLocked ? `<span class="unlock-badge-lock">Lvl ${theme.level}</span>` : ''}
      `;

      if (!isLocked) {
        item.addEventListener('click', () => {
          soundPlayer.playClick();
          playerProfile.equippedTheme = theme.id;
          saveProfile();
          document.body.className = `theme-${theme.id}`;
          renderCustomizationTab(tabId);
        });
      }
      grid.appendChild(item);
    });
  }

  else if (tabId === 'titles') {
    Unlockables.titles.forEach(title => {
      const isLocked = userLevel < title.level;
      const isActive = playerProfile.equippedTitle === title.name;

      const item = document.createElement('div');
      item.className = `unlock-item ${isLocked ? 'locked' : ''} ${isActive ? 'active' : ''}`;

      item.innerHTML = `
        <div class="unlock-name">
          <span>${title.name}</span>
        </div>
        ${isLocked ? `<span class="unlock-badge-lock">Lvl ${title.level}</span>` : ''}
      `;

      if (!isLocked) {
        item.addEventListener('click', () => {
          soundPlayer.playClick();
          playerProfile.equippedTitle = title.name;
          saveProfile();
          updateProfileUI();
          renderCustomizationTab(tabId);
        });
      }
      grid.appendChild(item);
    });
  }
}

// --- ENLACE GENERAL DE CONTROLES ---

// Modos de juego
document.getElementById('btn-mode-tournament').addEventListener('click', (e) => {
  soundPlayer.playClick();
  gameMode = 'tournament';
  e.target.classList.add('active');
  document.getElementById('btn-mode-sandbox').classList.remove('active');
  document.getElementById('difficulty-group').style.display = 'flex';
});

document.getElementById('btn-mode-sandbox').addEventListener('click', (e) => {
  soundPlayer.playClick();
  gameMode = 'sandbox';
  e.target.classList.add('active');
  document.getElementById('btn-mode-tournament').classList.remove('active');
  document.getElementById('difficulty-group').style.display = 'none';
});

// Control de audio procedural
document.getElementById('btn-toggle-music').addEventListener('click', (e) => {
  soundPlayer.playClick();
  if (musicSynth.isPlaying) {
    musicSynth.stop();
    e.target.innerText = "MÚSICA: APAGADO";
  } else {
    musicSynth.init(soundPlayer.ctx || new (window.AudioContext || window.webkitAudioContext)());
    musicSynth.start();
    e.target.innerText = "MÚSICA: ENCENDIDO";
  }
});

// Dice Setting
document.querySelectorAll('.face-selector-btn').forEach(btn => {
  btn.addEventListener('click', (e) => {
    soundPlayer.playClick();
    document.querySelectorAll('.face-selector-btn').forEach(b => b.classList.remove('active'));
    e.target.classList.add('active');
    selectedInitialFace = parseInt(e.target.getAttribute('data-face'));
    addLog(`⚙️ Cara inicial ajustada en ${selectedInitialFace} (Dice Setting)`, 'system');
    resetDiceToReady();
  });
});

// Lanzador rápido de botón
document.getElementById('btn-player-roll').addEventListener('click', () => {
  const rolling = tableDice.some(d => d.isRolling);
  if (rolling) return;
  const fx = (Math.random() - 0.5) * 5;
  const fz = -6.5 - Math.random() * 3.5;
  launchDiceFísica('player', fx, fz);
});

// Asegurar (Bank)
document.getElementById('btn-player-bank').addEventListener('click', () => {
  const rolling = tableDice.some(d => d.isRolling);
  if (rolling) return;

  const myself = players.find(p => p.id === 'player');
  if (myself.turn === 0) return;

  myself.secured += myself.turn;
  addLog(`👤 Aseguraste (BANK) ${myself.turn} pts (Total guardado: ${myself.secured}).`, 'success');
  myself.turn = 0;
  
  soundPlayer.playBank();
  document.getElementById('btn-player-bank').disabled = true;
  updateLeaderboard();
  resetDiceToReady();
});

// Habilidades Tácticas (Click)
document.getElementById('btn-skill-brake').addEventListener('click', triggerBrake);
document.getElementById('btn-skill-shield').addEventListener('click', activateShield);

// Botones de pantallas finales
document.getElementById('btn-start-game').addEventListener('click', startGame);
document.getElementById('btn-reset-lobby').addEventListener('click', () => {
  stopLoops();
  initLobby();
});
document.getElementById('btn-gameover-retry').addEventListener('click', startGame);
document.getElementById('btn-gameover-lobby').addEventListener('click', initLobby);
document.getElementById('btn-victory-retry').addEventListener('click', startGame);
document.getElementById('btn-victory-lobby').addEventListener('click', initLobby);

// Teclado rápido (1-6) y Habilidades
window.addEventListener('keydown', (e) => {
  if (currentGameState !== GameState.PLAYING) return;

  // Habilidades Tácticas (Teclado)
  if (e.key === ' ' || e.code === 'Space') {
    e.preventDefault(); // Evitar scroll
    triggerBrake();
    return;
  }
  if (e.key === 'c' || e.key === 'C') {
    activateShield();
    return;
  }

  const keyNum = parseInt(e.key);
  if (keyNum >= 1 && keyNum <= 6) {
    selectedInitialFace = keyNum;
    
    document.querySelectorAll('.face-selector-btn').forEach(btn => {
      if (parseInt(btn.getAttribute('data-face')) === selectedInitialFace) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    addLog(`⚙️ Teclado: Cara inicial de dado establecida en ${selectedInitialFace}`, 'system');
    resetDiceToReady();
  }
});

// Inicialización de arranque
window.addEventListener('DOMContentLoaded', () => {
  init3DAndPhysics();
  initLobby();
  updatePhysicsAndRender();
});
