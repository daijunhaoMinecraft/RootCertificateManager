package com.daijunhao.certificatemanager.data.cert

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale

/**
 * Android's CA store uses the legacy OpenSSL subject hash as the file name:
 * MD5(subject DER), with the first four digest bytes read as a little-endian
 * 32-bit integer. The file is then named "hash.0", "hash.1", and so on.
 */
object CertificateHasher {
    fun subjectHashOld(certificate: X509Certificate): String =
        subjectHashOld(certificate.encoded)

    fun subjectHashOld(certificateDer: ByteArray): String {
        val subjectDer = extractSubjectDer(certificateDer)
        val digest = MessageDigest.getInstance("MD5").digest(subjectDer)
        val value = (digest[0].toInt() and 0xff) or
            ((digest[1].toInt() and 0xff) shl 8) or
            ((digest[2].toInt() and 0xff) shl 16) or
            ((digest[3].toInt() and 0xff) shl 24)
        return String.format(Locale.ROOT, "%08x", value)
    }

    internal fun extractSubjectDer(certificateDer: ByteArray): ByteArray {
        val certificate = DerTlv.read(certificateDer, 0, certificateDer.size)
        require(certificate.tag == TAG_SEQUENCE) { "证书不是有效的 DER SEQUENCE" }

        val tbsCertificate = DerTlv.read(certificateDer, certificate.contentStart, certificate.end)
        require(tbsCertificate.tag == TAG_SEQUENCE) { "TBSCertificate 结构无效" }

        var cursor = tbsCertificate.contentStart
        if (cursor < tbsCertificate.end && (certificateDer[cursor].toInt() and 0xff) == TAG_VERSION) {
            cursor = DerTlv.read(certificateDer, cursor, tbsCertificate.end).end
        }

        cursor = skip(certificateDer, cursor, tbsCertificate.end, TAG_INTEGER)
        cursor = skip(certificateDer, cursor, tbsCertificate.end, TAG_SEQUENCE)
        cursor = skip(certificateDer, cursor, tbsCertificate.end, TAG_SEQUENCE)
        cursor = skip(certificateDer, cursor, tbsCertificate.end, TAG_SEQUENCE)

        val subject = DerTlv.read(certificateDer, cursor, tbsCertificate.end)
        require(subject.tag == TAG_SEQUENCE) { "证书 Subject 结构无效" }
        return certificateDer.copyOfRange(subject.start, subject.end)
    }

    private fun skip(bytes: ByteArray, offset: Int, limit: Int, expectedTag: Int): Int {
        val value = DerTlv.read(bytes, offset, limit)
        require(value.tag == expectedTag) { "证书字段标签无效" }
        return value.end
    }

    private data class DerTlv(
        val tag: Int,
        val start: Int,
        val contentStart: Int,
        val end: Int,
    ) {
        companion object {
            fun read(bytes: ByteArray, offset: Int, limit: Int): DerTlv {
                require(offset >= 0 && offset + 2 <= limit) { "DER 数据不完整" }
                var cursor = offset
                val tag = bytes[cursor].toInt() and 0xff
                cursor++

                if (tag and 0x1f == 0x1f) {
                    while (cursor < limit) {
                        val next = bytes[cursor].toInt() and 0xff
                        cursor++
                        if (next and 0x80 == 0) break
                    }
                }

                require(cursor < limit) { "DER 长度字段缺失" }
                val firstLength = bytes[cursor].toInt() and 0xff
                cursor++

                val contentLength = when {
                    firstLength < 0x80 -> firstLength
                    firstLength == 0x80 -> error("不支持 BER 不定长编码")
                    else -> {
                        val count = firstLength and 0x7f
                        require(count in 1..4) { "DER 长度字段无效" }
                        require(cursor + count <= limit) { "DER 长度字段不完整" }
                        var length = 0
                        repeat(count) {
                            length = (length shl 8) or (bytes[cursor].toInt() and 0xff)
                            cursor++
                        }
                        length
                    }
                }

                require(contentLength >= 0) { "DER 长度无效" }
                val contentStart = cursor
                val end = contentStart + contentLength
                require(end <= limit) { "DER 内容超出边界" }
                return DerTlv(tag = tag, start = offset, contentStart = contentStart, end = end)
            }
        }
    }

    private const val TAG_INTEGER = 0x02
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_VERSION = 0xa0
}
