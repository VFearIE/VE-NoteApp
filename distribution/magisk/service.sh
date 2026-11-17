#!/system/bin/sh
# One initialization per module version and Android release; no watchdog, no force-stop recovery loop.
MODDIR=${0%/*}
PKG=com.noteVE
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 5; done
sleep 10
SDK=$(getprop ro.build.version.sdk)
STAMP="$MODDIR/.reminder-init-305-$SDK"
[ -f "$STAMP" ] && exit 0
LOG="$MODDIR/reminder-init.log"
: > "$LOG"
OK=1
# Optional dangerous permissions stay user-controlled: microphone and image access are NOT pre-granted.
if [ "$SDK" -ge 33 ]; then
    pm grant "$PKG" android.permission.POST_NOTIFICATIONS >> "$LOG" 2>&1 || OK=0
fi
if [ "$SDK" -ge 34 ]; then
    cmd appops set "$PKG" USE_FULL_SCREEN_INTENT allow >> "$LOG" 2>&1 || OK=0
fi
# USE_EXACT_ALARM is normal on API 33+, declared by the app. Android 9 needs no special access.
[ "$OK" = 1 ] && touch "$STAMP"
exit 0
