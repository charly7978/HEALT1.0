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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.MonitorScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.MonitorViewModel
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import com.example.myapplication.camera.Camera2PpgController
import com.example.myapplication.haptics.BeatFeedbackController

class MainActivity : ComponentActivity() {

    private var monitorViewModel: MonitorViewModel? = null

    class MonitorViewModelFactory(
        private val cameraController: Camera2PpgController,
        private val feedbackController: BeatFeedbackController
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MonitorViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MonitorViewModel(cameraController, feedbackController) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            monitorViewModel?.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val cameraController = Camera2PpgController(this)
        val feedbackController = BeatFeedbackController(this)
        val factory = MonitorViewModelFactory(cameraController, feedbackController)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val vm: MonitorViewModel = viewModel(factory = factory)
                monitorViewModel = vm
                MonitorScreen(vm)
            }
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            monitorViewModel?.start()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            monitorViewModel?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        monitorViewModel?.stop()
    }
}
