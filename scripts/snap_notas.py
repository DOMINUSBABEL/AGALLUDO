import subprocess, os

template_file = r'C:\Users\jegom\.gemini\antigravity\scratch\agalludo_royale\docs\roadmap.html'
with open(template_file, 'r', encoding='utf-8') as f:
    html = f.read()

# Switch active tab to notas after renderApp
html = html.replace("renderApp();", "renderApp(); switchNavTab('notas');")

temp_html = r'C:\Users\jegom\.gemini\antigravity\scratch\agalludo_royale\docs\temp_notas.html'
with open(temp_html, 'w', encoding='utf-8') as f:
    f.write(html)

chrome_path = r'C:\Program Files\Google\Chrome\Application\chrome.exe'
out_png = r'C:\Users\jegom\.gemini\antigravity\scratch\agalludo_royale\docs\gdd\evidencia\roadmap_gdd_tab.png'

cmd = [chrome_path, '--headless=new', '--disable-gpu', '--window-size=1600,900', f'--screenshot={out_png}', f'file:///{temp_html}']
subprocess.run(cmd, timeout=15)
try:
    os.remove(temp_html)
except Exception:
    pass
print('GDD tab screenshot captured successfully.')
