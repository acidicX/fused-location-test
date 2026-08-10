package com.acidicx.fusedlocationtest

import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var locationManager: LocationManager
    private lateinit var latitudeValue: TextView
    private lateinit var longitudeValue: TextView
    private lateinit var lockStatusValue: TextView
    private lateinit var permissionButton: Button

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            showLocation(location)
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.FUSED_PROVIDER) {
                lockStatusValue.text = getString(R.string.lock_status_searching)
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.FUSED_PROVIDER) {
                lockStatusValue.text = getString(R.string.lock_status_unavailable)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = getSystemService(LocationManager::class.java)
        latitudeValue = findViewById(R.id.latitudeValue)
        longitudeValue = findViewById(R.id.longitudeValue)
        lockStatusValue = findViewById(R.id.lockStatusValue)
        permissionButton = findViewById(R.id.permissionButton)

        permissionButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_LOCATION)
        }

        showPermissionRequiredState()
    }

    override fun onStart() {
        super.onStart()
        if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    override fun onStop() {
        locationManager.removeUpdates(locationListener)
        super.onStop()
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            showPermissionRequiredState()
            return
        }

        if (!locationManager.hasProvider(LocationManager.FUSED_PROVIDER)) {
            permissionButton.visibility = View.GONE
            lockStatusValue.text = getString(R.string.lock_status_unavailable)
            return
        }

        permissionButton.visibility = View.GONE
        lockStatusValue.text = getString(R.string.lock_status_searching)

        val request = LocationRequest.Builder(2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()

        try {
            locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)?.let(::showLocation)
            locationManager.requestLocationUpdates(
                LocationManager.FUSED_PROVIDER,
                request,
                mainExecutor,
                locationListener,
            )
        } catch (_: SecurityException) {
            showPermissionRequiredState()
        }
    }

    private fun showPermissionRequiredState() {
        latitudeValue.text = getString(R.string.coordinate_unknown)
        longitudeValue.text = getString(R.string.coordinate_unknown)
        lockStatusValue.text = getString(R.string.lock_status_permission_required)
        permissionButton.visibility = View.VISIBLE
        permissionButton.isEnabled = true
    }

    private fun showLocation(location: Location) {
        latitudeValue.text = formatCoordinate(location.latitude)
        longitudeValue.text = formatCoordinate(location.longitude)
        lockStatusValue.text = statusText(true)
    }

    private fun formatCoordinate(value: Double): String {
        return String.format(Locale.US, "%.6f", value)
    }

    private fun statusText(locked: Boolean): String {
        return if (locked) {
            getString(R.string.lock_status_locked)
        } else {
            getString(R.string.lock_status_searching)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                showPermissionRequiredState()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_LOCATION = 1001
    }
}
