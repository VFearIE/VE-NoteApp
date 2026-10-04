#!/system/bin/sh
#
# VE笔记 Magisk 模块 · 安装脚本
#
# 模块功能极简：把 APK 与权限白名单以「静态 overlay」方式挂载到系统分区，
# 使应用获得 priv-app（特权应用）身份。
#
# ★ 本模块**不做**任何保活相关行为。
#   历史上尝试过的保活手段（常驻服务、看门狗、定时拉起等）已被验证会引入
#   系统级问题，故正式模块不再包含任何此类逻辑。
#   提醒可靠性由应用自身 + 可选的 Xposed/Vector 保活模块负责。
#
# 挂载映射（由 Magisk magic mount 完成）：
#   $MODPATH/system/priv-app/NoteVE/NoteVE.apk        → /system/priv-app/NoteVE/NoteVE.apk
#   $MODPATH/system/etc/permissions/com.noteVE.xml    → /system/etc/permissions/com.noteVE.xml

SKIPUNZIP=0

APK_PATH="$MODPATH/system/priv-app/NoteVE/NoteVE.apk"
XML_PATH="$MODPATH/system/etc/permissions/com.noteVE.xml"

# ── 校验必需文件 ────────────────────────────────────────────────
if [ ! -f "$APK_PATH" ]; then
  abort "! 缺少 APK：system/priv-app/NoteVE/NoteVE.apk
   请把 NoteVE-root-*.apk 重命名为 NoteVE.apk 后放入该目录，再打包模块。"
fi

if [ ! -f "$XML_PATH" ]; then
  abort "! 缺少权限白名单：system/etc/permissions/com.noteVE.xml"
fi

# ── 设定权限 ────────────────────────────────────────────────────
# 系统分区文件需为 0644（644 = rw-r--r--）；目录 0755。
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
set_perm "$APK_PATH" 0 0 0644
set_perm "$XML_PATH" 0 0 0644

ui_print " "
ui_print "**************************************"
ui_print "  VE笔记 系统应用模块"
ui_print "  作者: VFearIE"
ui_print "**************************************"
ui_print " "
ui_print "- 应用       : /system/priv-app/NoteVE/NoteVE.apk"
ui_print "- 权限白名单 : /system/etc/permissions/com.noteVE.xml"
ui_print " "
ui_print "- 白名单文件名必须是「包名.xml」，本模块已正确命名。"
ui_print "- 安装完成后请【重启设备】，系统才会扫描并授予特权。"
ui_print " "
