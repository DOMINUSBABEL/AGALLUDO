#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
run_server.py
Servidor local HTTP multi-hilo optimizado para Agalludo Royale Web (Three.js / WebGL / Web Audio).
Implementa cabeceras no-cache y tipos MIME explícitos.
"""

import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

PORT = 8092
DIRECTORY = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "web")

class ResilientHTTPHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

    def guess_type(self, path):
        ctype = super().guess_type(path)
        if path.endswith(".ogg"):
            return "audio/ogg"
        if path.endswith(".wav"):
            return "audio/wav"
        if path.endswith(".mp3"):
            return "audio/mpeg"
        if path.endswith(".json"):
            return "application/json"
        return ctype

def run():
    print(f"[*] Iniciando servidor local en http://localhost:{PORT}")
    print(f"[*] Directorio raíz web: {DIRECTORY}")
    server = ThreadingHTTPServer(("127.0.0.1", PORT), ResilientHTTPHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[*] Servidor detenido por el usuario.")
        server.server_close()

if __name__ == "__main__":
    run()
