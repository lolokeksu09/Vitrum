package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class VpnTruthTest {
    private val link = """
1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN mode DEFAULT group default qlen 1000\    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
10: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP mode DORMANT group default qlen 3000\    link/ether aa:bb:cc:dd:ee:ff brd ff:ff:ff:ff:ff:ff
12: tunl0@NONE: <NOARP> mtu 1480 qdisc noop state DOWN mode DEFAULT group default qlen 1000\    link/ipip 0.0.0.0 brd 0.0.0.0
22: tun0: <POINTOPOINT,UP,LOWER_UP> mtu 1500 qdisc pfifo_fast state UNKNOWN mode DEFAULT group default qlen 500\    link/none
"""
    private val links = VpnTruth.parseLinks(link)

    @Test fun linksAreParsedWithIndexAndWithoutParentSuffix() {
        assertEquals(listOf(1 to "lo", 10 to "wlan0", 12 to "tunl0", 22 to "tun0"), links)
        assertTrue(VpnTruth.parseLinks("").isEmpty()); assertTrue(VpnTruth.parseLinks("мусор\n: :").isEmpty())
    }

    @Test fun rulesByTableNameAndByNumericTable() {
        val byName = VpnTruth.parseVpnRules("0:\tfrom all lookup local\n13000:\tfrom all fwmark 0x10063/0x1ffff uidrange 10150-10199 lookup tun0\n", links)
        assertEquals(1, byName.size); assertEquals(10150, byName[0].from); assertEquals(10199, byName[0].to)
        // netd нумерует таблицу интерфейса как номер + 1000: tun0 имеет номер 22, значит таблица 1022
        val byNumber = VpnTruth.parseVpnRules("13000:\tfrom all uidrange 10200-10299 lookup 1022\n13001:\tfrom all uidrange 10300-10399 lookup 1010\n", links)
        assertEquals("1010 это wlan0, не VPN", listOf(10200), byNumber.map { it.from })
        assertTrue(VpnTruth.parseVpnRules("13000:\tfrom all uidrange 1-5 lookup wlan0\n10000:\tfrom all lookup tun0\n", links).isEmpty())
    }

    private fun item(key: String, st: TraceState) = TraceItem(key, key, st, "")

    @Test fun withoutVpnInterfaceNothingIsCompared() {
        val noVpn = VpnTruth.parseLinks("1: lo: <LOOPBACK> mtu 1\n10: wlan0: <UP> mtu 1\n")
        val n = VpnTruth.evaluate(listOf(item("iface_list", TraceState.CLEAN)), noVpn, emptyList(), 10123)
        assertEquals(TraceState.UNKNOWN, n[0].state); assertTrue(n.none { it.title == "iface_list" })
    }

    @Test fun cleanSeenAndClosedAreToldApart() {
        val items = listOf(item("iface_list", TraceState.CLEAN), item("proc_route", TraceState.SEEN), item("proc_dev", TraceState.UNKNOWN), item("sys_net", TraceState.CLEAN), item("caps", TraceState.SEEN))
        val n = VpnTruth.evaluate(items, links, listOf(VpnRule(10150, 10199, "tun0")), 10123)
        assertEquals(TraceState.SEEN, n[0].state); assertTrue(n[0].detail.contains("tun0")); assertFalse("tunl0 не VPN", n[0].detail.contains("tunl0"))
        assertEquals("uid вне правил VPN", TraceState.CLEAN, n[1].state); assertEquals("Вне VPN", n[1].word)
        val by = n.associateBy { it.title }
        assertEquals("Скрыто или закрыто", by["iface_list"]!!.word); assertEquals("Подтверждено", by["proc_route"]!!.word); assertEquals("Закрыто", by["proc_dev"]!!.word)
        assertNull("сравниваются только проверки по интерфейсам", by["caps"])
        assertEquals(6, n.size)
    }

    @Test fun uidInsideRuleAndMissingDataAreSaid() {
        assertEquals("Внутри VPN", VpnTruth.evaluate(emptyList(), links, listOf(VpnRule(10100, 10199, "tun0")), 10123)[1].word)
        assertEquals("Нет данных", VpnTruth.evaluate(emptyList(), links, emptyList(), 10123)[1].word)
        val none = VpnTruth.evaluate(emptyList(), null, null, 10123)
        assertTrue(none.all { it.state == TraceState.UNKNOWN }); assertEquals(2, none.size)
    }
}
