package com.example.ppu_wt.data

import android.content.Context
import com.example.ppu_wt.utils.NetworkUtils
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * Capa de acceso a datos encargada de la autenticación remota con Firebase Auth.
 * Separa la lógica de sesión respecto de las pantallas (Activities).
 */
class AuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun obtenerUidActual(): String? = auth.currentUser?.uid

    fun obtenerCorreoActual(): String? = auth.currentUser?.email

    fun estaAutenticado(): Boolean = auth.currentUser != null

    /**
     * Inicia sesión con correo y contraseña validando conectividad previa.
     */
    fun iniciarSesion(
        correo: String,
        contrasena: String,
        onExito: (uid: String) -> Unit,
        onError: (mensaje: String) -> Unit
    ) {
        if (!NetworkUtils.hayConexion(context)) {
            onError("Sin conexión a Internet. Verifica tu red e intenta nuevamente.")
            return
        }

        auth.signInWithEmailAndPassword(correo.trim(), contrasena)
            .addOnSuccessListener { resultado ->
                val uid = resultado.user?.uid
                if (!uid.isNullOrBlank()) {
                    onExito(uid)
                } else {
                    onError("No se pudo obtener el identificador de la cuenta.")
                }
            }
            .addOnFailureListener { excepcion ->
                onError(traducirErrorAuth(excepcion))
            }
    }

    /**
     * Registra un usuario nuevo en Firebase Auth validando conectividad previa.
     */
    fun registrarUsuario(
        correo: String,
        contrasena: String,
        onExito: (uid: String) -> Unit,
        onError: (mensaje: String) -> Unit
    ) {
        if (!NetworkUtils.hayConexion(context)) {
            onError("Sin conexión a Internet. No es posible crear la cuenta ahora.")
            return
        }

        auth.createUserWithEmailAndPassword(correo.trim(), contrasena)
            .addOnSuccessListener { resultado ->
                val uid = resultado.user?.uid
                if (!uid.isNullOrBlank()) {
                    onExito(uid)
                } else {
                    onError("La cuenta se creó, pero no se pudo iniciar la sesión.")
                }
            }
            .addOnFailureListener { excepcion ->
                onError(traducirErrorAuth(excepcion))
            }
    }

    /**
     * Cierra la sesión activa (usado en Logout y al ingresar en Modo Invitado).
     */
    fun cerrarSesion() {
        auth.signOut()
    }

    /**
     * Convierte excepciones técnicas de Firebase Auth en mensajes comprensibles.
     */
    private fun traducirErrorAuth(excepcion: Exception): String {
        return when (excepcion) {
            is FirebaseNetworkException ->
                "Error de conexión con Firebase. Revisa tu acceso a Internet."
            is FirebaseAuthUserCollisionException ->
                "Este correo ya está registrado. Intenta iniciar sesión."
            is FirebaseAuthWeakPasswordException ->
                "La contraseña es muy débil. Debe tener al menos 6 caracteres."
            is FirebaseAuthInvalidUserException ->
                "No existe una cuenta registrada con ese correo."
            is FirebaseAuthInvalidCredentialsException ->
                "Correo o contraseña incorrectos. Verifica tus datos."
            else ->
                "No se pudo completar la operación. Intenta nuevamente."
        }
    }
}