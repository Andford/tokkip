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
- [ ] **0.2: En casa y el reloj.** Rutinas guiadas con Tokkip haciendo los
  ejercicios (movimientos nuevos en `android/shared`: sentadilla, salto,
  plancha, respirar). `android/fitwear`: entrenar desde la muñeca con Health
  Services (pulso en vivo). Los entrenamientos se escriben en Health Connect.
- [ ] **0.3: Gym.** Rutinas, series, récords, historial por ejercicio. En el
  reloj: la serie actual y el descanso.
- [ ] **0.4: Cardio con GPS.** Ruta, ritmo, pulso y kilómetros, en teléfono y
  reloj. La ruta se guarda en Health Connect.
- [ ] **0.5: Respira, retos y logros.** Respiración y meditación guiada
  (mindfulness en Health Connect), retos semanales, logros, tarjetas del reloj
  y aviso a Tokkip cuando terminas algo.

## Permisos de Health Connect

| Fase | Lee | Escribe |
|---|---|---|
| 0.1 | Pasos, distancia, calorías activas, ejercicio, sueño, pulso, pulso en reposo | — |
| 0.2 | | Ejercicio, pulso, calorías activas |
| 0.4 | Ruta del ejercicio | Ruta del ejercicio, distancia |
| 0.5 | Mindfulness | Mindfulness |

Health Connect pide una pantalla que diga para qué quiere la app cada permiso:
es la pantalla de privacidad de Fit, y su texto también está en
`android/fit/PRIVACY.md`.
