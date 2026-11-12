package io.github.michael3dprint.mybambuddy.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BambuddyAddressTest {
    @Test
    fun `a plain address is kept as it is`() {
        assertEquals("http://100.64.0.10:8000", BambuddyAddress.webAddress("http://100.64.0.10:8000"))
        assertEquals("https://bambuddy.example", BambuddyAddress.webAddress("https://bambuddy.example"))
    }

    @Test
    fun `a missing scheme is taken as http and trailing slashes go`() {
        assertEquals("http://100.64.0.10:8000", BambuddyAddress.webAddress("100.64.0.10:8000/"))
        assertEquals("http://host.local", BambuddyAddress.webAddress("  host.local//  "))
    }

    @Test
    fun `a trailing api path is removed but a real sub-path is kept`() {
        assertEquals("http://h:8000", BambuddyAddress.webAddress("http://h:8000/api/v1"))
        assertEquals("http://h:8000", BambuddyAddress.webAddress("http://h:8000/api/v1/"))
        assertEquals("http://h:8000", BambuddyAddress.webAddress("http://h:8000/api"))
        assertEquals("http://h/bambuddy", BambuddyAddress.webAddress("http://h/bambuddy/api/v1"))
        assertEquals("http://h/bambuddy", BambuddyAddress.webAddress("http://h/bambuddy"))
    }

    @Test
    fun `a query or fragment is dropped`() {
        assertEquals("http://h:8000", BambuddyAddress.webAddress("http://h:8000/?x=1#top"))
    }

    @Test
    fun `blank, unusable or non-web addresses give null`() {
        assertNull(BambuddyAddress.webAddress(""))
        assertNull(BambuddyAddress.webAddress("   "))
        assertNull(BambuddyAddress.webAddress("ftp://host"))
        assertNull(BambuddyAddress.webAddress("http://"))
        assertNull(BambuddyAddress.webAddress("javascript:alert(1)"))
        assertNull(BambuddyAddress.webAddress("http://exa mple.com"))
    }
}
