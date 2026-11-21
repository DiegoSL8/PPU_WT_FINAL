package com.example.ppu_wt.data

import android.content.Context
import com.example.ppu_wt.model.LugarFavorito
import com.example.ppu_wt.utils.NetworkUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlin.math.abs

/**
 * Capa de acceso a datos remota para gestionar lugares favoritos en Cloud Firestore.
 * Ruta en Firebase: usuarios/{uid}/favoritos/{documentoId}
 */
class FavoritosFirebaseRepository(private val context: Context) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val COLECCION_USUARIOS = "usuarios"
        private const val SUBCOLECCION_FAVORITOS = "favoritos"
        private const val MAX_LARGO_NOMBRE = 80
        private const val TOLERANCIA_COORDENADAS = 0.0001
    }

    private fun coleccionFavoritos(uid: String) =
        db.collection(COLECCION_USUARIOS)
            .document(uid)
            .collection(SUBCOLECCION_FAVORITOS)

    /**
     * CREATE: Valida conectividad, formato, rangos y duplicados antes de registrar en Firestore.
     */
    fun guardarFavorito(
        uid: String,
        nombre: String,
        latitud: Double,
        longitud: Double,
        onExito: (idDocumento: String) -> Unit,
        onError: (mensaje: String) -> Unit
    ) {
        if (uid.isBlank()) {
            onError("Debes iniciar sesión para guardar lugares favoritos.")
            return
        }

        val errorValidacion = validarDatosFavorito(nombre, latitud, longitud)
        if (errorValidacion != null) {
            onError(errorValidacion)
            return
        }

        if (!NetworkUtils.hayConexion(context)) {
            onError("Sin conexión a Internet. No es posible guardar el lugar ahora.")
            return
        }

        val nombreLimpio = nombre.trim()

        // Consultamos al servidor para prevenir duplicados por nombre o coordenadas exactas
        coleccionFavoritos(uid)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val existeDuplicado = snapshot.documents.any { doc ->
                    val nombreExistente = doc.getString("nombre")?.trim() ?: ""
                    val latExistente = doc.getDouble("latitud")
                    val lonExistente = doc.getDouble("longitud")

                    val mismoNombre = nombreExistente.equals(nombreLimpio, ignoreCase = true)
                    val mismasCoordenadas = latExistente != null && lonExistente != null &&
                            abs(latExistente - latitud) < TOLERANCIA_COORDENADAS &&
                            abs(lonExistente - longitud) < TOLERANCIA_COORDENADAS

                    mismoNombre || mismasCoordenadas
                }

                if (existeDuplicado) {
                    onError("Ya tienes guardado un favorito con ese nombre o en esa misma ubicación.")
                    return@addOnSuccessListener
                }

                val datos = hashMapOf(
                    "nombre" to nombreLimpio,
                    "latitud" to latitud,
                    "longitud" to longitud
                )

                coleccionFavoritos(uid)
                    .add(datos)
                    .addOnSuccessListener { referencia ->
                        onExito(referencia.id)
                    }
                    .addOnFailureListener { excepcion ->
                        onError(traducirErrorFirestore(excepcion))
                    }
            }
            .addOnFailureListener { excepcion ->
                onError(traducirErrorFirestore(excepcion))
            }
    }

    /**
     * READ (Tiempo real): Escucha los cambios de la subcolección de favoritos del usuario.
     * Devuelve un ListenerRegistration que la Activity debe liberar en onStop/onDestroy.
     */
    fun escucharFavoritos(
        uid: String,
        onActualizacion: (List<LugarFavorito>) -> Unit,
        onError: (mensaje: String) -> Unit
    ): ListenerRegistration? {
        if (uid.isBlank()) {
            onError("Sesión no válida para consultar favoritos.")
            return null
        }

        if (!NetworkUtils.hayConexion(context)) {
            onError("Sin conexión a Internet. Conéctate para cargar tus favoritos desde Firebase.")
            return null
        }

        return coleccionFavoritos(uid)
            .orderBy("nombre", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, excepcion ->
                if (excepcion != null) {
                    onError(traducirErrorFirestore(excepcion))
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onActualizacion(emptyList())
                    return@addSnapshotListener
                }

                val lista = snapshot.documents.mapNotNull { doc ->
                    val nombre = doc.getString("nombre") ?: return@mapNotNull null
                    val latitud = doc.getDouble("latitud") ?: return@mapNotNull null
                    val longitud = doc.getDouble("longitud") ?: return@mapNotNull null
                    LugarFavorito(
                        id = doc.id,
                        nombre = nombre,
                        latitud = latitud,
                        longitud = longitud
                    )
                }

                onActualizacion(lista)
            }
    }

    /**
     * UPDATE: Renombra un favorito verificando que no exista otro con el mismo nombre.
     */
    fun actualizarNombreFavorito(
        uid: String,
        favoritoId: String,
        nuevoNombre: String,
        onExito: () -> Unit,
        onError: (mensaje: String) -> Unit
    ) {
        if (uid.isBlank() || favoritoId.isBlank()) {
            onError("No se pudo identificar el registro a actualizar.")
            return
        }

        val nombreLimpio = nuevoNombre.trim()
        if (nombreLimpio.isEmpty()) {
            onError("El nombre del lugar no puede estar vacío.")
            return
        }
        if (nombreLimpio.length > MAX_LARGO_NOMBRE) {
            onError("El nombre no puede superar los $MAX_LARGO_NOMBRE caracteres.")
            return
        }

        if (!NetworkUtils.hayConexion(context)) {
            onError("Sin conexión a Internet. No es posible actualizar el lugar ahora.")
            return
        }

        coleccionFavoritos(uid)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val nombreDuplicado = snapshot.documents.any { doc ->
                    doc.id != favoritoId &&
                            (doc.getString("nombre")?.trim() ?: "").equals(nombreLimpio, ignoreCase = true)
                }

                if (nombreDuplicado) {
                    onError("Ya existe otro favorito con ese nombre.")
                    return@addOnSuccessListener
                }

                coleccionFavoritos(uid)
                    .document(favoritoId)
                    .update("nombre", nombreLimpio)
                    .addOnSuccessListener { onExito() }
                    .addOnFailureListener { excepcion ->
                        onError(traducirErrorFirestore(excepcion))
                    }
            }
            .addOnFailureListener { excepcion ->
                onError(traducirErrorFirestore(excepcion))
            }
    }

    /**
     * DELETE: Elimina un lugar favorito por su ID de documento en Firestore.
     */
    fun eliminarFavorito(
        uid: String,
        favoritoId: String,
        onExito: () -> Unit,
        onError: (mensaje: String) -> Unit
    ) {
        if (uid.isBlank() || favoritoId.isBlank()) {
            onError("No se pudo identificar el registro a eliminar.")
            return
        }

        if (!NetworkUtils.hayConexion(context)) {
            onError("Sin conexión a Internet. No es posible eliminar el lugar ahora.")
            return
        }

        coleccionFavoritos(uid)
            .document(favoritoId)
            .delete()
            .addOnSuccessListener { onExito() }
            .addOnFailureListener { excepcion ->
                onError(traducirErrorFirestore(excepcion))
            }
    }

    private fun validarDatosFavorito(nombre: String, latitud: Double, longitud: Double): String? {
        val nombreLimpio = nombre.trim()
        return when {
            nombreLimpio.isEmpty() ->
                "Debes ingresar un nombre para el lugar."
            nombreLimpio.length > MAX_LARGO_NOMBRE ->
                "El nombre no puede superar los $MAX_LARGO_NOMBRE caracteres."
            latitud.isNaN() || latitud < -90.0 || latitud > 90.0 ->
                "La latitud seleccionada no es válida."
            longitud.isNaN() || longitud < -180.0 || longitud > 180.0 ->
                "La longitud seleccionada no es válida."
            else -> null
        }
    }

    private fun traducirErrorFirestore(excepcion: Exception): String {
        if (excepcion is FirebaseFirestoreException) {
            return when (excepcion.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "No se pudo conectar con Firebase. Revisa tu conexión a Internet."
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "No tienes permisos para realizar esta operación. Inicia sesión nuevamente."
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Tu sesión expiró. Vuelve a iniciar sesión."
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "La conexión tardó demasiado. Intenta nuevamente."
                FirebaseFirestoreException.Code.NOT_FOUND ->
                    "El registro solicitado ya no existe en la base de datos."
                else ->
                    "Error al comunicarse con Firebase. Intenta nuevamente."
            }
        }
        return "Ocurrió un error inesperado al conectar con la base de datos."
    }
}