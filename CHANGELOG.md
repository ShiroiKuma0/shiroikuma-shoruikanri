# Changelog — 白い熊 書類管理

All notable fork changes layered on top of [Material Files](https://github.com/zhanghai/MaterialFiles)
by Hai Zhang. Versions are `<upstream version>+<fork build>` — the upstream version this fork tracks,
plus our build increment (e.g. `1.7.4+36`). This fork installs side-by-side with upstream under the
app ID `shiroikuma.shoruikanri`.

## 1.7.4+36

First public release. Everything below is what this fork adds to (or changes from) stock Material
Files 1.7.4.

### 🔒 Encryption — gocryptfs volumes, in-app

- A JNI-backed NIO2 `FileSystemProvider` over **libgocryptfs** — browse, read and write encrypted
  **gocryptfs** volumes like any other location, with **no FUSE and no root**.
- `provider/gocryptfs/`: a `GocryptfsVolume` JNI client (wrapper adapted from DroidFS) plus
  `GocryptfsStat` / `GocryptfsEntry`; a read/write provider (list / stat / read, and
  write / truncate / mkdir / delete / rename / copy / move) over a 128 KiB-chunked seekable channel,
  with `LocalWatchService` notifications so listings auto-refresh. Registered in `FileSystemProviders`.
- Native libraries vendored in `jniLibs/arm64-v8a` (the `libgocryptfs.so` engine + a
  `libgocryptfs_jni.so` bridge, built in the `shiroikuma-gcfs` sister project); ProGuard keeps the
  JNI-constructed client types (invisible to R8).
- **Unlock / lock from the address row.** A padlock button sits left of the address-line hamburger:
  on a cipher directory (a real path whose listing contains `gocryptfs.conf`) it shows an open
  padlock and prompts for the password to unlock and navigate into the decrypted tree; inside an
  unlocked volume it shows a closed padlock that locks (navigates back to the cipher dir, then closes
  the native session). Hidden everywhere else, so it never prompts off a volume.
- **Path diagnostics** on breadcrumb long-press: URI / scheme / provider, a native stat & access
  probe, and the storage-volume list.

### 🗂 Tabs & navigation

- **Multi-folder tabs.** `FileListActivity` is a tab host — one full `FileListFragment` per tab,
  switched via `FragmentManager` attach/detach. The tab strip only appears with two or more tabs, so
  single-tab use looks stock; pick mode stays single-tab.
- **Folder-style tab bar** (`SkFolderTabBar`), between the toolbar and the breadcrumbs: stacked paper
  folders — a rounded border running under the inactive tabs, up and over the active one and slanting
  down on its right, with shaded outlines behind.
- **Reorder, swipe, favorite.** Long-press a tab and drag to rearrange; release without moving to add
  that folder to the favorites. Horizontal flings in the folder body switch to the adjacent tab,
  wrapping around at the ends.
- **Persistent tab set.** The open tabs (paths + selection + per-tab view) survive restarts, reboots
  and app updates via a parceled preference; explicit intents open on top of the restored set.
- **Per-tab listing view.** Each tab keeps its own view (`TabInfo.viewType`), which wins over the
  global / path-specific setting, persists with the tab set, and is inherited by tabs opened from it;
  tabs with no explicit choice follow the stock setting.
- "Open in new tab" in the file context menu, "New tab" in the overflow. Back closes the current tab
  (when more than one is open) once the tab itself no longer handles it.
- The navigation drawer no longer swipe-opens (it conflicted with the tab swipe) — it opens from the
  top-left icon and unlocks while open. The app bar no longer lifts on scroll (kept the surface from
  greying under the address bar).
- Each fragment saves its last visited path and prefers it over the args path when recreating the
  trail, so tabs restore their location after process death.
- Fixed a latent upstream bug exposed by attach/detach (`onActivityCreated` re-running with a null
  `savedInstanceState` on re-attach would duplicate the `NavigationFragment` — now it looks the child
  up), and a new-tab crash (`effectiveViewType` read the view-type `MediatorLiveData` before it was
  active; it now falls back through `skTabViewType` → `viewTypeLiveData.value` →
  `Settings.FILE_LIST_VIEW_TYPE` until the live data delivers).

### ⭐ Favorites (bookmarks)

- Moved to the **top** of the drawer and made drag-rearrangeable in place via an `ItemTouchHelper`
  (order persists); a long-press released without movement opens the existing rename/delete dialog.
- A long-press on a folder tab adds that folder to the favorites.

### 🎨 Black & yellow theme system (skui)

- A pure-**black** background with pure-**yellow `#FFFF00`** text, icons and borders across the whole
  app. `ThemeOverlay.Sk` (applied in `AppActivity`, default on) forces black surfaces and yellow
  `colorPrimary` / `Secondary` / `Accent` / `colorControlActivated` on every activity, dialog and
  menu; elevation overlays are disabled (they tinted elevated surfaces translucent-yellow) and popup
  menus get a solid black, yellow-bordered background.
- The **`skui/` engine**: `SkThemeSlot` colour slots in eight groups with two-tier inheritance from a
  Background / Accent / Text foundation; per-text-element fonts (importable `.ttf`/`.otf` — family,
  weight, size) with a picker that renders each font in its own glyphs; a colour picker with swatches
  and hex; file-list icon size and inter-file padding. All stored in the `sk_ui` preferences with a
  generation counter for cheap re-styling.
- The **白い熊 書類管理 UI page** (`SkUiActivity`): a section → subgroup → item cascade with deep
  indents, colour / text / slider / switch / value rows and live previews, opened from a new top entry
  in Settings. A one-time migration rewrites earlier material-yellow slot overrides to pure yellow.
- **Black, yellow-bordered dialogs everywhere** via a drop-in `SkMaterialAlertDialogBuilder` at every
  call site (M3 surface-container roles forced black, `colorOnSurfaceVariant` /
  `textColorAlertDialogListItem` pointed at yellow; AppCompat preference dialogs themed via
  `alertDialogTheme`).
- **Themed toasts**: `showToast` routes through `SkToast` — black background, yellow text and border.
- **Black/yellow speed-dial**: the new-item button is a black circle with a yellow ring and a yellow
  plus (a 2 dp ring drawn as the button's foreground, closed and opened).
- The internal **image viewer**'s toolbar chrome (back arrow, title, menu) is explicitly yellow.
- **Rebranded launcher icon**: a solid `#000000` field with upstream's folder glyph traced in
  `#FFFF00` scaled to the full masked icon — new adaptive layers (`launcher_icon_foreground_sk` /
  `launcher_icon_monochrome_sk`, leaving upstream's untouched) plus regenerated legacy rasters.
- **白い熊 書類管理 name everywhere**: launcher label via `sk_launcher_name`, and the in-app `app_name`
  set to 白い熊 書類管理 as a single untranslatable value (per-locale overrides removed) so About,
  notifications and every in-app surface use the fork name in all languages.

### 🔭 Listing views & per-folder styling

- **Six listing views** from a new view menu (the 3×3-grid icon between sort and overflow): **List**,
  **Grid**, **Compact** (name only), **Column** (name + right-aligned date/time), **Detailed** (exact
  size, date/time, POSIX attributes) and **Wrapped** (two columns with wrapping names). The added
  views always ellipsize with trailing dots; icon size and inter-file padding follow the UI-page
  sliders (rows shrink to `wrap_content`, so padding 0 means no gap between files).
- **Grid styling** — text size plus a full `GRID_TEXT` font slot (grid names get their own font and
  colour, separate from the list), image width and height, horizontal/vertical padding (down to 0),
  the image-to-name gap (down to 0), a text-over-image overlay (the name floats over the bottom of the
  photo with no background, for seamless photo walls) and a show-file-name toggle. Global defaults
  live in the UI page's Grid view subgroup with a live preview cell; the column count follows image
  width + padding.
- **File separators** — `SkSeparators` thickness (0 = none) and colour per listing view, with global
  defaults in the UI page's Separators subgroup. Drawn as a `RecyclerView` decoration: a line under
  every row/cell plus vertical lines between columns in multi-column (Wrapped) views, meeting the
  horizontal lines into a closed lattice.
- **Per-folder overrides** — a hamburger at the right of the address line opens a bottom sheet with
  live sliders/toggles, view-aware (grid controls in grid view, separator controls elsewhere). Values
  are stored per path (`sk_grid_styles`), so a folder opens with its style in any tab and after
  restarts/reboots/updates; Reset reverts the folder to the globals.

### 📂 Open-with

- `SkOpenWithDialog` replaces the system chooser: every handling app with its icon, the current
  default marked, an **"always use for this type"** checkbox that remembers the app per MIME type
  (`SkOpenWith` prefs), an **"Open as…"** entry to re-choose among another type's handlers, and a
  neutral button that forgets the default.
- Plain opens consult the remembered default first (cleared automatically if the app is gone); APK
  taps check it before upstream's install/view handling, so a remembered installer bypasses the
  prompt.

### 📤 Sharing & Termux integration

- A **custom share dialog** (`SkShareDialog`) replaces the system share sheet for file-list shares —
  every handler with its icon, long-press to pin/unpin (pinned sort to the top), and shared URIs
  granted to the chosen app explicitly so apps that read the file asynchronously still can.
- **AutoShare**: an "AutoShare command…" row opens AutoShare's own command chooser.
- **Termux script targets** (`SkTermux`): user-defined one-click targets, each running a chosen Termux
  script via `RunCommandService` with the selected file's real path(s) as command-line arguments
  (`$1`, `$2`…); long-press to move/edit/delete. Requires Termux's `allow-external-apps=true` and the
  new `com.termux.permission.RUN_COMMAND`.
- **Inbound system-share → Termux**: 白い熊 書類管理 receives `ACTION_SEND` / `SEND_MULTIPLE` and routes
  the shared file(s) to a Termux script (`SkShareReceiverActivity`), resolving each URI to a
  Termux-readable real path (a readable shared-storage path, else a copy staged under a settable
  folder, default `/storage/emulated/0/tmp`).
- **One-tap share tiles**: because some launchers (EMUI) group every `SEND` target of an app under one
  tile, the app exposes exactly **one** `SEND` tile (`SkShareShortcuts` toggles component-enabled
  state) — one-target mode fires the first script directly (`SkShareSlot1`), multi mode opens the
  in-app chooser (`SkShareChooser` alias). The upstream 「保存」 `SaveAsActivity` SEND tile is hidden
  whenever scripts exist; dynamic Direct-Share shortcuts are also published.
- New UI rows (共有 → Termux): the one-tap / multi toggle (default one-tap) and the staging-folder path.
- **Selection action-mode reorg**: Delete moved to the left and isolated by a transparent spacer so it
  isn't mis-tapped; Share and Select-all are visible icons on the right.

### 🔤 Sorting

- A new per-folder **"Name (literal)"** sort mode (`FileSortOptions.By.NAME_LITERAL`, appended last so
  persisted ordinals stay valid): it compares names with a plain `Collator`, so digit runs sort by
  character (`[06]`, `[3070]`, `[6473]`…) instead of by numeric magnitude. The default numeric-aware
  "Name" is unchanged.

### 📋 File operations

- **Paste from the top toolbar.** After Copy/Cut, the pending paste now appears top-right in the
  overlay toolbar (in place of the Copy/Cut icons) rather than in a bottom action-mode bar; the
  toolbar's close button clears whichever it is showing, and the `v` paste shortcut still works. The
  bottom bar is now used only for pick mode (create file / open directory).

### 🛠 Build, identity & FOSS strip

- **NONFREE strip** — removed Firebase Analytics/Crashlytics: the Google-services and Crashlytics
  Gradle plugins, the `firebaseCrashlytics` release option, the Firebase dependencies, the Crashlytics
  initializer and the privacy-policy entry; deleted `google-services.json` and `nonfree/`. A clean
  FOSS build (and required anyway — the google-services plugin refuses the changed application ID).
- **Side-by-side identity** — installed app ID `shiroikuma.shoruikanri` so the fork coexists with
  upstream; the code namespace stays `me.zhanghai.android.files`, and the provider authorities follow
  the app ID.
- **Fork versioning** — `VERSION_NAME` / `VERSION_CODE` track upstream (1.7.4 / 39); `BUILD_NUMBER` is
  our increment. `versionName = "<VERSION_NAME>+<BUILD_NUMBER>"`,
  `versionCode = VERSION_CODE * 10000 + BUILD_NUMBER`. Native libraries restricted to **arm64-v8a**.
- **`buildApk` Gradle task** — `assembleRelease` (signed via the gitignored `signing.properties`),
  copies the APK to `~/tmp/shiroikuma-shoruikanri_<version>_arm64-v8a.apk`, then bumps `BUILD_NUMBER`
  for the next build.
- **Dependency fix** — use the lowercase JitPack group `com.github.bitfireat` for `dav4jvm` (the
  mixed-case path 404s on a fresh resolve).
- **Fork docs & tooling** — `CLAUDE.md` (fork workflow, customization table, hard rules) plus the
  `build-apk`, `upstream-new-version` and `publish-version` skills.
