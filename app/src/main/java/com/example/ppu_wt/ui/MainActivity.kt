package com.example.ppu_wt.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ppu_wt.R
import com.example.ppu_wt.data.AuthRepository

/**
 * Pantalla de acceso (Login, Registro y Modo Invitado).
 * Delega la comunicación con Firebase Authentication a AuthRepository.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var etCorreo: EditText
    private lateinit var etContrasena: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegistro: Button
    private lateinit var btnInvitado: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        authRepository = AuthRepository(this)

        // Si ya existe una sesión remota activa en Firebase Auth, entrar directo al mapa
        if (authRepository.estaAutenticado()) {
            irAlHome()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etCorreo = findViewById(R.id.email_et)
        etContrasena = findViewById(R.id.password_et)
        btnLogin = findViewById(R.id.login_btn)
        btnRegistro = findViewById(R.id.register_btn)
        btnInvitado = findViewById(R.id.btnInvitado)

        // 1. Registro en Firebase Authentication
        btnRegistro.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val contrasena = etContrasena.text.toString()

            if (!validarCredenciales(correo, contrasena)) return@setOnClickListener

            establecerEstadoCarga(cargando = true)
            authRepository.registrarUsuario(
                correo = correo,
                contrasena = contrasena,
                onExito = {
                    if (isFinishing || isDestroyed) return@registrarUsuario
                    Toast.makeText(this, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show()
                    irAlHome()
                },
                onError = { mensaje ->
                    if (isFinishing || isDestroyed) return@registrarUsuario
                    establecerEstadoCarga(cargando = false)
                    Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                }
            )
        }

        // 2. Inicio de sesión en Firebase Authentication
        btnLogin.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val contrasena = etContrasena.text.toString()

            if (!validarCredenciales(correo, contrasena)) return@setOnClickListener

            establecerEstadoCarga(cargando = true)
            authRepository.iniciarSesion(
                correo = correo,
                contrasena = contrasena,
                onExito = {
                    if (isFinishing || isDestroyed) return@iniciarSesion
                    Toast.makeText(this, "Sesión iniciada: $correo", Toast.LENGTH_SHORT).show()
                    irAlHome()
                },
                onError = { mensaje ->
                    if (isFinishing || isDestroyed) return@iniciarSesion
                    establecerEstadoCarga(cargando = false)
                    Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                }
            )
        }

        // 3. Modo Invitado (cierra cualquier sesión previa para operar de forma anónima)
        btnInvitado.setOnClickListener {
            authRepository.cerrarSesion()
            irAlHome()
        }
    }

    /**
     * Valida campos obligatorios, formato de correo y longitud mínima de contraseña
     * antes de realizar peticiones remotas.
     */
    private fun validarCredenciales(correo: String, contrasena: String): Boolean {
        etCorreo.error = null
        etContrasena.error = null

        if (correo.isEmpty()) {
            etCorreo.error = "Ingresa tu correo electrónico"
            etCorreo.requestFocus()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.error = "Ingresa un formato de correo válido"
            etCorreo.requestFocus()
            return false
        }

        if (contrasena.isBlank()) {
            etContrasena.error = "Ingresa tu contraseña"
            etContrasena.requestFocus()
            return false
        }

        if (contrasena.length < 6) {
            etContrasena.error = "La contraseña debe tener al menos 6 caracteres"
            etContrasena.requestFocus()
            return false
        }

        return true
    }

    /**
     * Deshabilita temporalmente los controles durante peticiones asíncronas
     * para evitar envíos duplicados.
     */
    private fun establecerEstadoCarga(cargando: Boolean) {
        btnLogin.isEnabled = !cargando
        btnRegistro.isEnabled = !cargando
        btnInvitado.isEnabled = !cargando
        etCorreo.isEnabled = !cargando
        etContrasena.isEnabled = !cargando
    }

    /**
     * Navega a la pantalla principal cerrando el Login del historial.
     */
    private fun irAlHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}