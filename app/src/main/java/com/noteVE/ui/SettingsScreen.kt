package com.noteVE.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noteVE.R
import com.noteVE.domain.Settings

/**
 * 设置二级页面。
 * 交互约定：无需确认/取消，所有改动**立即生效**（无重启提示）。
 * 主题经 StateFlow 驱动重组即时切换；语言通过重建界面套用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onPermissions: () -> Unit) {
    val context = LocalContext.current
    val theme by Settings.theme.collectAsState()
    val language by Settings.language.collectAsState()
    val sortBy by Settings.sortBy.collectAsState()
    val sortReverse by Settings.sortReverse.collectAsState()
    val warnPerm by Settings.warnPermissionMissing.collectAsState()
    val dynamicColor by Settings.dynamicColor.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_action_back), stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 分段错位入场（Vector 式克制动画）
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(260, delayMillis = 40)) +
                    slideInVertically(tween(260, delayMillis = 40)) { it / 12 }
            ) {
                Column {
                    Spacer(Modifier.height(4.dp))

                    // ---------- 外观 ----------
                    SectionTitle(stringResource(R.string.settings_appearance))

                    ChoiceRow(
                        title = stringResource(R.string.language),
                        options = listOf(
                            Settings.LANG_SYSTEM to stringResource(R.string.follow_system),
                            Settings.LANG_ZH to "中文",
                            Settings.LANG_EN to "English"
                        ),
                        selected = language,
                        onSelect = { v ->
                            if (v != Settings.language.value) {
                                Settings.setLanguage(v)
                                // 立即重建界面以套用新语言（等价于无缝刷新，非重启应用）
                                (context as? android.app.Activity)?.recreate()
                            }
                        }
                    )

                    ChoiceRow(
                        title = stringResource(R.string.theme),
                        options = listOf(
                            Settings.THEME_SYSTEM to stringResource(R.string.follow_system),
                            Settings.THEME_LIGHT to stringResource(R.string.light),
                            Settings.THEME_DARK to stringResource(R.string.dark)
                        ),
                        selected = theme,
                        onSelect = { Settings.setTheme(it) }
                    )

                    // ★ 动态取色在本项目**所有受支持版本**都可用：
                    //   Android 12+ 走系统原生方案；更低版本由应用内本地生成
                    //   （见 ui/theme/DynamicColorsCompat）。故不再按 SDK 版本禁用开关。
                    SwitchRow(
                        title = stringResource(R.string.dynamic_color),
                        subtitle = stringResource(R.string.dynamic_color_desc),
                        checked = dynamicColor,
                        onCheckedChange = { Settings.setDynamicColor(it) }
                    )

                    Spacer(Modifier.height(20.dp))

                    // ---------- 通用 ----------
                    SectionTitle(stringResource(R.string.settings_general))

                    ChoiceRow(
                        title = stringResource(R.string.sort_by),
                        options = listOf(
                            Settings.SORT_NAME to stringResource(R.string.sort_name),
                            Settings.SORT_LAST_OPENED to stringResource(R.string.sort_last_opened)
                        ),
                        selected = sortBy,
                        onSelect = { Settings.setSort(it, Settings.sortReverse.value) }
                    )

                    SwitchRow(
                        title = stringResource(R.string.reverse_sort),
                        checked = sortReverse,
                        onCheckedChange = { Settings.setSort(Settings.sortBy.value, it) }
                    )

                    Spacer(Modifier.height(20.dp))

                    // ---------- 行为 ----------
                    SectionTitle(stringResource(R.string.settings_behavior))

                    SwitchRow(
                        title = stringResource(R.string.warn_permission),
                        checked = warnPerm,
                        onCheckedChange = { Settings.setWarnPermissionMissing(it) }
                    )

                    Spacer(Modifier.height(20.dp))

                    // ---------- 授权相关（三级页面入口） ----------
                    SectionTitle(stringResource(R.string.perm_section))
                    NavRow(
                        title = stringResource(R.string.perm_section),
                        subtitle = stringResource(R.string.perm_desc),
                        onClick = onPermissions
                    )

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
    )
}

/** 分段选择：2–3 个互斥选项，点选即生效。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

/** 开关项：标题（+可选副标题）与开关同排，整行可点。 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 带右箭头（drawable 绘制）的导航行，用于进入三级页面。 */
@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
