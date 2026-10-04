package com.noteVE.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noteVE.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LicenseItem(val name: String, val license: String, val url: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val components = listOf(
        LicenseItem("AndroidX / Jetpack", "Apache-2.0", "https://developer.android.com/jetpack"),
        LicenseItem("Jetpack Compose", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
        LicenseItem("AndroidX Room", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
        LicenseItem("AndroidX Navigation", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
        LicenseItem("Kotlin Standard Library", "Apache-2.0", "https://kotlinlang.org"),
        LicenseItem("kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        LicenseItem("SQLite", "Public Domain", "https://sqlite.org"),
        LicenseItem("org.json (AOSP)", "Apache-2.0", "https://source.android.com"),
    )

    val apacheText by produceState(initialValue = "") {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/apache-2.0.txt").bufferedReader().use { it.readText() } }
                .getOrDefault("Apache-2.0 license text unavailable.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.open_source_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.licenses_intro),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            items(components) { c ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("${c.name} — ${c.license}\n${c.url}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
            item {
                Text(
                    "Apache License 2.0\n\n$apacheText",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
