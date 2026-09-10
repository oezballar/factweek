package dev.factweek.ingestion.internal

import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

internal interface SourceUrlValidator {
    fun validate(url: URI)
}

@Component
internal class PublicSourceUrlValidator : SourceUrlValidator {
    override fun validate(url: URI) {
        val scheme = url.scheme?.lowercase(Locale.ROOT)
        if (scheme !in setOf("http", "https") || url.userInfo != null || url.host == null || (url.port != -1 && url.port !in setOf(80, 443))) {
            reject()
        }
        val addresses = try {
            InetAddress.getAllByName(url.host)
        } catch (_: Exception) {
            reject()
        }
        if (addresses.isEmpty() || addresses.any(::isNonPublic)) reject()
    }

    private fun isNonPublic(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> isNonPublicIpv4(address.address)
        is Inet6Address -> isNonPublicIpv6(address.address)
        else -> true
    }

    private fun isNonPublicIpv4(bytes: ByteArray): Boolean {
        val value = bytes.map { it.toInt() and 0xff }
        return IPV4_NON_PUBLIC_RANGES.any { (network, prefix) -> inCidr(bytes, network, prefix) } ||
            value[0] >= 224
    }

    private fun isNonPublicIpv6(bytes: ByteArray): Boolean {
        val address = InetAddress.getByAddress(bytes)
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) {
            return true
        }
        if (inCidr(bytes, IPV6_UNIQUE_LOCAL, 7) || inCidr(bytes, IPV6_LINK_LOCAL, 10) || inCidr(bytes, IPV6_MULTICAST, 8) ||
            inCidr(bytes, IPV6_DOCUMENTATION, 32) || inCidr(bytes, IPV6_ORCHID, 28)
        ) {
            return true
        }
        if (bytes.copyOfRange(0, 10).all { it == 0.toByte() } && bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()) {
            return isNonPublicIpv4(bytes.copyOfRange(12, 16))
        }
        return false
    }

    private fun inCidr(address: ByteArray, network: ByteArray, prefix: Int): Boolean {
        val wholeBytes = prefix / 8
        val remainingBits = prefix % 8
        if (!address.copyOfRange(0, wholeBytes).contentEquals(network.copyOfRange(0, wholeBytes))) return false
        if (remainingBits == 0) return true
        val mask = (0xff shl (8 - remainingBits)) and 0xff
        return (address[wholeBytes].toInt() and mask) == (network[wholeBytes].toInt() and mask)
    }

    private fun reject(): Nothing = throw SourceContentFetchException(SourceContentFailureReason.UNSAFE_URL)

    private companion object {
        val IPV4_NON_PUBLIC_RANGES = listOf(
            byteArrayOf(0, 0, 0, 0) to 8,
            byteArrayOf(10, 0, 0, 0) to 8,
            byteArrayOf(100.toByte(), 64, 0, 0) to 10,
            byteArrayOf(127, 0, 0, 0) to 8,
            byteArrayOf(169.toByte(), 254.toByte(), 0, 0) to 16,
            byteArrayOf(172.toByte(), 16, 0, 0) to 12,
            byteArrayOf(192.toByte(), 0, 0, 0) to 24,
            byteArrayOf(192.toByte(), 0, 2, 0) to 24,
            byteArrayOf(192.toByte(), 168.toByte(), 0, 0) to 16,
            byteArrayOf(198.toByte(), 18, 0, 0) to 15,
            byteArrayOf(198.toByte(), 51, 100.toByte(), 0) to 24,
            byteArrayOf(203.toByte(), 0, 113.toByte(), 0) to 24,
            byteArrayOf(224.toByte(), 0, 0, 0) to 4,
            byteArrayOf(240.toByte(), 0, 0, 0) to 4,
        )
        val IPV6_UNIQUE_LOCAL = byteArrayOf(0xfc.toByte())
        val IPV6_LINK_LOCAL = byteArrayOf(0xfe.toByte(), 0x80.toByte())
        val IPV6_MULTICAST = byteArrayOf(0xff.toByte())
        val IPV6_DOCUMENTATION = byteArrayOf(0x20, 0x01, 0x0d, 0xb8.toByte())
        val IPV6_ORCHID = byteArrayOf(0x20, 0x01, 0x00, 0x10)
    }
}
