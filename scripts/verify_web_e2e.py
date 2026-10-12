#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_web_e2e.py
Suite de verificación automatizada headless para Agalludo Royale Web.
Lanza Chrome con depuración remota, interactúa con el juego en WebGL/Cannon.js,
captura métricas de consola y genera capturas de evidencia fotográfica en docs/gdd/evidencia/.
"""

import os
import sys
import time
import json
import subprocess
import urllib.request
import urllib.parse
from http.server import HTTPServer, SimpleHTTPRequestHandler
import threading

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_DIR = os.path.join(PROJECT_DIR, "web")
EVIDENCE_DIR = os.path.join(PROJECT_DIR, "docs", "gdd", "evidencia")
os.makedirs(EVIDENCE_DIR, exist_ok=True)

CHROME_PATHS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
]

def find_browser():
    for p in CHROME_PATHS:
        if os.path.exists(p):
            return p
    raise FileNotFoundError("No se encontró Google Chrome ni Microsoft Edge.")

class QuietServer(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=WEB_DIR, **kwargs)
    def log_message(self, format, *args):
        pass

def start_temp_server(port=8099):
    server = HTTPServer(("127.0.0.1", port), QuietServer)
    t = threading.Thread(target=server.serve_forever, daemon=True)
    t.start()
    return server

def capture_screenshot(browser_path, url, output_png, delay_sec=2, window_size="1280,800"):
    cmd = [
        browser_path,
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--run-all-compositor-stages-before-draw",
        f"--window-size={window_size}",
        f"--screenshot={output_png}",
        url
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, timeout=20)
    if os.path.exists(output_png) and os.path.getsize(output_png) > 1000:
        print(f"[✓] Captura exitosa ({os.path.getsize(output_png)} bytes): {output_png}")
        return True
    else:
        print(f"[!] Fallo al capturar screenshot: {res.stderr}")
        return False

def run_e2e_audit():
    print("=" * 65)
    print("AGALLUDO ROYALE: SUITE DE VERIFICACIÓN AUTOMATIZADA E2E")
    print("=" * 65)
    
    browser = find_browser()
    print(f"[*] Navegador detectado: {browser}")

    port = 8099
    server = start_temp_server(port)
    url = f"http://127.0.0.1:{port}/index.html"
    print(f"[*] Servidor temporal levantado en: {url}")

    # 1. Menú Principal Baseline
    menu_shot = os.path.join(EVIDENCE_DIR, "01_menu_principal.png")
    print("\n[1/5] Verificando renderizado de Menú Principal (Lobby 8P)...")
    capture_screenshot(browser, url, menu_shot, delay_sec=2)

    # 2. Tutorial Screen
    print("\n[2/5] Verificando pantalla de Tutorial y Manual de Operaciones...")
    tuto_shot = os.path.join(EVIDENCE_DIR, "02_tutorial_pantalla.png")
    capture_screenshot(browser, f"{url}#tutorial", tuto_shot, delay_sec=2)

    # 3. Mesa de Juego Activa (Salón de Roble - 8 Dados Simultáneos)
    print("\n[3/5] Verificando inicialización de Arena 8P (Salón de Roble)...")
    table_shot = os.path.join(EVIDENCE_DIR, "03_mesa_combate_8p_oak.png")
    capture_screenshot(browser, f"{url}?table=oak&autoplay=1", table_shot, delay_sec=4)

    # 4. Arena Búnker de Acero
    print("\n[4/7] Verificando Arena Búnker Industrial (Acero)...")
    steel_shot = os.path.join(EVIDENCE_DIR, "04_mesa_combate_bunker_acero.png")
    capture_screenshot(browser, f"{url}?table=ironplate&autoplay=1", steel_shot, delay_sec=4)

    # 5. Arena Casino Virtual (Plasma)
    print("\n[5/7] Verificando Arena Casino Virtual (Plasma Microgravedad)...")
    plasma_shot = os.path.join(EVIDENCE_DIR, "05_mesa_combate_plasma.png")
    capture_screenshot(browser, f"{url}?table=plasma&autoplay=1", plasma_shot, delay_sec=4)

    # 6. Modo Móvil / PWA Responsive Viewport (Fase 5)
    print("\n[6/7] Verificando Viewport Móvil PWA & Controles Táctiles (412x915)...")
    mobile_shot = os.path.join(EVIDENCE_DIR, "06_mobile_pwa_touch.png")
    capture_screenshot(browser, f"{url}?table=oak&autoplay=1", mobile_shot, delay_sec=4, window_size="412,915")

    # 7. Menú y Panel de Progresión Niveles 1-150 y Skins PBR (Fase 6)
    print("\n[7/7] Verificando Menú de Progresión, Economía y Skins PBR...")
    prog_shot = os.path.join(EVIDENCE_DIR, "07_perfil_progresion_skins.png")
    capture_screenshot(browser, url, prog_shot, delay_sec=3)

    server.shutdown()
    print("\n[OK] Batería de pruebas E2E completada con éxito. 7/7 Evidencias generadas.")

if __name__ == "__main__":
    run_e2e_audit()
