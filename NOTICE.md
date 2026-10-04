# NoteApp 开源组件声明（NOTICE）

本项目（NoteApp）为独立自研 Android 原生笔记应用，作为 system/priv-app 随商用 ROM 预装分发。
本文件列出本项目所依赖的开源组件及其版权与许可证信息，以满足 Apache-2.0 等许可证的 NOTICE 义务。

## 依赖组件清单

| 组件 | 版本 | 许可证 | 来源 |
|---|---|---|---|
| AndroidX / Jetpack | — | Apache-2.0 | https://developer.android.com/jetpack |
| Jetpack Compose | 1.7.x (BOM 2024.10.00) | Apache-2.0 | https://developer.android.com/jetpack/compose |
| AndroidX Room | 2.6.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room |
| AndroidX Navigation | 2.8.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation |
| AndroidX Lifecycle | 2.8.6 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| AndroidX Activity | 1.9.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| Kotlin Standard Library | 2.0.21 | Apache-2.0 | https://kotlinlang.org |
| kotlinx.coroutines | 1.8.1 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| SQLite | (系统内置) | Public Domain | https://sqlite.org |
| org.json | (AOSP) | Apache-2.0 | https://source.android.com |
| material-color-utilities | 0.1.2 | Apache-2.0 | https://github.com/material-foundation/material-color-utilities |

## 版权归属

- AndroidX、Jetpack Compose、Room、Navigation、Lifecycle、Activity：Copyright © The Android Open Source Project，Apache License 2.0。
- Kotlin、kotlinx.coroutines：Copyright © JetBrains s.r.o. and Kotlin Programming Language contributors，Apache License 2.0。
- SQLite：Public Domain（D. Richard Hipp 及贡献者）。

完整许可证文本见应用内「开源许可」页面及 `app/src/main/assets/licenses/apache-2.0.txt`。

## 说明

- 本项目代码均为独立自研实现，未复制任何第三方项目源码文件。
- 项目名称「NoteApp」与任何第三方项目的名称、Logo 无关，不构成商标使用。
- priv-app 仅为 Android 系统权限白名单机制，不产生额外版权约束。
