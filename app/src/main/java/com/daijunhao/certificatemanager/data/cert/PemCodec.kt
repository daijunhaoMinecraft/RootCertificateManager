package com.daijunhao.certificatemanager.data.cert

import java.security.cert.X509Certificate
import java.util.Base64

object PemCodec {
    fun encode(certificate: X509Certificate): String = encode(certificate.encoded)

    fun encode(certificateDer: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray(Charsets.US_ASCII))
            .encodeToString(certificateDer)
        return buildString(body.length + 64) {
            append("-----BEGIN CERTIFICATE-----\n")
            append(body)
            append("\n-----END CERTIFICATE-----\n")
        }
    }

    fun decodeBlocks(pemText: String): List<ByteArray> {
        return PEM_CERTIFICATE.findAll(pemText).mapNotNull { match ->
            runCatching {
                val body = match.groupValues[1].filterNot(Char::isWhitespace)
                Base64.getDecoder().decode(body)
            }.getOrNull()
        }.toList()
    }

    private val PEM_CERTIFICATE = Regex(
        pattern = "-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    )
}
