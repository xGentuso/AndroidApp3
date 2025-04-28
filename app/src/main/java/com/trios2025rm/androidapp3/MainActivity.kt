package com.trios2025rm.androidapp3

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var weatherService: WeatherService

    // UI components
    private lateinit var locationTextView: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var weatherDescriptionTextView: TextView
    private lateinit var humidityTextView: TextView
    private lateinit var windTextView: TextView
    private lateinit var pressureTextView: TextView
    private lateinit var refreshButton: Button
    private lateinit var progressBar: ProgressBar

    // Location permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Approximate location access granted
                getCurrentLocation()
            }
            else -> {
                // No location access granted
                Toast.makeText(this, "Location permission is required for this app", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize UI components
        locationTextView = findViewById(R.id.locationTextView)
        temperatureTextView = findViewById(R.id.temperatureTextView)
        weatherDescriptionTextView = findViewById(R.id.weatherDescriptionTextView)
        humidityTextView = findViewById(R.id.humidityTextView)
        windTextView = findViewById(R.id.windTextView)
        pressureTextView = findViewById(R.id.pressureTextView)
        refreshButton = findViewById(R.id.refreshButton)
        progressBar = findViewById(R.id.progressBar)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Retrofit for API calls
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        weatherService = retrofit.create(WeatherService::class.java)

        // Set up refresh button click listener
        refreshButton.setOnClickListener {
            checkLocationPermission()
        }

        // Set up edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Check location permission when app starts
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        when {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted
                getCurrentLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show permission rationale
                Toast.makeText(this, "Location permission is needed to show weather for your location", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            return
        }

        showLoading(true)

        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    fetchWeatherForLocation(it.latitude, it.longitude)
                } ?: run {
                    showLoading(false)
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchWeatherForLocation(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val response = weatherService.getCurrentWeather(
                    lat = latitude,
                    lon = longitude,
                    appid = "1b9fe3c58f60dfeae21dafdf87e51dd2", // Replace with your actual OpenWeatherMap API key
                    units = "metric"
                )

                // Update UI with weather data
                locationTextView.text = response.name
                temperatureTextView.text = "${response.main.temp.toInt()}°C"
                weatherDescriptionTextView.text = response.weather[0].description.capitalize()
                humidityTextView.text = "${response.main.humidity}%"
                windTextView.text = "${response.wind.speed} m/s"
                pressureTextView.text = "${response.main.pressure} hPa"

                showLoading(false)
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, "Error fetching weather: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        refreshButton.isEnabled = !isLoading
    }

    // Extension function to capitalize first letter of weather description
    private fun String.capitalize(): String {
        return this.replaceFirstChar { it.uppercase() }
    }
}

// API Interface for OpenWeatherMap
interface WeatherService {
    @GET("weather")
    suspend fun getCurrentWeather(
        @retrofit2.http.Query("lat") lat: Double,
        @retrofit2.http.Query("lon") lon: Double,
        @retrofit2.http.Query("appid") appid: String,
        @retrofit2.http.Query("units") units: String
    ): WeatherResponse
}

// Data classes for weather response
data class WeatherResponse(
    val name: String,
    val main: Main,
    val weather: List<Weather>,
    val wind: Wind,
    val sys: Sys
)

data class Main(
    val temp: Double,
    val humidity: Int,
    val pressure: Int
)

data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class Wind(
    val speed: Double,
    val deg: Int
)

data class Sys(
    val country: String
)