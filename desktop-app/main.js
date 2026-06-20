const { app, BrowserWindow, Menu } = require('electron');
const path = require('path');

function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1024,
    minHeight: 768,
    backgroundColor: '#0F0B09',
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    },
    icon: path.join(__dirname, 'assets/icon.png'),
    title: "AGALLUDO ROYALE - BATTLE ROYALE 3D"
  });

  // Cargar index.html
  mainWindow.loadFile('index.html');

  // Eliminar barra de menú estándar para mayor inmersión
  Menu.setApplicationMenu(null);

  // Redirigir logs del renderizador a la consola del proceso principal
  mainWindow.webContents.on('console-message', (event, level, message, line, sourceId) => {
    console.log(`[RENDERER LOG] (${line}) ${message}`);
  });


  // Abrir herramientas de desarrollo si es necesario durante pruebas
  // mainWindow.webContents.openDevTools();
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', function () {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', function () {
  if (process.platform !== 'darwin') app.quit();
});
