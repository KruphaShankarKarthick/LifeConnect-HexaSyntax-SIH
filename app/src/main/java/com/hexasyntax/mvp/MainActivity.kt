package com.hexasyntax.mvp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var etContact: EditText
    private lateinit var switchDetect: SwitchCompat
    private lateinit var btnSimulate: Button
    private lateinit var tvStatus: TextView

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var gravity = FloatArray(3) { 0f }
    private val alpha = 0.8f

    private var countdownTimer: CountDownTimer? = null
    private var dialog: AlertDialog? = null

    companion object {
        private const val TAG = "HexaSyntax"
        private const val CRASH_THRESHOLD = 25f   // tuned threshold (m/s^2 of linear acceleration)
        private const val COUNTDOWN_MS = 30_000L  // 30 seconds
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val sms = perms[Manifest.permission.SEND_SMS] ?: false
        if (!fine || !sms) {
            Toast.makeText(this, "Permissions are required for crash alerts.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etContact = findViewById(R.id.etContact)
        switchDetect = findViewById(R.id.switchDetect)
        btnSimulate = findViewById(R.id.btnSimulate)
        tvStatus = findViewById(R.id.tvStatus)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Request permissions on start (user will still have to accept runtime dialog)
        requestPermissionsIfNeeded()

        switchDetect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startDetection() else stopDetection()
        }

        btnSimulate.setOnClickListener {
            triggerCrashSequence()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.SEND_SMS)
        }
        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startDetection() {
        accelSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            tvStatus.text = "Status: detection enabled"
        } ?: run {
            tvStatus.text = "Status: accelerometer not available"
        }
    }

    private fun stopDetection() {
        sensorManager.unregisterListener(this)
        tvStatus.text = "Status: detection disabled"
    }

    override fun onResume() {
        super.onResume()
        if (switchDetect.isChecked) startDetection()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        countdownTimer?.cancel()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // simple high-pass filter to remove gravity
        gravity[0] = alpha * gravity[0] + (1 - alpha) * x
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z

        val linearX = x - gravity[0]
        val linearY = y - gravity[1]
        val linearZ = z - gravity[2]

        val linAccelMag = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        if (linAccelMag > CRASH_THRESHOLD) {
            Log.d(TAG, "Crash-like acceleration detected: $linAccelMag")
            // Debounce: stop listening briefly to avoid multiples
            sensorManager.unregisterListener(this)
            triggerCrashSequence()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // not used
    }

    private fun triggerCrashSequence() {
        val contact = etContact.text.toString().trim()
        if (contact.isEmpty()) {
            Toast.makeText(this, "Please enter an emergency contact number first.", Toast.LENGTH_LONG).show()
            return
        }

        // Confirmation dialog with cancel and countdown
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Possible crash detected")
        builder.setMessage("An impact-like event was detected. SMS will be sent in 30 seconds with your last known location unless you cancel.")
        builder.setCancelable(false)
        builder.setPositiveButton("Cancel") { _, _ ->
            countdownTimer?.cancel()
            tvStatus.text = "Status: alert canceled"
            // resume sensor listening if detection still enabled
            if (switchDetect.isChecked) accelSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
        dialog = builder.create()
        dialog?.show()

        tvStatus.text = "Status: alert pending (30 s)"

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(COUNTDOWN_MS, 1000) {
            override fun onTick(ms: Long) {
                val sec = ms / 1000
                tvStatus.text = "Status: alert pending ($sec s)"
            }

            override fun onFinish() {
                dialog?.dismiss()
                tvStatus.text = "Status: sending SMS..."
                getLocationAndSendSms(contact)
            }
        }.start()
    }

    private fun getLocationAndSendSms(contact: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tvStatus.text = "Status: missing location permission"
            requestPermissionsIfNeeded()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val mapsLink = "https://maps.google.com/?q=$lat,$lon"
                    val message = "I may have been in an accident. My last known location: $mapsLink"
                    sendSms(contact, message)
                } else {
                    // fallback if no last location
                    fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                val mapsLink = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                                val message = "I may have been in an accident. My last known location: $mapsLink"
                                sendSms(contact, message)
                            } else {
                                val message = "I may have been in an accident. Location unavailable."
                                sendSms(contact, message)
                            }
                        }
                        .addOnFailureListener {
                            val message = "I may have been in an accident. Location unavailable."
                            sendSms(contact, message)
                        }
                }
            }
            .addOnFailureListener {
                val message = "I may have been in an accident. Location unavailable."
                sendSms(contact, message)
            }
    }

    private fun sendSms(phoneNumber: String, text: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            tvStatus.text = "Status: missing SEND_SMS permission"
            requestPermissionsIfNeeded()
            return
        }
        try {
            val sms = SmsManager.getDefault()
            sms.sendTextMessage(phoneNumber, null, text, null, null)
            tvStatus.text = "Status: SMS sent"
        } catch (e: Exception) {
            tvStatus.text = "Status: SMS failed: ${e.message}"
            e.printStackTrace()
        } finally {
            // resume sensor listening if detection still enabled
            if (switchDetect.isChecked) accelSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }
}
