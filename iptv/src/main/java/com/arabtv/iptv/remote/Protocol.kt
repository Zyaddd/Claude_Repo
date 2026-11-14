package com.arabtv.iptv.remote

import com.arabtv.iptv.data.Channel
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class Action { PLAY, TOGGLE, STOP, VOL_UP, VOL_DOWN, MUTE, NEXT, PREV }

/** A command from a phone to a TV. Same JSON on the local network and over the relay. */
data class Command(
    val action: Action,
    val channel: Channel? = null,
    val from: String = "",
    val ts: Long = System.currentTimeMillis(),
    val nonce: String = newNonce(),
) {
    fun toJson(): String = JSONObject().apply {
        put("v", Protocol.VERSION)
        put("action", action.name)
        channel?.let { put("channel", it.toJson()) }
        put("from", from)
        put("ts", ts)
        put("nonce", nonce)
    }.toString()

    companion object {
        fun parse(json: String): Command? = runCatching {
            val o = JSONObject(json)
            val action = Action.valueOf(o.getString("action"))
            val channel = o.optJSONObject("channel")?.let { Channel.fromJson(it) }
            if (action == Action.PLAY && channel == null) return null
            Command(
                action = action,
                channel = channel,
                from = o.optString("from").take(60),
                ts = o.optLong("ts"),
                nonce = o.optString("nonce").take(64),
            )
        }.getOrNull()

        private fun newNonce(): String {
            val b = ByteArray(12)
            SecureRandom().nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }
    }
}

object Protocol {
    const val VERSION = 1
    const val NSD_TYPE = "_arabtv._tcp."
    const val MAX_AGE_MS = 10 * 60 * 1000L

    /** Relay topic for a pairing code. A hash, so the code itself never appears on the relay. */
    fun topic(code: String): String = "arabtv_" + hex(sha256("arabtv-topic|$code".toByteArray())).take(32)

    /** Wraps a command for the relay, signed with the pairing code so nobody else can forge one. */
    fun seal(cmd: Command, code: String): String {
        val payload = cmd.toJson()
        return JSONObject().put("p", payload).put("s", sign(payload, code)).toString()
    }

    /** Returns the command if the envelope's signature matches the code; null otherwise. */
    fun open(envelope: String, code: String): Command? = runCatching {
        val o = JSONObject(envelope)
        val payload = o.getString("p")
        val sig = o.getString("s")
        if (!MessageDigest.isEqual(sig.toByteArray(), sign(payload, code).toByteArray())) return null
        Command.parse(payload)
    }.getOrNull()

    fun sign(payload: String, code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("arabtv-sign|$code".toByteArray(), "HmacSHA256"))
        return hex(mac.doFinal(payload.toByteArray()))
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}

/** Drops replays and stale commands (relays can redeliver; anyone could resend an old message). */
class ReplayGuard(private val maxAgeMs: Long = Protocol.MAX_AGE_MS) {
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 500
    }

    @Synchronized
    fun accept(cmd: Command, now: Long = System.currentTimeMillis()): Boolean {
        if (kotlin.math.abs(now - cmd.ts) > maxAgeMs) return false
        if (cmd.nonce.isEmpty() || seen.containsKey(cmd.nonce)) return false
        seen[cmd.nonce] = true
        return true
    }
}
