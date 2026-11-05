package com.daijunhao.certificatemanager.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daijunhao.certificatemanager.R
import com.daijunhao.certificatemanager.data.cert.CertificateParser
import com.daijunhao.certificatemanager.data.cert.CertificateRepository
import com.daijunhao.certificatemanager.data.cert.ManagedCertificate
import com.daijunhao.certificatemanager.data.cert.isCertificateAuthority
import com.daijunhao.certificatemanager.data.module.ModuleInstaller
import com.daijunhao.certificatemanager.data.module.ModulePaths
import com.daijunhao.certificatemanager.data.root.RootAccess
import com.daijunhao.certificatemanager.data.root.RootEnvironment
import com.daijunhao.certificatemanager.data.root.RootEnvironmentProbe
import com.daijunhao.certificatemanager.data.root.RootShell
import com.daijunhao.certificatemanager.data.root.RootUnavailableException
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppPhase {
    data object CheckingRoot : AppPhase
    data class RootDenied(val title: String, val detail: String) : AppPhase
    data object Ready : AppPhase
}

data class PendingImportFile(
    val uri: Uri,
    val displayName: String,
)

data class ImportSummaryDialog(
    val imported: Int,
    val duplicates: Int,
    val ignoredNonCa: Int,
    val failures: List<String>,
)

enum class ModuleUiState {
    Ready,
    Missing,
    Disabled,
    Outdated,
}

data class CertificateManagerUiState(
    val phase: AppPhase = AppPhase.CheckingRoot,
    val environment: RootEnvironment? = null,
    val certificates: List<ManagedCertificate> = emptyList(),
    val managedFileNames: Set<String> = emptySet(),
    val invalidFileCount: Int = 0,
    val isLoadingCertificates: Boolean = false,
    val isRefreshing: Boolean = false,
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val pendingImportFiles: List<PendingImportFile> = emptyList(),
    val showP12PasswordDialog: Boolean = false,
    val importSummary: ImportSummaryDialog? = null,
    val logText: String? = null,
    val isLogLoading: Boolean = false,
) {
    val moduleState: ModuleUiState
        get() {
            val env = environment ?: return ModuleUiState.Missing
            return when {
                !env.moduleInstalled -> ModuleUiState.Missing
                !env.moduleEnabled -> ModuleUiState.Disabled
                (env.moduleVersionCode ?: 0) < ModulePaths.EXPECTED_VERSION_CODE -> ModuleUiState.Outdated
                else -> ModuleUiState.Ready
            }
        }

    val isModuleReady: Boolean
        get() = moduleState == ModuleUiState.Ready
}

sealed interface UiEvent {
    data class Snackbar(val message: String) : UiEvent
}

class CertificateManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val rootShell = RootShell()
    private val parser = CertificateParser()
    private val repository = CertificateRepository(rootShell, parser)
    private val moduleInstaller = ModuleInstaller(application, rootShell)
    private val environmentProbe = RootEnvironmentProbe(rootShell)

    private val _state = MutableStateFlow(CertificateManagerUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        startBootstrap()
    }

    fun retryRoot() {
        if (_state.value.phase == AppPhase.CheckingRoot) return
        startBootstrap()
    }

    private fun startBootstrap() {
        _state.update {
            it.copy(
                phase = AppPhase.CheckingRoot,
                environment = null,
                certificates = emptyList(),
                managedFileNames = emptySet(),
                invalidFileCount = 0,
                isLoadingCertificates = false,
                isRefreshing = false,
                isBusy = false,
            )
        }
        viewModelScope.launch {
            val rootAccess = withContext(Dispatchers.IO) { rootShell.checkAccess() }
            if (rootAccess is RootAccess.Denied) {
                _state.update {
                    it.copy(
                        phase = AppPhase.RootDenied(rootAccess.title, rootAccess.detail),
                    )
                }
                return@launch
            }

            val environment = runCatching {
                withContext(Dispatchers.IO) { environmentProbe.probe() }
            }.getOrElse { error ->
                _state.update {
                    it.copy(
                        phase = AppPhase.RootDenied(
                            title = "无法读取 Root 环境",
                            detail = error.message ?: "请检查 Root 授权后重试。",
                        ),
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    phase = AppPhase.Ready,
                    environment = environment,
                    isLoadingCertificates = true,
                )
            }
            loadCertificates(showFullLoading = true)
        }
    }

    fun refresh() {
        if (_state.value.phase != AppPhase.Ready || _state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            runCatching {
                withContext(Dispatchers.IO) { environmentProbe.probe() }
            }.onSuccess { environment ->
                _state.update { it.copy(environment = environment) }
                loadCertificates(showFullLoading = false)
            }.onFailure { error ->
                handleRootEnvironmentFailure(error)
            }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun installModule() {
        if (_state.value.isBusy || _state.value.phase != AppPhase.Ready) return
        viewModelScope.launch {
            setBusy(getApplication<Application>().getString(R.string.module_install_in_progress))
            runCatching { moduleInstaller.install() }
                .onSuccess {
                    reloadEnvironmentAndCertificates()
                    emitSnackbar(getApplication<Application>().getString(R.string.module_installed_toast))
                }
                .onFailure { error ->
                    emitSnackbar(error.message ?: "模块安装失败。")
                }
            clearBusy()
        }
    }

    fun enableModule() {
        if (_state.value.isBusy || _state.value.phase != AppPhase.Ready) return
        viewModelScope.launch {
            setBusy(getApplication<Application>().getString(R.string.module_install_in_progress))
            runCatching {
                withContext(Dispatchers.IO) {
                    val result = rootShell.run(
                        "rm -f ${RootShell.shellQuote("${ModulePaths.MODULE_DIR}/disable")}",
                    )
                    if (!result.isSuccess) {
                        throw RootUnavailableException(result.errorText().ifBlank { "无法启用模块。" })
                    }
                }
            }.onSuccess {
                reloadEnvironmentAndCertificates()
                emitSnackbar(getApplication<Application>().getString(R.string.module_enabled_toast))
            }.onFailure { error ->
                emitSnackbar(error.message ?: "无法启用模块。")
            }
            clearBusy()
        }
    }

    fun prepareImport(uris: List<Uri>) {
        if (
            _state.value.isBusy ||
            _state.value.isLoadingCertificates ||
            _state.value.phase != AppPhase.Ready
        ) {
            return
        }
        if (!_state.value.isModuleReady) {
            emitSnackbar(getApplication<Application>().getString(R.string.module_not_ready_import))
            return
        }
        if (uris.isEmpty()) return

        viewModelScope.launch {
            setBusy(getApplication<Application>().getString(R.string.import_reading_files))
            val files = withContext(Dispatchers.IO) {
                uris.distinct().map { uri ->
                    PendingImportFile(uri = uri, displayName = resolveDisplayName(uri))
                }
            }
            clearBusy()
            if (files.any { it.needsPasswordPrompt() }) {
                _state.update {
                    it.copy(
                        pendingImportFiles = files,
                        showP12PasswordDialog = true,
                    )
                }
            } else {
                importFiles(files, password = null)
            }
        }
    }

    fun submitP12Password(password: String) {
        val files = _state.value.pendingImportFiles
        _state.update {
            it.copy(
                pendingImportFiles = emptyList(),
                showP12PasswordDialog = false,
            )
        }
        if (files.isNotEmpty()) {
            importFiles(files, password = password)
        }
    }

    fun cancelPendingImport() {
        _state.update {
            it.copy(
                pendingImportFiles = emptyList(),
                showP12PasswordDialog = false,
            )
        }
    }

    private fun importFiles(files: List<PendingImportFile>, password: String?) {
        if (_state.value.isBusy) return
        val environment = _state.value.environment ?: return
        if (!_state.value.isModuleReady) {
            emitSnackbar(getApplication<Application>().getString(R.string.module_not_ready_import))
            return
        }

        viewModelScope.launch {
            setBusy(getApplication<Application>().getString(R.string.import_in_progress))
            val failures = mutableListOf<String>()
            val parsedCertificates = withContext(Dispatchers.IO) {
                files.flatMap { file ->
                    runCatching {
                        parser.parse(readUriBytes(file.uri), file.displayName, password)
                    }.getOrElse { error ->
                        failures += "${file.displayName}: ${error.message ?: "无法读取文件。"}"
                        emptyList()
                    }
                }
            }

            val authorities = parsedCertificates.filter { it.isCertificateAuthority() }
            val ignoredNonCa = parsedCertificates.size - authorities.size
            var imported = 0
            var duplicates = 0

            if (authorities.isNotEmpty()) {
                runCatching {
                    repository.importCertificates(
                        certificates = authorities,
                        existingCertificates = _state.value.certificates,
                        existingFileNames = _state.value.managedFileNames,
                        environment = environment,
                    )
                }.onSuccess { result ->
                    imported = result.importedCount
                    duplicates = result.duplicateCount
                    result.failures.mapTo(failures) { failure ->
                        "${failure.item}: ${failure.message}"
                    }
                }.onFailure { error ->
                    failures += error.message ?: "写入系统证书目录失败。"
                }
            }

            if (imported > 0) {
                emitSnackbar(
                    getApplication<Application>().getString(R.string.import_success, imported),
                )
            }

            _state.update {
                it.copy(
                    importSummary = ImportSummaryDialog(
                        imported = imported,
                        duplicates = duplicates,
                        ignoredNonCa = ignoredNonCa,
                        failures = failures,
                    ),
                )
            }
            clearBusy()
            if (imported > 0) {
                loadCertificates(showFullLoading = false)
            }
        }
    }

    fun deleteCertificate(certificate: ManagedCertificate) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            setBusy(getApplication<Application>().getString(R.string.delete_in_progress))
            runCatching { repository.deleteCertificate(certificate) }
                .onSuccess {
                    emitSnackbar(getApplication<Application>().getString(R.string.delete_success))
                    _state.update { current ->
                        current.copy(
                            certificates = current.certificates.filterNot {
                                it.fileName == certificate.fileName
                            },
                            managedFileNames = current.managedFileNames - certificate.fileName,
                        )
                    }
                    loadCertificates(showFullLoading = false)
                }
                .onFailure { error ->
                    emitSnackbar(error.message ?: "删除证书失败。")
                }
            clearBusy()
        }
    }

    fun exportCertificate(certificate: ManagedCertificate, destination: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(destination)?.use { output ->
                        output.write(certificate.pem.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法打开导出目标。")
                }
            }.onSuccess {
                emitSnackbar(getApplication<Application>().getString(R.string.export_success))
            }.onFailure { error ->
                emitSnackbar(error.message ?: "导出证书失败。")
            }
        }
    }

    fun dismissImportSummary() {
        _state.update { it.copy(importSummary = null) }
    }

    fun loadModuleLog() {
        if (_state.value.isLogLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLogLoading = true, logText = null) }
            runCatching { repository.readInjectionLog() }
                .onSuccess { log -> _state.update { it.copy(logText = log) } }
                .onFailure { error ->
                    emitSnackbar(error.message ?: "无法读取注入日志。")
                    _state.update { it.copy(logText = "") }
                }
            _state.update { it.copy(isLogLoading = false) }
        }
    }

    private suspend fun reloadEnvironmentAndCertificates() {
        runCatching {
            withContext(Dispatchers.IO) { environmentProbe.probe() }
        }.onSuccess { environment ->
            _state.update { it.copy(environment = environment) }
            loadCertificates(showFullLoading = false)
        }.onFailure { error ->
            handleRootEnvironmentFailure(error)
        }
    }

    private suspend fun loadCertificates(showFullLoading: Boolean) {
        if (showFullLoading) {
            _state.update { it.copy(isLoadingCertificates = true) }
        }
        runCatching { loadCertificatesBlocking() }
            .onSuccess { result ->
                _state.update {
                    it.copy(
                        certificates = result.certificates,
                        managedFileNames = result.allFileNames,
                        invalidFileCount = result.invalidFileNames.size,
                        isLoadingCertificates = false,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(isLoadingCertificates = false) }
                if (error is RootUnavailableException) {
                    handleRootEnvironmentFailure(error)
                } else {
                    emitSnackbar(error.message ?: "读取证书列表失败。")
                }
            }
    }

    private suspend fun loadCertificatesBlocking() =
        withContext(Dispatchers.IO) { repository.loadCertificates() }

    private fun handleRootEnvironmentFailure(error: Throwable) {
        _state.update {
            it.copy(
                phase = AppPhase.RootDenied(
                    title = "Root 环境不可用",
                    detail = error.message ?: "请检查 Root 授权后重试。",
                ),
                isRefreshing = false,
                isLoadingCertificates = false,
                isBusy = false,
                busyMessage = null,
            )
        }
    }

    private fun setBusy(message: String) {
        _state.update { it.copy(isBusy = true, busyMessage = message) }
    }

    private fun clearBusy() {
        _state.update { it.copy(isBusy = false, busyMessage = null) }
    }

    private fun emitSnackbar(message: String) {
        _events.trySend(UiEvent.Snackbar(message))
    }

    private fun resolveDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }

        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "certificate"
    }

    private fun readUriBytes(uri: Uri): ByteArray {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件。")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    throw IllegalStateException("文件超过 8 MB，已拒绝导入。")
                }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun PendingImportFile.needsPasswordPrompt(): Boolean {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension == "p12" || extension == "pfx"
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024
    }
}
