package com.example.epicam2

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val esp32MacAddress = "00:11:22:33:44:55" // Udskift med ESP32 MAC-adresse
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private lateinit var countdownText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var captureButton: Button
    private lateinit var previewImage: ImageView
    private lateinit var saveButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private var lastPhotoFile: File? = null

    // Bluetooth fields
    private lateinit var bluetoothHelper: BluetoothHelper
    private val requestCode = 1001
    private val bluetoothPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

    /// On Create (Constructor)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Start the camera function
        startCamera()

        // Initialize Bluetooth helper
        bluetoothHelper = BluetoothHelper(this)
        checkAndRequestBluetoothPermissions()

        // Init components
        captureButton = findViewById<Button>(R.id.camera_capture_button)
        countdownText = findViewById(R.id.countdown_text)
        previewImage = findViewById(R.id.preview_image)
        saveButton = findViewById(R.id.save_button)
        deleteButton = findViewById(R.id.delete_button)

        // Capture button
        captureButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse).apply {
            repeatCount = Animation.INFINITE
            }
        )

        // Capture button Click
        captureButton.setOnClickListener {
            captureButton.clearAnimation() // Stop animation
            captureButton.visibility = View.GONE // Hide button
            startCountdown()
        }

        // Save button Click
        saveButton.setOnClickListener {
            if (lastPhotoFile != null && lastPhotoFile!!.exists()) {
                Toast.makeText(this, "Billede gemt", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ingen fil at gemme", Toast.LENGTH_SHORT).show()
            }

            // Fade out animation
            val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
            previewImage.startAnimation(fadeOut)

            fadeOut.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    previewImage.visibility = View.GONE
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })

            saveButton.visibility = View.GONE
            deleteButton.visibility = View.GONE

            captureButton.visibility = View.VISIBLE
            captureButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse).apply {
                repeatCount = Animation.INFINITE
                }
            )
        }

        // Delete button Click
        deleteButton.setOnClickListener {
            lastPhotoFile?.delete()
            Toast.makeText(this, "Billede slettet", Toast.LENGTH_SHORT).show()

            // Fade out animation
            val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
            previewImage.startAnimation(fadeOut)

            fadeOut.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    previewImage.visibility = View.GONE
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })

            saveButton.visibility = View.GONE
            deleteButton.visibility = View.GONE

            captureButton.visibility = View.VISIBLE
            captureButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse).apply {
                    repeatCount = Animation.INFINITE
                }
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCountdown() {
        var count = 3
        countdownText.visibility = View.VISIBLE

        // Light ON
        bluetoothHelper.sendLampCommand(true)

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (count > 0) {
                    countdownText.text = count.toString()
                    count--
                    handler.postDelayed(this, 1000)
                } else {
                    countdownText.visibility = View.GONE
                    takePhoto()
                }
            }
        }
        handler.post(countdownRunnable)
    }

    private fun takePhoto() {
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        if (!dcimDir.exists()) {
            dcimDir.mkdirs()
        }

        val photoFile = File(dcimDir,
            "SELFIE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        )

        val fadeInFx = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val outputOptions = OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    lastPhotoFile = photoFile
                    showImagePreview(photoFile)

                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    previewImage.setImageBitmap(bitmap)
                    previewImage.visibility = View.VISIBLE
                    previewImage.startAnimation(fadeInFx)
                    saveButton.visibility = View.VISIBLE
                    deleteButton.visibility = View.VISIBLE
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Fejl ved lagring af billede", Toast.LENGTH_SHORT).show()
                    exception.printStackTrace()
                }
            }
        )
    }

    /// Show image preview
    private fun showImagePreview(photoFile: File) {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        val previewImage = findViewById<ImageView>(R.id.preview_image)
        val saveButton = findViewById<ImageButton>(R.id.save_button)
        val deleteButton = findViewById<ImageButton>(R.id.delete_button)

        previewImage.setImageBitmap(bitmap)
        previewImage.visibility = View.VISIBLE
        saveButton.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE

        // Light OFF
        bluetoothHelper.sendLampCommand(false)
    }

    /// Start Camera
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.viewFinder).surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /// All permissions are granted - check
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /// On Request Permissions Result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth and camera need permissions", Toast.LENGTH_LONG).show()
            }
        }
    }

    /// Check and request Bluetooth Permissions
    private fun checkAndRequestBluetoothPermissions() {
        val missingPermissions = bluetoothPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), requestCode)
        }
        else {
            // Safe to start scanning
            bluetoothHelper.connectBle()
        }
    }

    /// On Destroy application
    override fun onDestroy() {
        super.onDestroy()
        bluetoothSocket?.close()
        cameraExecutor.shutdown()
    }

    /// Companion Object
    /// Set up permissions
    companion object {
        private const val TAG = "EPICAM-2"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
    }

}
