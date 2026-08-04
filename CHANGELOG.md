# Changelog — 白い熊 書類管理

All notable fork changes layered on top of [Material Files](https://github.com/zhanghai/MaterialFiles)
by Hai Zhang. Versions are `<upstream version>+<fork build>` — the upstream version this fork tracks,
plus our build increment (e.g. `1.7.4+050`; the counter is zero-padded to three digits from
`1.7.4+050` onwards, so builds sort in order). This fork installs side-by-side with upstream under
the app ID `shiroikuma.shoruikanri`.

## 1.7.4+053

### New since 1.7.4+050

#### 📦 XAPK bundles, handled properly

- **An XAPK now shows the app's own icon in the listing**, the way an APK does, instead of the blank
  generic-file icon it used to get. The icon is read straight out of the bundle (its root
  `icon.png`) — `PackageManager` cannot parse an XAPK, so the icon an APK gets could never have come
  from there. A bundle without one simply keeps the plain icon.
- **XAPKs browse and extract like the ZIPs they are.** Tap one to look inside at the base APK, the
  split APKs and the OBB files, or extract it like any other archive — previously the app treated it
  as an unrecognised blob and offered neither.
- **One-tap install still works.** Set your split-APK installer as the open-with default for XAPKs
  and a tap goes straight there; without a default, a tap opens the bundle for browsing. Either way
  “Open with” from the long-press menu behaves as usual.
- The type is recognised as its own kind of file, so an “always open with” choice made for XAPKs
  binds to XAPKs alone — before, an XAPK counted as an unknown file, and setting a default for it
  would have hijacked every other unrecognised file on the device.

#### 🚪 Exit app

- **A new “Exit app” entry at the bottom of the navigation drawer**, below “About”. It quits
  outright — every tab of the window closes and the app leaves the recents list, so the next launch
  starts fresh instead of restoring where you left the drawer open.
- A window opened with **“New window”** is a separate task and stays open, and **background work is
  never killed mid-operation**: a running copy/move job or the FTP server carries on under its own
  notification.

#### 🎨 Launcher icon

- **The folder glyph on the launcher icon is 10% smaller** (90% of the masked icon, down from
  full-bleed 100%), so it sits more comfortably inside the adaptive-icon mask and the launcher's own
  shape. The themed (monochrome) icon and the legacy pre-Android-8 rasters were regenerated to
  match.

Everything from 1.7.4+050 and earlier (below) is included in this build.

## 1.7.4+050

### New since 1.7.4+47

#### 🔭 File names are shown in full

- **A long file name is never cut short any more.** In every listing view — **List, Compact,
  Column, Detailed** and **Wrapped** — the name now flows onto as many lines as it needs instead of
  being ellipsized on a single line, and the row grows to fit it. Names like
  `shiroikuma-rindenwa_6.3.0-alpha.2026-07-30.g5c0ed6a3+002_arm64-v8a.apk` or a long video title,
  which used to lose their middle to a `…` exactly where the version or the episode number sits,
  are readable at a glance.
- Wrapping **never invents a hyphen**: automatic hyphenation is off on the name, so every character
  you see belongs to the file name.
- **Wrapped** (the two-column view) loses its old two-line cap and shows the whole name too, and the
  additional views no longer force a trailing `…`.
- The **Grid** view is unchanged, and is now the only view that uses the **"File name ellipsize"**
  preference (start / middle / end / marquee) — there is nothing left to ellipsize elsewhere.

#### 📦 Packaging

- **The fork build counter is zero-padded to three digits** — this build is `1.7.4+050`, not
  `1.7.4+49`. Versions and APK file names now sort in build order in any file listing, matching the
  convention used across the 白い熊 apps. `versionCode` is unaffected (`390050`), and releases up to
  `1.7.4+49` keep the names they were published under.

Everything from 1.7.4+47 and earlier (below) is included in this build.

## 1.7.4+47

### New since 1.7.4+46

#### 🤖 Headless automation export

- **A sister app can now run this app's export for you, without opening it.** 書類管理 answers the
  白い熊 family's state-export contract, so 自由作業盤's 保存復元 project can back up every app on the
  phone in one run: it fires a token-gated broadcast, the app exports itself in the background and
  replies with the written path, the exact byte count, a human-readable size and the number of
  categories.
- **Two intents**, both exported and gated by a shared secret rather than a permission (the caller
  can't hold one): `shiroikuma.shoruikanri.action.EXPORT_STATE` runs the export — with an optional
  absolute **target directory** that overrides the configured one, and an optional **`items` list**
  to export only some categories — and `…action.LIST_CATEGORIES` enumerates the categories so the
  caller can offer a picker.
- **Live progress with real numbers, never a percentage** — the export broadcasts `区分 3/8 —
  App settings` as it goes (with the structured current / total / unit alongside), so the calling
  task can show what is actually happening.
- **The gate lives in the UI page's Export / Import section**, right below the existing rows: an
  **Automation export** switch — **off until you turn it on**, and nothing answers before you do —
  and an **Automation token** row that shows the secret abbreviated, **copies it whole on tap** and
  regenerates it on demand. The token is kept in its own device-local store that belongs to no
  export category, so it can never travel inside a backup ZIP.
- **One export core, two callers** — the automation path runs exactly the same category ZIP the
  Export / Import panel writes, so a headless backup is a normal, restorable one.
- **Backup file names now follow the 白い熊 family convention**:
  `shiroikuma-shoruikanri_2026-07-25_22-31-05.zip` — no version, no `-export` infix, no decoration,
  and always exactly one ZIP per app, so every app's backups sort and read uniformly in one folder.
  Backups written by earlier builds are still recognised as ours.

Everything from 1.7.4+46 and earlier (below) is included in this build.

## 1.7.4+46

### New since 1.7.4+43

#### 💾 Settings Export / Import

- **Every setting in the app can now be exported and imported**, from the new **Export / Import**
  section at the top of the UI page. The export is a single ZIP of **plain, type-tagged JSON files —
  one per category** — plus your imported font files as real files and a manifest; nothing binary,
  nothing opaque.
- **Eight selectable categories** cover everything settable: **UI theme** (colours · fonts, with the
  font files), **separators & grid styles**, **app settings**, **storages & bookmarks**,
  **per-folder view & sort**, **open tabs**, **share** (Termux scripts · pinned targets), and
  **open-with defaults**. One checklist drives both directions — export saves the ticked categories,
  import applies the ticked categories the chosen ZIP contains (absent ones are skipped).
- **A persisted export directory** (picked with the app's own directory picker) enables **one-tap
  export**; the panel — and the UI page itself, on every open — shows the **latest export** found
  there, with red warnings when the folder is unset or empty. Export files are named
  `shiroikuma-shoruikanri-<version>-export_<timestamp>.zip`.
- **Safe import semantics:** merges per key — never clears — so device-local state survives; skips
  unknown keys and categories, so exports round-trip across app versions. After import, styling
  refreshes immediately and a **Restart now / Later** dialog offers the full reload.
- The panel is a bottom sheet in the sister-repo style: an inset yellow-bordered box, a tappable
  directory box, the category checklist, and **round pill buttons** — Cancel alone on the left,
  Import / Export on the right. Success ends in a **yellow-bordered info dialog**; acknowledging it
  closes the whole chain (dialog → panel → UI page). Failures toast and leave the panel open.

#### 🎨 UI page restyle (kxkb look)

- The whole UI page now follows the kxkb sister-repo visual system: **bold yellow section headings
  underlined exactly as wide as their text** (20 sp with a 2.5 dp underline; sub-sections 17 sp with
  1.5 dp), an **edge-to-edge 1 px yellow rule** separating consecutive sections, and a tightened
  **36/54/72/90 dp indent ladder** (18 dp per nesting level) replacing the old 72 dp jumps — deep
  controls no longer crawl across the screen.

#### 🧰 Toolbar

- **Long-press the overflow (⋮) button — top-right on the main page — to open the UI page**, same
  as the existing long-press on the drawer (hamburger) icon on the left. The selection-mode
  toolbars keep their black/yellow tooltip on long-press.

#### 🛠 Fixes

- **No more phantom errors when navigating quickly on slow (e.g. SFTP) folders.** Starting a new
  folder load cancels the previous one; the cancelled load's interrupt used to surface as an error
  flash — an error the app caused itself — and a slow stale load could even overwrite a newer
  listing. Superseded loads now discard their outcome entirely.

Everything from 1.7.4+43 and earlier (below) is included in this build.

## 1.7.4+43

### New since 1.7.4+42

#### 🗂 Tabs & favorites

- **Folder tabs show bookmark names.** A tab sitting at a bookmarked folder now titles itself with
  the bookmark's name — including a custom name given via long-press → rename in the drawer —
  instead of the folder's literal directory name. **Renaming a bookmark updates any open tab's
  title immediately**; navigating into a subfolder shows that folder's own name again, as before.

Everything from 1.7.4+42 and earlier (below) is included in this build.

## 1.7.4+42

### New since 1.7.4+37

#### 🎵 Built-in audio mini-player

- **Play audio in-app.** Tapping a voice recording, `.m4a` or other audio file opens a small
  **floating mini-player** docked at the bottom — not a full-screen player — so the file list stays
  visible and usable behind it (non-modal: no dimming, and touches pass straight through to the list).
- Three rows: **filename + close**, a **seek bar**, and **current time · play/pause · duration**. It
  auto-plays on open, rewinds to the start when it finishes, and **playback survives rotation** (the
  `MediaPlayer` lives in a ViewModel). Closing it (the ✕ or Back) stops and releases playback; only
  one track plays at a time, and it floats over the list across tab switches.
- Plays **any provider path** — local recordings and, via the file provider, remote / SAF sources.
  Tapping uses the mini-player by default, but a **remembered open-with default still wins**, and
  long-press → **Open with** → an external player still works (and can be set as the default).
- **Themeable** from the UI page: a new **Audio player** slot group — background, title, time and
  controls — with the **title and time** carrying their own font family / weight / size.

#### 🔭 Per-folder listing view

- The listing view (**List / Grid / Compact / Column / Detailed / Wrapped**) is now remembered
  **per folder** instead of per tab. Change the view in a folder and it sticks to **that folder
  only**; navigating away — including **Back** — restores each folder's own remembered view, falling
  back to **List** where you've set none. (This supersedes the earlier per-tab listing view.)
- **Fixed stale check marks** in the view menu: after switching views the previously selected ones
  stayed ticked (the six view items were independent checkboxes, so the marks piled up) — now only
  the active view is checked.

#### 🎨 Theme system (skui)

- **Black/yellow long-press tooltips.** The long-press tooltips on the toolbar buttons (e.g.
  **"More options"** on the overflow) now match the theme — a **black popup with a yellow border and
  yellow text** — instead of the platform's light box. The platform tooltip background is a private
  attribute that can't be themed in XML, so the fork suppresses it and draws its own; this covers the
  main toolbar and the selection-mode action toolbars.

Everything from 1.7.4+37 and 1.7.4+36 (below) is included in this build.

## 1.7.4+37

### New since 1.7.4+36

#### 📋 File operations

- **Copy/Cut now survives Back.** With a paste pending (the paste icon showing top-right in the
  overlay toolbar), **Back navigates the filesystem** — up the directory tree — instead of cancelling
  the pending copy/cut. The selected files stay armed for paste while you browse to the destination,
  so Back is pure navigation again.
- **Long-press the paste icon to cancel** a pending paste (the overlay toolbar's close button still
  cancels it too). The plain selection action mode is unchanged — Back there still clears the
  selection.

Everything from 1.7.4+36 (below) is included in this build.

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
