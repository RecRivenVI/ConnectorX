package io.github.recrivenvi.connectorx.windows

import com.sun.jna.platform.win32.Crypt32Util
import io.github.recrivenvi.connectorx.protocol.DeviceProfile
import io.github.recrivenvi.connectorx.protocol.Wire
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.math.BigInteger
import java.nio.file.*
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.*

data class DesktopPeer(val id:String, val name:String, val host:String, val profile:DeviceProfile)

/** Private key protected by Windows DPAPI for this user. No ADB identity, shared key or remote shell. */
class DesktopIdentity(val directory:Path = Path.of(System.getenv("LOCALAPPDATA"), "ConnectorX")) {
    private val store=KeyStore.getInstance("PKCS12")
    private val pass="ConnectorX-DPAPI-container".toCharArray()
    private val peersFile=directory.resolve("peers.json")
    private val keyFile=directory.resolve("identity.dpapi")
    init {
        Files.createDirectories(directory)
        if(Files.exists(keyFile)) {
            val plain=Crypt32Util.cryptUnprotectData(Files.readAllBytes(keyFile))
            try { store.load(ByteArrayInputStream(plain),pass) } finally { plain.fill(0) }
        } else {
            check(!Files.exists(peersFile)) { "身份丢失；配对记录已保留，请恢复身份" }
            store.load(null,pass)
            val pair=KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val subject=X500Name("CN=ConnectorX Windows")
            val holder=JcaX509v3CertificateBuilder(subject,BigInteger(120,SecureRandom()),Date(System.currentTimeMillis()-86400000),
                Date(System.currentTimeMillis()+20L*365*86400000),subject,pair.public)
                .build(JcaContentSignerBuilder("SHA256withECDSA").build(pair.private))
            val cert=JcaX509CertificateConverter().getCertificate(holder)
            store.setKeyEntry("device",pair.private,pass,arrayOf(cert))
            val bytes=ByteArrayOutputStream().also { store.store(it,pass) }.toByteArray()
            try { atomicWrite(keyFile,Crypt32Util.cryptProtectData(bytes)) } finally { bytes.fill(0) }
        }
    }
    val id:String get()=Wire.fingerprint(store.getCertificate("device").encoded)
    @Synchronized fun peers():List<DesktopPeer> {
        if(!Files.exists(peersFile)) return emptyList()
        val a=JSONArray(Files.readString(peersFile))
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { p ->
            DesktopPeer(p.getString("id"),p.getString("name"),p.getString("host"),DeviceProfile(
                p.getString("platform"),p.getBoolean("canHost"),p.getBoolean("canReceiveCast")))
        } }
    }
    @Synchronized fun paired(id:String)=peers().any { it.id==id }
    @Synchronized fun save(peer:DesktopPeer) { writePeers(peers().filterNot { it.id==peer.id }+peer) }
    @Synchronized fun remove(id:String) { writePeers(peers().filterNot { it.id==id }) }
    private fun writePeers(peers:List<DesktopPeer>) {
        val a=JSONArray(); peers.forEach { p -> a.put(JSONObject().put("id",p.id).put("name",p.name).put("host",p.host)
            .put("platform",p.profile.platform()).put("canHost",p.profile.canHost()).put("canReceiveCast",p.profile.canReceiveCast())) }
        atomicWrite(peersFile,a.toString(2).toByteArray(Charsets.UTF_8))
    }
    fun tls(accept:(String)->Boolean):SSLContext {
        val km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store,pass) }
        val trust=object:X509TrustManager {
            override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
            private fun verify(chain:Array<out X509Certificate>) {
                require(chain.size==1); chain[0].checkValidity(); chain[0].verify(chain[0].publicKey)
                if(!accept(Wire.fingerprint(chain[0].encoded))) throw java.security.cert.CertificateException("设备未配对或身份不匹配")
            }
            override fun checkClientTrusted(chain:Array<out X509Certificate>,type:String)=verify(chain)
            override fun checkServerTrusted(chain:Array<out X509Certificate>,type:String)=verify(chain)
        }
        return SSLContext.getInstance("TLSv1.3").apply { init(km.keyManagers,arrayOf(trust),SecureRandom()) }
    }
    fun remoteId(socket:SSLSocket)=Wire.fingerprint(socket.session.peerCertificates[0].encoded)
    private fun atomicWrite(path:Path,bytes:ByteArray) {
        val temp=Files.createTempFile(directory,"state-",".tmp")
        try { Files.write(temp,bytes); Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temp) }
    }
}
