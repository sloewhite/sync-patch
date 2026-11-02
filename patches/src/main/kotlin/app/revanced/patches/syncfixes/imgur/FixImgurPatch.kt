package app.revanced.patches.syncfixes.imgur

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.syncfixes.shared.EXTENSION
import app.revanced.patches.syncfixes.shared.EXTENSION_PACKAGE
import app.revanced.patches.syncfixes.shared.SYNC_COMPATIBILITY
import app.revanced.patches.syncfixes.shared.hookClientBuilder
import app.revanced.patches.syncfixes.shared.isClientGetterCall

/**
 * Your imgur API Client-ID (free: register an application at https://api.imgur.com/oauth2/addclient, choosing
 * "OAuth 2 authorization without a callback URL"). Set it before building; the patch refuses to apply without it.
 */
private const val IMGUR_CLIENT_ID = ""

private const val EXTENSION_CLASS = "$EXTENSION_PACKAGE/FixImgurPatch;"

/** Glide's module: registers the OkHttp client (OkHttpHelper's a()) that loads every thumbnail. */
private val glideClientCallFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.sourceFile == "YourAppGlideModule.java" &&
            method.implementation?.instructions?.any { it.isClientGetterCall() } == true
    }
}

/** Volley's network: sends album requests through OkHttpHelper's other client. */
private val volleyClientCallFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/android/volley/toolbox/BasicNetwork;" &&
            method.implementation?.instructions?.any { it.isClientGetterCall() } == true
    }
}

private val extensionClientIdFingerprint = fingerprint {
    custom { method, classDef -> classDef.type == EXTENSION_CLASS && method.name == "clientId" }
}

@Suppress("unused")
val fixImgurPatch = bytecodePatch(
    name = "Fix imgur thumbnails",
    description = "Fixes imgur album thumbnails (and opening imgur albums): Sync loaded them through its own " +
        "servers, which no longer answer. They are loaded from imgur's API instead. Needs your own imgur " +
        "Client-ID set in FixImgurPatch.kt before building.",
) {
    compatibleWith(*SYNC_COMPATIBILITY)
    extendWith(EXTENSION)

    execute {
        if (!Regex("[A-Za-z0-9]{8,32}").matches(IMGUR_CLIENT_ID)) {
            throw PatchException(
                "Set IMGUR_CLIENT_ID in FixImgurPatch.kt to your own imgur API Client-ID and rebuild.",
            )
        }

        extensionClientIdFingerprint.method.addInstructions(
            0,
            """
                const-string v0, "$IMGUR_CLIENT_ID"
                return-object v0
            """,
        )

        hookClientBuilder(glideClientCallFingerprint.method, "$EXTENSION_CLASS->install")
        hookClientBuilder(volleyClientCallFingerprint.method, "$EXTENSION_CLASS->install")
    }
}
