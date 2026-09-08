package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.AppRequest
import org.json.JSONObject
import java.awt.GraphicsEnvironment
import java.nio.file.*

data class AppOptions(val width:Int=960,val height:Int=720,val dpi:Int=160,val refreshRate:Float=60f,
    val systemDecorations:Boolean=false,val followWindow:Boolean=true,val unicodePaste:Boolean=true) {
    fun request(pkg:String)=AppRequest(pkg,width,height,dpi,refreshRate,systemDecorations)
    fun json()=JSONObject().put("width",width).put("height",height).put("dpi",dpi).put("refreshRate",refreshRate)
        .put("systemDecorations",systemDecorations).put("followWindow",followWindow).put("unicodePaste",unicodePaste)
    companion object {
        fun defaults():AppOptions {
            if(GraphicsEnvironment.isHeadless()) return AppOptions()
            val screen=GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            val scale=screen.defaultConfiguration.defaultTransform.scaleX
            val rate=screen.displayMode.refreshRate.takeIf {it in 24..240} ?: 60
            return AppOptions((960*scale).toInt().coerceIn(128,4096),(720*scale).toInt().coerceIn(128,4096),
                (160*scale).toInt().coerceIn(72,960),rate.toFloat())
        }
        fun read(j:JSONObject)=AppOptions(j.getInt("width"),j.getInt("height"),j.getInt("dpi"),j.getDouble("refreshRate").toFloat(),
            j.optBoolean("systemDecorations"),j.optBoolean("followWindow",true),j.optBoolean("unicodePaste",true))
    }
}

class AppOptionsStore(private val directory:Path) {
    private val file=directory.resolve("remote-app-options.json")
    private val data=if(Files.exists(file)) JSONObject(Files.readString(file)) else JSONObject()
    @Synchronized fun load(pkg:String):AppOptions=data.optJSONObject(pkg)?.let { AppOptions.read(it) } ?: AppOptions.defaults()
    @Synchronized fun save(pkg:String,options:AppOptions) {
        options.request(pkg) // Same bounds and package validation as the wire request.
        data.put(pkg,options.json()); Files.createDirectories(directory)
        val temp=Files.createTempFile(directory,"app-options-",".tmp")
        try { Files.writeString(temp,data.toString(2)); Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temp) }
    }
}
