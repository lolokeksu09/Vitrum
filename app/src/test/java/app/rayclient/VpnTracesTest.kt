package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class VpnTracesTest {
    private fun item(l: List<TraceItem>, key: String) = l.first { it.key == key }

    private val clean = TraceSnapshot(
        activeVpnTransport = false, activeNotVpn = true, transportInfoVpn = false, vpnNetworks = 0, activeIfaces = listOf("wlan0"),
        legacyVpnConnected = false, ifaces = listOf("lo", "wlan0", "rmnet_data0", "tunl0", "ip6tnl0", "sit0"), callbackVpn = false,
        procRoute = listOf("wlan0"), procDev = listOf("lo", "wlan0"), sysNet = listOf("lo", "wlan0"))

    @Test fun ifaceNamesAreStrictEnoughNotToFlagCarrierAndKernelTunnels() {
        listOf("tun0", "tun12", "tap0", "wg0", "awg1", "ppp0", "utun3", "l2tp0", "my_vpn", "VPN0").forEach { assertTrue(it, VpnTraces.isVpnIface(it)) }
        // tunl0, ip6tnl0, sit0 есть почти на каждом телефоне; ipsec/xfrm заводят операторы для IMS
        listOf("tunl0", "ip6tnl0", "sit0", "ip_vti0", "wlan0", "rmnet_data0", "ccmni0", "eth0", "lo", "ipsec0", "xfrm0", "dummy0").forEach { assertFalse(it, VpnTraces.isVpnIface(it)) }
    }

    @Test fun allNullMeansNothingWasMeasuredNeverClean() {
        val l = VpnTraces.evaluate(TraceSnapshot())
        assertTrue("без наблюдений нельзя писать «не виден»", l.all { it.state == TraceState.UNKNOWN })
        assertEquals(0 to 0, VpnTraces.summary(l))
    }

    @Test fun cleanSnapshotIsAllCleanAndIgnoresKernelTunnelDevices() {
        val l = VpnTraces.evaluate(clean)
        assertTrue(l.all { it.state == TraceState.CLEAN })
        assertEquals(0 to l.size, VpnTraces.summary(l))
    }

    @Test fun eachSignalIsReportedOnItsOwn() {
        assertEquals(TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(activeVpnTransport = true, activeNotVpn = false)), "caps").state)
        assertEquals("нет признака «не VPN» — тоже VPN", TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(activeVpnTransport = false, activeNotVpn = false)), "caps").state)
        assertEquals(TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(transportInfoVpn = true)), "transport_info").state)
        val nets = item(VpnTraces.evaluate(TraceSnapshot(vpnNetworks = 2)), "all_networks")
        assertEquals(TraceState.SEEN, nets.state); assertTrue(nets.detail.contains("2"))
        assertEquals(TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(activeIfaces = listOf("tun0"))), "link").state)
        assertEquals(TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(legacyVpnConnected = true)), "legacy").state)
        val ifs = item(VpnTraces.evaluate(TraceSnapshot(ifaces = listOf("wlan0", "tun0"))), "iface_list")
        assertEquals(TraceState.SEEN, ifs.state); assertTrue(ifs.detail.contains("tun0")); assertFalse(ifs.detail.contains("wlan0"))
        assertEquals(TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(callbackVpn = true)), "callback").state)
        assertEquals(TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(procRoute = listOf("wlan0", "tun0"))), "proc_route").state)
        assertEquals(TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(procDev = listOf("tun0"))), "proc_dev").state)
        assertEquals(TraceState.SEEN, item(VpnTraces.evaluate(TraceSnapshot(sysNet = listOf("tun0"))), "sys_net").state)
    }

    @Test fun closedFileIsUnknownAndSaysItDoesNotMeanHidden() {
        val r = item(VpnTraces.evaluate(TraceSnapshot(procRoute = null)), "proc_route")
        assertEquals(TraceState.UNKNOWN, r.state); assertTrue(r.detail.contains("не значит"))
    }

    @Test fun summaryCountsSeenAndMeasured() {
        val l = VpnTraces.evaluate(TraceSnapshot(activeVpnTransport = true, ifaces = listOf("tun0"), callbackVpn = null))
        val (seen, measured) = VpnTraces.summary(l)
        assertEquals(2, seen); assertEquals(l.count { it.state != TraceState.UNKNOWN }, measured)
    }

    @Test fun procTextParsing() {
        val route = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\tMTU\tWindow\tIRTT\nwlan0\t00000000\t0100A8C0\t0003\t0\t0\t0\t00000000\t0\t0\t0\nwlan0\t0000A8C0\t00000000\t0001\t0\t0\t0\t00FFFFFF\t0\t0\t0\ntun0\t00000000\t00000000\t0001\t0\t0\t0\t00000000\t0\t0\t0\n"
        assertEquals(listOf("wlan0", "tun0"), VpnTraces.routeNames(route))
        val dev = "Inter-|   Receive                                                |  Transmit\n face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed\n    lo: 1 2 0 0 0 0 0 0 1 2 0 0 0 0 0 0\n wlan0: 5 6 0 0 0 0 0 0 5 6 0 0 0 0 0 0\n  tun0: 7 8 0 0 0 0 0 0 7 8 0 0 0 0 0 0\n"
        assertEquals(listOf("lo", "wlan0", "tun0"), VpnTraces.devNames(dev))
        assertEquals(emptyList<String>(), VpnTraces.routeNames("")); assertEquals(emptyList<String>(), VpnTraces.devNames(""))
    }
}
