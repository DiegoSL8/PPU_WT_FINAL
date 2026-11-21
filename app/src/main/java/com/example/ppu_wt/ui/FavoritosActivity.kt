package com.example.ppu_wt.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ppu_wt.R
import com.example.ppu_wt.data.AuthRepository
import com.example.ppu_wt.data.FavoritosFirebaseRepository
import com.example.ppu_wt.model.LugarFavorito
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration

/**
 * Pantalla de gestión CRUD de lugares favoritos conectados a Cloud Firestore.
 * Delega sesión y acceso remoto a AuthRepository y FavoritosFirebaseRepository.
 */
class FavoritosActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var favoritosRepository: FavoritosFirebaseRepository

    private lateinit var listViewFavoritos: ListView
    private lateinit var adaptador: FavoritosAdapter
    private val listaLugaresFavoritos = mutableListOf<LugarFavorito>()

    private var uidActual: String = ""
    private var listenerFavoritos: ListenerRegistration? = null
    private var avisoListaVaciaMostrado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_favoritos)

        authRepository = AuthRepository(this)
        favoritosRepository = FavoritosFirebaseRepository(this)

        // Regla de negocio: bloquear acceso si es Modo Invitado o no hay sesión activa
        val uid = authRepository.obtenerUidActual()
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Modo Invitado: No tienes acceso a favoritos.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        uidActual = uid

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<FloatingActionButton>(R.id.btnVolver).setOnClickListener { finish() }

        listViewFavoritos = findViewById(R.id.listaFavoritos)
        adaptador = FavoritosAdapter(this, listaLugaresFavoritos)
        listViewFavoritos.adapter = adaptador
    }

    override fun onStart() {
        super.onStart()
        if (uidActual.isNotBlank()) {
            iniciarEscuchaFavoritos()
        }
    }

    override fun onStop() {
        super.onStop()
        // Libera el listener en tiempo real para evitar fugas de memoria y lecturas innecesarias
        listenerFavoritos?.remove()
        listenerFavoritos = null
    }

    /**
     * READ (Tiempo real): Escucha los favoritos del usuario en Firestore y sincroniza la lista.
     */
    private fun iniciarEscuchaFavoritos() {
        listenerFavoritos?.remove()
        listenerFavoritos = favoritosRepository.escucharFavoritos(
            uid = uidActual,
            onActualizacion = { favoritosRemotos ->
                if (isFinishing || isDestroyed) return@escucharFavoritos

                listaLugaresFavoritos.clear()
                listaLugaresFavoritos.addAll(favoritosRemotos)
                adaptador.notifyDataSetChanged()

                if (listaLugaresFavoritos.isEmpty() && !avisoListaVaciaMostrado) {
                    avisoListaVaciaMostrado = true
                    Toast.makeText(this, "Aún no tienes lugares favoritos", Toast.LENGTH_SHORT).show()
                } else if (listaLugaresFavoritos.isNotEmpty()) {
                    avisoListaVaciaMostrado = false
                }
            },
            onError = { mensaje ->
                if (isFinishing || isDestroyed) return@escucharFavoritos
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
            }
        )
    }

    /**
     * UPDATE: Abre diálogo para renombrar el favorito validando formato, duplicados y conectividad.
     */
    private fun mostrarDialogoEditar(lugar: LugarFavorito) {
        val input = EditText(this).apply {
            setText(lugar.nombre)
            setSelection(lugar.nombre.length)
            setSingleLine(true)
        }

        val dialogo = AlertDialog.Builder(this)
            .setTitle("Editar Favorito")
            .setMessage("Ingresa un nuevo nombre para esta ubicación:")
            .setView(input)
            .setPositiveButton("Actualizar", null)
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
            .create()

        dialogo.setOnShowListener {
            val btnActualizar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancelar = dialogo.getButton(AlertDialog.BUTTON_NEGATIVE)

            btnActualizar.setOnClickListener {
                val nuevoNombre = input.text.toString().trim()

                if (nuevoNombre.isEmpty()) {
                    input.error = "El nombre no puede estar vacío"
                    input.requestFocus()
                    return@setOnClickListener
                }
                if (nuevoNombre.length > 80) {
                    input.error = "El nombre no puede superar los 80 caracteres"
                    input.requestFocus()
                    return@setOnClickListener
                }
                if (nuevoNombre == lugar.nombre.trim()) {
                    dialogo.dismiss()
                    return@setOnClickListener
                }

                btnActualizar.isEnabled = false
                btnCancelar.isEnabled = false
                input.isEnabled = false
                dialogo.setCancelable(false)

                favoritosRepository.actualizarNombreFavorito(
                    uid = uidActual,
                    favoritoId = lugar.id,
                    nuevoNombre = nuevoNombre,
                    onExito = {
                        if (isFinishing || isDestroyed) return@actualizarNombreFavorito
                        dialogo.dismiss()
                        Toast.makeText(this, "Nombre actualizado", Toast.LENGTH_SHORT).show()
                    },
                    onError = { mensaje ->
                        if (isFinishing || isDestroyed) return@actualizarNombreFavorito
                        btnActualizar.isEnabled = true
                        btnCancelar.isEnabled = true
                        input.isEnabled = true
                        dialogo.setCancelable(true)
                        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        dialogo.show()
    }

    /**
     * DELETE: Solicita confirmación explícita antes de eliminar el documento en Firestore.
     */
    private fun mostrarDialogoEliminar(lugar: LugarFavorito) {
        val dialogo = AlertDialog.Builder(this)
            .setTitle("Eliminar Favorito")
            .setMessage("¿Estás seguro de borrar '${lugar.nombre}'?")
            .setPositiveButton("Eliminar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialogo.setOnShowListener {
            val btnEliminar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancelar = dialogo.getButton(AlertDialog.BUTTON_NEGATIVE)

            btnEliminar.setOnClickListener {
                btnEliminar.isEnabled = false
                btnCancelar.isEnabled = false
                dialogo.setCancelable(false)

                favoritosRepository.eliminarFavorito(
                    uid = uidActual,
                    favoritoId = lugar.id,
                    onExito = {
                        if (isFinishing || isDestroyed) return@eliminarFavorito
                        dialogo.dismiss()
                        Toast.makeText(this, "Lugar eliminado", Toast.LENGTH_SHORT).show()
                    },
                    onError = { mensaje ->
                        if (isFinishing || isDestroyed) return@eliminarFavorito
                        btnEliminar.isEnabled = true
                        btnCancelar.isEnabled = true
                        dialogo.setCancelable(true)
                        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        dialogo.show()
    }

    // =========================================================================
    // ADAPTADOR PERSONALIZADO PARA LA LISTA (Interactividad por ítem)
    // =========================================================================
    inner class FavoritosAdapter(
        context: Context,
        private val lugares: List<LugarFavorito>
    ) : ArrayAdapter<LugarFavorito>(context, 0, lugares) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_favorito, parent, false)
            val lugar = lugares[position]

            val tvNombre = view.findViewById<TextView>(R.id.tvNombreLugar)
            val btnCompartir = view.findViewById<ImageButton>(R.id.btnCompartir)
            val btnEliminar = view.findViewById<ImageButton>(R.id.btnEliminar)
            val btnEditar = view.findViewById<ImageButton>(R.id.btnEditar)
            val contenedorClickeable = view.findViewById<View>(R.id.contenedorUbicacion)

            tvNombre.text = lugar.nombre

            // 1. VIAJAR AL MAPA (Clic en toda la fila)
            contenedorClickeable.setOnClickListener {
                val intent = Intent(this@FavoritosActivity, HomeActivity::class.java).apply {
                    putExtra("viajar_lat", lugar.latitud)
                    putExtra("viajar_lon", lugar.longitud)
                    putExtra("viajar_nombre", lugar.nombre)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }

            // 2. ACTUALIZAR (UPDATE en Firestore)
            btnEditar.setOnClickListener {
                mostrarDialogoEditar(lugar)
            }

            // 3. COMPARTIR
            btnCompartir.setOnClickListener {
                val urlMapa = "https://www.google.com/maps/search/?api=1&query=${lugar.latitud},${lugar.longitud}"
                val mensaje = "¡Mira mi lugar favorito!\n📍 ${lugar.nombre}\n$urlMapa"

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, mensaje)
                }
                startActivity(Intent.createChooser(shareIntent, "Compartir ubicación vía:"))
            }

            // 4. ELIMINAR (DELETE en Firestore con confirmación)
            btnEliminar.setOnClickListener {
                mostrarDialogoEliminar(lugar)
            }

            return view
        }
    }
}