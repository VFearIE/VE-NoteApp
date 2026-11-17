package com.noteVE.domain.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.noteVE.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

data class UpdateUiState(
    val busy: Boolean = false,
    val downloading: Boolean = false,
    val cancelling: Boolean = false,
    val bytes: Long = 0,
    val total: Long = -1,
    val release: UpdateRelease? = null,
    val ready: ReadyUpdate? = null,
    val checked: Boolean = false,
    val current: Boolean = false,
    val cancelled: Boolean = false,
    val awaitingPermission: Boolean = false,
    val installerOpened: Boolean = false,
    val error: UpdateFailure.Kind? = null
)

class UpdateViewModel(context: Context) : ViewModel() {
    private val repository = UpdateRepository(context)
    private val mutable = MutableStateFlow(UpdateUiState())
    val state = mutable.asStateFlow()
    private var work: Job? = null

    init {
        // Disk-only restore; opening About never initiates network access.
        mutable.value = mutable.value.copy(busy = true)
        work = viewModelScope.launch {
            val ready = if (BuildConfig.FLAVOR != "platform") repository.restore() else null
            mutable.value = mutable.value.copy(busy = false, ready = ready)
        }
    }

    fun check() {
        if (mutable.value.busy) return
        work?.cancel()
        mutable.value = UpdateUiState(busy = true, ready = mutable.value.ready)
        work = viewModelScope.launch {
            try {
                val release = withTimeout(90_000) { repository.check() }
                val comparison = UpdatePolicy.compare(release.version, BuildConfig.VERSION_NAME)
                    ?: throw UpdateFailure(UpdateFailure.Kind.VERSION)
                mutable.value = mutable.value.copy(busy = false, checked = true,
                    release = release, current = comparison <= 0)
            } catch (e: TimeoutCancellationException) { fail(UpdateFailure.Kind.TIMEOUT)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { fail((e as? UpdateFailure)?.kind ?: UpdateFailure.Kind.NETWORK) }
        }
    }

    fun download() {
        val release = mutable.value.release ?: return
        if (mutable.value.busy || mutable.value.current || BuildConfig.FLAVOR == "platform") return
        mutable.value = mutable.value.copy(busy = true, downloading = true, error = null,
            cancelled = false, bytes = 0, total = -1, ready = null)
        work = viewModelScope.launch {
            try {
                val ready = withTimeout(15 * 60_000L) {
                    repository.download(release) { bytes, total ->
                        mutable.value = mutable.value.copy(bytes = bytes, total = total)
                    }
                }
                mutable.value = mutable.value.copy(busy = false, downloading = false, ready = ready)
            } catch (e: TimeoutCancellationException) { fail(UpdateFailure.Kind.TIMEOUT)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { fail((e as? UpdateFailure)?.kind ?: UpdateFailure.Kind.NETWORK) }
        }
    }

    fun cancel() {
        if (mutable.value.cancelling) return
        work?.cancel()
        repository.cancelNetwork()
        // Do not allow a new transfer to race the old transfer's .part cleanup.
        val old = work
        mutable.value = mutable.value.copy(busy = true, cancelling = true)
        work = viewModelScope.launch {
            old?.join()
            mutable.value = mutable.value.copy(busy = false, downloading = false, cancelling = false, cancelled = true, error = null)
        }
    }

    fun install(action: (ReadyUpdate) -> Boolean) {
        val ready = mutable.value.ready ?: return
        if (mutable.value.busy || BuildConfig.FLAVOR == "platform") return
        mutable.value = mutable.value.copy(busy = true, error = null, cancelled = false)
        work = viewModelScope.launch {
            try {
                repository.validateReady(ready)
                val permissionRequired = action(ready)
                mutable.value = mutable.value.copy(busy = false, awaitingPermission = permissionRequired,
                    installerOpened = !permissionRequired)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { fail((e as? UpdateFailure)?.kind ?: UpdateFailure.Kind.INSTALL) }
        }
    }

    private fun fail(kind: UpdateFailure.Kind) {
        val invalidFile = kind in setOf(UpdateFailure.Kind.SIZE, UpdateFailure.Kind.DIGEST,
            UpdateFailure.Kind.PACKAGE, UpdateFailure.Kind.SIGNATURE, UpdateFailure.Kind.VERSION,
            UpdateFailure.Kind.STORAGE)
        mutable.value = mutable.value.copy(busy = false, downloading = false, error = kind,
            ready = if (invalidFile) null else mutable.value.ready)
    }

    override fun onCleared() { repository.cancelNetwork(); super.onCleared() }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UpdateViewModel(context.applicationContext) as T
    }
}
