package com.daijunhao.certificatemanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daijunhao.certificatemanager.R
import com.daijunhao.certificatemanager.data.cert.ManagedCertificate
import com.daijunhao.certificatemanager.data.root.RootEnvironment
import com.daijunhao.certificatemanager.ui.CertificateManagerUiState

private const val TAB_STATUS = 0
private const val TAB_CERTIFICATES = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: CertificateManagerUiState,
    environment: RootEnvironment,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onInfo: () -> Unit,
    onImport: () -> Unit,
    onInstallModule: () -> Unit,
    onEnableModule: () -> Unit,
    onSelectCertificate: (ManagedCertificate) -> Unit,
    onExportCertificate: (ManagedCertificate) -> Unit,
    onDeleteCertificate: (ManagedCertificate) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_STATUS) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val busy = state.isBusy || state.isRefreshing || state.isLoadingCertificates

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = state.busyMessage ?: stringResource(
                                    if (selectedTab == TAB_STATUS) {
                                        R.string.status_title
                                    } else {
                                        R.string.certificates_tab_title
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !busy,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                            )
                        }
                        IconButton(onClick = onInfo) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.info),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == TAB_STATUS,
                    onClick = { selectedTab = TAB_STATUS },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Dashboard,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.nav_status)) },
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_CERTIFICATES,
                    onClick = { selectedTab = TAB_CERTIFICATES },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.nav_certificates)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == TAB_CERTIFICATES && state.isModuleReady) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!busy) onImport()
                    },
                    modifier = Modifier.alpha(if (busy) 0.55f else 1f),
                    icon = {
                        if (state.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.FileUpload,
                                contentDescription = null,
                            )
                        }
                    },
                    text = { Text(stringResource(R.string.import_certificate)) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                TAB_STATUS -> StatusScreen(
                    state = state,
                    environment = environment,
                    onInstallModule = onInstallModule,
                    onEnableModule = onEnableModule,
                    onViewCertificates = { selectedTab = TAB_CERTIFICATES },
                )

                else -> CertificatesTab(
                    state = state,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onGoToStatus = { selectedTab = TAB_STATUS },
                    onImport = onImport,
                    onSelectCertificate = onSelectCertificate,
                    onExportCertificate = onExportCertificate,
                    onDeleteCertificate = onDeleteCertificate,
                )
            }
        }
    }
}
