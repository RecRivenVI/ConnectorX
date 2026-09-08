package io.github.recrivenvi.connectorx

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Debug-only shell-launched receiver of real WindowInsets; no pairing or remote commands. */
class DisplaySemanticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT < 30) { finish(); return }
        enableEdgeToEdge()
        window.decorView.setOnApplyWindowInsetsListener { _, i ->
            Log.i("CX_SEMANTICS", "display=${display?.displayId} rotation=${display?.rotation} " +
                "status=${i.getInsets(WindowInsets.Type.statusBars())} statusVisible=${i.isVisible(WindowInsets.Type.statusBars())} " +
                "nav=${i.getInsets(WindowInsets.Type.navigationBars())} navVisible=${i.isVisible(WindowInsets.Type.navigationBars())} " +
                "stableNav=${i.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())} config=${resources.configuration}")
            i
        }
        setContent { MiuixTheme { Box(Modifier.fillMaxSize().background(Color(0xFF167B89)).safeDrawingPadding()) {
            Text("ConnectorX Display semantics probe")
            Text("SAFE BOTTOM", Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
        } } }
        applyIntent(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (android.os.Build.VERSION.SDK_INT >= 30) applyIntent(intent)
    }
    @androidx.annotation.RequiresApi(30)
    private fun applyIntent(i: Intent) {
        requestedOrientation = i.getIntExtra("orientation", -1)
        window.decorView.postDelayed({
            if(i.getBooleanExtra("fullscreen", false)) window.insetsController?.hide(WindowInsets.Type.systemBars())
            else window.insetsController?.show(WindowInsets.Type.systemBars())
            Log.i("CX_SEMANTICS", "request orientation=$requestedOrientation fullscreen=${i.getBooleanExtra("fullscreen",false)}")
            if (i.getBooleanExtra("conceal", false)) {
                window.insetsController?.controlWindowInsetsAnimation(WindowInsets.Type.navigationBars(), -1, null,
                    android.os.CancellationSignal(), object : android.view.WindowInsetsAnimationControlListener {
                        override fun onReady(c: android.view.WindowInsetsAnimationController, types: Int) {
                            if (i.getBooleanExtra("direct", false)) {
                                try {
                                    val f = c.javaClass.getDeclaredField("mControls").apply { isAccessible = true }
                                    val controls = f.get(c) as android.util.SparseArray<*>
                                    val source = controls.valueAt(0)!!
                                    val leash = source.javaClass.getMethod("getLeash").invoke(source) as android.view.SurfaceControl
                                    val copy = android.view.SurfaceControl::class.java.getConstructor(android.view.SurfaceControl::class.java, String::class.java)
                                        .newInstance(leash, "ConnectorXProbeCopy")
                                    c.finish(true)
                                    window.decorView.postDelayed({
                                        try {
                                            android.view.SurfaceControl.Transaction().use { t ->
                                                t.javaClass.getMethod("setAlpha", android.view.SurfaceControl::class.java, Float::class.javaPrimitiveType)
                                                    .invoke(t, copy, 0f)
                                                t.apply()
                                            }
                                            Log.i("CX_SEMANTICS", "direct-alpha-applied")
                                        } catch(e: Exception) { Log.e("CX_SEMANTICS", "direct-alpha-failed",e) }
                                    }, 500)
                                    window.decorView.postDelayed({
                                        val actual=window.decorView.rootWindowInsets
                                        Log.i("CX_SEMANTICS", "direct-observed nav=${actual?.getInsets(WindowInsets.Type.navigationBars())} visible=${actual?.isVisible(WindowInsets.Type.navigationBars())}")
                                    },1500)
                                    window.decorView.postDelayed({
                                        try { android.view.SurfaceControl.Transaction().use { t ->
                                            t.javaClass.getMethod("setAlpha", android.view.SurfaceControl::class.java, Float::class.javaPrimitiveType).invoke(t,copy,1f)
                                            t.apply()
                                        } } finally { copy.release() }
                                    },6500)
                                } catch(e: Exception) {
                                    Log.e("CX_SEMANTICS", "direct-leash-unavailable",e)
                                    if(!c.isFinished && !c.isCancelled) c.finish(true)
                                }
                                return
                            }
                            c.setInsetsAndAlpha(c.shownStateInsets, 0f, 1f)
                            Log.i("CX_SEMANTICS", "alpha-zero shown=${c.shownStateInsets}")
                            window.decorView.postDelayed({
                                val actual = window.decorView.rootWindowInsets
                                Log.i("CX_SEMANTICS", "alpha-zero-observed nav=${actual?.getInsets(WindowInsets.Type.navigationBars())} visible=${actual?.isVisible(WindowInsets.Type.navigationBars())}")
                            }, 1500)
                            window.decorView.postDelayed({ if (!c.isFinished && !c.isCancelled) c.finish(true) }, 6500)
                        }
                        override fun onFinished(c: android.view.WindowInsetsAnimationController) { Log.i("CX_SEMANTICS", "alpha-finished") }
                        override fun onCancelled(c: android.view.WindowInsetsAnimationController?) { Log.i("CX_SEMANTICS", "alpha-cancelled") }
                    })
            }
        }, 500)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (android.os.Build.VERSION.SDK_INT >= 30)
            Log.i("CX_SEMANTICS", "configuration display=${display?.displayId} rotation=${display?.rotation} $newConfig")
    }
}
