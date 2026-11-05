package com.daijunhao.certificatemanager.data.root

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0

    fun errorText(): String = stderr.trim().ifBlank {
        if (exitCode == 0) "" else "命令执行失败（退出码 $exitCode）"
    }
}

sealed interface RootAccess {
    data object Granted : RootAccess
    data class Denied(
        val title: String,
        val detail: String,
    ) : RootAccess
}

class RootUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class RootTimeoutException(
    message: String,
) : Exception(message)

/**
 * Small root-shell gateway. All filesystem work that touches /data/adb is done
 * through su; the app itself never asks the user for storage permissions.
 */
class RootShell(
    private val suCommand: String = "su",
) {
    fun run(script: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): ShellResult {
        val command = """
            export PATH=/system/bin:/system/xbin:/data/adb/ksu/bin:/data/adb/magisk:${'$'}PATH
            $script
        """.trimIndent()

        val process = try {
            ProcessBuilder(suCommand, "-c", command).start()
        } catch (error: IOException) {
            throw RootUnavailableException("无法启动 su，请确认设备已 Root 且 Root 管理器可用。", error)
        } catch (error: SecurityException) {
            throw RootUnavailableException("当前系统拒绝执行 su。", error)
        }

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutThread = thread(name = "root-stdout") {
            process.inputStream.use { input -> input.copyTo(stdout) }
        }
        val stderrThread = thread(name = "root-stderr") {
            process.errorStream.use { input -> input.copyTo(stderr) }
        }

        val finished = try {
            process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            throw RootUnavailableException("Root 命令被中断。", interrupted)
        }

        if (!finished) {
            process.destroy()
            process.destroyForcibly()
            stdoutThread.join(1_000)
            stderrThread.join(1_000)
            throw RootTimeoutException("等待 Root 授权或命令执行超时。")
        }

        stdoutThread.join(2_000)
        stderrThread.join(2_000)

        return ShellResult(
            exitCode = process.exitValue(),
            stdout = stdout.toString(Charsets.UTF_8.name()).trim(),
            stderr = stderr.toString(Charsets.UTF_8.name()).trim(),
        )
    }

    fun checkAccess(timeoutMillis: Long = ROOT_GRANT_TIMEOUT_MILLIS): RootAccess {
        return try {
            val result = run("id", timeoutMillis)
            when {
                result.isSuccess && UID_ZERO.containsMatchIn(result.stdout) -> RootAccess.Granted
                result.isSuccess -> RootAccess.Denied(
                    title = "Root 授权无效",
                    detail = "su 返回了结果，但当前进程不是 root 用户。",
                )
                else -> RootAccess.Denied(
                    title = "Root 权限被拒绝",
                    detail = result.errorText().ifBlank {
                        "请在 KernelSU 管理器中允许本应用使用 Root 权限后重试。"
                    },
                )
            }
        } catch (error: RootUnavailableException) {
            RootAccess.Denied(
                title = "未找到可用的 Root",
                detail = error.message ?: "设备可能没有 Root，或 su 不可用。",
            )
        } catch (error: RootTimeoutException) {
            RootAccess.Denied(
                title = "等待 Root 授权超时",
                detail = "请打开 KernelSU 管理器完成授权，然后点击重试。",
            )
        } catch (error: Exception) {
            RootAccess.Denied(
                title = "无法获取 Root 权限",
                detail = error.message ?: "发生未知错误。",
            )
        }
    }

    fun writeFile(path: String, data: ByteArray, mode: Int = 644) {
        require(path.startsWith('/')) { "Root file path must be absolute" }
        val encoded = Base64.getEncoder().encodeToString(data)
        val quotedPath = shellQuote(path)
        val quotedTempPath = shellQuote("$path.rcm-tmp")
        val command = """
            mkdir -p "${'$'}(dirname $quotedPath)" || exit 1
            printf '%s' '$encoded' | base64 -d > $quotedTempPath || exit 1
            chown 0:0 $quotedTempPath || exit 1
            chmod $mode $quotedTempPath || exit 1
            mv -f $quotedTempPath $quotedPath || exit 1
        """.trimIndent()
        val result = run(command)
        if (!result.isSuccess) {
            throw RootUnavailableException(result.errorText().ifBlank { "无法写入 $path" })
        }
    }

    fun makeDirectory(path: String, mode: Int = 755) {
        require(path.startsWith('/')) { "Root directory path must be absolute" }
        val result = run("mkdir -p ${shellQuote(path)} && chmod $mode ${shellQuote(path)}")
        if (!result.isSuccess) {
            throw RootUnavailableException(result.errorText().ifBlank { "无法创建 $path" })
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 20_000L
        private const val ROOT_GRANT_TIMEOUT_MILLIS = 45_000L
        private val UID_ZERO = Regex("""(?:^|\s)uid=0(?:\(|\s|$)""")

        fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
