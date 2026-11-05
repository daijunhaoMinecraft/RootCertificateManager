package com.daijunhao.certificatemanager.data.cert

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale
import javax.naming.ldap.LdapName
import javax.security.auth.x500.X500Principal

data class ManagedCertificate(
    val fileName: String,
    val commonName: String,
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val notBefore: Long,
    val notAfter: Long,
    val fingerprintSha256: String,
    val isCa: Boolean,
    val isSelfSigned: Boolean,
    val keyAlgorithm: String,
    val signatureAlgorithm: String,
    val pem: String,
) {
    val displayName: String
        get() = commonName.ifBlank { subject }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now > notAfter

    fun isNotYetValid(now: Long = System.currentTimeMillis()): Boolean = now < notBefore
}

fun X509Certificate.isCertificateAuthority(): Boolean {
    return basicConstraints >= 0 || isSelfSignedCertificate()
}

fun X509Certificate.isSelfSignedCertificate(): Boolean {
    if (subjectX500Principal != issuerX500Principal) return false
    return runCatching { verify(publicKey) }.isSuccess
}

fun X509Certificate.toManagedCertificate(fileName: String): ManagedCertificate {
    val normalizedSubject = normalizePrincipal(subjectX500Principal)
    val normalizedIssuer = normalizePrincipal(issuerX500Principal)
    return ManagedCertificate(
        fileName = fileName,
        commonName = readCommonName(subjectX500Principal) ?: normalizedSubject,
        subject = normalizedSubject,
        issuer = normalizedIssuer,
        serialNumber = serialNumber.toString(16).uppercase(Locale.ROOT),
        notBefore = notBefore.time,
        notAfter = notAfter.time,
        fingerprintSha256 = fingerprintSha256(),
        isCa = basicConstraints >= 0,
        isSelfSigned = isSelfSignedCertificate(),
        keyAlgorithm = publicKey.algorithm,
        signatureAlgorithm = sigAlgName,
        pem = PemCodec.encode(this),
    )
}

fun X509Certificate.fingerprintSha256Raw(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
    return digest.joinToString(separator = "") { byte ->
        "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
    }
}

fun X509Certificate.fingerprintSha256(): String {
    return fingerprintSha256Raw()
        .chunked(2)
        .joinToString(separator = ":")
        .uppercase(Locale.ROOT)
}

private fun normalizePrincipal(principal: X500Principal): String {
    return principal.getName(X500Principal.RFC2253)
}

private fun readCommonName(principal: X500Principal): String? {
    return runCatching {
        LdapName(principal.name).rdns
            .asReversed()
            .firstOrNull { it.type.equals("CN", ignoreCase = true) }
            ?.value
            ?.toString()
    }.getOrNull()
}
