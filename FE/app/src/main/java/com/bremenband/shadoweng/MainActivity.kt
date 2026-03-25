package com.bremenband.shadoweng

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bremenband.shadoweng.ui.theme.ShadowengTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var showPermissionDialog by mutableStateOf(false)

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) showPermissionDialog = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        setContent {
            ShadowengTheme {
                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionDialog = false },
                        title = { Text("마이크 권한 필요") },
                        text = { Text("발음 피드백을 위해 마이크 권한이 필요합니다.\n설정에서 허용해주세요.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showPermissionDialog = false
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                })
                            }) { Text("설정으로 이동") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionDialog = false }) { Text("취소") }
                        }
                    )
                }
                MainScreen()
            }
        }
    }
}