package com.daijunhao.certificatemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daijunhao.certificatemanager.R
import com.daijunhao.certificatemanager.data.cert.ManagedCertificate
import com.daijunhao.certificatemanager.ui.CertificateManagerUiState
import com.daijunhao.certificatemanager.ui.components.CertificateCard
import com.daijunhao.certificatemanager.ui.components.EmptyCertificatesCard
import com.daijunhao.certificatemanager.ui.components.RefreshIndicatorCard
import com.daijunhao.certificatemanager.ui.components.SearchEmptyCard

@Composable
fun CertificatesTab(
    state: CertificateManagerUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onGoToStatus: () -> Unit,
    onImport: () -> Unit,
    onSelectCertificate: (ManagedCertificate) -> Unit,
    onExportCertificate: (ManagedCertificate) -> Unit,
    onDeleteCertificate: (ManagedCertificate) -> Unit,
) {
    val filteredCertificates = remember(searchQuery, state.certificates) {
        if (searchQuery.isBlank()) {
            state.certificates
        } else {
            val query = searchQuery.trim()
            state.certificates.filter { certificate ->
                certificate.commonName.contains(query, ignoreCase = true) ||
                    certificate.subject.contains(query, ignoreCase = true) ||
                    certificate.issuer.contains(query, ignoreCase = true) ||
                    certificate.fingerprintSha256.contains(query, ignoreCase = true) ||
                    certificate.fileName.contains(query, ignoreCase = true)
            }
        }
    }
    val emptyActionLabel = if (state.isModuleReady) {
        stringResource(R.string.import_certificate)
    } else {
        stringResource(R.string.go_to_status)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(bottom = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.managed_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.managed_count, state.certificates.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.certificates_tab_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.isModuleReady && state.certificates.isNotEmpty()) {
            item(key = "module-not-ready") {
                ModuleNotReadyNotice(onGoToStatus = onGoToStatus)
            }
        }

        if (state.isLoadingCertificates) {
            item(key = "loading") {
                RefreshIndicatorCard()
            }
        } else if (state.certificates.isEmpty()) {
            item(key = "empty") {
                EmptyCertificatesCard(
                    actionLabel = emptyActionLabel,
                    onAction = if (state.isModuleReady) onImport else onGoToStatus,
                )
            }
        } else {
            item(key = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.search_clear),
                                )
                            }
                        }
                    },
                    singleLine = true,
                )
            }

            if (filteredCertificates.isEmpty()) {
                item(key = "search-empty") {
                    SearchEmptyCard()
                }
            } else {
                items(
                    items = filteredCertificates,
                    key = { it.fileName },
                ) { certificate ->
                    CertificateCard(
                        certificate = certificate,
                        onClick = { onSelectCertificate(certificate) },
                        onExport = { onExportCertificate(certificate) },
                        onDelete = { onDeleteCertificate(certificate) },
                    )
                }
            }
        }

        item(key = "footer") {
            Text(
                text = stringResource(R.string.changes_require_reboot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ModuleNotReadyNotice(
    onGoToStatus: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.module_not_ready_cert_tab_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.module_not_ready_cert_tab_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onGoToStatus) {
                Text(stringResource(R.string.go_to_status))
            }
        }
    }
}
