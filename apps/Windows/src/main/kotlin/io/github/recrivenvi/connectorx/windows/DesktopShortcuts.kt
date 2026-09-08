package io.github.recrivenvi.connectorx.windows

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import org.json.JSONObject
import java.nio.file.*
import java.util.UUID

/** Shortcuts contain an opaque local ID; launch always resolves it against the current pairing store. */
class DesktopShortcuts(private val directory:Path) {
    private val file=directory.resolve("shortcuts.json")
    @Synchronized fun resolve(id:String):Pair<String,String> {
        require(runCatching {UUID.fromString(id)}.isSuccess) {"快捷方式标识无效"}
        val obj=JSONObject(Files.readString(file)).getJSONObject(id)
        return obj.getString("peerId") to obj.getString("package")
    }
    @Synchronized fun create(peer:DesktopPeer,app:DesktopApp):Path {
        val exe=System.getProperty("jpackage.app-path") ?: error("请使用打包的 ConnectorX.exe 创建快捷方式")
        val data=if(Files.exists(file)) JSONObject(Files.readString(file)) else JSONObject()
        val id=data.keySet().firstOrNull {data.getJSONObject(it).let {o->o.optString("peerId")==peer.id&&o.optString("package")==app.packageName}} ?: UUID.randomUUID().toString()
        data.put(id,JSONObject().put("peerId",peer.id).put("package",app.packageName))
        Files.createDirectories(directory);val temp=Files.createTempFile(directory,"shortcut-",".tmp")
        try {Files.writeString(temp,data.toString(2));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)} finally {Files.deleteIfExists(temp)}
        val label=(peer.name+" · "+app.name).replace(Regex("[\\\\/:*?\"<>|]"),"_").take(100)
        val path=Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Desktop),"$label-${id.take(8)}.lnk")
        // Fixed script: paths and opaque ID are data in environment variables, never executable interpolation.
        val script="""
            ${'$'}ErrorActionPreference='Stop'
            ${'$'}shell=New-Object -ComObject WScript.Shell
            ${'$'}link=${'$'}shell.CreateShortcut(${'$'}env:CONNECTORX_LINK)
            ${'$'}link.TargetPath=${'$'}env:CONNECTORX_EXE
            ${'$'}link.Arguments='--open '+${'$'}env:CONNECTORX_TARGET
            ${'$'}link.WorkingDirectory=[System.IO.Path]::GetDirectoryName(${'$'}env:CONNECTORX_EXE)
            ${'$'}link.Save()
        """.trimIndent()
        val builder=ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-Command",script)
        builder.environment().putAll(mapOf("CONNECTORX_LINK" to path.toString(),"CONNECTORX_EXE" to exe,"CONNECTORX_TARGET" to id))
        val process=builder.redirectErrorStream(true).start()
        val log=process.inputStream.bufferedReader().readText()
        check(process.waitFor()==0&&Files.exists(path)) {"快捷方式创建失败：${log.take(300)}"}
        return path
    }
}
