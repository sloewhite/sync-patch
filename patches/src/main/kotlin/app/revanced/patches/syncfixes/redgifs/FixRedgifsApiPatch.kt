/*
 * Adapted from trulow/sync-reddit-fixes (Morphe), KingOfPoptart/patcheddit and wchill/patcheddit, GPLv3.
 */
package app.revanced.patches.syncfixes.redgifs

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.syncfixes.shared.EXTENSION
import app.revanced.patches.syncfixes.shared.EXTENSION_PACKAGE
import app.revanced.patches.syncfixes.shared.STRING
import app.revanced.patches.syncfixes.shared.hookClientBuilder
import app.revanced.patches.syncfixes.shared.isClientGetterCall
import app.revanced.patches.syncfixes.shared.SYNC_COMPATIBILITY
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val EXTENSION_CLASS = "$EXTENSION_PACKAGE/FixRedgifsApiPatch;"

/** Volley's network, which sends Sync's requests (Redgifs included) through OkHttpHelper's client getter. */
private val volleyClientGetterCallFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/android/volley/toolbox/BasicNetwork;" &&
            method.implementation?.instructions?.any { it.isClientGetterCall() } == true
    }
}

/** AccountSingleton's user agent getter, the user agent Sync sends everywhere. */
private val userAgentFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns(STRING)
    parameters()
    custom { _, classDef -> classDef.sourceFile == "AccountSingleton.java" }
}

/** The extension's placeholder for Sync's user agent. */
private val extensionUserAgentFingerprint = fingerprint {
    custom { method, classDef -> classDef.type == EXTENSION_CLASS && method.name == "defaultUserAgent" }
}

/** LinkHandler.getGfycatId(url), which supplies the id of the /v2/gifs/{id} request. */
private val getGfycatIdFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns(STRING)
    parameters(STRING)
    strings("-size_restricted", "-max-1mb-poster")
    custom { _, classDef -> classDef.sourceFile == "LinkHandler.java" }
}

@Suppress("unused")
val fixRedgifsApiPatch = bytecodePatch(
    name = "Fix Redgifs API",
    description = "Fixes \"Error connecting to Redgifs\": answers the Redgifs endpoints Sync relies on that " +
        "were removed, uses Redgifs' temporary token, and fixes video ids taken from links like " +
        "redgifs.com/watch/{id}/?92. Turn off Patcheddit's \"Fix Redgifs API\" when using this one: " +
        "both change the same code.",
) {
    compatibleWith(*SYNC_COMPATIBILITY)
    extendWith(EXTENSION)

    execute {
        // Install the interceptor on the OkHttp client behind Volley.
        hookClientBuilder(volleyClientGetterCallFingerprint.method, "$EXTENSION_CLASS->install")

        // Send Sync's own user agent: Redgifs ties each token to the user agent that requested it.
        extensionUserAgentFingerprint.method.addInstructions(
            0,
            """
                invoke-static { }, ${userAgentFingerprint.method}
                move-result-object v0
                return-object v0
            """,
        )

        // Take the gif id from the URL with the query and fragment removed first.
        getGfycatIdFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->extractGifId(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                return-object p0
            """,
        )
    }
}
