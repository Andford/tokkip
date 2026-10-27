# Bienestar — tareas, Pomodoro, respiración y Google

La pill **Bienestar** vive en la isla, junto a Tokkip, con cuatro pestañas:

| Pestaña | Qué hace |
|---|---|
| ✓ **Tareas** | Lista de pendientes. Sin Google se guarda en esta PC; con Google, es tu lista principal de **Google Tasks** (la misma que ves en Gmail, Calendar y el celular). |
| ⏱ **Enfoque** | Pomodoro 25/5, descanso largo de 15 cada 4 bloques. Tokkip se pone lentes mientras te enfocas y la isla se abre cuando termina cada fase. |
| ○ **Respirar** | Sesiones de 1, 5 o 10 min (4 s inhalar, 6 s exhalar). Tokkip cierra los ojos y se infla con cada inhalación. |
| 📅 **Agenda** | Tus eventos de hoy y mañana de **Google Calendar** (todos los calendarios que tienes visibles). Tokkip te avisa **10 minutos antes** de cada evento, y la pill muestra "En 12 min". Botón **Unirse** para Meet. |

## Conectar Google Calendar y Google Tasks (una sola vez)

Tokkip usa tu propio proyecto de Google Cloud: nadie más ve tus datos, y el token
se guarda en el Administrador de credenciales de Windows.

1. Entra a <https://console.cloud.google.com/> y crea un proyecto (por ejemplo "Tokkip").
2. **APIs y servicios → Biblioteca**: habilita **Google Calendar API** y **Google Tasks API**.
3. **APIs y servicios → Pantalla de consentimiento de OAuth** (Google Auth Platform):
   - Tipo de usuario: **Externo**. Nombre de la app: "Tokkip". Tu correo como soporte y contacto.
   - En **Público / Audience**, pulsa **Publicar app** para que quede *En producción*.
     Si la dejas *En prueba*, Google desconecta la sesión cada 7 días.
     Como no está verificada, al conectar verás "Google no verificó esta app":
     **Configuración avanzada → Ir a Tokkip (no seguro)**. Es tu propia app, es normal.
4. **APIs y servicios → Credenciales → Crear credenciales → ID de cliente de OAuth**:
   tipo **App de escritorio**. Copia el **ID de cliente** y el **Secreto del cliente**.
5. En Tokkip: **Settings → Integrations → Bienestar**, pega el ID y el secreto
   (**Guardar** en cada uno) y pulsa **Conectar Google**. Se abre el navegador, eliges tu
   cuenta, aceptas, y listo.

Permisos que pide: ver tus calendarios (solo lectura), administrar tus tareas y ver tu correo
(para mostrar con qué cuenta estás conectado). **Desconectar** revoca el acceso.

Al conectar por primera vez, las tareas pendientes que tenías en la PC se suben a Google Tasks.
