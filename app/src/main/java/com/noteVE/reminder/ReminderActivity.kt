package com.noteVE.reminder

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.noteVE.R
import com.noteVE.domain.LocaleHelper
import com.noteVE.domain.Settings
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** A display/controller only: the service owns feedback and every durable transition. */
class ReminderActivity : ComponentActivity() {
    private var noteId = -1L
    private var expectedAt = -1L
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var slider: DismissSlider
    private lateinit var snooze: Button
    private lateinit var mute: Button
    private var sending = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.apply(newBase, Settings.currentLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        // Never requestDismissKeyguard: viewing a reminder must not unlock private notes.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildContent()
        acceptIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ReminderService.presentation.collect { snapshot ->
                    showSnapshot(snapshot)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent) {
        noteId = intent.getLongExtra(ReminderService.EXTRA_NOTE_ID, -1)
        expectedAt = intent.getLongExtra(ReminderService.EXTRA_EXPECTED_AT, -1)
        if (noteId <= 0 || expectedAt < 0) { finish(); return }
        sending = false
        slider.reset()
        slider.isEnabled = true
        snooze.isEnabled = true
        title.setText(R.string.rv3_loading)
        status.setText(R.string.rv3_slide)
        // RESTORE is not FIRE. An old window must never claim/advance another occurrence.
        if (!ReminderService.send(this, ReminderService.ACTION_RESTORE, noteId, expectedAt)) {
            status.setText(R.string.rv3_command_failed)
        }
        showSnapshot(ReminderService.presentation.value)
    }

    private fun showSnapshot(snapshot: ReminderService.Presentation) {
        if (!snapshot.ready) {
            if (sending) {
                sending = false
                status.setText(R.string.rv3_command_failed)
                slider.isEnabled = true
                snooze.isEnabled = true
            }
            return
        }
        val row = snapshot.rows.firstOrNull { it.noteId == noteId && it.expectedAt == expectedAt }
        if (row == null || row.phase == Occurrence.SNOOZED) {
            finish()
        } else {
            title.text = row.title.ifBlank { getString(R.string.notes) }
            status.setText(if (row.phase == Occurrence.MUTED) R.string.rv3_muted else R.string.rv3_slide)
            if (row.phase == Occurrence.MUTED) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            sending = false
            slider.isEnabled = true
            snooze.isEnabled = true
        }
    }

    private fun command(action: String) {
        if (sending) return
        sending = true
        slider.isEnabled = false
        snooze.isEnabled = false
        if (!ReminderService.send(this, action, noteId, expectedAt)) {
            sending = false
            slider.isEnabled = true
            snooze.isEnabled = true
            slider.reset()
            status.setText(R.string.rv3_command_failed)
        }
        // Wait for the durable service result; lifecycle/navigation alone never acknowledges.
    }

    private fun buildContent() {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(19, 25, 29))
        }
        fun text(size: Float) = TextView(this).apply {
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, padding / 2, 0, padding / 2)
        }
        title = text(24f).apply {
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        status = text(14f).apply { setTextColor(Color.rgb(183, 198, 195)) }
        column.addView(text(14f).apply { setText(R.string.rv3_alarm) })
        column.addView(TextClock(this).apply {
            textSize = if (resources.configuration.screenWidthDp < 280) 52f else 76f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.rgb(215, 235, 226))
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding / 2)
            if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(28, 76, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
        }, LinearLayout.LayoutParams(-1, -2))
        column.addView(title, LinearLayout.LayoutParams(-1, -2))
        column.addView(status, LinearLayout.LayoutParams(-1, -2))
        // Flexible spacer pushes actions down on phones, collapses on short/square windows.
        column.addView(android.view.View(this), LinearLayout.LayoutParams(1, 0, 1f))
        slider = DismissSlider(this) { command(ReminderService.ACTION_DISMISS) }
        column.addView(slider, LinearLayout.LayoutParams(-1, -2))
        snooze = Button(this).apply {
            setText(R.string.rv3_snooze)
            isAllCaps = false
            setTextColor(Color.rgb(210, 226, 222))
            background = GradientDrawable().apply {
                setColor(Color.rgb(33, 42, 46))
                cornerRadius = 28 * resources.displayMetrics.density
            }
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setOnClickListener { command(ReminderService.ACTION_SNOOZE) }
        }
        column.addView(snooze, LinearLayout.LayoutParams(-1, -2).apply { topMargin = padding })
        mute = Button(this).apply {
            setText(R.string.rv3_mute)
            isAllCaps = false
            setTextColor(Color.rgb(183, 198, 195))
            background = ColorDrawable(Color.TRANSPARENT)
            minHeight = (44 * resources.displayMetrics.density).toInt()
            setOnClickListener { command(ReminderService.ACTION_MUTE) }
        }
        column.addView(mute, LinearLayout.LayoutParams(-1, -2))
        // No fixed pixel heights: all controls remain reachable on 480-square/dense windows.
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(column, android.widget.FrameLayout.LayoutParams(-1, -1))
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                }
                insets
            }
        })
    }

    companion object {
        fun intent(context: Context, id: Long, at: Long): Intent =
            Intent(context, ReminderActivity::class.java)
                .setData(Uri.parse("noteve://ring/$id/$at"))
                .putExtra(ReminderService.EXTRA_NOTE_ID, id)
                .putExtra(ReminderService.EXTRA_EXPECTED_AT, at)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
