package com.daijunhao.certificatemanager.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daijunhao.certificatemanager.ui.components.CertificateDetailsSheet
import com.daijunhao.certificatemanager.ui.components.DeleteCertificateDialog
import com.daijunhao.certificatemanager.ui.components.ImportSummaryDialog
import com.daijunhao.certificatemanager.ui.components.InfoSheet
import com.daijunhao.certificatemanager.ui.components.ModuleLogDialog
import com.daijunhao.certificatemanager.ui.components.P12PasswordDialog
import com.daijunhao.certificatemanager.ui.screens.MainScreen
import com.daijunhao.certificatemanager.ui.screens.RootGateScreen

@Composable
fun CertificateManagerApp(
    viewModel: CertificateManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var selectedCertificateName by rememberSaveable { mutableStateOf<String?>(null) }
    var certificateToDeleteName by rememberSaveable { mutableStateOf<String?>(null) }
    var exportTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }

    val selectedCertificate = state.certificates.firstOrNull {
        it.fileName == selectedCertificateName
    }
    val certificateToDelete = state.certificates.firstOrNull {
        it.fileName == certificateToDeleteName
    }
    val exportTarget = state.certificates.firstOrNull {
        it.fileName == exportTargetName
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase !is AppPhase.Ready) {
            selectedCertificateName = null
            certificateToDeleteName = null
            exportTargetName = null
            showInfo = false
            showLog = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.prepareImport(uris)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pem-file"),
    ) { uri ->
        val target = exportTarget
        if (uri != null && target != null) {
            viewModel.exportCertificate(target, uri)
        }
        exportTargetName = null
    }

    when (val phase = state.phase) {
        AppPhase.CheckingRoot, is AppPhase.RootDenied -> {
            RootGateScreen(
                phase = phase,
                onRetry = viewModel::retryRoot,
                onExit = { context.findActivity()?.finish() },
            )
        }

        AppPhase.Ready -> {
            val environment = state.environment
            if (environment == null) {
                RootGateScreen(
                    phase = AppPhase.CheckingRoot,
                    onRetry = viewModel::retryRoot,
                    onExit = { context.findActivity()?.finish() },
                )
            } else {
                MainScreen(
                    state = state,
                    environment = environment,
                    snackbarHostState = snackbarHostState,
                    onRefresh = viewModel::refresh,
                    onInfo = { showInfo = true },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onInstallModule = viewModel::installModule,
                    onEnableModule = viewModel::enableModule,
                    onSelectCertificate = { selectedCertificateName = it.fileName },
                    onExportCertificate = { certificate ->
                        exportTargetName = certificate.fileName
                        exportLauncher.launch(suggestedExportName(certificate))
                    },
                    onDeleteCertificate = { certificateToDeleteName = it.fileName },
                )

                if (showInfo) {
                    InfoSheet(
                        environment = environment,
                        onDismiss = { showInfo = false },
                        onViewLog = {
                            showInfo = false
                            showLog = true
                            viewModel.loadModuleLog()
                        },
                    )
                }

                selectedCertificate?.let { certificate ->
                    CertificateDetailsSheet(
                        certificate = certificate,
                        onDismiss = { selectedCertificateName = null },
                        onExport = {
                            exportTargetName = certificate.fileName
                            exportLauncher.launch(suggestedExportName(certificate))
                        },
                        onDelete = { certificateToDeleteName = certificate.fileName },
                    )
                }

                certificateToDelete?.let { certificate ->
                    DeleteCertificateDialog(
                        certificate = certificate,
                        onDismiss = { certificateToDeleteName = null },
                        onConfirm = {
                            certificateToDeleteName = null
                            if (selectedCertificateName == certificate.fileName) {
                                selectedCertificateName = null
                            }
                            viewModel.deleteCertificate(certificate)
                        },
                    )
                }

                state.importSummary?.let { summary ->
                    ImportSummaryDialog(
                        summary = summary,
                        onDismiss = viewModel::dismissImportSummary,
                    )
                }
            }
        }
    }

    if (state.showP12PasswordDialog) {
        P12PasswordDialog(
            onDismiss = viewModel::cancelPendingImport,
            onConfirm = viewModel::submitP12Password,
        )
    }

    if (showLog) {
        ModuleLogDialog(
            logText = state.logText,
            isLoading = state.isLogLoading,
            onDismiss = { showLog = false },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
