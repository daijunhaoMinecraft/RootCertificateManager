package com.daijunhao.certificatemanager.data.cert

import com.daijunhao.certificatemanager.data.module.ModulePaths
import com.daijunhao.certificatemanager.data.root.RootEnvironment
import com.daijunhao.certificatemanager.data.root.RootShell
import com.daijunhao.certificatemanager.data.root.RootUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.X509Certificate
import java.util.Base64

data class CertificateLoadResult(
    val certificates: List<ManagedCertificate>,
    val invalidFileNames: List<String>,
    val allFileNames: Set<String>,
)

data class ImportFailure(
    val item: String,
    val message: String,
)

data class CertificateImportResult(
    val importedCount: Int,
    val duplicateCount: Int,
    val builtInDuplicateCount: Int,
    val failures: List<ImportFailure>,
)

class CertificateRepository(
    private val rootShell: RootShell,
    private val parser: CertificateParser,
) {
    suspend fun loadCertificates(): CertificateLoadResult = withContext(Dispatchers.IO) {
        val quotedCerts = shellQuote(ModulePaths.CERT_DIR)
        val script = """
            CERT_DIR=$quotedCerts
            [ -d "${'$'}CERT_DIR" ] || exit 0
            for f in "${'$'}CERT_DIR"/*; do
                [ -f "${'$'}f" ] || continue
                name=${'$'}{f##*/}
                printf '@@RCM_FILE:%s@@\n' "${'$'}name"
                base64 "${'$'}f" 2>/dev/null || true
            done
        """.trimIndent()

        val result = rootShell.run(script)
        if (!result.isSuccess) {
            throw RootUnavailableException(result.errorText().ifBlank { "无法读取已管理证书。" })
        }

        val rawFiles = parseBatchOutput(result.stdout)
            .filterKeys { CERT_FILE_NAME.matches(it) }
            .toSortedMap()
        val allFileNames = rawFiles.keys.toSet()
        val certificates = mutableListOf<ManagedCertificate>()
        val invalidFiles = mutableListOf<String>()

        rawFiles.forEach { (fileName, bytes) ->
            runCatching {
                val certificate = parser.parse(bytes, fileName).first()
                certificate.toManagedCertificate(fileName)
            }.onSuccess { certificate ->
                certificates += certificate
            }.onFailure {
                invalidFiles += fileName
            }
        }

        CertificateLoadResult(
            certificates = certificates.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName },
            ),
            invalidFileNames = invalidFiles,
            allFileNames = allFileNames,
        )
    }

    suspend fun importCertificates(
        certificates: List<X509Certificate>,
        existingCertificates: List<ManagedCertificate>,
        existingFileNames: Set<String>,
        environment: RootEnvironment,
    ): CertificateImportResult = withContext(Dispatchers.IO) {
        val systemPath = environment.systemCertificatePath
        val systemFileNames = listSystemFileNames(systemPath)
        val occupiedNames = existingFileNames.toMutableSet()
        val existingFingerprints = existingCertificates
            .mapTo(mutableSetOf()) { it.fingerprintSha256 }
        val importedFingerprints = mutableSetOf<String>()
        val failures = mutableListOf<ImportFailure>()
        val systemFingerprintCache = mutableMapOf<String, String?>()
        var imported = 0
        var duplicate = 0
        var builtInDuplicate = 0

        for (certificate in certificates) {
            val fingerprint = certificate.fingerprintSha256()
            if (!importedFingerprints.add(fingerprint) || fingerprint in existingFingerprints) {
                duplicate++
                continue
            }

            val hash = runCatching { CertificateHasher.subjectHashOld(certificate) }
                .getOrElse { error ->
                    failures += ImportFailure(
                        item = certificate.subjectX500Principal.name,
                        message = error.message ?: "无法计算证书哈希。",
                    )
                    continue
                }

            var index = 0
            var targetName: String? = null
            while (targetName == null) {
                val candidate = "$hash.$index"
                when {
                    candidate in existingFileNames -> {
                        index++
                    }

                    candidate in systemFileNames -> {
                        val systemFingerprint = systemFingerprintCache.getOrPut(candidate) {
                            readSystemCertificateFingerprint(systemPath, candidate)
                        }
                        if (systemFingerprint == fingerprint) {
                            builtInDuplicate++
                            duplicate++
                            break
                        }
                        index++
                    }

                    candidate in occupiedNames -> {
                        index++
                    }

                    else -> {
                        targetName = candidate
                    }
                }
            }

            val fileName = targetName ?: continue
            val record = runCatching { certificate.toManagedCertificate(fileName) }
                .getOrElse { error ->
                    failures += ImportFailure(
                        item = certificate.subjectX500Principal.name,
                        message = error.message ?: "无法解析证书。",
                    )
                    continue
                }

            try {
                rootShell.writeFile(
                    path = "${ModulePaths.CERT_DIR}/$fileName",
                    data = record.pem.toByteArray(Charsets.UTF_8),
                    mode = 644,
                )
                rootShell.run(
                    """
                        chcon u:object_r:system_security_cacerts_file:s0 \
                            ${shellQuote("${ModulePaths.CERT_DIR}/$fileName")} 2>/dev/null \
                            || chcon u:object_r:system_file:s0 \
                            ${shellQuote("${ModulePaths.CERT_DIR}/$fileName")} 2>/dev/null || true
                    """.trimIndent(),
                )
                occupiedNames += fileName
                existingFingerprints += fingerprint
                imported++
            } catch (error: Exception) {
                failures += ImportFailure(
                    item = record.displayName,
                    message = error.message ?: "写入系统证书目录失败。",
                )
            }
        }

        CertificateImportResult(
            importedCount = imported,
            duplicateCount = duplicate,
            builtInDuplicateCount = builtInDuplicate,
            failures = failures,
        )
    }

    suspend fun deleteCertificate(certificate: ManagedCertificate) = withContext(Dispatchers.IO) {
        if (!CERT_FILE_NAME.matches(certificate.fileName)) {
            throw IllegalArgumentException("证书文件名无效。")
        }
        val path = "${ModulePaths.CERT_DIR}/${certificate.fileName}"
        val result = rootShell.run("rm -f -- ${shellQuote(path)}")
        if (!result.isSuccess) {
            throw RootUnavailableException(result.errorText().ifBlank { "删除证书失败。" })
        }
    }

    suspend fun readInjectionLog(): String = withContext(Dispatchers.IO) {
        val result = rootShell.run(
            "if [ -f ${shellQuote(ModulePaths.LOG_FILE)} ]; then tail -n 200 ${shellQuote(ModulePaths.LOG_FILE)}; fi",
        )
        if (!result.isSuccess) {
            throw RootUnavailableException(result.errorText().ifBlank { "无法读取注入日志。" })
        }
        result.stdout
    }

    private fun listSystemFileNames(systemPath: String): Set<String> {
        val result = rootShell.run(
            "if [ -d ${shellQuote(systemPath)} ]; then ls -1 ${shellQuote(systemPath)}; fi",
        )
        if (!result.isSuccess) return emptySet()
        return result.stdout.lineSequence()
            .filter { CERT_FILE_NAME.matches(it) }
            .toSet()
    }

    private fun readSystemCertificateFingerprint(systemPath: String, fileName: String): String? {
        val path = "$systemPath/$fileName"
        val result = rootShell.run("if [ -f ${shellQuote(path)} ]; then base64 ${shellQuote(path)}; fi")
        if (!result.isSuccess || result.stdout.isBlank()) return null
        return runCatching {
            val bytes = Base64.getMimeDecoder().decode(result.stdout)
            parser.parse(bytes, fileName).first().fingerprintSha256()
        }.getOrNull()
    }

    private fun parseBatchOutput(output: String): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var currentName: String? = null
        val currentData = StringBuilder()

        fun flush() {
            val name = currentName ?: return
            if (currentData.isNotBlank()) {
                runCatching {
                    Base64.getMimeDecoder().decode(currentData.toString())
                }.onSuccess { result[name] = it }
            }
            currentData.clear()
        }

        output.lineSequence().forEach { line ->
            if (line.startsWith(FILE_MARKER_PREFIX) && line.endsWith(FILE_MARKER_SUFFIX)) {
                flush()
                currentName = line.substring(
                    FILE_MARKER_PREFIX.length,
                    line.length - FILE_MARKER_SUFFIX.length,
                )
            } else if (currentName != null && line.isNotBlank()) {
                currentData.append(line)
            }
        }
        flush()
        return result
    }

    companion object {
        private const val FILE_MARKER_PREFIX = "@@RCM_FILE:"
        private const val FILE_MARKER_SUFFIX = "@@"
        private val CERT_FILE_NAME = Regex("""^[0-9a-fA-F]{8}\.[0-9]+$""")
    }
}

private fun shellQuote(value: String): String = RootShell.shellQuote(value)
