package com.noteVE.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noteVE.R
import com.noteVE.domain.Permissions

/**
 * 授权相关（三级页面）。
 *
 * 按 iOS Settings「inset grouped」风格实现：
 *  - 分组（Section：标题 + 脚注）+ 行（Row：图标 + 标题 + 副标题 + 控件）
 *  - 在 ON_RESUME 刷新状态，从系统设置返回后自动刷新，无需手动重进
 *  - 每行左侧 drawable 图标，右侧胶囊开关；开关状态 = 真实授权状态
 *  - 点开关 → 申请（运行时权限弹系统框；特殊权限跳对应系统页面）
 *
 * 所有状态以实际系统查询为准，不因 priv-app/platform 身份放宽。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPriv by remember { mutableStateOf(Permissions.isPrivApp(context)) }
    var notif by remember { mutableStateOf(Permissions.notificationsEnabled(context)) }
    var mic by remember { mutableStateOf(Permissions.runtimeGranted(context, android.Manifest.permission.RECORD_AUDIO)) }
    var storage by remember { mutableStateOf(storageGranted(context)) }
    var vibrate by remember { mutableStateOf(Permissions.runtimeGranted(context, android.Manifest.permission.VIBRATE)) }
    var fullScreen by remember { mutableStateOf(Permissions.fullScreenIntentGranted(context)) }
    var exact by remember { mutableStateOf(Permissions.exactAlarmGranted(context)) }

    fun refresh() {
        isPriv = Permissions.isPrivApp(context)
        notif = Permissions.notificationsEnabled(context)
        mic = Permissions.runtimeGranted(context, android.Manifest.permission.RECORD_AUDIO)
        storage = storageGranted(context)
        vibrate = Permissions.runtimeGranted(context, android.Manifest.permission.VIBRATE)
        exact = Permissions.exactAlarmGranted(context)
        fullScreen = Permissions.fullScreenIntentGranted(context)
    }
    DisposableEffect(lifecycleOwner) {
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.perm_section)) },
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
                .padding(vertical = 4.dp)
        ) {
            // ---------- 系统应用状态 ----------
            PermSection(
                header = stringResource(R.string.perm_system_app),
                footer = stringResource(R.string.perm_desc)
            ) {
                PermRow(
                    icon = R.drawable.ic_perm_system,
                    title = stringResource(R.string.perm_system_app),
                    subtitle = stringResource(R.string.perm_system_app_desc),
                    checked = isPriv,
                    locked = true,           // 非用户可控，仅展示
                    tint = Color(0xFF34C759),
                    onToggle = { }
                )
            }

            // ---------- 运行时权限 ----------
            PermSection(index = 1, header = stringResource(R.string.perm_section)) {
                PermRow(
                    icon = R.drawable.ic_perm_notification,
                    tint = Color(0xFFE5484D),
                    title = stringResource(R.string.perm_notification),
                    subtitle = stringResource(R.string.perm_notification_desc),
                    checked = notif,
                    onToggle = { activity?.let { Permissions.requestNotifications(it) } }
                )
                PermRow(
                    icon = R.drawable.ic_perm_mic,
                    tint = Color(0xFF8E4EC6),
                    title = stringResource(R.string.perm_microphone),
                    subtitle = stringResource(R.string.perm_microphone_desc),
                    checked = mic,
                    onToggle = { requestRuntime(activity, listOf(android.Manifest.permission.RECORD_AUDIO)) }
                )
                PermRow(
                    icon = R.drawable.ic_perm_storage,
                    tint = Color(0xFF2F80ED),
                    title = stringResource(R.string.perm_storage),
                    subtitle = stringResource(R.string.perm_storage_desc),
                    checked = storage,
                    onToggle = { requestRuntime(activity, storagePerms()) }
                )
                PermRow(
                    icon = R.drawable.ic_perm_vibrate,
                    tint = Color(0xFFFF9500),
                    title = stringResource(R.string.perm_vibrate),
                    subtitle = stringResource(R.string.reminder_vibration_permission_desc),
                    checked = vibrate,
                    locked = true,
                    onToggle = { }
                )
            }

            PermSection(index = 2, header = stringResource(R.string.reminder_special_access)) {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    PermRow(
                        icon = R.drawable.ic_perm_notification,
                        title = stringResource(R.string.fullscreen_reminder_permission),
                        subtitle = stringResource(R.string.fullscreen_reminder_permission_desc),
                        checked = fullScreen,
                        onToggle = { Permissions.requestFullScreenIntent(context) }
                    )
                }
                PermRow(
                    icon = R.drawable.ic_perm_alarm,
                    tint = Color(0xFF34C759),
                    title = stringResource(R.string.perm_exact_alarm),
                    subtitle = stringResource(R.string.perm_exact_alarm_desc),
                    checked = exact,
                    locked = android.os.Build.VERSION.SDK_INT < 31,
                    last = true,
                    onToggle = { activity?.let { Permissions.requestExactAlarm(it) } }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * 分组卡片（iOS Settings「inset grouped」风格）：
 * 大写分组头 + 14dp 圆角卡片 + 可选脚注。
 */
@Composable
private fun PermSection(
    index: Int = 0,
    header: String,
    footer: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    // 统一样式：委托给公共组件（见 DesignKit.kt）
    SettingsGroup(header = header, footer = footer, modifier = Modifier.enterReveal(index), content = content)
}

/**
 * 权限行：30dp 圆角色块图标（内嵌 18dp 白图标）+ 标题/副标题 + 右侧胶囊开关。
 * 行高固定 min 56dp，分隔线 0.5dp 且缩进到图标之后。
 */
@Composable
private fun PermRow(
    icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    last: Boolean = false,
    locked: Boolean = false,
    tint: Color = Color.Unspecified
) {
    // 统一样式：委托给公共组件（见 DesignKit.kt）
    SettingsSwitchRow(
        iconRes = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        onToggle = onToggle,
        blockColor = if (tint == Color.Unspecified) null else tint,
        showDivider = !last,
        enabled = !locked
    )
}

// ---------------- 辅助 ----------------

private fun storageGranted(context: android.content.Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= 33)
        Permissions.runtimeGranted(context, android.Manifest.permission.READ_MEDIA_IMAGES)
    else
        Permissions.runtimeGranted(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)

private fun storagePerms(): List<String> =
    if (android.os.Build.VERSION.SDK_INT >= 33)
        listOf(android.Manifest.permission.READ_MEDIA_IMAGES)
    else
        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)

private fun requestRuntime(activity: Activity?, perms: List<String>) {
    if (activity == null) return
    val missing = perms.filter {
        activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
    }
    if (missing.isNotEmpty()) {
        activity.requestPermissions(missing.toTypedArray(), 100)
    }
}
