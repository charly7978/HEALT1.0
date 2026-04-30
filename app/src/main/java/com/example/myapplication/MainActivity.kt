package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.myapplication.camera.Camera2Controller
import com.example.myapplication.signal.MotionArtifactDetector
import com.example.myapplication.ui.FullScreenPpgMonitor
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.MonitorViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MonitorViewModel
    private lateinit var motionDetector: MotionArtifactDetector

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.startMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mantener pantalla encendida y pantalla completa
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val cameraController = Camera2Controller(this)
        motionDetector = MotionArtifactDetector(this)
        viewModel = MonitorViewModel(cameraController, motionDetector)

        setContent {
            MyApplicationTheme {
                FullScreenPpgMonitor(viewModel)
            }
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.startMonitoring()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        motionDetector.start()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startMonitoring()
        }
    }

    override fun onPause() {
        super.onPause()
        motionDetector.stop()
        viewModel.stopMonitoring()
    }
}
