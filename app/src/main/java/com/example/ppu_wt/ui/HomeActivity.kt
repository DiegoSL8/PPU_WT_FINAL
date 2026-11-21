package com.example.ppu_wt.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ppu_wt.R
import com.example.ppu_wt.api.WeatherApiService
import com.example.ppu_wt.data.AuthRepository
import com.example.ppu_wt.data.FavoritosFirebaseRepository
import com.example.ppu_wt.model.ClimaResponse
import com.example.ppu_wt.utils.NetworkUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

/**
 * Pantalla principal del mapa y clima.
 * Delega la sesión a AuthRepository y el guardado de favoritos a FavoritosFirebaseRepository.
 */
class HomeActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    // =========================================================================
    // 1. VARIABLES GLOBALES (UI, Mapa y Repositorios)
    // =========================================================================
    private lateinit var mapa: GoogleMap
    private var marcadorActual: Marker? = null
    private var ubicacionSeleccionada: LatLng? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var panelLateral: CardView
    private lateinit var vistaExpandida: LinearLayout
    private lateinit var tvTempCompacta: TextView
    private lateinit var tvVientoExpandido: TextView
    private lateinit var tvUvExpandido: TextView
    private lateinit var tvAptoBanio: TextView

    private lateinit var authRepository: AuthRepository
    private lateinit var favoritosRepository: FavoritosFirebaseRepository

    private val weatherService: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private val IQUIQUE_DEFECTO = LatLng(-20.2133, -70.1503)
    }

    // =========================================================================
    // 2. CICLO DE VIDA
    // =========================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        authRepository = AuthRepository(this)
        favoritosRepository = FavoritosFirebaseRepository(this)

        configurarInterfaz()
        prepararMapa()
        configurarBuscador()
        configurarBotonesMapa()
    }

    // =========================================================================
    // 3. CONFIGURACIÓN DE INTERFAZ Y NAVEGACIÓN
    // =========================================================================
    private fun configurarInterfaz() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mapaFondo)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        panelLateral = findViewById(R.id.panelLateral)
        vistaExpandida = findViewById(R.id.vistaExpandida)
        tvTempCompacta = findViewById(R.id.tvTempCompacta)
        tvVientoExpandido = findViewById(R.id.tvVientoExpandido)
        tvUvExpandido = findViewById(R.id.tvUvExpandido)
        tvAptoBanio = findViewById(R.id.tvAptoBanio)

        // Expansión del panel lateral del clima
        panelLateral.setOnClickListener {
            vistaExpandida.visibility =
                if (vistaExpandida.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        // Menú desplegable superior (Top Bar)
        val btnOpciones = findViewById<ImageButton>(R.id.btnOpciones)
        btnOpciones.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_opciones, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.opcion_favoritos -> {
                        if (!authRepository.estaAutenticado()) {
                            Toast.makeText(
                                this,
                                "Modo invitado: inicia sesión para ver tus favoritos",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            startActivity(Intent(this, FavoritosActivity::class.java))
                        }
                        true
                    }
                    R.id.opcion_salir -> {
                        authRepository.cerrarSesion()
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // Botón guardar PIN en Cloud Firestore
        findViewById<FloatingActionButton>(R.id.btnGuardarPin).setOnClickListener {
            guardarLugarFavorito()
        }
    }

    private fun configurarBuscador() {
        val etBuscar = findViewById<EditText>(R.id.etBuscar)
        etBuscar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                buscarUbicacionEnMapa(etBuscar.text.toString())
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etBuscar.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun configurarBotonesMapa() {
        findViewById<FloatingActionButton>(R.id.btnMiUbicacion).setOnClickListener {
            centrarEnUbicacionUsuario()
        }
        findViewById<FloatingActionButton>(R.id.btnZoomIn).setOnClickListener {
            if (::mapa.isInitialized) mapa.animateCamera(CameraUpdateFactory.zoomIn())
        }
        findViewById<FloatingActionButton>(R.id.btnZoomOut).setOnClickListener {
            if (::mapa.isInitialized) mapa.animateCamera(CameraUpdateFactory.zoomOut())
        }
    }

    // =========================================================================
    // 4. LÓGICA DEL MAPA Y UBICACIÓN (GPS)
    // =========================================================================
    private fun prepararMapa() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapaFondo) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        mapa.setOnMapClickListener(this)

        val viajarLat = intent.getDoubleExtra("viajar_lat", 0.0)
        val viajarLon = intent.getDoubleExtra("viajar_lon", 0.0)
        val viajarNombre = intent.getStringExtra("viajar_nombre")

        if (viajarLat != 0.0 && viajarLon != 0.0) {
            enfocarCoordenada(LatLng(viajarLat, viajarLon), viajarNombre ?: "Favorito", 14f)
            if (tienePermisoUbicacion()) {
                mapa.isMyLocationEnabled = true
            }
        } else {
            if (tienePermisoUbicacion()) {
                mapa.isMyLocationEnabled = true
                centrarEnUbicacionUsuario()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val viajarLat = intent.getDoubleExtra("viajar_lat", 0.0)
        val viajarLon = intent.getDoubleExtra("viajar_lon", 0.0)
        val viajarNombre = intent.getStringExtra("viajar_nombre")

        if (viajarLat != 0.0 && viajarLon != 0.0 && ::mapa.isInitialized) {
            enfocarCoordenada(LatLng(viajarLat, viajarLon), viajarNombre ?: "Favorito", 14f)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && ::mapa.isInitialized) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (tienePermisoUbicacion()) {
                    mapa.isMyLocationEnabled = true
                    centrarEnUbicacionUsuario()
                }
            } else {
                Toast.makeText(
                    this,
                    "Permiso de ubicación denegado. Mostrando Iquique por defecto.",
                    Toast.LENGTH_LONG
                ).show()
                enfocarCoordenada(IQUIQUE_DEFECTO, "Iquique", 12f)
            }
        }
    }

    private fun tienePermisoUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun centrarEnUbicacionUsuario() {
        if (!::mapa.isInitialized) return
        if (!tienePermisoUbicacion()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val pos = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else {
                    IQUIQUE_DEFECTO
                }
                enfocarCoordenada(pos, "Mi Ubicación", 15f)
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                enfocarCoordenada(IQUIQUE_DEFECTO, "Iquique", 12f)
            }
    }

    override fun onMapClick(coordenadas: LatLng) {
        colocarMarcador(coordenadas, "Pin Manual")
        ubicacionSeleccionada = coordenadas
        obtenerClima(coordenadas.latitude, coordenadas.longitude)
    }

    private fun enfocarCoordenada(coordenadas: LatLng, titulo: String, zoom: Float) {
        colocarMarcador(coordenadas, titulo)
        ubicacionSeleccionada = coordenadas
        mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(coordenadas, zoom))
        obtenerClima(coordenadas.latitude, coordenadas.longitude)
    }

    private fun colocarMarcador(coordenadas: LatLng, titulo: String) {
        marcadorActual?.remove()
        marcadorActual = mapa.addMarker(MarkerOptions().position(coordenadas).title(titulo))
    }

    @Suppress("DEPRECATION")
    private fun buscarUbicacionEnMapa(nombre: String) {
        val consulta = nombre.trim()
        if (consulta.isEmpty() || !::mapa.isInitialized) return

        if (!NetworkUtils.hayConexion(this)) {
            Toast.makeText(this, "Sin conexión a Internet para buscar ubicaciones.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val direcciones = Geocoder(this, Locale.getDefault()).getFromLocationName(consulta, 1)
            if (!direcciones.isNullOrEmpty()) {
                val coords = LatLng(direcciones[0].latitude, direcciones[0].longitude)
                enfocarCoordenada(coords, consulta, 12f)
            } else {
                Toast.makeText(this, "Lugar no encontrado", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo completar la búsqueda. Revisa tu conexión.", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // 5. CLIMA (API) Y REGISTRO DE FAVORITOS EN FIREBASE
    // =========================================================================
    private fun obtenerClima(lat: Double, lon: Double) {
        if (!NetworkUtils.hayConexion(this)) {
            Toast.makeText(this, "Sin conexión a Internet para consultar el clima.", Toast.LENGTH_SHORT).show()
            return
        }

        weatherService.obtenerClimaActual(lat, lon).enqueue(object : Callback<ClimaResponse> {
            override fun onResponse(call: Call<ClimaResponse>, response: Response<ClimaResponse>) {
                if (isFinishing || isDestroyed) return
                val clima = response.body()?.actual
                if (response.isSuccessful && clima != null) {
                    tvTempCompacta.text = "${clima.temperatura}°C"
                    tvVientoExpandido.text = "🌬️ Viento: ${clima.viento} km/h"
                    tvUvExpandido.text = "☀️ Índice UV: ${if (clima.esDeDia == 1) "Alto (8)" else "Bajo (0)"}"

                    val esApto = if (clima.viento > 15.0) "No (Bandera Roja)" else "Sí (Bandera Verde)"
                    tvAptoBanio.text = "🌊 Apto para Baño: $esApto"
                    tvAptoBanio.setTextColor(
                        if (clima.viento > 15.0) Color.parseColor("#D32F2F")
                        else Color.parseColor("#2E7D32")
                    )
                }
            }

            override fun onFailure(call: Call<ClimaResponse>, t: Throwable) {
                if (isFinishing || isDestroyed) return
                Toast.makeText(this@HomeActivity, "No se pudo obtener el clima actual", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Solicita un nombre al usuario y registra la ubicación seleccionada en Cloud Firestore
     * mediante FavoritosFirebaseRepository (validando campos, duplicados y conectividad).
     */
    private fun guardarLugarFavorito() {
        val uidActual = authRepository.obtenerUidActual()
        if (uidActual.isNullOrBlank()) {
            Toast.makeText(this, "El modo invitado no puede guardar lugares favoritos", Toast.LENGTH_SHORT).show()
            return
        }

        val punto = ubicacionSeleccionada
        if (punto == null) {
            Toast.makeText(this, "Selecciona un punto en el mapa primero", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Ej: Casa, Playa Cavancha..."
            setSingleLine(true)
        }

        val dialogo = AlertDialog.Builder(this)
            .setTitle("Guardar Ubicación")
            .setView(input)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
            .create()

        dialogo.setOnShowListener {
            val btnGuardar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancelar = dialogo.getButton(AlertDialog.BUTTON_NEGATIVE)

            btnGuardar.setOnClickListener {
                val nombreIngresado = input.text.toString().trim()
                if (nombreIngresado.isEmpty()) {
                    input.error = "Debes ingresar un nombre para guardar"
                    input.requestFocus()
                    return@setOnClickListener
                }
                if (nombreIngresado.length > 80) {
                    input.error = "El nombre no puede superar los 80 caracteres"
                    input.requestFocus()
                    return@setOnClickListener
                }

                // Bloqueamos el diálogo mientras Firebase procesa la operación asíncrona
                btnGuardar.isEnabled = false
                btnCancelar.isEnabled = false
                input.isEnabled = false
                dialogo.setCancelable(false)

                favoritosRepository.guardarFavorito(
                    uid = uidActual,
                    nombre = nombreIngresado,
                    latitud = punto.latitude,
                    longitud = punto.longitude,
                    onExito = {
                        if (isFinishing || isDestroyed) return@guardarFavorito
                        dialogo.dismiss()
                        Toast.makeText(
                            this,
                            "¡'$nombreIngresado' guardado en Firebase!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = { mensaje ->
                        if (isFinishing || isDestroyed) return@guardarFavorito
                        btnGuardar.isEnabled = true
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
}