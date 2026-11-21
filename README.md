# PPU - Weather App (`PPU_WT`) — Etapa 3: Integración con Firebase 🌦️

Aplicación móvil Android desarrollada en Kotlin para consultar el clima en tiempo real (enfocada inicialmente en la región de Iquique), interactuar con Google Maps y gestionar ubicaciones favoritas sincronizadas en la nube mediante **Firebase Authentication** y **Cloud Firestore**.

## 👥 Integrantes
* Diego Sereño  
* Cristobal Lancellotti

## Funcionalidades Principales

* **Autenticación Remota (`Firebase Authentication`):** Registro e inicio de sesión con correo y contraseña, persistencia automática de sesión al cerrar y reabrir la aplicación, y **Modo Invitado** con bloqueo controlado de funciones de escritura y consulta de favoritos.
* **CRUD Remoto en Tiempo Real (`Cloud Firestore`):**
    * **Create:** Guardado de ubicaciones seleccionadas en el mapa en `usuarios/{uid}/favoritos/{docId}`, validando campos obligatorios, rangos de coordenadas y prevención de duplicados contra el servidor.
    * **Read:** Sincronización en tiempo real (`addSnapshotListener`) liberada correctamente en el ciclo de vida (`onStart`/`onStop`) para evitar datos desactualizados.
    * **Update:** Renombrado de favoritos con validación de duplicados y formato.
    * **Delete:** Eliminación remota protegida con diálogo de confirmación (`AlertDialog`).
* **Mapa Interactivo y Clima en Tiempo Real:** Geolocalización GPS (`FusedLocationProviderClient`), búsqueda de direcciones (`Geocoder`), pines manuales, consulta meteorológica a **Open-Meteo** (Retrofit + Gson) con alerta visual de aptitud para el baño, navegación directa desde favoritos al mapa y opción de compartir ubicación.
* **Validaciones y Resiliencia ante Cortes de Red:** Verificación previa de conectividad (`NetworkUtils` con `ConnectivityManager`) y manejo de excepciones de Firebase para informar errores al usuario sin cierres inesperados.

## Arquitectura por Capas

El proyecto separa la interfaz de usuario, la lógica de negocio y el acceso a datos remotos en paquetes dedicados:

* `ui/` — Pantallas y adaptadores (`MainActivity`, `HomeActivity`, `FavoritosActivity`). No acceden directamente a `FirebaseFirestore` ni `FirebaseAuth`.
* `data/` — Repositorios de acceso remoto (`AuthRepository`, `FavoritosFirebaseRepository`).
* `model/` — Modelos de datos (`LugarFavorito`, `ClimaResponse`).
* `api/` — Servicio Retrofit para la API de Open-Meteo (`WeatherApiService`).
* `utils/` — Utilidades transversales (`NetworkUtils`).

## Seguridad y Reglas en Firebase

* No se almacenan credenciales, contraseñas ni API Keys en el código fuente.
* **Cloud Firestore Rules:** Reglas activas que restringen lectura, escritura y eliminación exclusivamente al usuario autenticado propietario (`request.auth.uid == userId`), además de validar en servidor tipos de datos, longitud de nombre (1–80 caracteres) y rangos geográficos de latitud/longitud.

## Configuración y Ejecución 

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/DiegoSL8/PPU_WT_FINAL.git
