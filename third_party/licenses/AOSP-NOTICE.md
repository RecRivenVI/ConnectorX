# AOSP icons

Copyright (C) 2018 The Android Open Source Project.
Licensed under the Apache License, Version 2.0; see Apache-2.0.txt in this directory.

Source: aosp-mirror/platform_frameworks_base, fixed tag `android-16.0.0_r3`.

| Upstream source | ConnectorX destination |
| --- | --- |
| [ic_sysbar_recent.xml](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r3/packages/SystemUI/res/drawable/ic_sysbar_recent.xml) | app/src/main/res/drawable/ic_aosp_recent.xml |
| [ic_sysbar_home.xml](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r3/packages/SystemUI/res/drawable/ic_sysbar_home.xml) | app/src/main/res/drawable/ic_aosp_home.xml |
| [ic_sysbar_back.xml](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r3/packages/SystemUI/res/drawable/ic_sysbar_back.xml) | app/src/main/res/drawable/ic_aosp_back.xml |
| [ic_smartphone.xml](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r3/packages/SettingsLib/res/drawable/ic_smartphone.xml) | core/ui/src/commonMain/composeResources/drawable/ic_aosp_smartphone.xml |

Modifications: resource filenames and `?attr/singleToneColor` changed to
`@android:color/white`, with final tint supplied by the Compose Miuix theme.
Vector geometry, viewport, intrinsic dimensions, Back auto-mirroring and original
copyright/license headers are preserved. Navigation icons are displayed at 22dp.

The smartphone icon is Copyright (C) 2019 The Android Open Source Project,
under the same Apache-2.0 license. Its resource filename is changed and the
Android theme tint attribute is removed for Compose resource loading. Geometry,
fill, dimensions and the original license header are preserved; Miuix supplies
the final tint. Navigation and device cards share this resource.

This notice and the license are bundled in the Android APK under assets/licenses/.
