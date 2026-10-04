#!/bin/sh
# ============================================================
# 构建 Xposed 保活模块（LSPosed / Vector）
# 构建流程要点（含 4 个已验证的坑）：
#   ① meta-data 必须在 <application> 内（否则管理器识别不出）
#   ② API 桩编译成 jar，d8 用 --lib 引用（不进 dex）
#   ③ 作用域默认含 system（Hook 点在 system_server）
#   ④ 产物校验 meta-data 层级
# ============================================================
set -e
cd "$(dirname "$0")"

# 工具链路径（可用环境变量覆盖）
BT="${BT:-/opt/android-sdk/build-tools/34.0.0}"
AJ="${AJ:-/opt/android-sdk/platforms/android-34/android.jar}"

# 签名配置（本模块仅需**自签名**；发布用你自己的 keystore）
#   KS      密钥库路径
#   KS_PASS 密钥库口令 / 别名口令（默认用 Android 调试密钥）
#   KS_ALIAS 别名
KS="${KS:-$HOME/.android/debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KS_ALIAS="${KS_ALIAS:-androiddebugkey}"

# 若指定了密钥库但不存在 → 自动生成一个调试密钥库（仅用于本地测试）
if [ ! -f "$KS" ]; then
  echo "== 0/7 生成调试密钥库（仅本地测试用）=="
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -v -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1 || true
  echo "   $KS"
fi

OUT=XposedKeepAlive.apk

rm -rf build api-stubs.jar res.zip out.apk "$OUT" XposedKeepAlive.aligned.apk
mkdir -p build/classes build/stubs

echo "== 1/7 编译 Xposed API 桩 =="
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
    -d build/stubs $(find stubs -name '*.java') 2>&1 | grep -v 'bootstrap class path' || true
(cd build/stubs && jar cf ../../api-stubs.jar .)
echo "   api-stubs.jar $(du -h api-stubs.jar | cut -f1)"

echo "== 2/7 编译模块源码 =="
javac -source 8 -target 8 -nowarn -cp api-stubs.jar -bootclasspath "$AJ" \
    -d build/classes -encoding UTF-8 \
    $(find src -name '*.java') 2>&1 | grep -v 'bootstrap class path' || true

echo "== 3/7 dex（API 桩以 --lib 引用，不进包）=="
"$BT/d8" --min-api 28 --lib "$AJ" --lib api-stubs.jar --output build \
    $(find build/classes -name '*.class')

echo "== 4/7 编译资源（作用域默认值）=="
if [ -d res ]; then
    "$BT/aapt2" compile --dir res -o res.zip 2>/dev/null || { : > res.zip; }
else
    : > res.zip
fi

echo "== 5/7 链接清单 + assets =="
if [ -s res.zip ]; then
    "$BT/aapt2" link -o out.apk --manifest AndroidManifest.xml -I "$AJ" \
        -A assets --min-sdk-version 28 --target-sdk-version 28 res.zip
else
    "$BT/aapt2" link -o out.apk --manifest AndroidManifest.xml -I "$AJ" \
        -A assets --min-sdk-version 28 --target-sdk-version 28
fi

echo "== 6/7 注入 classes.dex =="
python3 - <<'PY'
import zipfile, shutil, os
shutil.copy('out.apk', 'XposedKeepAlive.apk')
z = zipfile.ZipFile('XposedKeepAlive.apk', 'a', zipfile.ZIP_DEFLATED)
z.write('build/classes.dex', 'classes.dex')
z.close()
print('   classes.dex', os.path.getsize('build/classes.dex'), 'bytes')
PY

echo "== 7/7 对齐 + 签名 =="
"$BT/zipalign" -f 4 XposedKeepAlive.apk XposedKeepAlive.aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --ks-key-alias "$KS_ALIAS" --out "$OUT" XposedKeepAlive.aligned.apk
"$BT/apksigner" verify "$OUT" >/dev/null && echo "   签名 OK"

echo
echo "== 校验（★ meta-data 必须在 application 内）=="
"$BT/aapt2" dump xmltree --file AndroidManifest.xml "$OUT" 2>/dev/null \
    | grep -E "E: application|E: meta-data|E: manifest|xposed" | head -10
echo
echo "== 包内容 =="
python3 -c "
import zipfile
z=zipfile.ZipFile('$OUT')
names=[n for n in z.namelist() if n.endswith(('.dex','xposed_init')) or 'xposed' in n]
print(' ', names)
"
ls -la "$OUT"
