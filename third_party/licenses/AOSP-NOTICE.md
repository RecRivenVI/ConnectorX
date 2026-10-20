# AOSP navigation icons

Copyright (C) 2018 The Android Open Source Project.
Licensed under the Apache License, Version 2.0; see Apache-2.0.txt in this directory.

Source: aosp-mirror/platform_frameworks_base, fixed tag `android-16.0.0_r3`.

| Upstream source | ConnectorX destination |
| --- | --- |
| [ic_sysbar_recent.xml](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r3/packages/SystemUI/res/drawable/ic_sysbar_recent.xml) | app/src/main/res/drawable/ic_aosp_recent.xml |
| [ic_sysbar_home.xml](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r3/packages/SystemUI/res/drawable/ic_sysbar_home.xml) | app/src/main/res/drawable/ic_aosp_home.xml |
| [ic_sysbar_back.xml](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r3/packages/SystemUI/res/drawable/ic_sysbar_back.xml) | app/src/main/res/drawable/ic_aosp_back.xml |

Modifications: resource filenames and `?attr/singleToneColor` changed to
`@android:color/white`, with final tint supplied by the Compose Miuix theme.
Vector geometry, viewport, intrinsic dimensions, Back auto-mirroring and original
copyright/license headers are preserved. Icons are displayed at 22dp.

This notice and the license are bundled in the Android APK under assets/licenses/.
