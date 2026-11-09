package app.rayclient

import org.junit.Test
import java.io.File
import java.util.Base64

class ProfilesTest {
    @Test fun profiles() {
        val dir = File("/tmp/vitrum-test/cfgtest5").apply { deleteRecursively(); mkdirs() }
        val json = """{"Name":"RU direct","GlobalProxy":"true","DomainStrategy":"AsIs","DirectSites":["geosite:category-ru","domain:ru"],"DirectIp":["geoip:ru"],"ProxySites":["geosite:netflix"],"ProxyIp":[],"BlockSites":["geosite:category-ads-all"],"BlockIp":["10.9.9.9"]}"""
        val forms = mapOf("json" to json, "b64" to Base64.getEncoder().encodeToString(json.toByteArray()),
            "b64prefix" to "base64:" + Base64.getEncoder().encodeToString(json.toByteArray()),
            "happ" to "happ://routing/add/" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray()))
        val sb = StringBuilder()
        forms.forEach { (n, f) -> val r = HappRouting.parse(f); sb.append("$n: ${r?.rules?.size} rules, ds=${r?.domainStrategy}, name=${r?.name}\n") }
        sb.append("empty header: ${HappRouting.parse("")}\ngarbage: ${HappRouting.parse("hello")}\n")
        File(dir, "parse.txt").writeText(sb.toString())
        val sr = HappRouting.parse(json)!!
        val link = Links.parse("vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome#R")
        val out = Links.withAddress(link, "203.0.113.5")
        File(dir, "sub-routing.json").writeText(ConfigBuilder.build(link, out, RoutingOptions(sr.rules, listOf("AsIs", "IPIfNonMatch", "IPOnDemand")[sr.domainStrategy]), "/tmp/x/main.sock"))
        File(dir, "doh.json").writeText(ConfigBuilder.build(link, out, RoutingOptions(dnsServers = listOf("9.9.9.9", "149.112.112.112"), doh = true), "/tmp/x/main.sock"))
        File(dir, "plain-dns.json").writeText(ConfigBuilder.build(link, out, RoutingOptions(dnsServers = listOf("94.140.14.14")), null))
        File(dir, "doh-v6.json").writeText(ConfigBuilder.build(link, out, RoutingOptions(dnsServers = listOf("2606:4700:4700::1111"), doh = true), null))
    }
}
