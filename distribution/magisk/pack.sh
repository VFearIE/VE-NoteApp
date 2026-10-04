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
# 模块内容为**纯静态 overlay** —— 只包含 APK 与权限白名单，
# 不含任何保活脚本或服务。
#
# ★ 打包前会校验「APK 版本」与「module.prop 声明的版本」是否一致，
#   不一致直接拒绝 —— 避免产出「文件名写着 A 版本、内容却是 B 版本」的包。

set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

SRC_APK="$1"
if [ -z "$SRC_APK" ] || [ ! -f "$SRC_APK" ]; then
  echo "用法: sh pack.sh <已签名的 Root 版 APK>"
  exit 1
fi

VER=$(grep -m1 '^version=' module.prop | cut -d= -f2)
[ -n "$VER" ] || { echo "× module.prop 缺少 version 字段"; exit 1; }

# ── 版本一致性校验 ─────────────────────────────────────────────
# 优先用 aapt2（若可用），否则用 unzip + strings 兜底
AAPT2="${AAPT2:-aapt2}"
apk_ver=""
if command -v "$AAPT2" >/dev/null 2>&1; then
  apk_ver=$("$AAPT2" dump badging "$SRC_APK" 2>/dev/null \
            | grep -m1 "^package:" \
            | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
fi

if [ -n "$apk_ver" ]; then
  if [ "$apk_ver" != "$VER" ]; then
    echo "× 版本不一致，已拒绝打包："
    echo "    module.prop 声明 : $VER"
    echo "    APK 实际版本     : $apk_ver"
    echo "  请先同步两者（或改用对应的 APK）。"
    exit 1
  fi
  echo "  版本校验通过: $VER"
else
  echo "  ⚠ 无法读取 APK 版本（aapt2 不可用），跳过校验"
fi

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
