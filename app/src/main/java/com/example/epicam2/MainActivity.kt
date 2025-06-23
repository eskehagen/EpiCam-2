package com.example.epicam2
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
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
    private val esp32MacAddress = "00:11:22:33:44:55" // Udskift med ESP32 MAC
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID

    private lateinit var countdownText: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        startCamera()

        countdownText = findViewById(R.id.countdown_text)
        val captureButton = findViewById<Button>(R.id.camera_capture_button)
        captureButton.setOnClickListener {
            startCountdown()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        //connectToESP32()

    }

    private fun startCountdown() {
        var count = 3
        countdownText.visibility = View.VISIBLE
        val countdownRunnable = object : Runnable {
            override fun run() {
                if (count > 0) {
                    countdownText.text = count.toString()
                    count--
                    handler.postDelayed(this, 1000)
                } else {
                    countdownText.visibility = View.GONE
                    sendBluetoothSignal()
                    takePhoto()
                }
            }
        }
        handler.post(countdownRunnable)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToESP32() {
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(esp32MacAddress)
        try {
            if (device != null) {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            }
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            Toast.makeText(this, "Bluetooth connected", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Bluetooth failed to connect!", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendBluetoothSignal() {
        try {
            outputStream?.write("LIGHT_ON\n".toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun takePhoto() {
        // Directory on the device for the photos
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        if (!dcimDir.exists()) {
            dcimDir.mkdirs()
        }

        val photoFile = File(
            dcimDir,
            "SELFIE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        )

        val outputOptions = OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    //Toast.makeText(baseContext, "Billede gemt!", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    //Toast.makeText(baseContext, "Fejl ved lagring af billede", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

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
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothSocket?.close()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "EPICAM-2"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

}
