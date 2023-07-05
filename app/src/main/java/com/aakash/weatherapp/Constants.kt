package com.aakash.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

object Constants {

    const val APP_ID : String = "0d75afd055fa5c032b27afc8a19902d0"
    const val BASE_URI: String = "https://api.openweathermap.org/data/"
    const val METRIC_UNIT : String = "metric"
    const val PREFERENCE_NAME = "WeatherAppPreference"
    const val WEATHER_RESPONSE_DATA = "weather_response_data"

    fun isNetworkAvailable(context: Context) : Boolean{
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        //this for new versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // here use your version M

            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }else{
            //this for old versions
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo !=null && networkInfo.isConnectedOrConnecting
        }


    }
}