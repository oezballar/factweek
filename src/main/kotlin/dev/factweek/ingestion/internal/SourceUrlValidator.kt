package dev.factweek.ingestion.internal

import org.springframework.stereotype.Component
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

internal interface SourceUrlValidator {
    fun validate(url: URI)
}

@Component
internal class PublicSourceUrlValidator : SourceUrlValidator {
    override fun validate(url: URI) {
        val scheme = url.scheme?.lowercase()
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

    private fun isNonPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) {
            return true
        }
        if (address is Inet6Address) {
            val first = address.address.first().toInt() and 0xff
            if ((first and 0xfe) == 0xfc) return true
        }
        return false
    }

    private fun reject(): Nothing = throw SourceContentFetchException(SourceContentFailureReason.UNSAFE_URL)
}
