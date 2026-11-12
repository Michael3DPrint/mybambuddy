package io.github.michael3dprint.mybambuddy.domain.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetRulesTest {
    @Test
    fun `a slash 24 lists the 253 other hosts`() {
        val hosts = SubnetRules.hosts(Subnet("192.168.1.20", 24))
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.20" in hosts)
    }

    @Test
    fun `a larger network is cut down to the slash 24 around the phone`() {
        val hosts = SubnetRules.hosts(Subnet("10.0.5.7", 16))
        assertEquals(253, hosts.size)
        assertTrue(hosts.all { it.startsWith("10.0.5.") })
    }

    @Test
    fun `a small subnet lists only its own hosts`() {
        assertEquals(
            listOf("192.168.1.130", "192.168.1.131"),
            SubnetRules.hosts(Subnet("192.168.1.129", 29)).filter {
                it in
                    listOf("192.168.1.130", "192.168.1.131")
            },
        )
        assertEquals(5, SubnetRules.hosts(Subnet("192.168.1.129", 29)).size)
    }

    @Test
    fun `a public or invalid address is never listed`() {
        assertTrue(SubnetRules.hosts(Subnet("8.8.8.8", 24)).isEmpty())
        assertTrue(SubnetRules.hosts(Subnet("100.64.1.1", 24)).isEmpty())
        assertTrue(SubnetRules.hosts(Subnet("not an address", 24)).isEmpty())
        assertTrue(SubnetRules.hosts(Subnet("192.168.1.1", 31)).isEmpty())
        assertTrue(SubnetRules.hosts(Subnet("192.168.1.1", 0)).isEmpty())
        assertTrue(SubnetRules.hosts(Subnet("192.168.1.300", 24)).isEmpty())
    }

    @Test
    fun `172 16 to 31 is private and 172 32 is not`() {
        assertTrue(SubnetRules.isPrivateHost("172.16.0.5"))
        assertTrue(SubnetRules.isPrivateHost("172.31.255.1"))
        assertFalse(SubnetRules.isPrivateHost("172.32.0.1"))
    }

    @Test
    fun `tailnet hosts are the 100 64 block and the ts net domain`() {
        assertTrue(SubnetRules.isTailnetHost("100.64.0.1"))
        assertTrue(SubnetRules.isTailnetHost("100.127.255.254"))
        assertFalse(SubnetRules.isTailnetHost("100.128.0.1"))
        assertFalse(SubnetRules.isTailnetHost("100.63.255.255"))
        assertTrue(SubnetRules.isTailnetHost("my-host.example-tailnet.ts.net"))
        assertFalse(SubnetRules.isTailnetHost("my-host"))
    }

    @Test
    fun `the host is read from a server address`() {
        assertEquals("my-host", SubnetRules.hostOf("http://my-host:8000"))
        assertEquals("192.168.1.5", SubnetRules.hostOf(" http://192.168.1.5:8000/ "))
        assertEquals("a.b.ts.net", SubnetRules.hostOf("https://a.b.ts.net"))
        assertEquals("", SubnetRules.hostOf(""))
    }
}
