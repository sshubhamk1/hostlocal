package com.hostlocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotspotTest {
    @Test fun picksApInterface() {
        val r = Hotspot.pickAddress(listOf(
            IfaceInfo("wlan0", listOf("192.168.1.5")),
            IfaceInfo("swlan0", listOf("192.168.43.1")),
        ))
        assertEquals("192.168.43.1", r)
    }

    @Test fun ignoresClientWifiAndMobile() {
        assertNull(Hotspot.pickAddress(listOf(
            IfaceInfo("wlan0", listOf("192.168.1.5")),
            IfaceInfo("rmnet_data0", listOf("10.1.2.3")),
        )))
    }

    @Test fun ignoresApWithoutIpv4() {
        assertNull(Hotspot.pickAddress(listOf(IfaceInfo("ap0", emptyList()))))
    }

    @Test fun matchesWlan1() {
        assertEquals("10.0.0.1", Hotspot.pickAddress(listOf(IfaceInfo("wlan1", listOf("10.0.0.1")))))
    }
}
