package com.ofit.protocol

import java.net.URI

/**
 * The address of an OpenFit backend. Self-hosters point the app at their own deployment of
 * backend/ (any Vercel project, or anything serving the same /api routes). HTTPS is required,
 * except on a home network or this device, where plain HTTP is accepted for a local server.
 */
object ServerUrl {
    const val DEFAULT = "https://ofit-backend.vercel.app"

    /** The cleaned address (no trailing slash), or null if it cannot be used. */
    fun normalize(input: String): String? {
        var text = input.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) text = "https://$text"
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return null
        when (scheme) {
            "https" -> Unit
            "http" -> if (!isLocal(host)) return null
            else -> return null
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = (uri.rawPath ?: "").trimEnd('/')
        return "$scheme://$host$port$path"
    }

    private fun isLocal(host: String): Boolean =
        host == "localhost" || host.endsWith(".local") || host.endsWith(".localhost") ||
            host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.") ||
            Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(host)
}
