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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daijunhao.certificatemanager.R
import com.daijunhao.certificatemanager.data.root.RootEnvironment
import com.daijunhao.certificatemanager.ui.CertificateManagerUiState
import com.daijunhao.certificatemanager.ui.components.InlineWarningCard
import com.daijunhao.certificatemanager.ui.components.ModuleStatusCard

@Composable
fun StatusScreen(
    state: CertificateManagerUiState,
    environment: RootEnvironment,
    onInstallModule: () -> Unit,
    onEnableModule: () -> Unit,
    onViewCertificates: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "environment-title") {
            SectionTitle(stringResource(R.string.status_section_environment))
        }
        item(key = "environment") {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    StatusRow(
                        label = stringResource(R.string.status_root_access),
                        value = stringResource(R.string.status_authorized),
                        valueIsPrimary = true,
                    )
                    HorizontalDivider()
                    StatusRow(
                        label = stringResource(R.string.status_root_environment),
                        value = stringResource(
                            if (environment.isKernelSu) {
                                R.string.kernelsu_badge
                            } else {
                                R.string.root_other_badge
                            },
                        ),
                    )
                    HorizontalDivider()
                    StatusRow(
                        label = stringResource(R.string.status_android_version),
                        value = stringResource(R.string.android_badge, environment.sdkInt),
                    )
                    HorizontalDivider()
                    StatusRow(
                        label = stringResource(R.string.status_certificate_directory),
                        value = environment.systemCertificatePath,
                        monospace = true,
                    )
                }
            }
        }

        item(key = "module-title") {
            SectionTitle(
                text = stringResource(R.string.status_section_injection),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        item(key = "module") {
            ModuleStatusCard(
                moduleState = state.moduleState,
                environment = environment,
                certificateCount = state.certificates.size,
                busy = state.isBusy,
                onInstall = onInstallModule,
                onEnable = onEnableModule,
            )
        }

        if (!environment.isKernelSu) {
            item(key = "ksu-warning") {
                InlineWarningCard(text = stringResource(R.string.warning_kernelsu_missing))
            }
        }
        if (state.invalidFileCount > 0) {
            item(key = "invalid-warning") {
                InlineWarningCard(
                    text = stringResource(R.string.invalid_files_warning, state.invalidFileCount),
                    isError = true,
                )
            }
        }

        item(key = "certificates-title") {
            SectionTitle(
                text = stringResource(R.string.status_section_certificates),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        item(key = "certificates") {
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
                            text = stringResource(R.string.managed_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = stringResource(
                                R.string.status_certificate_count,
                                state.certificates.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onViewCertificates) {
                        Text(stringResource(R.string.status_view_certificates))
                    }
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
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    valueIsPrimary: Boolean = false,
) {
    if (monospace) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        SelectionContainer(modifier = Modifier.weight(0.62f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (valueIsPrimary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}
