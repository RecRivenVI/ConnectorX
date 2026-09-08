package io.github.recrivenvi.connectorx.connection

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import io.github.recrivenvi.connectorx.protocol.DeviceProfile
import io.github.recrivenvi.connectorx.protocol.Wire
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.Socket
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

data class Peer(val id: String, val name: String, val host: String, val profile: DeviceProfile = DeviceProfile.ANDROID)

class Identity(context: Context) {
    private val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val legacyAlias = "connectorx-device-v1"
    private val tlsAlias = "connectorx-device-tls-v2"
    private val alias = when {
        store.containsAlias(tlsAlias) -> tlsAlias
        store.containsAlias(legacyAlias) && (supportsRawSignature(legacyAlias) || peers().isNotEmpty()) -> legacyAlias
        else -> tlsAlias
    }
    init {
        if (!store.containsAlias(alias)) {
            // Never silently transfer existing authorizations to a newly generated identity.
            check(peers().isEmpty()) { "设备密钥不可用；保留配对记录，需显式处理身份恢复" }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    // Conscrypt hashes TLS messages itself and requests NONEwithECDSA from Keystore.
                    // DIGEST_NONE authorizes signing that digest; it does not disable TLS hashing.
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setCertificateSubject(X500Principal("CN=ConnectorX"))
                    .setCertificateSerialNumber(BigInteger(120, SecureRandom()))
                    .setCertificateNotBefore(Date(0))
                    .setCertificateNotAfter(Date(4102444800000L)).build())
                generateKeyPair()
            }
        }
    }
    private fun supportsRawSignature(name: String): Boolean {
        val key = store.getKey(name, null) as PrivateKey
        val info = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)
        return KeyProperties.DIGEST_NONE in info.digests
    }
    private fun verifyTlsSigning() {
        check(supportsRawSignature(alias)) { "旧身份缺少 TLS 签名权限；已有配对记录已保留，未自动更换身份" }
        val digest = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val signature = Signature.getInstance("NONEwithECDSA").run {
                initSign(store.getKey(alias, null) as PrivateKey); update(digest); sign()
            }
            check(Signature.getInstance("NONEwithECDSA").run {
                initVerify(cert.publicKey); update(digest); verify(signature)
            }) { "签名自检不匹配" }
        } catch (e: Exception) { throw IllegalStateException("本机 TLS 密钥签名自检失败：${e.message}", e) }
    }
    private val cert get() = store.getCertificate(alias) as X509Certificate
    val id: String get() = Wire.fingerprint(cert.encoded)
    @Synchronized fun peers(): List<Peer> {
        val data = JSONArray(prefs.getString("peers", "[]"))
        return (0 until data.length()).map { data.getJSONObject(it).let { p ->
            Peer(p.getString("id"), p.getString("name"), p.getString("host"), DeviceProfile(p.optString("platform", "android"), p.optBoolean("canHost", true), p.optBoolean("canReceiveCast", false)))
        } }
    }
    @Synchronized fun paired(id: String) = peers().any { it.id == id }
    @Synchronized fun save(peer: Peer) = write(peers().filterNot { it.id == peer.id } + peer)
    @Synchronized fun remove(id: String) = write(peers().filterNot { it.id == id })
    private fun write(peers: List<Peer>) {
        val arr = JSONArray()
        peers.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("platform", it.profile.platform()).put("canHost", it.profile.canHost()).put("canReceiveCast", it.profile.canReceiveCast())) }
        check(prefs.edit().putString("peers", arr.toString()).commit()) { "Cannot persist pairing" }
    }
    fun tls(accept: (String) -> Boolean): SSLContext {
        verifyTlsSigning()
        val keyManager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(type: String?, issuers: Array<out Principal>?) = arrayOf(alias)
            override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias
            override fun getServerAliases(type: String?, issuers: Array<out Principal>?) = if (type == "EC") arrayOf(alias) else null
            override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?) = if (type == "EC") alias else null
            override fun getCertificateChain(a: String?) = arrayOf(cert)
            override fun getPrivateKey(a: String?) = store.getKey(alias, null) as PrivateKey
        }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            private fun verify(chain: Array<out X509Certificate>) {
                require(chain.size == 1) { "Expected device certificate" }
                chain[0].checkValidity()
                chain[0].verify(chain[0].publicKey)
                if (!accept(Wire.fingerprint(chain[0].encoded))) throw java.security.cert.CertificateException("Device is not paired")
            }
            override fun checkClientTrusted(chain: Array<out X509Certificate>, type: String?) = verify(chain)
            override fun checkServerTrusted(chain: Array<out X509Certificate>, type: String?) = verify(chain)
        }
        return SSLContext.getInstance("TLSv1.3").apply { init(arrayOf(keyManager), arrayOf(trust), SecureRandom()) }
    }
    fun remoteId(socket: SSLSocket) = Wire.fingerprint(socket.session.peerCertificates[0].encoded)
}
