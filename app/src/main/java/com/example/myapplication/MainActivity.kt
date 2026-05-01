package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import com.example.myapplication.ppg.BeatFeedbackController

class MainActivity : ComponentActivity() {

    private var monitorViewModel: MonitorViewModel? = null

    class MonitorViewModelFactory(
        private val cameraController: Camera2PpgController,
        private val feedbackController: BeatFeedbackController,
        private val context: android.content.Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MonitorViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MonitorViewModel(cameraController, feedbackController, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            try {
                monitorViewModel?.start()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting after permission", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val cameraController: Camera2PpgController
        val feedbackController: BeatFeedbackController
        
        try {
            cameraController = Camera2PpgController(this)
            feedbackController = BeatFeedbackController(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating controllers", e)
            return
        }
        
        val factory = MonitorViewModelFactory(cameraController, feedbackController, this)

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
