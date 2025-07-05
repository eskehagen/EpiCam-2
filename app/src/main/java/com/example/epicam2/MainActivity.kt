package com.example.epicam2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var countdownText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var captureButton: Button
    private lateinit var previewImage: ImageView
    private lateinit var saveButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var menuButton: ImageButton
    private var lastPhotoFile: File? = null
    private val countdownTime = 3

    // Bluetooth fields
    private lateinit var bluetoothManager: BluetoothManager

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
        bluetoothManager = BluetoothManager(this)
        onRequestPermissionsResult(REQUEST_CODE_PERMISSIONS, REQUIRED_PERMISSIONS, intArrayOf(PackageManager.PERMISSION_GRANTED))

        // Init components
        captureButton = findViewById<Button>(R.id.camera_capture_button)
        countdownText = findViewById(R.id.countdown_text)
        previewImage = findViewById(R.id.preview_image)
        saveButton = findViewById(R.id.save_button)
        deleteButton = findViewById(R.id.delete_button)
        menuButton = findViewById(R.id.menuButton)

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

        // Menu button Click
        menuButton.setOnClickListener {
            SettingsDialogHelper.showPinDialog(this) {
                SettingsDialogHelper.showSettingsDialog(this)
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCountdown() {
        var count = countdownTime
        countdownText.visibility = View.VISIBLE

        // Light ON
        bluetoothManager.sendLampCommand(true)

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
                    val mirroredImage = mirrorBitmap(bitmap)
                    previewImage.setImageBitmap(mirroredImage)
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
        bluetoothManager.sendLampCommand(false)
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

                // Connect Bluetooth
                bluetoothManager.connectBle()
            } else {
                Toast.makeText(this, "Bluetooth and camera need permissions", Toast.LENGTH_LONG).show()
            }
        }
    }

    /// Mirror bitmap (taken selfie image)
    private fun mirrorBitmap(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /// On Destroy application
    override fun onDestroy() {
        bluetoothManager.disconnectBle()
        cameraExecutor.shutdown()

        super.onDestroy()
    }

    /// Companion Object
    /// Set up permissions
    companion object {
        private const val TAG = "EPICAM-2"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).toTypedArray()
    }

}
