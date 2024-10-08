package com.example.fused_location_api

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var latitudeT: TextView
    private lateinit var longitudeT: TextView
    private val locationPermissionCode = 1000
    private lateinit var mMap: GoogleMap

    // Define LocationRequest as a class variable to use in lifecycle methods
    private lateinit var locationRequest: LocationRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set your layout
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "onCreate called")

        // Initialize Views
        latitudeT = findViewById(R.id.latitudeValue)
        longitudeT = findViewById(R.id.longitudeValue)

        title = "Location App"

        // Initialize the Map Fragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define the location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    onLocationChanged(location)
                }
            }
        }

        // Initialize LocationRequest
        locationRequest = LocationRequest.create().apply {
            interval = 5000 // 5 seconds
            fastestInterval = 2000 // 2 seconds
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart called")
        // Check permissions and start location updates
        if (checkLocationPermissions()) {
            startLocationUpdates()
        } else {
            requestLocationPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called")
        // Resume location updates if permissions are granted
        if (checkLocationPermissions()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause called")
        // Stop location updates to conserve battery
        stopLocationUpdates()
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop called")
        // Ensure location updates are stopped
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        Log.d("MainActivity", "startLocationUpdates called")
        // Start location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Get last known location
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                Log.d("MainActivity", "Last known location received")
                onLocationChanged(location)
            } else {
                Log.d("MainActivity", "Last known location is null")
                Toast.makeText(this, "Unable to fetch last location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopLocationUpdates() {
        Log.d("MainActivity", "stopLocationUpdates called")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun checkLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        Log.d("MainActivity", "checkLocationPermissions: FINE = $fineLocation, COARSE = $coarseLocation")
        return fineLocation == PackageManager.PERMISSION_GRANTED ||
                coarseLocation == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        Log.d("MainActivity", "requestLocationPermissions called")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            locationPermissionCode
        )
    }

    @SuppressLint("SetTextI18n")
    private fun onLocationChanged(location: Location) {
        Log.d("MainActivity", "onLocationChanged called with lat: ${location.latitude}, lon: ${location.longitude}")

        val latitude = location.latitude
        val longitude = location.longitude
        val currentTime = System.currentTimeMillis()

        // Format date and time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(currentTime))
        val formattedTime = timeFormat.format(Date(currentTime))

        // Save to Room Database
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val lastLocation = AppDatabase.getDatabase(this@MainActivity).locationDao().getLastLocation()
                if (lastLocation == null || (currentTime - lastLocation.timestamp) > 180000) { // 3 minutes
                    // Save new location if no location exists or 3 minutes have passed
                    val newLocation = LocationEntity(
                        gpsId = 1001, // Fixed GPS ID
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = currentTime,
                        date = formattedDate,
                        time = formattedTime
                    )
                    AppDatabase.getDatabase(this@MainActivity).locationDao().insertLocation(newLocation)

                    Log.d("MainActivity", "Saved new location with GPS ID: 1001, Date: $formattedDate, Time: $formattedTime")

                    // Update UI on the main thread
                    withContext(Dispatchers.Main) {
                        formatCoordinates(latitude, longitude)
                    }
                } else {
                    // Use the last saved location if it is less than 3 minutes old
                    Log.d(
                        "MainActivity",
                        "Using last saved location: GPS ID: ${lastLocation.gpsId}, Date: ${lastLocation.date}, Time: ${lastLocation.time}"
                    )

                    // Optionally update UI with last saved location
                    withContext(Dispatchers.Main) {
                        formatCoordinates(lastLocation.latitude, lastLocation.longitude)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error accessing database: ${e.message}")
            }
        }

        // Update Map with current location
        if (::mMap.isInitialized) {
            val currentLatLng = LatLng(location.latitude, location.longitude)
            mMap.clear()
            mMap.addMarker(
                MarkerOptions()
                    .position(currentLatLng)
                    .title("You are here")
            )
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
        }
    }

    private fun formatCoordinates(latitude: Double, longitude: Double) {
        val latDirection = if (latitude >= 0) "N" else "S"
        val lonDirection = if (longitude >= 0) "E" else "W"

        val formattedLat = "%.4f° $latDirection".format(Math.abs(latitude))
        val formattedLon = "%.4f° $lonDirection".format(Math.abs(longitude))
        latitudeT.text = " $formattedLat"
        longitudeT.text = " $formattedLon"

        Log.d("MainActivity", "Formatted Latitude: $formattedLat, Formatted Longitude: $formattedLon")
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d("MainActivity", "onMapReady called")
        mMap = googleMap

        // Check and enable My Location layer
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            Log.d("MainActivity", "My Location layer enabled")
        } else {
            requestLocationPermissions()
        }

        // Set a default location (optional)
        val defaultLocation = LatLng(-34.0, 151.0) // Sydney
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        // Enable UI settings (optional)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true
    }

    // Handle Permission Request Response
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("MainActivity", "onRequestPermissionsResult called with requestCode: $requestCode")
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                Log.d("MainActivity", "Location permission granted")
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                if (checkLocationPermissions()) {
                    startLocationUpdates()
                    // Enable My Location layer if map is ready
                    if (::mMap.isInitialized) {
                        if (ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            mMap.isMyLocationEnabled = true
                            Log.d("MainActivity", "My Location layer enabled after permission")
                        }
                    }
                }
            } else {
                // Permission Denied
                Log.d("MainActivity", "Location permission denied")
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
