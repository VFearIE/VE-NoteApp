#!/bin/sh
#
# 打包 VE笔记 Magisk 模块。
#
# 用法：
#   sh pack.sh <已签名的 Root 版 APK 路径>
#
# 产出：
#   ../VE笔记-Magisk-<version>.zip
#
# 说明：模块内容为纯静态 overlay —— 只包含 APK 与权限白名单，
#       不含任何保活脚本或服务。

set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

SRC_APK="$1"
if [ -z "$SRC_APK" ] || [ ! -f "$SRC_APK" ]; then
  echo "用法: sh pack.sh <已签名的 Root 版 APK>"
  exit 1
fi

VER=$(grep -m1 '^version=' module.prop | cut -d= -f2)
OUT="$HERE/../VE笔记-Magisk-${VER}.zip"
TMP="$(mktemp -d)"

cp module.prop customize.sh "$TMP/"
mkdir -p "$TMP/system/priv-app/NoteVE" "$TMP/system/etc/permissions"
cp "$SRC_APK" "$TMP/system/priv-app/NoteVE/NoteVE.apk"
cp system/etc/permissions/com.noteVE.xml "$TMP/system/etc/permissions/"

rm -f "$OUT"
(cd "$TMP" && zip -qr "$OUT" .)

rm -rf "$TMP"
echo "已生成: $OUT"
unzip -l "$OUT" | sed -n '3,12p'
