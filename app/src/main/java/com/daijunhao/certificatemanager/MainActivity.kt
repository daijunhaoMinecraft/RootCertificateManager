package com.daijunhao.certificatemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daijunhao.certificatemanager.ui.CertificateManagerApp
import com.daijunhao.certificatemanager.ui.theme.CertificateManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CertificateManagerTheme {
                CertificateManagerApp()
            }
        }
    }
}
