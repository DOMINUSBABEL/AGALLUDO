#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""
sync_to_github_repo.py
Sincroniza la versión curada de Agalludo Royale (Web 8P, Docs, GDD, Evidencias, Scripts)
al repositorio Git formal de GitHub: C:\Users\jegom\AGALLUDO
"""

import os
import shutil

ROOT_SRC = r"C:\Users\jegom\.gemini\antigravity\scratch\agalludo_royale"
DEST_REPO = r"C:\Users\jegom\AGALLUDO"

def copy_tree(src, dst):
    os.makedirs(dst, exist_ok=True)
    for root, dirs, files in os.walk(src):
        rel_dir = os.path.relpath(root, src)
        dest_dir = os.path.join(dst, rel_dir) if rel_dir != "." else dst
        os.makedirs(dest_dir, exist_ok=True)
        for f in files:
            shutil.copy2(os.path.join(root, f), os.path.join(dest_dir, f))

def sync():
    print("[1/4] Copiando web/ a AGALLUDO/web/...")
    copy_tree(os.path.join(ROOT_SRC, "web"), os.path.join(DEST_REPO, "web"))

    print("[2/4] Sincronizando desktop-app/ con motor Web 8P...")
    # Copiar index, renderer, style y libs a desktop-app
    for f in ["index.html", "renderer.js", "style.css", "manifest.json", "sw.js"]:
        src_f = os.path.join(ROOT_SRC, "web", f)
        if os.path.exists(src_f):
            shutil.copy2(src_f, os.path.join(DEST_REPO, "desktop-app", f))
    copy_tree(os.path.join(ROOT_SRC, "web", "libs"), os.path.join(DEST_REPO, "desktop-app", "libs"))
    copy_tree(os.path.join(ROOT_SRC, "web", "icons"), os.path.join(DEST_REPO, "desktop-app", "icons"))

    print("[3/4] Copiando docs/ (GDD, Auditoría, Roadmap y Evidencias)...")
    copy_tree(os.path.join(ROOT_SRC, "docs"), os.path.join(DEST_REPO, "docs"))

    print("[4/4] Copiando scripts/ de automatización y E2E...")
    copy_tree(os.path.join(ROOT_SRC, "scripts"), os.path.join(DEST_REPO, "scripts"))

    print("[OK] Sincronización completada hacia:", DEST_REPO)

if __name__ == "__main__":
    sync()
