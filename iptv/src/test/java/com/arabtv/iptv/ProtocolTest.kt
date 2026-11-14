package com.arabtv.iptv

import com.arabtv.iptv.data.Category
import com.arabtv.iptv.data.Channel
import com.arabtv.iptv.data.Pairing
import com.arabtv.iptv.data.StreamSource
import com.arabtv.iptv.remote.Action
import com.arabtv.iptv.remote.Command
import com.arabtv.iptv.remote.Protocol
import com.arabtv.iptv.remote.ReplayGuard
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    private val channel = Channel(
        id = "id:AlJazeera.qa@SD",
        name = "Al Jazeera",
        nameAr = "الجزيرة",
        logo = "https://x/aj.png",
        categories = setOf(Category.NEWS),
        country = "QA",
        sources = listOf(StreamSource("https://live.example/aj.m3u8", userAgent = "UA", referrer = "https://ref/")),
    )

    @Test
    fun commandRoundTrips() {
        val cmd = Command(Action.PLAY, channel, from = "Pixel")
        val back = Command.parse(cmd.toJson())!!
        assertEquals(Action.PLAY, back.action)
        assertEquals(channel, back.channel)
        assertEquals("Pixel", back.from)
        assertEquals(cmd.nonce, back.nonce)
    }

    @Test
    fun playWithoutChannelOrWithBadUrlIsRejected() {
        assertNull(Command.parse("""{"action":"PLAY","ts":1,"nonce":"a"}"""))
        val bad = JSONObject(Command(Action.PLAY, channel).toJson())
        bad.getJSONObject("channel").getJSONArray("sources").getJSONObject(0).put("url", "file:///sdcard/x")
        assertNull(Command.parse(bad.toString()))
        assertNull(Command.parse("""{"action":"RM_RF"}"""))
    }

    @Test
    fun sealedEnvelopeOnlyOpensWithTheRightCode() {
        val code = "ABCDE23456"
        val env = Protocol.seal(Command(Action.TOGGLE), code)
        assertNotNull(Protocol.open(env, code))
        assertNull(Protocol.open(env, "ABCDE23457"))
        // Tampering with the payload breaks the signature.
        val o = JSONObject(env)
        o.put("p", o.getString("p").replace("TOGGLE", "STOP"))
        assertNull(Protocol.open(o.toString(), code))
    }

    @Test
    fun topicHidesCodeAndIsValidForNtfy() {
        val t = Protocol.topic("ABCDE23456")
        assertFalse(t.contains("ABCDE"))
        assertTrue(t.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        assertEquals(t, Protocol.topic("ABCDE23456"))
    }

    @Test
    fun replayGuardDropsRepeatsAndStaleCommands() {
        val g = ReplayGuard()
        val now = System.currentTimeMillis()
        val c = Command(Action.TOGGLE, ts = now)
        assertTrue(g.accept(c, now))
        assertFalse(g.accept(c, now))
        assertFalse(g.accept(Command(Action.TOGGLE, ts = now - 3_600_000), now))
    }

    @Test
    fun pairingCodesNormalize() {
        assertEquals("ABCDE23456", Pairing.normalize("abcde-23456 "))
        assertEquals("ABCDE-23456", Pairing.format("ABCDE23456"))
    }

    @Test
    fun relayEnvelopeStaysUnderNtfyLimit() {
        val big = channel.copy(sources = List(8) { StreamSource("https://cdn.example.com/very/long/path/$it/index.m3u8?token=" + "x".repeat(120), "Mozilla/5.0 " + "y".repeat(80)) })
        val slim = Command(Action.PLAY, big.copy(sources = big.sources.take(3)))
        assertTrue(Protocol.seal(slim, "ABCDE23456").toByteArray().size < 4000)
    }
}
