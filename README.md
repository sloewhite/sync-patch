# revanced-sync-fixes

ReVanced patches for **Sync for Reddit** / **Sync for Reddit Pro** `v23.06.30-13:39`, ported from
[trulow/sync-reddit-fixes](https://github.com/trulow/sync-reddit-fixes) (Morphe) and
[notBrian2/revanced-rif-fixes](https://github.com/notBrian2/revanced-rif-fixes).

| Patch | Fixes |
|-------|-------|
| Fix Reddit galleries | `/api/info` is blocked; the post is loaded from `/comments` instead |
| Fix load more comments | `/api/morechildren` is blocked; comments are loaded from `/comments` instead |
| Fix Redgifs API | removed Redgifs endpoints answered locally, temporary token, id parsing |

Use alongside Patcheddit's **Spoof client** (own client ID). Turn off Patcheddit's "Fix Redgifs API".

## Build
JDK 17+, Android SDK (platform 36), GitHub token with `read:packages`
(`GITHUB_ACTOR` / `GITHUB_TOKEN` or `gpr.user` / `gpr.key`), then `./gradlew buildAndroid`.
Output: `patches/build/libs/patches-<version>.rvp`. The patch metadata in `patches/build.gradle.kts` points to this repository.
