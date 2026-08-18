package com.knicventures.mediakit

import com.knicventures.mediakit.hls.M3u8Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3u8ParserTest {

    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42c01e,mp4a.40.2"
        360/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
        720/index.m3u8
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=YES,URI="audio/en.m3u8"
    """.trimIndent()

    private val media = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:10
        #EXT-X-MEDIA-SEQUENCE:5
        #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k1",IV=0x0123456789ABCDEF0123456789ABCDEF
        #EXTINF:9.009,
        seg5.ts
        #EXTINF:8.5,
        seg6.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun `detects master playlists`() {
        assertTrue(M3u8Parser.isMasterPlaylist(master))
        assertFalse(M3u8Parser.isMasterPlaylist(media))
        assertTrue(M3u8Parser.looksLikePlaylist(media))
    }

    @Test
    fun `parses variants sorted by bandwidth and resolves relative URIs`() {
        val parsed = M3u8Parser.parseMaster(master, "https://cdn.example.com/video/master.m3u8")

        assertEquals(2, parsed.variants.size)
        assertEquals("https://cdn.example.com/video/720/index.m3u8", parsed.variants[0].url)
        assertEquals(2_400_000L, parsed.variants[0].bandwidth)
        assertEquals(720, parsed.variants[0].height)

        assertEquals(1, parsed.renditions.size)
        assertEquals("https://cdn.example.com/video/audio/en.m3u8", parsed.renditions[0].url)
        assertTrue(parsed.renditions[0].isDefault)
    }

    @Test
    fun `keeps commas inside quoted attribute values`() {
        val attrs = M3u8Parser.parseAttributes("""BANDWIDTH=100,CODECS="avc1.4d401f,mp4a.40.2",NAME="A,B"""")

        assertEquals("100", attrs["BANDWIDTH"])
        assertEquals("avc1.4d401f,mp4a.40.2", attrs["CODECS"])
        assertEquals("A,B", attrs["NAME"])
    }

    @Test
    fun `parses segments with encryption and sequence numbers`() {
        val parsed = M3u8Parser.parseMedia(media, "https://cdn.example.com/video/720/index.m3u8")

        assertEquals(2, parsed.segments.size)
        assertFalse(parsed.isLive)
        assertEquals(10.0, parsed.targetDuration, 0.001)
        assertEquals(17.509, parsed.totalDurationSeconds, 0.001)

        val first = parsed.segments[0]
        assertEquals("https://cdn.example.com/video/720/seg5.ts", first.url)
        assertEquals(5L, first.sequence)
        assertEquals("AES-128", first.key?.method)
        assertEquals("https://keys.example.com/k1", first.key?.uri)
        assertEquals(6L, parsed.segments[1].sequence)
        assertNull(parsed.initSegmentUrl)
    }

    @Test
    fun `treats a playlist without ENDLIST as live`() {
        val live = media.replace("#EXT-X-ENDLIST", "")
        assertTrue(M3u8Parser.parseMedia(live, "https://cdn.example.com/x.m3u8").isLive)
    }

    @Test
    fun `parses byte-range segments and fMP4 init segments`() {
        val fmp4 = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:1000@0
            media.mp4
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:2000
            media.mp4
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = M3u8Parser.parseMedia(fmp4, "https://cdn.example.com/v/index.m3u8")

        assertEquals("https://cdn.example.com/v/init.mp4", parsed.initSegmentUrl)
        assertEquals(0L, parsed.segments[0].byteRangeOffset)
        assertEquals(1000L, parsed.segments[0].byteRangeLength)
        // An offset-less BYTERANGE continues from the end of the previous one.
        assertEquals(1000L, parsed.segments[1].byteRangeOffset)
        assertEquals(2000L, parsed.segments[1].byteRangeLength)
    }

    @Test
    fun `resolves absolute protocol-relative and rooted references`() {
        val base = "https://cdn.example.com/a/b/index.m3u8"

        assertEquals("https://other.com/x.ts", M3u8Parser.resolve(base, "https://other.com/x.ts"))
        assertEquals("https://cdn.example.com/x.ts", M3u8Parser.resolve(base, "/x.ts"))
        assertEquals("https://cdn.example.com/a/b/x.ts", M3u8Parser.resolve(base, "x.ts"))
        assertEquals("https://cdn.example.com/a/x.ts", M3u8Parser.resolve(base, "../x.ts"))
    }
}
