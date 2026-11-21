package com.example.ppu_wt.model

/**
 * Modelo de datos para un lugar favorito almacenado en Cloud Firestore.
 * Incluye valores por defecto requeridos para la deserialización de Firebase.
 */
data class LugarFavorito(
    val id: String = "",
    val nombre: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0
)