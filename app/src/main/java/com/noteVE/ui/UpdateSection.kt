package com.noteVE.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noteVE.BuildConfig
import com.noteVE.R
import com.noteVE.domain.update.UpdateFailure
import com.noteVE.domain.update.UpdatePolicy
import com.noteVE.domain.update.UpdateViewModel

/** Fits inside the About page's vertical scroll; no implicit network effects. */
@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val factory = remember(context) { UpdateViewModel.Factory(context) }
    val model: UpdateViewModel = viewModel(key = "ve-manual-update", factory = factory)
    val state by model.state.collectAsState()
    val platform = BuildConfig.FLAVOR == "platform"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.update_manual), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (platform) Text(stringResource(R.string.update_platform), color = MaterialTheme.colorScheme.primary)
            if (state.busy && !state.downloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.update_working))
            }
            if (state.downloading) {
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.update_progress,
                        (state.bytes * 100 / state.total).coerceIn(0L, 100L).toInt(),
                        state.bytes / 1024, state.total / 1024))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.update_progress_unknown, state.bytes / 1024))
                }
            }
            if (state.cancelling) Text(stringResource(R.string.update_cancelling))
            if (state.cancelled) Text(stringResource(R.string.update_cancelled))
            if (state.current) Text(stringResource(R.string.update_current))
            state.release?.takeUnless { state.current }?.let { release ->
                Text(stringResource(R.string.update_available, BuildConfig.VERSION_NAME, release.version),
                    style = MaterialTheme.typography.titleMedium)
                if (release.notes.isNotBlank()) {
                    Text(release.notes, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()))
                }
                if (!platform && release.asset == null) {
                    Text(stringResource(R.string.update_error_no_apk), color = MaterialTheme.colorScheme.error)
                }
                if (!platform && release.asset?.digest == null && release.asset != null) {
                    Text(stringResource(R.string.update_digest_optional),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!platform && release.asset != null && state.ready == null) {
                    Button(onClick = model::download, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_download))
                    }
                }
            }
            state.error?.let {
                Text(stringResource(updateErrorString(it)), color = MaterialTheme.colorScheme.error)
            }
            if (state.awaitingPermission) Text(stringResource(R.string.update_permission_retry))
            if (state.installerOpened) Text(stringResource(R.string.update_installer_opened))
            if (!platform && state.ready != null) {
                Text(stringResource(R.string.update_ready, state.ready!!.version))
                Button(enabled = !state.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    model.install { ready ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            true
                        } else {
                            val uri = FileProvider.getUriForFile(context,
                                "${context.packageName}.fileprovider", ready.file)
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                clipData = ClipData.newRawUri("VE update", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            false
                        }
                    }
                }) { Text(stringResource(R.string.update_install)) }
            }
            if (state.busy) {
                OutlinedButton(onClick = model::cancel, enabled = !state.cancelling, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_cancel))
                }
            } else {
                OutlinedButton(onClick = model::check, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_check))
                }
            }
            OutlinedButton(onClick = { openUpdateLink(context, UpdatePolicy.REPOSITORY) },
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_open_repository)) }
            OutlinedButton(onClick = { openUpdateLink(context, UpdatePolicy.RELEASES) },
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_open_releases)) }
        }
    }
}

internal fun openUpdateLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.update_error_browser), Toast.LENGTH_LONG).show()
    }
}

private fun updateErrorString(kind: UpdateFailure.Kind): Int = when (kind) {
    UpdateFailure.Kind.TIMEOUT -> R.string.update_error_timeout
    UpdateFailure.Kind.NETWORK -> R.string.update_error_network
    UpdateFailure.Kind.BLOCKED -> R.string.update_error_blocked
    UpdateFailure.Kind.NOT_FOUND -> R.string.update_error_not_found
    UpdateFailure.Kind.SERVER -> R.string.update_error_server
    UpdateFailure.Kind.INVALID_RELEASE -> R.string.update_error_release
    UpdateFailure.Kind.NO_APK -> R.string.update_error_no_apk
    UpdateFailure.Kind.SIZE -> R.string.update_error_size
    UpdateFailure.Kind.DIGEST -> R.string.update_error_digest
    UpdateFailure.Kind.PACKAGE -> R.string.update_error_package
    UpdateFailure.Kind.SIGNATURE -> R.string.update_error_signature
    UpdateFailure.Kind.VERSION -> R.string.update_error_version
    UpdateFailure.Kind.STORAGE -> R.string.update_error_storage
    UpdateFailure.Kind.INSTALL -> R.string.update_error_install
    UpdateFailure.Kind.BROWSER -> R.string.update_error_browser
}
