package com.daijunhao.certificatemanager.ui

import com.daijunhao.certificatemanager.data.cert.ManagedCertificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CertificateValidity {
    Valid,
    Expired,
    NotYetValid,
}

fun ManagedCertificate.validity(now: Long = System.currentTimeMillis()): CertificateValidity {
    return when {
        now > notAfter -> CertificateValidity.Expired
        now < notBefore -> CertificateValidity.NotYetValid
        else -> CertificateValidity.Valid
    }
}

fun formatDate(epochMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis))
}

fun formatDateTime(epochMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}

fun suggestedExportName(certificate: ManagedCertificate): String {
    val sanitized = certificate.displayName
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('.')
        .take(80)
        .ifBlank { certificate.fileName }
    return "$sanitized.pem"
}
