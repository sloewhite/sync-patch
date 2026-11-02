package app.revanced.patches.syncfixes.reddit

import app.revanced.patcher.fingerprint
import app.revanced.patches.syncfixes.shared.STRING
import app.revanced.patches.syncfixes.shared.parseNetworkResponseFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

private const val GALLERY_REQUEST_SOURCE = "OAuthGalleryRequest.java"
private const val MORE_REQUEST_SOURCE = "OAuthCommentsMoreRequest.java"

/** OAuthGalleryRequest's static URL builder (also the key Sync reads its gallery cache with). */
internal val galleryUrlFingerprint = fingerprint {
    accessFlags(AccessFlags.STATIC)
    returns(STRING)
    parameters(STRING)
    strings("https://oauth.reddit.com/api/info.json?id=t3_")
    custom { _, classDef -> classDef.sourceFile == GALLERY_REQUEST_SOURCE }
}

internal val galleryParseResponseFingerprint = parseNetworkResponseFingerprint(GALLERY_REQUEST_SOURCE)

/**
 * CommentsPresenterImpl's "load more" handler: logs "Requesting more: ", then builds the request from
 * its only parameter, the Post row of the "more" item.
 */
internal val requestMoreFingerprint = fingerprint {
    returns("V")
    parameters("L")
    strings("Requesting more: ")
    custom { _, classDef -> classDef.sourceFile == "CommentsPresenterImpl.java" }
}

/**
 * OAuthCommentsMoreRequest(Context, String listId, String linkId, String moreId, String children,
 * int position, int level, String sort, Listener, ErrorListener).
 */
internal val moreRequestConstructorFingerprint = fingerprint {
    returns("V")
    parameters(
        "Landroid/content/Context;", STRING, STRING, STRING, STRING, "I", "I", STRING,
        "Lcom/android/volley/Response${'$'}Listener;",
        "Lcom/android/volley/Response${'$'}ErrorListener;",
    )
    strings("api/morechildren?always_show_media=1&profile_img=true")
    custom { method, classDef -> classDef.sourceFile == MORE_REQUEST_SOURCE && method.name == "<init>" }
}

internal val moreParseResponseFingerprint = parseNetworkResponseFingerprint(MORE_REQUEST_SOURCE)
