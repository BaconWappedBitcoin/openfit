package com.ofit.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServerUrlTest {
    @Test
    fun `addresses are cleaned, https required except at home`() {
        assertEquals("https://my-openfit.vercel.app", ServerUrl.normalize(" https://my-openfit.vercel.app/ "))
        assertEquals("https://my-openfit.vercel.app", ServerUrl.normalize("my-openfit.vercel.app"))
        assertEquals("https://example.org/openfit", ServerUrl.normalize("https://Example.org/openfit/"))
        assertEquals("http://192.168.1.20:3000", ServerUrl.normalize("http://192.168.1.20:3000"))
        assertEquals("http://nas.local:8080", ServerUrl.normalize("http://nas.local:8080/"))
        assertNull(ServerUrl.normalize("http://example.com"))
        assertNull(ServerUrl.normalize("ftp://example.com"))
        assertNull(ServerUrl.normalize("https://example.com/?x=1"))
        assertNull(ServerUrl.normalize("https://user:pw@example.com"))
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("not a url at all"))
    }
}
