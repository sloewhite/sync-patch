package app.revanced.extension.syncfixes;

import android.util.Log;

import com.android.volley.NetworkResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reddit blocks /api/morechildren for third-party clients. /comments/... still works, so "load more comments" is
 * rebuilt on it: the POST to morechildren becomes a GET of the parent's subtree, and the reply is converted back
 * into morechildren's flat {"json":{"data":{"things":[...]}}} shape that Sync parses.
 *
 * Rewritten requests carry syncfixes=more so the response hook can recognise them.
 */
@SuppressWarnings("unused")
public final class FixLoadMoreCommentsPatch {
    private static final String MARK = "syncfixes=more";
    private static final Pattern MORECHILDREN =
            Pattern.compile("^(https://[a-z]+\\.reddit\\.com/)api/morechildren.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern THING_ID = Pattern.compile("^t[13]_[a-z0-9]+$");
    private static final int VOLLEY_GET = 0;

    private static final ThreadLocal<Object> MORE_ITEM = new ThreadLocal<>();
    private static final ThreadLocal<String[]> MORE_ARGS = new ThreadLocal<>();
    /** The child id lists by key: hundreds of ids would make the URL too long (HTTP 414). */
    private static final Map<String, String> WANTED = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_KEY = new AtomicInteger();

    private FixLoadMoreCommentsPatch() {
    }

    /** Hooked into CommentsPresenterImpl's "load more" handler with the "more" row. */
    private static void setMoreItemImpl(Object item) {
        MORE_ITEM.set(item);
    }

    /** Hooked into OAuthCommentsMoreRequest's constructor: link id, comma separated child ids, sort. */
    private static void setMoreArgsImpl(String linkId, String children, String sort) {
        MORE_ARGS.set(new String[]{linkId, children, sort});
    }

    /** Replaces the URL passed to the request's super constructor. */
    private static String moreUrlImpl(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = MORECHILDREN.matcher(url);
        String[] args = MORE_ARGS.get();
        Object item = MORE_ITEM.get();
        MORE_ARGS.remove();
        MORE_ITEM.remove();
        if (!m.matches() || args == null || args[0] == null) {
            return url;
        }
        String link = args[0].startsWith("t3_") ? args[0].substring(3) : args[0];
        String parent = parentId(item);
        boolean topLevel = parent == null || !parent.startsWith("t1_");

        StringBuilder sb = new StringBuilder(m.group(1)).append("comments/").append(link);
        if (!topLevel) {
            sb.append("/_/").append(parent.substring(3));
        }
        // Reddit caps a fetch at 500 comments. At the top level spend them on top-level comments: deeper
        // replies come back as their own "load more" (the nested case, which loads fully).
        sb.append(".json?limit=500&depth=").append(topLevel ? 2 : 10);
        if (args[2] != null && !args[2].isEmpty()) {
            sb.append("&sort=").append(args[2].toLowerCase(java.util.Locale.ENGLISH));
        }
        if (WANTED.size() > 50) {
            WANTED.clear();
        }
        String key = Integer.toString(NEXT_KEY.incrementAndGet());
        WANTED.put(key, args[1] == null ? "" : args[1]);
        sb.append('&').append(MARK).append("&parent=").append(parent == null ? "" : parent)
                .append("&want=").append(key);
        return sb.toString();
    }

    /** Replaces the HTTP method passed to the super constructor: rewritten requests are GETs. */
    private static int moreMethodImpl(int method, String url) {
        return url != null && url.contains(MARK) ? VOLLEY_GET : method;
    }

    /** Hooked into OAuthCommentsMoreRequest.parseNetworkResponse(). */
    private static NetworkResponse moreResponseImpl(String url, NetworkResponse response) {
        if (url == null || !url.contains(MARK) || response == null || response.data == null) {
            return response;
        }
        String body = Responses.body(response);
        if (!body.trim().startsWith("[")) {
            return response;  // Not a /comments reply (an error object, for example).
        }
        try {
            String want = WANTED.get(param(url, "want"));
            return Responses.withBody(response, more(body, param(url, "parent"), want == null ? "" : want));
        } catch (JSONException e) {
            Log.w(Responses.TAG, "Could not convert comments response", e);
            return response;
        }
    }

    /**
     * The parent_id of the "more" row. The row is Sync's obfuscated Post class, so its fields are found by
     * reflection: the String field holding a t1_/t3_ id, preferring one named like a parent.
     */
    private static String parentId(Object item) {
        if (item == null) {
            return null;
        }
        String fallback = null;
        for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() != String.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    String value = (String) field.get(item);
                    if (value == null || !THING_ID.matcher(value).matches()) {
                        continue;
                    }
                    if (field.getName().toLowerCase().contains("parent")) {
                        return value;
                    }
                    if (fallback == null) {
                        fallback = value;
                    }
                } catch (Exception ignored) {
                    // Unreadable field: try the next one.
                }
            }
        }
        return fallback;
    }

    private static String more(String text, String parent, String want) throws JSONException {
        Set<String> wanted = new HashSet<>(Arrays.asList(want.split(",")));
        Set<String> shown = new HashSet<>();
        shown.add(parent);
        JSONArray things = new JSONArray();
        JSONArray top = new JSONArray(text).getJSONObject(1).getJSONObject("data").getJSONArray("children");
        int comments = walk(top, parent, wanted, shown, things);
        if (comments == 0 && hasMoreUnder(top, parent)) {
            // Nothing new within Reddit's 500-comment window (bottom of a huge thread): return Reddit's error
            // shape so Sync shows its normal error and keeps the row instead of spinning.
            return "{\"json\":{\"errors\":[[\"SYNC_FIXES\",\"Reddit no longer lets Sync load these comments\","
                    + "\"\"]]}}";
        }
        return new JSONObject().put("json", new JSONObject()
                .put("errors", new JSONArray())
                .put("data", new JSONObject().put("things", things))).toString();
    }

    /** Pre-order walk, like morechildren: a parent always comes before its replies. Returns comments added. */
    private static int walk(JSONArray children, String parent, Set<String> wanted, Set<String> shown,
                            JSONArray things) throws JSONException {
        int added = 0;
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            JSONObject data = child == null ? null : child.optJSONObject("data");
            if (data == null) {
                continue;
            }
            String parentId = data.optString("parent_id");
            if ("t1".equals(child.optString("kind"))) {
                JSONObject replies = data.optJSONObject("replies");
                // Like morechildren: the asked-for comments plus everything under them.
                boolean underAdded = shown.contains(parentId) && !parentId.equals(parent);
                if (underAdded || (wanted.contains(data.optString("id")) && shown.contains(parentId))) {
                    data.put("replies", "");
                    things.put(child);
                    shown.add(data.optString("name"));
                    added++;
                }
                if (replies != null && replies.optJSONObject("data") != null) {
                    JSONArray sub = replies.optJSONObject("data").optJSONArray("children");
                    if (sub != null) {
                        added += walk(sub, parent, wanted, shown, things);
                    }
                }
            } else if ("more".equals(child.optString("kind")) && shown.contains(parentId)) {
                // Deeper "load more" under a comment we added: keep it. One directly under the parent is
                // only useful if it still holds comments that were asked for.
                if (!parentId.equals(parent) || overlaps(data.optJSONArray("children"), wanted)) {
                    things.put(child);
                }
            }
        }
        return added;
    }

    /** Whether Reddit held back comments directly under the parent. */
    private static boolean hasMoreUnder(JSONArray children, String parent) {
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            JSONObject data = child == null ? null : child.optJSONObject("data");
            if (data == null) {
                continue;
            }
            if ("more".equals(child.optString("kind"))) {
                JSONArray ids = data.optJSONArray("children");
                if (parent.equals(data.optString("parent_id")) && ids != null && ids.length() > 0) {
                    return true;
                }
                continue;
            }
            JSONObject replies = data.optJSONObject("replies");
            JSONArray sub = replies == null || replies.optJSONObject("data") == null
                    ? null : replies.optJSONObject("data").optJSONArray("children");
            if (sub != null && hasMoreUnder(sub, parent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(JSONArray ids, Set<String> wanted) {
        if (ids == null) {
            return false;
        }
        for (int i = 0; i < ids.length(); i++) {
            if (wanted.contains(ids.optString(i))) {
                return true;
            }
        }
        return false;
    }

    private static String param(String url, String name) {
        Matcher m = Pattern.compile("[?&]" + name + "=([^&]*)").matcher(url);
        return m.find() ? m.group(1) : "";
    }

    // The public hooks below never throw: on any error Sync carries on with its own behaviour.

    public static void setMoreItem(Object item) {
        try {
            setMoreItemImpl(item);
        } catch (Throwable t) {
            Log.e(Responses.TAG, "setMoreItem failed", t);
        }
    }

    public static void setMoreArgs(String linkId, String children, String sort) {
        try {
            setMoreArgsImpl(linkId, children, sort);
        } catch (Throwable t) {
            Log.e(Responses.TAG, "setMoreArgs failed", t);
        }
    }

    public static String moreUrl(String url) {
        try {
            return moreUrlImpl(url);
        } catch (Throwable t) {
            Log.e(Responses.TAG, "moreUrl failed", t);
            return url;
        }
    }

    public static int moreMethod(int method, String url) {
        try {
            return moreMethodImpl(method, url);
        } catch (Throwable t) {
            Log.e(Responses.TAG, "moreMethod failed", t);
            return method;
        }
    }

    public static NetworkResponse moreResponse(String url, NetworkResponse response) {
        try {
            return moreResponseImpl(url, response);
        } catch (Throwable t) {
            Log.e(Responses.TAG, "moreResponse failed", t);
            return response;
        }
    }
}
