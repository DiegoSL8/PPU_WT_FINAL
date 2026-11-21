package com.example.ppu_wt.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utilidad para comprobar si el dispositivo cuenta con conectividad activa
 * antes de ejecutar operaciones remotas contra Firebase.
 */
object NetworkUtils {

    fun hayConexion(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        val redActiva = connectivityManager.activeNetwork ?: return false
        val capacidades = connectivityManager.getNetworkCapabilities(redActiva) ?: return false

        val tieneInternet = capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val tieneTransporteValido =
            capacidades.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capacidades.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capacidades.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return tieneInternet && tieneTransporteValido
    }
}