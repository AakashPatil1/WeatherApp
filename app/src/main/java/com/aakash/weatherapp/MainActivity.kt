package com.aakash.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.aakash.weatherapp.models.WeatherResponse
import com.aakash.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences :SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()){
            Toast.makeText(this, "Your Location provider is turned off. Please turn it on.", Toast.LENGTH_SHORT).show()


            //open settings location
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
                .withListener(object : MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity, "You have denied location permission. Please enable them as it is mandatory for the app  to work.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermission()
                    }
                }).onSameThread().check()
        }
    }


    //requset location data
    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
         val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.QUALITY_HIGH_ACCURACY  // this is cahnge by me PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback,
            Looper.myLooper()
        )
    }

    //this function find latitude and longitude
    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude","$latitude")
            val longitude = mLastLocation.longitude
            Log.i("Current Longitude","$longitude")

            //here he put the latitude and longitude in function
            getLocationWeatherDetails(latitude,longitude)
        }
    }


    //
    private fun getLocationWeatherDetails(latitude: Double,longitude : Double){
        if(Constants.isNetworkAvailable(this)){

            //this was bas url
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URI)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            //this is service url
            val service : WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            // call api
            val listCall : Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )
            //before enqueue show dialog
            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){

                        hideProgressDialog()
                        val weatherList : WeatherResponse = response.body()!!

                        //sharepreference
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor =mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response Result","$weatherList")
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 ->{
                                Log.e("Error 400","BAD Connection")
                            }
                            404 ->{
                                Log.e("Error 404","Not Found")
                            }else ->{
                                Log.e("Error","Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable?) {
                   Log.e("Errorrr",t!!.message.toString())
                    hideProgressDialog()
                }

            })

        }else{
            Toast.makeText(this@MainActivity, "No internet connection available.", Toast.LENGTH_SHORT).show()
        }
    }



    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton("GO TO SETTINGS"){_,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){dialog, _ ->
                dialog.dismiss()
            }.show()
    }


    private fun isLocationEnabled(): Boolean{
        //this provides access to the system location services.
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        mProgressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{

                requestLocationData()

                true
            }else-> super.onOptionsItemSelected(item)
        }

    }

    private fun hideProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            for(i in weatherList.weather.indices){
                Log.i("weather Name",weatherList.weather.toString())


                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
                tv_min.text = weatherList.main.temp_min.toString() + " min"
                tv_max.text = weatherList.main.temp_max.toString() + " max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                    tv_sunset_time.text = unixTime(weatherList.sys.sunset)
                }

                when(weatherList.weather[i].icon){
                    "01d" -> iv_main.setImageResource(R.drawable.d01)
                    "02d" -> iv_main.setImageResource(R.drawable.d02)
                    "03d" -> iv_main.setImageResource(R.drawable.d03)
                    "04d" -> iv_main.setImageResource(R.drawable.d04)
                    "09d" -> iv_main.setImageResource(R.drawable.d09)
                    "10d" -> iv_main.setImageResource(R.drawable.d10)
                    "11d" -> iv_main.setImageResource(R.drawable.d11)
                    "13d" -> iv_main.setImageResource(R.drawable.d13)
                    "50d" -> iv_main.setImageResource(R.drawable.d50)
                    "01n" -> iv_main.setImageResource(R.drawable.n01)
                    "02n" -> iv_main.setImageResource(R.drawable.n02)
                    "03n" -> iv_main.setImageResource(R.drawable.n03)
                    "04n" -> iv_main.setImageResource(R.drawable.n04)
                    "09n" -> iv_main.setImageResource(R.drawable.n09)
                    "10n" -> iv_main.setImageResource(R.drawable.n10)
                    "11n" -> iv_main.setImageResource(R.drawable.n11)
                    "13n" -> iv_main.setImageResource(R.drawable.n13)
                    "50n" -> iv_main.setImageResource(R.drawable.n50)

                }
            }
        }


    }

    private fun getUnit(value: String):String?{
        var value = "°C"
        if("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    //this fun is convert long value to right format
    @RequiresApi(Build.VERSION_CODES.N) // this is some error
    private fun unixTime(timex: Long) : String?{
//        time is mili second then maltiple by 1000
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}