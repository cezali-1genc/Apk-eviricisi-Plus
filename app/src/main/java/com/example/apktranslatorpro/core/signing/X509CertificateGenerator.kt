package com.example.apktranslatorpro.core.signing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object X509CertificateGenerator {

    data class GeneratedSigningIdentity(
        val keyPair: KeyPair,
        val certificate: X509Certificate
    )

    fun generateSelfSignedRsaIdentity(
        commonName: String = "APK Translator Pro",
        organization: String = "Cezali 1Genc",
        country: String = "TR",
        validityDays: Long = 365L * 25L
    ): GeneratedSigningIdentity {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        val keyPair = keyGen.generateKeyPair()

        val now = Date()
        val expiry = Date(now.time + validityDays * 24L * 60L * 60L * 1000L)
        val serial = BigInteger(63, SecureRandom()).abs().max(BigInteger.ONE)

        val sha256WithRsaAlgId = derSequence(
            derOid(intArrayOf(1, 2, 840, 113549, 1, 1, 11)), // sha256WithRSAEncryption
            byteArrayOf(0x05, 0x00) // NULL
        )

        val nameDer = derSequence(
            derRdn(intArrayOf(2, 5, 4, 3), commonName), // CN
            derRdn(intArrayOf(2, 5, 4, 10), organization), // O
            derRdn(intArrayOf(2, 5, 4, 6), country, printableString = true) // C
        )

        val validityDer = derSequence(
            derTime(now),
            derTime(expiry)
        )

        // BasicConstraints extension: cA = FALSE
        val basicConstraintsExt = derSequence(
            derOid(intArrayOf(2, 5, 29, 19)),
            derOctetString(derSequence())
        )

        val tbsCert = derSequence(
            derExplicitTag(0, derInteger(BigInteger.valueOf(2))), // v3
            derInteger(serial),
            sha256WithRsaAlgId,
            nameDer,
            validityDer,
            nameDer,
            keyPair.public.encoded,
            derExplicitTag(3, derSequence(basicConstraintsExt))
        )

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(tbsCert)
        val signatureBytes = signer.sign()

        val certDer = derSequence(
            tbsCert,
            sha256WithRsaAlgId,
            derBitString(signatureBytes)
        )

        val factory = CertificateFactory.getInstance("X.509")
        val cert = factory.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
        return GeneratedSigningIdentity(keyPair, cert)
    }

    private fun derRdn(oid: IntArray, value: String, printableString: Boolean = false): ByteArray {
        val valueTag = if (printableString) 0x13 else 0x0C // PrintableString vs UTF8String
        val strBytes = value.toByteArray(StandardCharsets.UTF_8)
        val attrTypeAndValue = derSequence(
            derOid(oid),
            derTlv(valueTag, strBytes)
        )
        return derTlv(0x31, attrTypeAndValue) // SET
    }

    private fun derTime(date: Date): ByteArray {
        val calYear = SimpleDateFormat("yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date).toInt()
        return if (calYear in 1950..2049) {
            val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            derTlv(0x17, fmt.format(date).toByteArray(StandardCharsets.US_ASCII)) // UTCTime
        } else {
            val fmt = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            derTlv(0x18, fmt.format(date).toByteArray(StandardCharsets.US_ASCII)) // GeneralizedTime
        }
    }

    private fun derInteger(value: BigInteger): ByteArray = derTlv(0x02, value.toByteArray())

    private fun derBitString(bytes: ByteArray): ByteArray {
        val payload = ByteArray(bytes.size + 1)
        payload[0] = 0x00 // 0 unused bits
        System.arraycopy(bytes, 0, payload, 1, bytes.size)
        return derTlv(0x03, payload)
    }

    private fun derOctetString(bytes: ByteArray): ByteArray = derTlv(0x04, bytes)

    private fun derExplicitTag(tagNumber: Int, inner: ByteArray): ByteArray =
        derTlv(0xA0 or tagNumber, inner)

    private fun derSequence(vararg elements: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (el in elements) out.write(el)
        return derTlv(0x30, out.toByteArray())
    }

    private fun derOid(components: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(components[0] * 40 + components[1])
        for (i in 2 until components.size) {
            var v = components[i]
            val stack = IntArray(8)
            var len = 0
            stack[len++] = v and 0x7F
            v = v ushr 7
            while (v > 0) {
                stack[len++] = (v and 0x7F) or 0x80
                v = v ushr 7
            }
            for (j in len - 1 downTo 0) {
                out.write(stack[j])
            }
        }
        return derTlv(0x06, out.toByteArray())
    }

    private fun derTlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(content.size + 6)
        out.write(tag)
        val len = content.size
        when {
            len < 128 -> out.write(len)
            len <= 0xFF -> {
                out.write(0x81)
                out.write(len)
            }
            len <= 0xFFFF -> {
                out.write(0x82)
                out.write((len ushr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            else -> {
                out.write(0x83)
                out.write((len ushr 16) and 0xFF)
                out.write((len ushr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }
}
