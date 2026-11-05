package com.daijunhao.certificatemanager.data.module

import android.content.Context
import com.daijunhao.certificatemanager.data.root.RootShell
import com.daijunhao.certificatemanager.data.root.RootUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModuleInstaller(
    private val context: Context,
    private val rootShell: RootShell,
) {
    suspend fun install() = withContext(Dispatchers.IO) {
        val moduleProp = readAsset("kernelsu/module.prop")
            .toString(Charsets.UTF_8)
        if (!moduleProp.lineSequence().any { it == "id=${ModulePaths.MODULE_ID}" }) {
            throw IllegalStateException("内置模块配置无效。")
        }

        rootShell.makeDirectory(ModulePaths.CERT_DIR)
        val scripts = listOf(
            "kernelsu/post-fs-data.sh",
            "kernelsu/service.sh",
            "kernelsu/apex-inject.sh",
            "kernelsu/uninstall.sh",
        )
        scripts.forEach { assetPath ->
            val fileName = assetPath.substringAfterLast('/')
            rootShell.writeFile(
                path = "${ModulePaths.MODULE_DIR}/$fileName",
                data = readAsset(assetPath),
                mode = 755,
            )
        }

        // Write module.prop last so a half-finished installation is not
        // presented as a ready module.
        rootShell.writeFile(
            path = "${ModulePaths.MODULE_DIR}/module.prop",
            data = moduleProp.toByteArray(Charsets.UTF_8),
            mode = 644,
        )

        val quotedDir = shellQuote(ModulePaths.MODULE_DIR)
        val quotedSystem = shellQuote("${ModulePaths.MODULE_DIR}/system")
        val quotedEtc = shellQuote("${ModulePaths.MODULE_DIR}/system/etc")
        val quotedCerts = shellQuote(ModulePaths.CERT_DIR)
        val result = rootShell.run(
            """
                chown -R 0:0 $quotedDir || exit 1
                rm -f $quotedDir/remove $quotedDir/skip_mount
                chmod 755 $quotedDir $quotedSystem $quotedEtc $quotedCerts || exit 1
                chmod 755 $quotedDir/*.sh || exit 1
                chmod 644 $quotedDir/module.prop || exit 1
                find $quotedCerts -maxdepth 1 -type f -exec chmod 644 {} + 2>/dev/null || true
                chcon -R u:object_r:system_file:s0 $quotedSystem 2>/dev/null || true
                chcon -R u:object_r:system_security_cacerts_file:s0 $quotedCerts 2>/dev/null \
                    || chcon -R u:object_r:system_file:s0 $quotedCerts 2>/dev/null || true
            """.trimIndent(),
        )
        if (!result.isSuccess) {
            throw RootUnavailableException(result.errorText().ifBlank { "模块安装失败。" })
        }
    }

    private fun readAsset(path: String): ByteArray {
        return context.assets.open(path).use { it.readBytes() }
    }
}

private fun shellQuote(value: String): String = RootShell.shellQuote(value)
