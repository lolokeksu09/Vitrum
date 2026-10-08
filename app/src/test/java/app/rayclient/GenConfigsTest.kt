package app.rayclient

import org.junit.Test
import java.io.File

class GenConfigsTest {
    @Test fun gen() {
        val dir = File("/tmp/vitrum-test/cfgtest").apply { mkdirs() }
        val links = mapOf(
            "vless-reality" to "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#Reality",
            "vless-ws-tls" to "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=ws&security=tls&path=%2Fws&host=example.com&sni=example.com#WS",
            "vless-grpc" to "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=grpc&security=tls&serviceName=svc&mode=multi#GRPC",
            "vless-xhttp" to "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=xhttp&security=tls&path=%2Fx&mode=auto#XH",
            "vmess" to "vmess://" + java.util.Base64.getEncoder().encodeToString("""{"v":"2","ps":"VM","add":"example.com","port":"443","id":"11111111-2222-3333-4444-555555555555","aid":"0","scy":"auto","net":"ws","type":"none","host":"example.com","path":"/vm","tls":"tls","sni":"example.com"}""".toByteArray()),
            "trojan" to "trojan://secretpass@example.com:443?security=tls&sni=example.com&type=tcp#TR",
            "ss" to "ss://" + java.util.Base64.getEncoder().encodeToString("chacha20-ietf-poly1305:pass123".toByteArray()) + "@example.com:8388#SS",
            "hy2" to "hy2://authpass@example.com:443?sni=example.com&insecure=1#HY",
            "hy2-obfs" to "hysteria2://authpass@example.com:443?sni=example.com&obfs=salamander&obfs-password=zzz#HYO",
        )
        val wl = File("src/main/assets/whitelist.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val ro = RoutingOptions(
            rules = listOf(Rule(0, "example.org, geosite:cn", 0), Rule(1, "8.8.4.4, geoip:ru, 10.0.0.0/8", 1), Rule(2, "6881-6889", 2), Rule(0, "mydomain.ru", 1)),
            bypassPresets = PRESETS.map { it.id }.toSet(),
            whitelistDomains = wl, transport = 1, exceptRu = true)
        for ((n, l) in links) {
            val p = Links.parse(l)
            File(dir, "$n.json").writeText(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), ro))
        }
    }
}
