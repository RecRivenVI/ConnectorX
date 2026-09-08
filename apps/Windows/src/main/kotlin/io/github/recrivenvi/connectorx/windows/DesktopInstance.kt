package io.github.recrivenvi.connectorx.windows

import com.sun.jna.platform.win32.Crypt32Util
import org.json.JSONObject
import java.io.*
import java.net.*
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Per-user single instance and authenticated loopback handoff for opaque shortcut IDs. */
class DesktopInstance(private val directory:Path):AutoCloseable {
    private val channel:FileChannel
    private val lock:java.nio.channels.FileLock?
    private val endpoint=directory.resolve("instance.dpapi")
    private var listener:ServerSocket?=null
    val primary:Boolean
    init {
        Files.createDirectories(directory)
        channel=FileChannel.open(directory.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)
        lock=runCatching {channel.tryLock()}.getOrNull();primary=lock!=null
    }
    fun listen(onCommand:(String)->Unit) {
        check(primary)
        val token=Base64.getEncoder().encodeToString(ByteArray(32).also {SecureRandom().nextBytes(it)})
        val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));listener=server
        val bytes=JSONObject().put("port",server.localPort).put("token",token).toString().toByteArray()
        val temp=Files.createTempFile(directory,"instance-",".tmp")
        try {Files.write(temp,Crypt32Util.cryptProtectData(bytes));Files.move(temp,endpoint,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)} finally {Files.deleteIfExists(temp)}
        Thread({while(!server.isClosed) {
            try {server.accept().use {socket->
                socket.soTimeout=2000;val input=DataInputStream(socket.inputStream)
                val received=input.readUTF()
                if(MessageDigest.isEqual(received.toByteArray(),token.toByteArray())) {
                    val cmd=input.readUTF();if(cmd.isEmpty()||runCatching {java.util.UUID.fromString(cmd)}.isSuccess) onCommand(cmd)
                }
            }} catch(_:IOException) {if(server.isClosed) break}
        }},"shortcut-handoff").apply {isDaemon=true;start()}
    }
    fun forward(command:String) {
        check(!primary)
        val end=System.nanoTime()+5_000_000_000L
        var failure:Exception?=null
        while(System.nanoTime()<end) {
            try {
                val obj=JSONObject(String(Crypt32Util.cryptUnprotectData(Files.readAllBytes(endpoint)),Charsets.UTF_8))
                Socket().use {socket->socket.connect(InetSocketAddress("127.0.0.1",obj.getInt("port")),1000)
                    DataOutputStream(socket.outputStream).apply {writeUTF(obj.getString("token"));writeUTF(command);flush()}}
                return
            } catch(e:Exception) {failure=e;Thread.sleep(100)}
        }
        throw IOException("已有客户端尚未准备好接收启动请求",failure)
    }
    override fun close() {
        listener?.close()
        if(primary) Files.deleteIfExists(endpoint)
        lock?.release();channel.close()
    }
}
