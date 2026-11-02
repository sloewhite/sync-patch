package app.revanced.extension.syncforreddit;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Sync loaded imgur albums through its own servers, which now answer 401:
 * <ul>
 *   <li>album thumbnails: https://ap.syncforreddit.com/image?url=imgur-album-{id} (Glide's client)</li>
 *   <li>the album itself: https://images.syncforreddit.com/a/{hash}/{id} (Volley's client)</li>
 * </ul>
 * Both are answered here from imgur's own API (/3/album/{id}) with the Client-ID filled in by the patch. Sync's
 * album parser already expects that API's JSON, so the album reply is passed through unchanged. A thumbnail is
 * the album's cover as a medium-size JPEG.
 */
@SuppressWarnings("unused")
public final class FixImgurPatch implements Interceptor {
    private static final String TAG = "SyncFixes";
    private static final String PROXY_HOST = "ap.syncforreddit.com";
    private static final String ALBUM_HOST = "images.syncforreddit.com";
    private static final String ALBUM_PREFIX = "imgur-album-";
    private static final FixImgurPatch INSTANCE = new FixImgurPatch();

    /** Cover image ids by album id, so scrolling a thread doesn't ask imgur again for the same album. */
    private static final Map<String, String> COVERS = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<String, String> eldest) {
            return size() > 200;
        }
    };

    private FixImgurPatch() {
    }

    /** Replaces OkHttpClient.Builder.build() for the clients behind Glide and Volley. */
    public static OkHttpClient install(OkHttpClient.Builder builder) {
        return builder.addInterceptor(INSTANCE).build();
    }

    /** Your imgur API Client-ID. Filled in by the patch. */
    private static String clientId() {
        return "";
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        String host = url.host();

        if (PROXY_HOST.equals(host) && "/image".equals(url.encodedPath())) {
            String target = url.queryParameter("url");
            if (target != null && target.startsWith(ALBUM_PREFIX)) {
                return thumbnail(chain, request, target.substring(ALBUM_PREFIX.length()));
            }
        } else if (ALBUM_HOST.equals(host) && url.encodedPath().startsWith("/a/")) {
            // /a/{hash}/{id}
            String path = url.encodedPath();
            return album(chain, request, path.substring(path.lastIndexOf('/') + 1));
        }
        return chain.proceed(request);
    }

    /** The album's JSON as imgur's API returns it (including its 404 "Unable to find an album" error). */
    private static Response album(Chain chain, Request original, String id) throws IOException {
        Response response = chain.proceed(apiRequest(original, id));
        return response.newBuilder().request(original).build();
    }

    private static Response thumbnail(Chain chain, Request original, String id) throws IOException {
        String cover;
        synchronized (COVERS) {
            cover = COVERS.get(id);
        }
        if (cover == null) {
            try (Response response = chain.proceed(apiRequest(original, id))) {
                if (response.isSuccessful() && response.body() != null) {
                    cover = coverId(response.body().string());
                }
            } catch (JSONException e) {
                Log.w(TAG, "Could not read imgur album " + id, e);
            }
            if (cover == null) {
                return chain.proceed(original);  // Fails like before: the thumbnail just stays empty.
            }
            synchronized (COVERS) {
                COVERS.put(id, cover);
            }
        }
        return chain.proceed(original.newBuilder().url("https://i.imgur.com/" + cover + "m.jpg").build());
    }

    private static String coverId(String body) throws JSONException {
        JSONObject data = new JSONObject(body).getJSONObject("data");
        String cover = data.optString("cover", "");
        if (!cover.isEmpty() && !"null".equals(cover)) {
            return cover;
        }
        JSONArray images = data.optJSONArray("images");
        if (images != null && images.length() > 0) {
            return images.getJSONObject(0).getString("id");
        }
        return null;
    }

    private static Request apiRequest(Request original, String id) {
        return original.newBuilder()
                .url("https://api.imgur.com/3/album/" + id)
                .header("Authorization", "Client-ID " + clientId())
                .build();
    }
}
