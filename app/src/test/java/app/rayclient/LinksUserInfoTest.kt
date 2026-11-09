package app.rayclient

import org.junit.Assert.*
import org.junit.Test

/** «+» в пароле или ключе до «@» — обычный символ, а не пробел (раньше URLDecoder превращал «pa+ss» в «pa ss»). */
class LinksUserInfoTest {
    private fun server(link: String) = Links.parse(link).outbound.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)

    @Test fun plusInPasswordIsKept() {
        assertEquals("pa+ss+word", server("trojan://pa+ss%2Bword@example.com:443?security=tls&sni=example.com#T").getString("password"))
        assertEquals("se+cret", server("ss://aes-256-gcm:se+cret@example.com:8388#S").getString("password"))
        val hy = Links.parse("hy2://au+th@example.com:443?sni=example.com#H").outbound
        assertEquals("au+th", hy.getJSONObject("streamSettings").getJSONObject("hysteriaSettings").getString("auth"))
    }

    @Test fun shadowsocksStandardBase64WithPlus() {
        // base64 от «chacha20-ietf-poly1305:x>>>» содержит «+»: раньше он становился пробелом, и ссылка не разбиралась
        val s = server("ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTp4Pj4+@example.com:8388#SS")
        assertEquals("chacha20-ietf-poly1305", s.getString("method")); assertEquals("x>>>", s.getString("password"))
    }

    @Test fun percentEscapesStillDecoded() {
        assertEquals("p@ss:w", server("trojan://p%40ss%3Aw@example.com:443?security=tls#T").getString("password"))
    }
}
