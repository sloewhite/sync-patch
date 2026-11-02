package app.revanced.patches.syncfixes.shared

import app.revanced.patcher.fingerprint

/** Sync's final release; the free and Pro apps have the same code but different obfuscated names. */
internal const val SYNC_VERSION = "v23.06.30-13:39"

internal val SYNC_COMPATIBILITY = arrayOf(
    "com.laurencedawson.reddit_sync" to setOf(SYNC_VERSION),
    "com.laurencedawson.reddit_sync.pro" to setOf(SYNC_VERSION),
)

internal const val EXTENSION = "extensions/syncfixes.rve"
internal const val EXTENSION_PACKAGE = "Lapp/revanced/extension/syncfixes"

internal const val STRING = "Ljava/lang/String;"
internal const val NETWORK_RESPONSE = "Lcom/android/volley/NetworkResponse;"
internal const val VOLLEY_RESPONSE = "Lcom/android/volley/Response;"
internal const val VOLLEY_REQUEST = "Lcom/android/volley/Request;"

/**
 * Sync keeps its source file names, so classes are matched by them rather than by obfuscated names
 * (which differ between Sync and Sync Pro).
 */
internal fun parseNetworkResponseFingerprint(sourceFile: String) = fingerprint {
    returns(VOLLEY_RESPONSE)
    parameters(NETWORK_RESPONSE)
    custom { method, classDef ->
        classDef.sourceFile == sourceFile && method.name == "parseNetworkResponse"
    }
}
