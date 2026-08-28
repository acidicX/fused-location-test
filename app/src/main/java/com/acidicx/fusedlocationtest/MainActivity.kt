package com.acidicx.fusedlocationtest

import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest.Builder
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var latitudeValue: TextView
    private lateinit var longitudeValue: TextView
    private lateinit var lockStatusValue: TextView
    private lateinit var permissionButton: Button
    private lateinit var apiModeGroup: RadioGroup
    private lateinit var providerModeGroup: RadioGroup
    private lateinit var gmsModeGroup: RadioGroup

    private val gmsLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            Log.d(TAG, "GMS onLocationResult lat=${location.latitude}, lon=${location.longitude}")
            showLocation(location)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(
                TAG,
                "Framework onLocationChanged provider=${location.provider}, lat=${location.latitude}, lon=${location.longitude}",
            )
            showLocation(location)
        }

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Framework provider enabled: $provider")
            lockStatusValue.text = getString(R.string.lock_status_searching)
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "Framework provider disabled: $provider")
            lockStatusValue.text = providerUnavailableStatusText(provider)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LocationManager::class.java)
        latitudeValue = findViewById(R.id.latitudeValue)
        longitudeValue = findViewById(R.id.longitudeValue)
        lockStatusValue = findViewById(R.id.lockStatusValue)
        permissionButton = findViewById(R.id.permissionButton)
        apiModeGroup = findViewById(R.id.apiModeGroup)
        providerModeGroup = findViewById(R.id.providerModeGroup)
        gmsModeGroup = findViewById(R.id.gmsModeGroup)

        permissionButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_LOCATION)
        }

        restoreToggleSelections()
        apiModeGroup.setOnCheckedChangeListener { _, _ ->
            saveToggleSelections()
            updateProviderModeAvailability()
            restartLocationUpdatesIfAllowed()
        }
        providerModeGroup.setOnCheckedChangeListener { _, _ ->
            saveToggleSelections()
            restartLocationUpdatesIfAllowed()
        }
        gmsModeGroup.setOnCheckedChangeListener { _, _ ->
            saveToggleSelections()
            restartLocationUpdatesIfAllowed()
        }
        updateProviderModeAvailability()
        showPermissionRequiredState()
    }

    override fun onStart() {
        super.onStart()
        if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    override fun onStop() {
        stopLocationUpdates()
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

        if (!locationManager.isLocationEnabled) {
            lockStatusValue.text = getString(R.string.lock_status_location_off)
            Log.d(TAG, "Location services are disabled")
            return
        }

        stopLocationUpdates()
        val useGmsFused = selectedApiMode() == ApiMode.GMS_FUSED
        val selectedProvider = selectedFrameworkProvider()
        val effectiveProvider = if (useGmsFused) LocationManager.FUSED_PROVIDER else selectedProvider
        Log.d(
            TAG,
            "Starting updates apiMode=${selectedApiMode().name}, requestedProvider=$selectedProvider, effectiveProvider=$effectiveProvider",
        )

        if (useGmsFused) {
            startGmsFusedLocationUpdates()
            return
        }

        if (!locationManager.hasProvider(effectiveProvider)) {
            permissionButton.visibility = View.GONE
            lockStatusValue.text = providerUnavailableStatusText(effectiveProvider)
            Log.w(TAG, "Requested framework provider unavailable: $effectiveProvider")
            return
        }

        permissionButton.visibility = View.GONE
        lockStatusValue.text = getString(R.string.lock_status_searching)

        // Newer fused provider logic only activates GPS for high-accuracy requests at <= 5s.
        val fusedIntervalMillis = minOf(FUSED_REQUEST_INTERVAL_MS, MAX_FUSED_GPS_INTERVAL_MS)
        val request = LocationRequest.Builder(fusedIntervalMillis)
            .setMinUpdateIntervalMillis(minOf(FUSED_REQUEST_MIN_INTERVAL_MS, fusedIntervalMillis))
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()

        try {
            locationManager.getLastKnownLocation(effectiveProvider)?.let {
                Log.d(
                    TAG,
                    "Framework lastKnown provider=$effectiveProvider lat=${it.latitude}, lon=${it.longitude}",
                )
                showLocation(it)
            }
            locationManager.requestLocationUpdates(
                effectiveProvider,
                request,
                mainExecutor,
                locationListener,
            )
            Log.d(TAG, "Framework requestLocationUpdates registered for provider=$effectiveProvider")
        } catch (_: SecurityException) {
            Log.e(TAG, "Framework request failed due to missing permission")
            showPermissionRequiredState()
        }
    }

    private fun showPermissionRequiredState() {
        latitudeValue.text = getString(R.string.coordinate_unknown)
        longitudeValue.text = getString(R.string.coordinate_unknown)
        lockStatusValue.text = getString(R.string.lock_status_permission_required)
        permissionButton.visibility = View.VISIBLE
        permissionButton.isEnabled = true
        stopLocationUpdates()
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

    private fun providerUnavailableStatusText(provider: String): String {
        val providerLabel = when (provider) {
            LocationManager.GPS_PROVIDER -> getString(R.string.provider_mode_gps)
            LocationManager.NETWORK_PROVIDER -> getString(R.string.provider_mode_network)
            LocationManager.FUSED_PROVIDER -> getString(R.string.provider_mode_fused)
            else -> provider
        }
        return getString(R.string.lock_status_provider_unavailable, providerLabel)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted")
                startLocationUpdates()
            } else {
                Log.d(TAG, "Location permission denied")
                showPermissionRequiredState()
            }
        }
    }

    private fun restartLocationUpdatesIfAllowed() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        locationManager.removeUpdates(locationListener)
        fusedLocationClient.removeLocationUpdates(gmsLocationCallback)
    }

    private fun selectedApiMode(): ApiMode {
        return if (apiModeGroup.checkedRadioButtonId == R.id.apiModeGms) {
            ApiMode.GMS_FUSED
        } else {
            ApiMode.FRAMEWORK
        }
    }

    private fun selectedFrameworkProvider(): String {
        return when (providerModeGroup.checkedRadioButtonId) {
            R.id.providerModeGps -> LocationManager.GPS_PROVIDER
            R.id.providerModeNetwork -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.FUSED_PROVIDER
        }
    }

    private fun updateProviderModeAvailability() {
        val useGmsFused = selectedApiMode() == ApiMode.GMS_FUSED
        providerModeGroup.isEnabled = !useGmsFused
        gmsModeGroup.isEnabled = useGmsFused
        for (i in 0 until providerModeGroup.childCount) {
            providerModeGroup.getChildAt(i).isEnabled = !useGmsFused
        }
        for (i in 0 until gmsModeGroup.childCount) {
            gmsModeGroup.getChildAt(i).isEnabled = useGmsFused
        }
        if (useGmsFused) {
            providerModeGroup.check(R.id.providerModeFused)
        }
        saveToggleSelections()
    }

    private fun saveToggleSelections() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(PREF_KEY_API_MODE, apiModeGroup.checkedRadioButtonId)
            .putInt(PREF_KEY_PROVIDER_MODE, providerModeGroup.checkedRadioButtonId)
            .putInt(PREF_KEY_GMS_MODE, gmsModeGroup.checkedRadioButtonId)
            .apply()
    }

    private fun restoreToggleSelections() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedApiMode = prefs.getInt(PREF_KEY_API_MODE, R.id.apiModeFramework)
        val savedProviderMode = prefs.getInt(PREF_KEY_PROVIDER_MODE, R.id.providerModeFused)
        val savedGmsMode = prefs.getInt(PREF_KEY_GMS_MODE, R.id.gmsModeHighAccuracy)

        val apiModeId = when (savedApiMode) {
            R.id.apiModeGms -> R.id.apiModeGms
            else -> R.id.apiModeFramework
        }
        val providerModeId = when (savedProviderMode) {
            R.id.providerModeGps -> R.id.providerModeGps
            R.id.providerModeNetwork -> R.id.providerModeNetwork
            else -> R.id.providerModeFused
        }
        val gmsModeId = when (savedGmsMode) {
            R.id.gmsModeBalanced -> R.id.gmsModeBalanced
            R.id.gmsModeLowPower -> R.id.gmsModeLowPower
            else -> R.id.gmsModeHighAccuracy
        }

        apiModeGroup.check(apiModeId)
        providerModeGroup.check(providerModeId)
        gmsModeGroup.check(gmsModeId)
        Log.d(TAG, "Restored toggles apiModeId=$apiModeId, providerModeId=$providerModeId, gmsModeId=$gmsModeId")
    }

    private fun startGmsFusedLocationUpdates() {
        permissionButton.visibility = View.GONE
        lockStatusValue.text = getString(R.string.lock_status_searching)
        val selectedGmsMode = selectedGmsMode()
        val request = Builder(selectedGmsMode.priority, FUSED_REQUEST_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FUSED_REQUEST_MIN_INTERVAL_MS)
            .setWaitForAccurateLocation(selectedGmsMode == GmsMode.HIGH_ACCURACY)
            .setMaxUpdateDelayMillis(MAX_FUSED_GPS_INTERVAL_MS)
            .build()
        Log.d(TAG, "GMS request mode=${selectedGmsMode.name}, priority=${selectedGmsMode.priority}")

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(
                            TAG,
                            "GMS lastLocation lat=${location.latitude}, lon=${location.longitude}",
                        )
                        showLocation(location)
                    } else {
                        Log.d(TAG, "GMS lastLocation is null")
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "GMS lastLocation failed", error)
                }

            fusedLocationClient.requestLocationUpdates(
                request,
                gmsLocationCallback,
                Looper.getMainLooper(),
            )
            Log.d(TAG, "GMS requestLocationUpdates registered")
        } catch (_: SecurityException) {
            Log.e(TAG, "GMS request failed due to missing permission")
            showPermissionRequiredState()
        }
    }

    private enum class ApiMode {
        FRAMEWORK,
        GMS_FUSED,
    }

    private fun selectedGmsMode(): GmsMode {
        return when (gmsModeGroup.checkedRadioButtonId) {
            R.id.gmsModeBalanced -> GmsMode.BALANCED
            R.id.gmsModeLowPower -> GmsMode.LOW_POWER
            else -> GmsMode.HIGH_ACCURACY
        }
    }

    private enum class GmsMode(val priority: Int) {
        HIGH_ACCURACY(Priority.PRIORITY_HIGH_ACCURACY),
        BALANCED(Priority.PRIORITY_BALANCED_POWER_ACCURACY),
        LOW_POWER(Priority.PRIORITY_LOW_POWER),
    }

    companion object {
        private const val TAG = "FusedLocationTest"
        private const val PREFS_NAME = "location_test_prefs"
        private const val PREF_KEY_API_MODE = "pref_key_api_mode"
        private const val PREF_KEY_PROVIDER_MODE = "pref_key_provider_mode"
        private const val PREF_KEY_GMS_MODE = "pref_key_gms_mode"
        private const val REQUEST_CODE_LOCATION = 1001
        private const val FUSED_REQUEST_INTERVAL_MS = 2_000L
        private const val FUSED_REQUEST_MIN_INTERVAL_MS = 1_000L
        private const val MAX_FUSED_GPS_INTERVAL_MS = 5_000L
    }
}
