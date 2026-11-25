# Tokkip Fit: plan

Ejercicio con Tokkip acompañándote: muy dinámico, muy estético, con
animaciones por todos lados. Es su propia app (`mx.loperstack.tokkipfit`), con
su app de reloj, en este repo junto a Tokkip Notas: `android/fit` para el
teléfono y `android/fitwear` para el Pixel Watch. Tokkip viene de
`android/shared`, el mismo de todas las apps. Español primero (mexicano, de tú).

## Principios

- **Health Connect es el puente.** Samsung Health, Fitbit y el Pixel Watch ya
  escriben ahí pasos, entrenamientos, pulso y sueño. Fit los lee y escribe sus
  propios entrenamientos de vuelta, para que también salgan en Samsung Health y
  en Fitbit. Nada de Google Fit (sus APIs van de salida) ni del SDK de Samsung
  (pide ser socio aprobado, y Health Connect ya lo cubre).
- **Nada sale del teléfono.** Sin nube nuestra ni telemetría: los datos viven
  en el teléfono y en Health Connect.
- **Tokkip es el coach.** Reacciona a cómo va tu día, entrena contigo, se
  cansa cuando tú te cansas y celebra cada meta. Todo número se mueve: los
  anillos se llenan, los contadores ruedan, las tarjetas entran con resorte.
- **El reloj desde el inicio.** Cualquier entrenamiento se puede llevar desde
  la muñeca (pulso en vivo, serie actual, descanso) y llega al teléfono y a
  Health Connect al terminar.

## Secciones

1. **Hoy.** Pasos, minutos activos, calorías, distancia, sueño y pulso en
   reposo de Health Connect. Anillos animados, la semana, tu racha y el reto
   del día, con Tokkip mostrando cómo vas.
2. **Gym.** Rutinas, series, repeticiones, peso y descansos. Tokkip cuenta
   contigo, guarda el historial de cada ejercicio y celebra tus récords.
3. **Cardio.** Correr, caminar y bici con GPS: ruta en mapa, ritmo, pulso en
   vivo desde el reloj, aviso por kilómetro y resumen con la ruta dibujada.
4. **En casa.** Rutinas HIIT guiadas con temporizador, con Tokkip haciendo el
   ejercicio (sentadillas, saltos, plancha).
5. **Respira.** Respiración y meditación guiada. Tokkip respira contigo, y
   cada sesión se guarda como mindfulness en Health Connect.

## Fases

- [x] **0.1: Hoy y la base.** El módulo, el tema (Material 3 Expressive,
  Google Sans, colores de Material You), la navegación de cinco secciones, Hoy
  con Health Connect (solo lectura), Tokkip reaccionando al día, la racha, el
  reto del día y las metas. Datos de ejemplo mientras no conectes Health
  Connect, para que nunca se vea vacía.
- [x] **0.2: En casa, el reloj y tu Tokkip.** Rutinas guiadas con Tokkip
  haciendo los ejercicios (movimientos nuevos en `android/shared`,
  `character/Exercise.kt`: sentadilla, saltos de tijera, plancha, rodillas
  arriba, golpes, estiramiento y descanso; las rutinas en `fit/Routines.kt`,
  compartidas con el reloj). `android/fitwear`: entrenar desde la muñeca con el
  pulso en vivo del sensor; el entrenamiento llega al teléfono y se escribe en
  Health Connect. Tokkip personalizable como en la app de Tokkip (especie,
  piel, colores, accesorios), o igual que en ella (`character/Look.kt`, que la
  app de Tokkip comparte solo con apps firmadas con su llave).
- [x] **0.3: Gym.** Cuatro planes y sesión libre, 16 ejercicios
  (`fit/Gym.kt`), series con peso y repeticiones (el último peso ya puesto),
  descanso con su anillo, récords (el más pesado y el máximo estimado) con
  fiesta de Tokkip, que hace el movimiento nuevo LIFT. En el reloj: el
  descanso y el conteo de series. Se escribe como entrenamiento de fuerza.
- [x] **0.4: Cardio con GPS.** Correr, caminar y bici (`fit/Cardio.kt`): en el
  teléfono con un servicio en primer plano (sigue con la pantalla apagada), la
  ruta dibujándose, ritmo y aviso por kilómetro; en el reloj con su propio GPS
  y el pulso. La ruta y la distancia se escriben en Health Connect. La ruta se
  dibuja sola, sin mapa de fondo (un mapa pediría un servicio de mapas).
- [x] **0.5: Respira, retos, logros y widgets.** Respiración con los ritmos de
  Tokkip (también en el reloj), guardada como respiración guiada; el reto de
  la semana y 15 logros (`fit/Achievements.kt`). Widgets del teléfono (Tus
  anillos, Entrena), tarjeta del reloj con los anillos y complicación de
  pasos.

- [x] **0.6: Lo tuyo y Tokkip.** Rutinas propias en En casa (movimientos,
  segundos, descanso y rondas; también en el reloj, por `/tokkipfit/mine`) y
  planes propios en el Gym (`fit/Custom.kt`). Cada entrenamiento se le cuenta a
  la app de Tokkip (`FitProvider`, solo apps con la misma firma): lo festeja con
  una notificación y le da cariño (Ejercicio, +3, hasta 6 al día). Respira se
  guarda como mindfulness en Health Connect donde lo tiene (connect-client
  1.1.0, compileSdk 36, AGP 8.10).

- [x] **0.7: El Gym en el reloj y el mapa.** Los planes (de Fit y tuyos) en el
  reloj: cada ejercicio con tu último peso y repeticiones (el teléfono se los
  manda por `/tokkipfit/mine`), - y +, "Hecha" y el descanso; al terminar, las
  series vuelven al teléfono y cuentan para tus récords. En Cardio, un mapa de
  OpenStreetMap bajo la ruta, solo si lo prendes en Ajustes (`data/Tiles.kt`:
  es lo único para lo que Fit usa internet; las teselas se guardan 30 días y
  se borran al apagarlo).

## Lo que sigue

- Leer las sesiones de mindfulness de otras apps para el anillo de Respira.

## Permisos de Health Connect

| Fase | Lee | Escribe |
|---|---|---|
| 0.1 | Pasos, distancia, calorías activas, ejercicio, sueño, pulso, pulso en reposo | — |
| 0.2 | | Ejercicio, pulso, calorías activas |
| 0.4 | | Ruta del ejercicio, distancia |
| 0.5 | Salud en segundo plano (opcional, para los widgets) | |
| 0.6 | | Mindfulness (donde Health Connect lo tiene) |

Health Connect pide una pantalla que diga para qué quiere la app cada permiso:
es la pantalla de privacidad de Fit, y su texto también está en
`android/fit/PRIVACY.md`.
