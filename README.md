# revanced-sync-fixes

ReVanced patches for **Sync for Reddit** / **Sync for Reddit Pro** `v23.06.30-13:39`, ported from
[trulow/sync-reddit-fixes](https://github.com/trulow/sync-reddit-fixes) (Morphe) and
[notBrian2/revanced-rif-fixes](https://github.com/notBrian2/revanced-rif-fixes).

| Patch | Fixes |
|-------|-------|
| Fix Reddit galleries | `/api/info` is blocked; the post is loaded from `/comments` instead |
| Fix load more comments | `/api/morechildren` is blocked; comments are loaded from `/comments` instead |
| Fix Redgifs API (sync-patch) | removed Redgifs endpoints answered locally, temporary token, id parsing |

## Use with ReVanced Manager

1. In ReVanced Manager add this as a remote patch source (Settings -> Patch bundles; the manager needs this JSON link, a link to the `.rvp` does not work):

   ```
   https://raw.githubusercontent.com/sloewhite/sync-patch/main/bundles/stable.json
   ```

   It always points at the newest release. `bundles/latest.json` also includes pre-releases. Alternatively download the `.rvp` from [Releases](https://github.com/sloewhite/sync-patch/releases) and add it as a local file.
2. Patch **Sync for Reddit** `v23.06.30-13:39`, selecting the official ReVanced bundle's **Spoof client** patch (Sync cannot reach Reddit without it) and the patches from this bundle.

The release workflow publishes the `.rvp` and updates `bundles/*.json` when a `v*` tag is pushed. Tags with a hyphen (`v0.2.0-dev.1`) are pre-releases.

## Build
JDK 17+, Android SDK (platform 36), GitHub token with `read:packages`
(`GITHUB_ACTOR` / `GITHUB_TOKEN` or `gpr.user` / `gpr.key`), then `./gradlew buildAndroid`.
Output: `patches/build/libs/patches-<version>.rvp`. The patch metadata in `patches/build.gradle.kts` points to this repository.
