package com.daijunhao.certificatemanager.data.cert

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale

class CertificateParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class CertificateParser {
    fun parse(
        data: ByteArray,
        fileName: String,
        password: String? = null,
    ): List<X509Certificate> {
        if (data.isEmpty()) {
            throw CertificateParseException("文件为空。")
        }
        if (data.size > MAX_FILE_SIZE) {
            throw CertificateParseException("文件超过 8 MB，已拒绝导入。")
        }

        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (extension == "p12" || extension == "pfx") {
            parsePkcs12(data, password)
        } else {
            parseX509(data)
        }
    }

    private fun parseX509(data: ByteArray): List<X509Certificate> {
        val text = data.toString(Charsets.UTF_8)
        if (text.contains("-----BEGIN CERTIFICATE-----")) {
            val derBlocks = PemCodec.decodeBlocks(text)
            if (derBlocks.isEmpty()) {
                throw CertificateParseException("PEM 文件中没有有效的 CERTIFICATE 内容。")
            }
            val certificates = derBlocks.mapIndexed { index, block ->
                decodeCertificate(block, "第 ${index + 1} 张证书")
            }
            return distinctCertificates(certificates)
        }

        val factory = CertificateFactory.getInstance("X.509")
        val certificates = runCatching {
            factory.generateCertificates(ByteArrayInputStream(data))
                .filterIsInstance<X509Certificate>()
        }.getOrElse { collectionError ->
            runCatching {
                listOf(factory.generateCertificate(ByteArrayInputStream(data)) as X509Certificate)
            }.getOrElse { singleError ->
                throw CertificateParseException(
                    "无法识别证书格式，请确认文件是 .crt、.pem 或 .0。",
                    singleError.also { it.addSuppressed(collectionError) },
                )
            }
        }

        if (certificates.isEmpty()) {
            throw CertificateParseException("文件中没有找到 X.509 证书。")
        }
        return distinctCertificates(certificates)
    }

    private fun decodeCertificate(der: ByteArray, label: String): X509Certificate {
        return try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        } catch (error: Exception) {
            throw CertificateParseException("$label 解析失败。", error)
        }
    }

    private fun parsePkcs12(data: ByteArray, password: String?): List<X509Certificate> {
        val passwordChars = password?.toCharArray() ?: CharArray(0)
        try {
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(ByteArrayInputStream(data), passwordChars)

            val certificates = mutableListOf<X509Certificate>()
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                runCatching { keyStore.getCertificate(alias) }
                    .getOrNull()
                    ?.let { certificate ->
                        (certificate as? X509Certificate)?.let { certificates += it }
                    }
                runCatching { keyStore.getCertificateChain(alias) }
                    .getOrNull()
                    ?.let { certificates += it.filterIsInstance<X509Certificate>() }
            }

            if (certificates.isEmpty()) {
                throw CertificateParseException("PKCS#12 文件中没有证书。")
            }
            return distinctCertificates(certificates)
        } catch (error: CertificateParseException) {
            throw error
        } catch (error: IOException) {
            val hint = if (password.isNullOrEmpty()) {
                "PKCS#12 文件可能需要密码，或文件已损坏。"
            } else {
                "PKCS#12 密码错误，或文件已损坏。"
            }
            throw CertificateParseException(hint, error)
        } catch (error: Exception) {
            throw CertificateParseException("PKCS#12 解析失败。", error)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    private fun distinctCertificates(certificates: List<X509Certificate>): List<X509Certificate> {
        val seen = HashSet<String>()
        return certificates.filter { certificate ->
            seen.add(sha256(certificate.encoded))
        }
    }

    private fun sha256(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
    }

    companion object {
        private const val MAX_FILE_SIZE = 8 * 1024 * 1024
    }
}
