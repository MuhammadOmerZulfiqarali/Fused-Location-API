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
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        // Check permissions and start location updates
        if (checkLocationPermissions()) {
            startLocationUpdates()
        } else {
            requestLocationPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume location updates if permissions are granted
        if (checkLocationPermissions()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop location updates to conserve battery
        stopLocationUpdates()
    }

    override fun onStop() {
        super.onStop()
        // Ensure location updates are stopped
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Start location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Get last known location
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                onLocationChanged(it)
            } ?: run {
                Log.d("MainActivity", "Last known location is null")
                Toast.makeText(this, "Unable to fetch last location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun checkLocationPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            locationPermissionCode
        )
    }

    /**
     * Formats the latitude and longitude with directional indicators.
     * For example:
     * Latitude: 37.7749° N
     * Longitude: 122.4194° W
     */
    private fun formatCoordinates(latitude: Double, longitude: Double): String {
        val latDirection = if (latitude >= 0) "N" else "S"
        val lonDirection = if (longitude >= 0) "E" else "W"

        val formattedLat = "%.4f° $latDirection".format(Math.abs(latitude))
        val formattedLon = "%.4f° $lonDirection".format(Math.abs(longitude))
        latitudeT.text = formattedLat
        longitudeT.text = formattedLon
        return "Latitude: $formattedLat\nLongitude: $formattedLon"
    }

    @SuppressLint("SetTextI18n")
    private fun onLocationChanged(location: Location) {
        Log.d("MainActivity", "Location changed: ${location.latitude}, ${location.longitude}")

        val latitude = location.latitude
        val longitude = location.longitude
        // Format and display the coordinates with directions
        val latDirection = if (latitude >= 0) "N" else "S"
        val lonDirection = if (longitude >= 0) "E" else "W"

        val formattedLat = "%.4f° $latDirection".format(Math.abs(latitude))
        val formattedLon = "%.4f° $lonDirection".format(Math.abs(longitude))
        latitudeT.text = formattedLat
        longitudeT.text = formattedLon
//        val formattedCoordinates = formatCoordinates(location.latitude, location.longitude)
//        tvGpsLocation.text = formattedCoordinates

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

    override fun onMapReady(googleMap: GoogleMap) {
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
        } else {
            requestLocationPermissions()
        }

        // Optional: Set a default location
        val defaultLocation = LatLng(-34.0, 151.0) // Sydney
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        // Optional: Enable UI settings
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
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
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
                        }
                    }
                }
            } else {
                // Permission Denied
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove location updates to prevent memory leaks
        stopLocationUpdates()
    }
}
