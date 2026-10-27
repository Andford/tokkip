# Tokkip para Windows

Tokkip vive arriba de tu pantalla, como una isla. Es la versión de PC de la app
del Pixel: el mismo personaje (conejito, gatito, perrito, robotito o mapache),
con tus colores.

## Qué hace

- **Tu día**: tareas (y Google Tasks), tu agenda de Google Calendar con avisos,
  Pomodoro y respiración guiada.
- **Portapapeles inteligente**: guarda lo que copias (texto, imágenes y archivos)
  y lo que sueltas en la isla. Clic para copiarlo otra vez, arrástralo para
  soltarlo en otra app, fija lo que quieras conservar. Lo que los gestores de
  contraseñas marcan como privado nunca se guarda.
- **Chat con IA** en globitos: Claude, Gemini, OpenAI o un modelo local (Ollama,
  LM Studio). Puedes preguntarle por cualquier cosa del portapapeles.
- **Música** (Spotify y lo que suene en Windows) y **GitHub**.
- **Tokkip en el escritorio**: arrástralo fuera de la isla y se queda paseando.

## Atajos

| Atajo | Qué hace |
|---|---|
| `Ctrl+Alt+V` | Abre el portapapeles |
| `Ctrl+Alt+Espacio` | Abre el chat |
| `Ctrl+Alt+→` / `←` | Siguiente / anterior pastilla |
| `Ctrl+Alt+S` | Silencia a Tokkip |
| `Ctrl+Alt+D` | Tokkip al escritorio, o de vuelta |
| `Ctrl+Alt+G` | El guardarropa |

## Compilar

```
npm install
npm run tauri dev      # para probar
npm run pack           # instalador en release/
```

El CI (`.github/workflows/tokkip-pc.yml`) compila el instalador en cada push a
la rama `android` y lo deja como artefacto unos días.

## Créditos

Tokkip parte del código abierto (MIT) de [Coucou](https://github.com/Louis-CFM/coucou),
de Louis Raillé; su aviso de licencia está en `LICENSE`. Tokkip tiene su propio
nombre, personaje y sonidos.

Todo se queda en tu PC: sin telemetría, y solo se conecta a los servicios que tú
configures.
