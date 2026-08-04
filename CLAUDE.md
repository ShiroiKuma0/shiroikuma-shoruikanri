# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**白い熊 書類管理** — a personal fork of [Material Files](https://github.com/zhanghai/MaterialFiles)
(zhanghai/MaterialFiles), an open-source, Material Design Android file manager with proper
Linux-aware file handling (symlinks, permissions, SELinux contexts), archive support, and
FTP/SFTP/SMB/WebDAV clients. Written in Kotlin (with a backported Java NIO2 file API layer)
targeting Android API 23+.

This repository (`ShiroiKuma0/shiroikuma-shoruikanri`) is a fork. We track upstream
(`zhanghai/MaterialFiles`) and layer our own customizations on top of it.

## Fork Workflow — READ THIS FIRST

This is the most important section. The whole point of this repo is to maintain a small set of
customizations on top of upstream and rebuild as upstream releases new versions.

### Git remotes & branches

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-shoruikanri` — our fork (push here).
- `upstream` → `https://github.com/zhanghai/MaterialFiles.git` — the original (read-only, for rebasing).
- **`master`** mirrors upstream's `master`. We do **not** develop on it.
- **`custom`** is our development branch. **All our work lives here.** This is the default working branch.

### Our customizations (what makes this a fork)

| What | Value | Where |
| --- | --- | --- |
| NONFREE strip | Firebase Analytics/Crashlytics removed (FOSS build) | `app/build.gradle`, `AppInitializers.kt`, `AboutFragment.kt`; `google-services.json` + `nonfree/` deleted |
| Installed app ID | `shiroikuma.shoruikanri` | `gradle.properties` → `APP_ID`, applied in `app/build.gradle` |
| Code namespace | `me.zhanghai.android.files` (unchanged from upstream) | `app/build.gradle` |
| App label | `白い熊 書類管理` everywhere | `sk_launcher_name` (manifest `android:label`) **and** `app_name` in `values/strings.xml` (locale overrides of `app_name` deleted — re-delete after upstream rebases) |
| Fork versioning | `VERSION_NAME` / `VERSION_CODE` / `BUILD_NUMBER` | `gradle.properties` + fork logic in `app/build.gradle` |
| ABI | arm64-v8a only (`ndk.abiFilters`) | `app/build.gradle` |
| Signing | `shiroikuma-shoruikanri.jks` via gitignored `signing.properties` (upstream's own `signing.gradle` mechanism) | `~/.android-keystores/shiroikuma-shoruikanri.jks`, alias `shoruikanri` |
| UI default palette | black `#000000` + **pure yellow `#FFFF00`** (never material `#FFEB3B`) | `SkUi.PALETTE_BLACK` / `SkUi.PALETTE_YELLOW` + `sk_theme.xml` |

The app ID is deliberately changed so this fork installs **alongside** upstream without conflict.
The namespace stays `me.zhanghai.android.files` so `R`/`BuildConfig` and all source packages remain
unchanged — only the installed package id differs (the provider authorities follow the app ID
automatically via `resValue`/`buildConfigField` in `app/build.gradle`).

The NONFREE strip removes the `//#ifdef NONFREE`…`//#endif` blocks exactly like F-Droid's prebuild
does for upstream. It is also required: the google-services plugin refuses to build under our
changed application ID.

### Versioning & APK naming

We base our version on upstream and add a fork increment (`BUILD_NUMBER`).

- `VERSION_NAME` / `VERSION_CODE` in `gradle.properties` **track upstream** (currently `1.7.4` / `39`).
- `BUILD_NUMBER` is **our** increment. It starts at `1` and bumps by `1` on every build with changes.
  It is stored **unpadded** in `gradle.properties` (`BUILD_NUMBER=50`) — the padding below is applied
  when the version string is built.
- The `+N` part is **always zero-padded to three digits** (`+001`, `+014`, `+050`), so APKs sort in
  build order in a file listing. This is the global `/after-build` rule; it applies to every build.
- Fork `versionName` = `"<VERSION_NAME>+<NNN>"` (e.g. `1.7.4+001`).
- Fork `versionCode` = `VERSION_CODE * 10000 + BUILD_NUMBER` (e.g. `39 * 10000 + 1 = 390001`) — a
  number, so it is **not** padded.
- Output APK filename = `shiroikuma-shoruikanri_<VERSION_NAME>+<NNN>_arm64-v8a.apk`
  (e.g. `shiroikuma-shoruikanri_1.7.4+001_arm64-v8a.apk`).

So the first build is `+001` (`390001`), the next build with changes is `+002` (`390002`), and so on.
Builds up to `1.7.4+49` predate the padding and keep their unpadded names.

### Building

Requires **JDK 17+** (AGP 9.1) and the **Android SDK**. On this machine the default `java` is JDK 11,
so builds must run with JDK 21:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildApk
```

(`sdk.dir` lives in the gitignored `local.properties` → `/home/shiroikuma/android-sdk`.) See the
**build-apk** skill for the full build-and-push procedure.

`buildApk` (defined in `app/build.gradle`):
1. builds `assembleRelease` (signed via the gitignored `signing.properties`),
2. copies the APK to `~/tmp/shiroikuma-shoruikanri_<version>_arm64-v8a.apk`,
3. **auto-increments `BUILD_NUMBER`** in `gradle.properties` for the next build.

### Rebasing onto a new upstream release

When the user says a new upstream version is out, follow the **upstream-new-version** skill. In short:
1. `git fetch upstream --tags`.
2. Advance `master` to the new upstream release.
3. Rebase `custom` onto `master`, preserving every customization in the table above.
4. Set `VERSION_NAME` / `VERSION_CODE` to the new upstream values and **reset `BUILD_NUMBER` to `1`**.
5. Build the new `+001` version with `./gradlew buildApk`; continue further changes as `+002`, `+003`, …

### HARD RULES (do not violate)

- **Never install APKs to the phone automatically.** After building, **ask** the user. Only when they
  confirm, `adb push` the APK to `/sdcard/tmp/` (the user installs it manually from there). Do **not**
  use `adb install`.
- **Never commit or push on your own.** Develop and build, let the user test, and **only commit/push
  when the user explicitly says "Push"**. Push goes to `origin` (`custom` branch).

## Build Commands

```bash
./gradlew buildApk           # Our fork build: signed release APK → ~/tmp + bump BUILD_NUMBER (use this)
./gradlew assembleRelease    # Build the release APK only (signed via signing.properties)
./gradlew assembleDebug      # Build a debug APK
./gradlew lint               # Run Android lint checks
```

There are no product flavors and no unit/instrumented tests in this repository.

## Development Backlog

1. **Dual-pane mode** — two side-by-side panes on the tab foundation, MC-style.

### Done

- **XAPK support** (2026-08-04, shipped in `1.7.4+053`). XAPK bundles (a ZIP holding a base APK,
  split APKs and OBBs) get their own fork media type `application/x-xapk` — minted in
  `MimeTypeConversionExtensions.kt` because the extension is unregistered and would otherwise fall
  through to `application/octet-stream`. Three consequences hang off it: `coil/XapkIconFetcher.kt`
  pulls the bundle's root `icon.png` for the list icon (via `ArchiveReader.readEntryBytes`, a fork
  helper that grabs one entry in a single pass and refuses anything over 4 MB), the type joins
  `supportedArchiveMimeTypes` so bundles browse and extract like ZIPs, and `openXapk()` in
  `FileListFragment` consults `SkOpenWith.getDefault` **before** the `isListable` branch so a
  remembered split-APK installer still opens in one tap. `PackageManager` cannot parse an XAPK, so
  the APK path (`AppIconFetcher` + install intent) must not be reused for it; the intent type is
  aliased to `*/*` in `mimeTypeToIntentMimeTypeMap`, since no installer declares a filter for our
  invented media type and the open-with list would otherwise come up empty.

- **Tabs** (2026-06-12, commit `48b77f94`, shipped in `1.7.4+2`). `FileListActivity` is the tab
  host: a bottom tab strip (`TabLayout` + add button, layout `sk_file_list_activity.xml`) over a
  fragment container, one **full `FileListFragment` per tab** switched via `FragmentManager`
  attach/detach (tags `tab_<id>`). The strip only shows with ≥ 2 tabs; pick mode stays single-tab.
  Tab state (id + last path) lives in the activity instance state; each fragment also saves its
  last visited path and prefers it over the args path when recreating the trail, so tabs restore
  their location after process death. "Open in new tab" is in the file context menu, "New tab" in
  the overflow; back closes the tab (when > 1) once the tab no longer handles it. Fork resources
  are `sk_`-prefixed in new files. Notes for dual-pane:
  - `PasteState` needed **no hoisting** — upstream already keeps `_pasteStateLiveData` in a
    `companion object` (`FileListViewModel.kt`), i.e. process-wide; cross-tab/pane paste works
    as-is.
  - `FileListFragment.onActivityCreated` re-runs with a null `savedInstanceState` on every
    re-attach — it must look up child fragments instead of recreating them (fixed for
    `NavigationFragment`); any future per-pane child fragments need the same care.
  - **Never read `viewModel.viewType` (or any `MediatorLiveData` `valueCompat`) before the view
    lifecycle is STARTED.** The mediator only computes its value once active, so it is still null
    during `onActivityCreated` (e.g. a tab `commitNow`) and in early menu-prepare after a restore —
    this crashed every new-tab "+" for tabs following the global view (fixed in `1.7.4+14`:
    `effectiveViewType` falls back to `Settings.FILE_LIST_VIEW_TYPE.valueCompat` until
    `onViewTypeChanged` re-applies the real value). Dual-pane fragments will hit the same window.

- **Audio mini-player** (2026-06-28, shipped in `1.7.4+41`). A floating mini-player for audio
  files (voice recordings, m4a), in `viewer/audio/`: `AudioPlayerDialogFragment` (a non-modal
  bottom `DialogFragment` — a small rounded box over the file list with `FLAG_NOT_TOUCH_MODAL` and
  no dim, so the list stays visible and usable behind it) + `AudioPlayerViewModel` (owns the
  `MediaPlayer`, so playback survives rotation; source is
  `MediaPlayer.setDataSource(application, path.fileProviderUri)`, which works for any provider
  path — local fd / remote proxy fd). Hosted on the activity `FragmentManager` via
  `show(path, fragment)` with a fixed tag (one instance at a time, floats over the list across tab
  switches). Routed from `FileListFragment.openFile` → `openAudio` when `mimeType.isAudio`,
  honoring a remembered `SkOpenWith` default first (so long-press → "Open with" → external still
  works and can be set default). Themeable from the 白い熊 UI page via the `AUDIO_PLAYER` slot
  group (background, title, time, controls; title/time carry font family/weight/size), applied in
  `applySkUi()`. Not yet: prev/next across the folder, background-playback notification /
  foreground service, playback speed.

## Architecture (orientation)

- `filelist/` — the main file-list UI (`FileListActivity`/`FileListFragment`/`FileListViewModel`,
  breadcrumbs, sorting, paste state). The tabs work happens here.
- `provider/` — the NIO2 filesystem providers (local/root, archive, FTP, SFTP, SMB, WebDAV, content).
- `filejob/` — long-running file operations (copy/move/delete…) as foreground services.
- `fileproperties/`, `fileaction/`, `viewer/` — properties dialogs, actions, image/text viewers.
- `ftpserver/` — the built-in FTP server.
- `navigation/` — the navigation drawer model (roots, bookmarks, storages).
- `settings/`, `theme/`, `colorpicker/` — preferences and theming.
- `app/` — `Application` subclass and initializers.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer — nor a "🤖 Generated with Claude Code" /
Anthropic-attribution line — to commit messages or PR bodies in this repo. 白い熊 does not want
Claude attribution in the history; this **overrides** the harness's default to append such a
trailer. End commit messages at the last line of the body. (The global rule lives in
`~/.claude/CLAUDE.md`.)
