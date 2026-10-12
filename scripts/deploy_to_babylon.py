#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
deploy_to_babylon.py
1. Copia la nueva versión curada de Agalludo Royale a las carpetas locales de BABYLON_WEB y babylonias_web_deploy.
2. Empaqueta el zip descargable para Windows / Navegador en downloads/Agalludo-win.zip.
3. Se conecta por FTP al servidor de producción de babylonias.com (198.54.119.185) y sube todos los archivos a /public_html/agalludo/ y /public_html/downloads/.
"""

import os
import sys
import shutil
import zipfile
import ftplib

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

SRC_WEB = r"C:\Users\jegom\.gemini\antigravity\scratch\agalludo_royale\web"
DEST_BABYLON_WEB = r"C:\Users\jegom\BABYLON_WEB\agalludo"
DEST_BABYLONIAS_DEPLOY = r"C:\Users\jegom\.gemini\antigravity\scratch\babylonias_web_deploy\agalludo"
BABYLON_DOWNLOADS = r"C:\Users\jegom\BABYLON_WEB\downloads"
BABYLONIAS_DOWNLOADS = r"C:\Users\jegom\.gemini\antigravity\scratch\babylonias_web_deploy\downloads"

FTP_HOST = "198.54.119.185"
FTP_USER = "DOMINUSBABEL@babylonias.com"
FTP_PASS = "Juanesteban9910*"

def copy_local_targets():
    print("[1/3] Sincronizando archivos locales con BABYLON_WEB y babylonias_web_deploy...")
    for target in [DEST_BABYLON_WEB, DEST_BABYLONIAS_DEPLOY]:
        os.makedirs(target, exist_ok=True)
        # Copiar recursivamente
        for root, dirs, files in os.walk(SRC_WEB):
            rel_dir = os.path.relpath(root, SRC_WEB)
            dest_dir = os.path.join(target, rel_dir) if rel_dir != "." else target
            os.makedirs(dest_dir, exist_ok=True)
            for f in files:
                src_file = os.path.join(root, f)
                dst_file = os.path.join(dest_dir, f)
                shutil.copy2(src_file, dst_file)
        print(f"  [OK] Copiado exitoso a: {target}")

def create_download_zip():
    print("[2/3] Generando paquete descargable offline Agalludo-win.zip...")
    os.makedirs(BABYLON_DOWNLOADS, exist_ok=True)
    os.makedirs(BABYLONIAS_DOWNLOADS, exist_ok=True)

    zip_path_1 = os.path.join(BABYLON_DOWNLOADS, "Agalludo-win.zip")
    
    with zipfile.ZipFile(zip_path_1, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(SRC_WEB):
            for file in files:
                abs_path = os.path.join(root, file)
                rel_path = os.path.relpath(abs_path, SRC_WEB)
                zf.write(abs_path, os.path.join("AgalludoRoyale", rel_path))
        
        # Agregar launcher por lotes simple para Windows
        launcher_bat = "@echo off\r\nstart \"\" \"%~dp0index.html\"\r\n"
        zf.writestr(os.path.join("AgalludoRoyale", "JUGAR_AGALLUDO.bat"), launcher_bat)

    size_mb = os.path.getsize(zip_path_1) / (1024 * 1024)
    print(f"  [OK] Agalludo-win.zip creado ({size_mb:.2f} MB): {zip_path_1}")

    # Duplicar a babylonias_web_deploy
    zip_path_2 = os.path.join(BABYLONIAS_DOWNLOADS, "Agalludo-win.zip")
    shutil.copy2(zip_path_1, zip_path_2)
    print(f"  [OK] Copia sincronizada en: {zip_path_2}")

def ftp_mkdirs(ftp, path):
    parts = path.strip("/").split("/")
    cur = ""
    for p in parts:
        cur += "/" + p
        try:
            ftp.cwd(cur)
        except Exception:
            try:
                ftp.mkd(cur)
                ftp.cwd(cur)
            except Exception as e:
                pass

def upload_ftp():
    print(f"[3/3] Desplegando por FTP a servidor de producción: {FTP_HOST}...")
    try:
        ftp = ftplib.FTP(FTP_HOST, timeout=30)
        ftp.login(FTP_USER, FTP_PASS)
        print("  [OK] Autenticado en cPanel FTP.")

        # Subir carpeta agalludo
        remote_base = "/public_html/agalludo"
        ftp_mkdirs(ftp, remote_base)

        for root, dirs, files in os.walk(SRC_WEB):
            rel_dir = os.path.relpath(root, SRC_WEB).replace("\\", "/")
            cur_remote_dir = remote_base if rel_dir == "." else f"{remote_base}/{rel_dir}"
            ftp_mkdirs(ftp, cur_remote_dir)
            ftp.cwd(cur_remote_dir)

            for f in files:
                local_path = os.path.join(root, f)
                with open(local_path, "rb") as fp:
                    ftp.storbinary(f"STOR {f}", fp)
                print(f"    -> Subido a {cur_remote_dir}: {f} ({os.path.getsize(local_path)} bytes)")

        # Subir el zip descargable
        print("  -> Subiendo Agalludo-win.zip a /public_html/downloads/...")
        ftp_mkdirs(ftp, "/public_html/downloads")
        ftp.cwd("/public_html/downloads")
        local_zip = os.path.join(BABYLON_DOWNLOADS, "Agalludo-win.zip")
        with open(local_zip, "rb") as fp:
            ftp.storbinary("STOR Agalludo-win.zip", fp)
        print("    -> Agalludo-win.zip subido exitosamente a /public_html/downloads/.")

        ftp.quit()
        print("  [OK] ¡Despliegue a BABYLON.IA completado al 100%!")
        return True
    except Exception as e:
        print(f"  [!] Advertencia en FTP: {e}")
        return False

def main():
    copy_local_targets()
    create_download_zip()
    upload_ftp()
    print("\n[FIN] Todo el despliegue a BABYLON.IA fue ejecutado.")

if __name__ == "__main__":
    main()
