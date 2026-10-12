#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate_pwa_icons.py
Renders icons/icon-192.png and icons/icon-512.png from icons/icon.svg using Chrome headless.
"""

import subprocess
import os

def render_icons():
    web_dir = r"C:\Users\jegom\.gemini\antigravity\scratch\agalludo_royale\web"
    icons_dir = os.path.join(web_dir, "icons")
    os.makedirs(icons_dir, exist_ok=True)
    svg_path = os.path.join(icons_dir, "icon.svg")

    chrome_path = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
    if not os.path.exists(chrome_path):
        print("[!] Chrome not found")
        return

    # Temporary HTML wrapper
    temp_html = os.path.join(icons_dir, "temp_icon.html")
    with open(temp_html, "w", encoding="utf-8") as f:
        f.write(f'''<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body, html {{ margin:0; padding:0; width:100%; height:100%; overflow:hidden; background:transparent; display:flex; align-items:center; justify-content:center; }}
  img {{ width:100%; height:100%; object-fit:contain; }}
</style>
</head>
<body>
<img src="icon.svg" />
</body>
</html>''')

    for size in [192, 512]:
        out_png = os.path.join(icons_dir, f"icon-{size}.png")
        cmd = [
            chrome_path,
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            f"--window-size={size},{size}",
            f"--screenshot={out_png}",
            f"file:///{temp_html}"
        ]
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        if os.path.exists(out_png) and os.path.getsize(out_png) > 1000:
            print(f"[OK] Generated icon-{size}.png ({os.path.getsize(out_png)} bytes)")
        else:
            print(f"[!] Error generating icon-{size}.png: {res.stderr}")

    try:
        os.remove(temp_html)
    except Exception:
        pass

if __name__ == "__main__":
    render_icons()
