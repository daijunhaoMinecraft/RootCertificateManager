package com.daijunhao.certificatemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daijunhao.certificatemanager.R
import com.daijunhao.certificatemanager.data.module.ModulePaths
import com.daijunhao.certificatemanager.data.root.RootEnvironment
import com.daijunhao.certificatemanager.ui.ModuleUiState

@Composable
fun ModuleStatusCard(
    moduleState: ModuleUiState,
    environment: RootEnvironment,
    certificateCount: Int,
    busy: Boolean,
    onInstall: () -> Unit,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = moduleState == ModuleUiState.Ready
    val title = when (moduleState) {
        ModuleUiState.Ready -> stringResource(R.string.module_ready_title)
        ModuleUiState.Missing -> stringResource(R.string.module_missing_title)
        ModuleUiState.Disabled -> stringResource(R.string.module_disabled_title)
        ModuleUiState.Outdated -> stringResource(R.string.module_outdated_title)
    }
    val body = when (moduleState) {
        ModuleUiState.Ready -> stringResource(R.string.module_ready_body, certificateCount)
        ModuleUiState.Missing -> stringResource(R.string.module_missing_body)
        ModuleUiState.Disabled -> stringResource(R.string.module_disabled_body)
        ModuleUiState.Outdated -> stringResource(
            R.string.module_outdated_body,
            environment.moduleVersionName ?: stringResource(R.string.unknown),
            ModulePaths.EXPECTED_VERSION_NAME,
        )
    }
    val icon = when (moduleState) {
        ModuleUiState.Ready -> Icons.Outlined.CheckCircle
        ModuleUiState.Disabled -> Icons.Outlined.WarningAmber
        ModuleUiState.Missing, ModuleUiState.Outdated -> Icons.Outlined.Extension
    }
    val actionLabel = when (moduleState) {
        ModuleUiState.Missing -> stringResource(R.string.module_install)
        ModuleUiState.Outdated -> stringResource(R.string.module_update)
        ModuleUiState.Disabled -> stringResource(R.string.module_enable)
        ModuleUiState.Ready -> null
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (ready) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (environment.moduleInstalled) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.module_version,
                                environment.moduleVersionName ?: stringResource(R.string.unknown),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (actionLabel != null) {
                FilledTonalButton(
                    onClick = {
                        if (moduleState == ModuleUiState.Disabled) onEnable() else onInstall()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(actionLabel)
                }
            } else {
                TextButton(
                    onClick = onInstall,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.module_reinstall))
                }
            }
        }
    }
}

@Composable
fun InlineWarningCard(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun EmptyCertificatesCard(
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
fun SearchEmptyCard(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.search_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    )
}

@Composable
fun RefreshIndicatorCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.loading_certificates),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
