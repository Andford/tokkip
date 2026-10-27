# Google Tasks y Google Calendar en Tokkip para Android

## Calendar: no hay que configurar nada

Los eventos que creas en Tokkip ("+ Evento" en Inicio) se guardan en el calendario
de Google que ya tiene tu teléfono. La app de Google Calendar los sube sola, así
que aparecen en la web, en la PC y en todos lados. Tokkip solo pide permiso para
leer y escribir el calendario.

## Tasks: un cliente OAuth "Android" (una sola vez, 3 minutos)

Tokkip usa el inicio de sesión de Google Play services: no guarda contraseñas ni
secretos. Google solo pide saber que la app es tuya: paquete + huella SHA-1 de la
firma. Usa el **mismo proyecto de Google Cloud** que configuraste para la PC
(ahí ya están habilitadas la API de Google Tasks y tu pantalla de consentimiento).

1. Abre https://console.cloud.google.com/apis/credentials con ese proyecto.
2. **Crear credenciales → ID de cliente de OAuth**.
3. Tipo de aplicación: **Android**.
4. Nombre: `Tokkip Android`.
5. Nombre del paquete: `mx.loperstack.tokkip`
6. Huella SHA-1 del certificado (la de `tokkip.jks`, la clave de tus builds):

   ```
   38:9F:C2:A5:F9:1F:D1:7B:A8:FD:CE:72:42:2F:78:D0:A8:E6:58:9C
   ```

   Para comprobarla tú mismo:
   `keytool -list -v -keystore tokkip.jks -alias tokkip` (pide la contraseña de `contrasena.txt`).
7. **Crear**. No hay que copiar nada a la app.
8. Si tu pantalla de consentimiento está en modo **Prueba**, revisa que tu correo
   esté en **Usuarios de prueba** (ya debería, por la PC).

Luego, en Tokkip: engrane de Inicio → **Google Tasks → Conectar** → elige tu cuenta
→ Permitir. Tus tareas locales suben a Google y desde ahí la lista es la misma
en el teléfono, el reloj, los widgets y la PC (la lista principal, `@default`).

### Si dice "Falta el cliente OAuth de Android"

Es el error 10 de Google: el paquete o la SHA-1 no coinciden. Revisa que el APK
venga de una build firmada con `tokkip.jks` (los secretos del repo) y que la
SHA-1 sea exactamente la de arriba. Los cambios en Google Cloud pueden tardar
unos minutos.
