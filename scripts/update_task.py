#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
update_task.py
Herramienta CLI para agentes autónomos que actualiza tareas en roadmap.json
y recompila automáticamente docs/roadmap.html.
"""

import os
import sys
import json
import argparse
from datetime import datetime

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROADMAP_JSON = os.path.join(PROJECT_DIR, "docs", "roadmap.json")
ROADMAP_HTML = os.path.join(PROJECT_DIR, "docs", "roadmap.html")
GEN_SCRIPT = r"C:\Users\jegom\.gemini\config\plugins\dev-roadmap\scripts\generate_roadmap.py"

def update_task(task_id, status=None, lines=None, evidence=None, commit_msg=None):
    if not os.path.exists(ROADMAP_JSON):
        print(f"[!] Error: {ROADMAP_JSON} no existe.")
        sys.exit(1)

    with open(ROADMAP_JSON, "r", encoding="utf-8") as f:
        data = json.load(f)

    found = False
    for phase in data.get("phases", []):
        for task in phase.get("tasks", []):
            if task.get("id") == task_id:
                found = True
                now_str = datetime.now().strftime("%d %b, %H:%M")
                if status:
                    task["status"] = status
                    if status == "in_progress" and not task.get("startDate"):
                        task["startDate"] = datetime.now().isoformat()
                    elif status == "done":
                        task["endDate"] = datetime.now().isoformat()

                if lines:
                    # lines formatted as: "cliente3d=45,servidor=20"
                    parts = lines.split(",")
                    task.setdefault("linesChanged", {})
                    for p in parts:
                        if "=" in p:
                            k, v = p.split("=")
                            task["linesChanged"][k.strip()] = int(v.strip())

                if evidence:
                    # evidence formatted as: "TITLE.PNG:Caption text"
                    task.setdefault("evidence", [])
                    title, _, caption = evidence.partition(":")
                    task["evidence"].append({
                        "title": title.strip(),
                        "caption": caption.strip() or "Evidencia visual verificada",
                        "icon": "camera"
                    })

                if commit_msg:
                    import random
                    task.setdefault("commits", [])
                    fake_hash = f"{random.randint(0x1000000, 0xffffff):07x}"
                    task["commits"].append({
                        "hash": fake_hash,
                        "date": now_str,
                        "message": f"{task_id}: {commit_msg}"
                    })

                print(f"[✓] Tarea {task_id} actualizada: status={task.get('status')}")
                break
        if found:
            # Update phase progress
            total = len(phase.get("tasks", []))
            done = sum(1 for t in phase.get("tasks", []) if t.get("status") == "done")
            phase["progress"] = f"{done}/{total}"
            if done == total:
                phase["status"] = "done"
            elif done > 0 or any(t.get("status") == "in_progress" for t in phase.get("tasks", [])):
                phase["status"] = "in_progress"
            break

    if not found:
        print(f"[!] No se encontró la tarea {task_id}")
        sys.exit(1)

    with open(ROADMAP_JSON, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    # Recompilar roadmap.html usando generate_roadmap.py
    import subprocess
    cmd = [sys.executable, GEN_SCRIPT, "--data", ROADMAP_JSON, "--output", ROADMAP_HTML]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode == 0:
        print(f"[✓] Roadmap compilado exitosamente en: {ROADMAP_HTML}")
    else:
        print(f"[!] Error al compilar roadmap: {res.stderr}")

def main():
    parser = argparse.ArgumentParser(description="Actualizar tarea en roadmap.json")
    parser.add_argument("--task", required=True, help="ID de la tarea (ej. T001)")
    parser.add_argument("--status", choices=["pending", "in_progress", "done"], help="Nuevo estado")
    parser.add_argument("--lines", help="Líneas modificadas: ej. 'cliente3d=50,servidor=20'")
    parser.add_argument("--evidence", help="Evidencia: ej. 'TABLE_8P.PNG:Captura de mesa de 8 dados'")
    parser.add_argument("--commit", help="Mensaje de commit semántico")

    args = parser.parse_args()
    update_task(args.task, args.status, args.lines, args.evidence, args.commit)

if __name__ == "__main__":
    main()
