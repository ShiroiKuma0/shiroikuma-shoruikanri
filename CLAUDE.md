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
| App launcher label | `白い熊 書類管理` | `sk_launcher_name` in `values/strings.xml`, referenced by the manifest `android:label` (in-app `app_name` stays "Material Files") |
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
- Fork `versionName` = `"<VERSION_NAME>+<BUILD_NUMBER>"` (e.g. `1.7.4+1`).
- Fork `versionCode` = `VERSION_CODE * 10000 + BUILD_NUMBER` (e.g. `39 * 10000 + 1 = 390001`).
- Output APK filename = `shiroikuma-shoruikanri_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`
  (e.g. `shiroikuma-shoruikanri_1.7.4+1_arm64-v8a.apk`).

So the first build is `+1` (`390001`), the next build with changes is `+2` (`390002`), and so on.

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
5. Build the new `+1` version with `./gradlew buildApk`; continue further changes as `+2`, `+3`, …

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
