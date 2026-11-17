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
import androidx.compose.ui.graphics.Color
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
    val reminderMuted by Settings.reminderMuted.collectAsState()
    val reminderVibration by Settings.reminderVibrationEnabled.collectAsState()

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
                    SettingsGroup(header = stringResource(R.string.settings_appearance), modifier = Modifier.enterReveal(0)) {
                        SettingsChoiceRow(
                            iconRes = R.drawable.ic_set_language,
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
                        SettingsChoiceRow(
                            iconRes = R.drawable.ic_set_theme,
                            title = stringResource(R.string.theme),
                            options = listOf(
                                Settings.THEME_SYSTEM to stringResource(R.string.follow_system),
                                Settings.THEME_LIGHT to stringResource(R.string.light),
                                Settings.THEME_DARK to stringResource(R.string.dark)
                            ),
                            selected = theme,
                            onSelect = { Settings.setTheme(it) }
                        )
                        // ★ 动态取色在所有受支持版本都可用（低版本由应用内本地生成）
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_set_palette,
                            title = stringResource(R.string.dynamic_color),
                            subtitle = stringResource(R.string.dynamic_color_desc),
                            checked = dynamicColor,
                            onToggle = { Settings.setDynamicColor(!dynamicColor) },
                            showDivider = false
                        )
                    }

                    // ---------- 通用 ----------
                    SettingsGroup(header = stringResource(R.string.settings_general), modifier = Modifier.enterReveal(1)) {
                        SettingsChoiceRow(
                            iconRes = R.drawable.ic_set_sort,
                            title = stringResource(R.string.sort_by),
                            options = listOf(
                                Settings.SORT_NAME to stringResource(R.string.sort_name),
                                Settings.SORT_LAST_OPENED to stringResource(R.string.sort_last_opened)
                            ),
                            selected = sortBy,
                            onSelect = { Settings.setSort(it, Settings.sortReverse.value) }
                        )
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_set_reverse,
                            title = stringResource(R.string.reverse_sort),
                            subtitle = null,
                            checked = sortReverse,
                            onToggle = { Settings.setSort(Settings.sortBy.value, !sortReverse) },
                            showDivider = false
                        )
                    }

                    // ---------- 提醒（静音与震动独立、持久） ----------
                    SettingsGroup(header = stringResource(R.string.reminder_item), modifier = Modifier.enterReveal(2)) {
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_perm_notification,
                            title = stringResource(R.string.reminder_muted_setting),
                            subtitle = stringResource(R.string.reminder_muted_setting_desc),
                            checked = reminderMuted,
                            onToggle = { Settings.setReminderMuted(!reminderMuted) },
                            blockColor = Color(0xFFE5484D)
                        )
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_perm_vibrate,
                            title = stringResource(R.string.reminder_vibration_setting),
                            subtitle = stringResource(R.string.reminder_vibration_setting_desc),
                            checked = reminderVibration,
                            onToggle = { Settings.setReminderVibrationEnabled(!reminderVibration) },
                            blockColor = Color(0xFFFF9500),
                            showDivider = false
                        )
                    }

                    // ---------- 行为 + 授权入口 ----------
                    SettingsGroup(
                        header = stringResource(R.string.settings_behavior),
                        footer = stringResource(R.string.perm_desc),
                        modifier = Modifier.enterReveal(3)
                    ) {
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_set_warn,
                            title = stringResource(R.string.warn_permission),
                            subtitle = null,
                            checked = warnPerm,
                            onToggle = { Settings.setWarnPermissionMissing(!warnPerm) },
                            blockColor = Color(0xFFF5A524)
                        )
                        SettingsNavRow(
                            iconRes = R.drawable.ic_perm_system,
                            title = stringResource(R.string.perm_section),
                            blockColor = Color(0xFF34C759),
                            showDivider = false,
                            onClick = onPermissions
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

