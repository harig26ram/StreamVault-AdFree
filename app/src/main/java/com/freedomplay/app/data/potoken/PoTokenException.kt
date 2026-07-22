package com.freedomplay.app.data.potoken

// Ported from NewPipe (GPLv3) — app/src/main/java/org/schabi/newpipe/util/potoken.

class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError")) {
        BadWebViewException(error)
    } else {
        PoTokenException(error)
    }
}
