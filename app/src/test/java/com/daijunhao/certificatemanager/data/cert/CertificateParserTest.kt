package com.daijunhao.certificatemanager.data.cert

import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateParserTest {
    private val parser = CertificateParser()
    private val pemBytes: ByteArray by lazy {
        requireNotNull(javaClass.getResourceAsStream("/test-ca.pem")).use { it.readBytes() }
    }
    private val certificate: X509Certificate by lazy {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(pemBytes.inputStream()) as X509Certificate
    }

    @Test
    fun parsesPemAndDer() {
        assertEquals(1, parser.parse(pemBytes, "test-ca.pem").size)
        assertEquals(1, parser.parse(certificate.encoded, "test-ca.0").size)
    }

    @Test
    fun deduplicatesRepeatedPemCertificates() {
        val bundle = pemBytes + "\n".toByteArray() + pemBytes
        assertEquals(1, parser.parse(bundle, "bundle.pem").size)
    }

    @Test
    fun parsesPasswordProtectedPkcs12() {
        val p12 = createPkcs12("testpass")
        val parsed = parser.parse(p12, "test-ca.p12", "testpass")

        assertEquals(1, parsed.size)
        assertEquals(certificate.encoded.toList(), parsed.single().encoded.toList())
    }

    @Test
    fun rejectsWrongPkcs12Password() {
        val p12 = createPkcs12("testpass")
        assertThrows(CertificateParseException::class.java) {
            parser.parse(p12, "test-ca.p12", "wrong-password")
        }
    }

    @Test
    fun rejectsInvalidFile() {
        assertThrows(CertificateParseException::class.java) {
            parser.parse("not a certificate".toByteArray(), "invalid.pem")
        }
    }

    @Test
    fun mapsCertificateMetadata() {
        val managed = certificate.toManagedCertificate("ff73556b.0")

        assertEquals("RCM Test Root CA", managed.commonName)
        assertEquals("ff73556b.0", managed.fileName)
        assertTrue(managed.isCa)
        assertTrue(managed.isSelfSigned)
        assertTrue(certificate.isCertificateAuthority())
        assertEquals(95, managed.fingerprintSha256.length)
        assertTrue(managed.pem.startsWith("-----BEGIN CERTIFICATE-----"))
    }

    private fun createPkcs12(password: String): ByteArray {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, password.toCharArray())
        keyStore.setCertificateEntry("test-ca", certificate)
        return ByteArrayOutputStream().use { output ->
            keyStore.store(output, password.toCharArray())
            output.toByteArray()
        }
    }
}
