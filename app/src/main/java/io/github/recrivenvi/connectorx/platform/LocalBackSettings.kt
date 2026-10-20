// Local navigation setting adapted from MishkaApplication 52f2a7c; see third_party/licenses/Mishka-NOTICE.md.
package io.github.recrivenvi.connectorx.platform

import android.content.pm.ApplicationInfo
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

object LocalBackSettings {
    fun apply(info:ApplicationInfo,enabled:Boolean) {
        if(Build.VERSION.SDK_INT<34) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
            ApplicationInfo::class.java.getDeclaredMethod("setEnableOnBackInvokedCallback",Boolean::class.javaPrimitiveType).apply {
                isAccessible=true; invoke(info,enabled)
            }
        }.onFailure { android.util.Log.w("ConnectorX","Local predictive back preference unavailable",it) }
    }
}
