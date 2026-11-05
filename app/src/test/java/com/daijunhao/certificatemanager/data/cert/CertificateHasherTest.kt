package com.daijunhao.certificatemanager.data.cert

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CertificateHasherTest {
    private val certificate: X509Certificate by lazy {
        val pem = requireNotNull(javaClass.getResourceAsStream("/test-ca.pem")).use { it.readBytes() }
        CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.inputStream()) as X509Certificate
    }

    @Test
    fun subjectHashOld_matchesOpenSsl() {
        // openssl x509 -in test-ca.pem -noout -subject_hash_old
        assertEquals("ff73556b", CertificateHasher.subjectHashOld(certificate))
    }

    @Test
    fun malformedDer_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CertificateHasher.subjectHashOld(byteArrayOf(0x30, 0x03, 0x02, 0x01))
        }
    }
}
