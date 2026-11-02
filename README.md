# revanced-sync-fixes

ReVanced patches for **Sync for Reddit** / **Sync for Reddit Pro** `v23.06.30-13:39`, ported from
[trulow/sync-reddit-fixes](https://github.com/trulow/sync-reddit-fixes) (Morphe) and
[notBrian2/revanced-rif-fixes](https://github.com/notBrian2/revanced-rif-fixes).

| Patch | Fixes |
|-------|-------|
| Fix Reddit galleries | `/api/info` is blocked; the post is loaded from `/comments` instead |
| Fix load more comments | `/api/morechildren` is blocked; comments are loaded from `/comments` instead |
| Fix Redgifs API | removed Redgifs endpoints answered locally, temporary token, id parsing |
| Fix imgur thumbnails | Sync's own imgur proxy (`ap/images.syncforreddit.com`) answers 401; album thumbnails and albums are loaded from imgur's API. **Set `IMGUR_CLIENT_ID` in `patches/.../imgur/FixImgurPatch.kt` (your own free imgur app) before building** |

Use alongside Patcheddit's **Spoof client** (own client ID). Turn off Patcheddit's "Fix Redgifs API".

## Build
JDK 17+, Android SDK (platform 36), GitHub token with `read:packages`
(`GITHUB_ACTOR` / `GITHUB_TOKEN` or `gpr.user` / `gpr.key`), then `./gradlew buildAndroid`.
Output: `patches/build/libs/patches-<version>.rvp`. Replace `OWNER` in `patches/build.gradle.kts`.
