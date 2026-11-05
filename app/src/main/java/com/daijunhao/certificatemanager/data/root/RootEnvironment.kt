package com.daijunhao.certificatemanager.data.root

data class RootEnvironment(
    val sdkInt: Int,
    val isKernelSu: Boolean,
    val hasConscryptApex: Boolean,
    val moduleInstalled: Boolean,
    val moduleEnabled: Boolean,
    val moduleVersionName: String?,
    val moduleVersionCode: Int?,
) {
    val isAndroid14OrNewer: Boolean
        get() = sdkInt >= 34

    val systemCertificatePath: String
        get() = if (isAndroid14OrNewer && hasConscryptApex) {
            "/apex/com.android.conscrypt/cacerts"
        } else {
            "/system/etc/security/cacerts"
        }
}

class RootEnvironmentProbe(
    private val rootShell: RootShell,
) {
    fun probe(): RootEnvironment {
        val script = """
            MODULE_DIR=/data/adb/modules/root_cert_manager
            echo "RCM_SDK=${'$'}(getprop ro.build.version.sdk)"
            if [ -d /data/adb/ksu ] || [ -d /data/adb/ksud ]; then
                echo "RCM_KSU=1"
            else
                echo "RCM_KSU=0"
            fi
            if [ -d /apex/com.android.conscrypt/cacerts ]; then
                echo "RCM_APEX=1"
            else
                echo "RCM_APEX=0"
            fi
            if [ -f "${'$'}MODULE_DIR/module.prop" ]; then
                echo "RCM_MODULE=1"
            else
                echo "RCM_MODULE=0"
            fi
            if [ -f "${'$'}MODULE_DIR/disable" ]; then
                echo "RCM_DISABLED=1"
            else
                echo "RCM_DISABLED=0"
            fi
            if [ -f "${'$'}MODULE_DIR/module.prop" ]; then
                echo "RCM_MODULE_VERSION=${'$'}(sed -n 's/^version=//p' "${'$'}MODULE_DIR/module.prop" | head -n 1)"
                echo "RCM_MODULE_VERSION_CODE=${'$'}(sed -n 's/^versionCode=//p' "${'$'}MODULE_DIR/module.prop" | head -n 1)"
            fi
        """.trimIndent()

        val result = rootShell.run(script)
        if (!result.isSuccess) {
            throw RootUnavailableException(result.errorText().ifBlank { "无法读取 Root 环境。" })
        }

        val values = result.stdout.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()

        val sdk = values["RCM_SDK"]?.toIntOrNull()
            ?: throw RootUnavailableException("无法读取 Android API 版本。")

        return RootEnvironment(
            sdkInt = sdk,
            isKernelSu = values["RCM_KSU"] == "1",
            hasConscryptApex = values["RCM_APEX"] == "1",
            moduleInstalled = values["RCM_MODULE"] == "1",
            moduleEnabled = values["RCM_MODULE"] == "1" && values["RCM_DISABLED"] != "1",
            moduleVersionName = values["RCM_MODULE_VERSION"]?.takeIf { it.isNotBlank() },
            moduleVersionCode = values["RCM_MODULE_VERSION_CODE"]?.toIntOrNull(),
        )
    }
}
