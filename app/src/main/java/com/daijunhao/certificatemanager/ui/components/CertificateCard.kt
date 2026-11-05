package com.daijunhao.certificatemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daijunhao.certificatemanager.R
import com.daijunhao.certificatemanager.data.cert.ManagedCertificate
import com.daijunhao.certificatemanager.ui.CertificateValidity
import com.daijunhao.certificatemanager.ui.formatDate
import com.daijunhao.certificatemanager.ui.validity

@Composable
fun CertificateCard(
    certificate: ManagedCertificate,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val certificateValidity = certificate.validity()
    val hasProblem = certificateValidity != CertificateValidity.Valid
    val statusText = when (certificateValidity) {
        CertificateValidity.Valid -> stringResource(R.string.status_valid)
        CertificateValidity.Expired -> stringResource(R.string.status_expired)
        CertificateValidity.NotYetValid -> stringResource(R.string.status_not_yet_valid)
    }
    val validityText = when (certificateValidity) {
        CertificateValidity.Expired -> stringResource(
            R.string.validity_expired,
            formatDate(certificate.notAfter),
        )

        CertificateValidity.NotYetValid -> stringResource(
            R.string.validity_starts,
            formatDate(certificate.notBefore),
        )

        CertificateValidity.Valid -> stringResource(
            R.string.validity_until,
            formatDate(certificate.notAfter),
        )
    }
    val caLabel = if (certificate.isCa) stringResource(R.string.badge_ca) else null
    val selfSignedLabel = if (certificate.isSelfSigned) {
        stringResource(R.string.badge_self_signed)
    } else {
        null
    }
    val details = listOfNotNull(statusText, caLabel, selfSignedLabel).joinToString(" · ")

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = stringResource(R.string.certificate_icon),
                tint = if (hasProblem) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(23.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = certificate.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = certificate.issuer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasProblem) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = validityText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_pem)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.delete_certificate),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
