package com.noteVE.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noteVE.BuildConfig
import com.noteVE.R

/**
 * 关于页（二级页面）。
 *
 * 卡片式身份信息、手动更新、项目地址及原有开源许可；不自动联网。
 * 深色模式与中英双语均支持；项目地址支持长按复制。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    if (showLicenses) {
        androidx.activity.compose.BackHandler { showLicenses = false }
        LicenseScreen(onBack = { showLicenses = false })
        return
    }

    // 版本类型随 flavor 变化：root → 正式版；platform → 平台定制版
    val editionRes = if (BuildConfig.FLAVOR == "platform") {
        R.string.edition_platform
    } else {
        R.string.edition_official
    }
    val edition = stringResource(editionRes)
    val versionLine = stringResource(R.string.about_version_fmt, BuildConfig.VERSION_NAME, edition)
    // 项目主页（公开仓库）
    val github = "github.com/VFearIE/VE-NoteApp"

    GlassScaffold(

        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = { Text(stringResource(R.string.about), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, brush = brandGradient())) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_action_back), stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().glassCard(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // 应用图标
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialTheme.shapes.large)
            )

            Spacer(Modifier.height(16.dp))

            // 应用名（大字）
            Text(
                stringResource(R.string.app_name),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            // 版本号 + 版本类型（小字）
            Text(
                versionLine,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

                }
            }

            Spacer(Modifier.height(16.dp))
            UpdateSection()
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    InfoLine(stringResource(R.string.about_author), "VFearIE")
                    InfoLine(stringResource(R.string.about_release_date), "2026-10-02")
                    TextButton(onClick = { showLicenses = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.open_source_licenses))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // GitHub 地址（长按复制）
            Text(
                github,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .combinedClickable(
                        onClick = { openUpdateLink(context, "https://$github") },
                        onLongClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                            cm?.setPrimaryClip(
                                android.content.ClipData.newPlainText("github", "https://$github")
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.about_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    .padding(vertical = 10.dp)
            )
            Text(
                stringResource(R.string.about_long_press_copy),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 一行「标签：值」，标签为次要色，值为主要色。 */
@Composable
private fun InfoLine(label: String, value: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        if (label.isNotBlank()) {
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
        Text(
            value,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}
