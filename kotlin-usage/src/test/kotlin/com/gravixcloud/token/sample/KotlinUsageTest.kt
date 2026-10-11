package com.gravixcloud.token.sample

import com.gravixcloud.token.GravixToken
import com.gravixcloud.token.GravixTokenException
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The Java library used from Kotlin, the way a Ktor or Spring Kotlin backend would. Dummy values. */
class KotlinUsageTest {
    private val appId = "APPkotlin"
    private val appCertificate = "cert-0123456789abcdef0123456789abcdef" // dummy, never a real one

    private fun payload(jwt: String): String =
        String(Base64.getUrlDecoder().decode(jwt.split('.')[1]), Charsets.UTF_8)

    // A Kotlin extension on the Java builder: a reusable "listener" preset.
    private fun GravixToken.Builder.listener(): GravixToken.Builder =
        canPublish(false).canPublishData(false)

    @Test
    fun `mint a join token from Kotlin`() {
        val jwt = GravixToken.builder(appId, appCertificate)
            .room("standup")
            .identity("user-42")
            .name("Ada")
            .attributes(mapOf("seat" to "3"))
            .ttl(30.minutes.toJavaDuration())
            .canPublishSources("camera", "microphone")
            .toJwt()

        val claims = payload(jwt)
        assertTrue("\"room\":\"APPkotlin_standup\"" in claims, claims)
        assertTrue("\"sub\":\"user-42\"" in claims, claims)
        assertTrue("\"attributes\":{\"seat\":\"3\"}" in claims, claims)
        assertTrue("\"canPublishSources\":[\"camera\",\"microphone\"]" in claims, claims)
    }

    @Test
    fun `extension functions and lists work on the builder`() {
        val sources = listOf("microphone")
        val jwt = GravixToken.builder(appId, appCertificate)
            .room("town-hall")
            .identity("listener-7")
            .listener()
            .canPublishSources(sources)
            .toJwt()

        val claims = payload(jwt)
        assertTrue("\"canPublish\":false" in claims, claims)
        assertTrue("\"canPublishData\":false" in claims, claims)
    }

    @Test
    fun `validation errors are GravixTokenException and never quote values`() {
        val e = assertThrows<GravixTokenException> {
            GravixToken.builder(appId, "short-dummy").room("r").identity("a").toJwt()
        }
        assertTrue("app certificate" in e.message!!)
        assertFalse("short-dummy" in e.message!!)

        // runCatching gives a Result for callers that prefer not to throw.
        val result = runCatching { GravixToken.builder(appId, appCertificate).room("r").toJwt() }
        assertTrue(result.exceptionOrNull() is GravixTokenException)
    }

    @Test
    fun `room helpers and constants`() {
        assertEquals("APPkotlin_lobby", GravixToken.roomName(appId, "lobby"))
        assertEquals("lobby", GravixToken.displayName(appId, "APPkotlin_lobby"))
        assertEquals(java.time.Duration.ofHours(6), GravixToken.DEFAULT_TTL)
    }
}
