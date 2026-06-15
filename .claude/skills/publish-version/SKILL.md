---
name: publish-version
description: Publish the latest built APK as a GitHub release of this fork. Derives the version from the newest ~/tmp APK, regenerates a futokxkb-style fork README and a very specific changelog from `git log master..custom`, commits & pushes them to `custom`, ensures GitHub's default branch is `custom` (so the repo page lands on our fork), then creates the tag + release with the APK attached. Use when 白い熊 says publish a version, cut/make a release, publish to GitHub, tag a release, or "publish-version".
---

# Publish a fork version to GitHub

This codifies the "ship a release" half of the fork workflow. One run takes the **latest build**
and turns it into a public GitHub release: a tag, the signed APK as an asset, a regenerated
fork-landing **README** on `custom`, the GitHub **default branch** flipped to `custom`, and a
**very specific changelog** as the release notes.

> **Invoking this skill *is* the authorization to publish.** Unlike everyday development (where you
> never push/commit unprompted), running `/publish-version` means 白い熊 wants the README/changelog
> committed and pushed to `origin custom`, the default branch switched, and a public release + tag
> created. You still **never `adb install`**, you still **never add a Claude/Anthropic attribution
> trailer** to the commit, and you **confirm the version string once** (it taints the tag + release
> if wrong) before the outward-facing actions.

> **Regenerate, don't hand-wave.** The README major-features list and the changelog are **rebuilt
> from `git log master..custom` every run** so they track whatever we've shipped since. Never copy a
> stale list — read the commit bodies and write from them.

## Background — what gets published and how it's versioned

- The **latest build** is the newest `shiroikuma-shoruikanri_<VERSION>_arm64-v8a.apk` in `~/tmp/`.
  Derive `<VERSION>` (e.g. `1.7.4+36`) straight from that filename — that is the release.
- Cross-check: `<VERSION>` should equal `<VERSION_NAME>+<BUILD_NUMBER − 1>` read from
  `gradle.properties` (the `buildApk` task bumps `BUILD_NUMBER` **after** each build, so the number
  in the file is the *next* build, and the latest *built* one is `BUILD_NUMBER − 1`).
- **Tag = the version string verbatim**, matching the sister repo's convention
  (`shiroikuma-futokxkb` tags releases `0.1.29+10`, no `v` prefix). So our tag is `1.7.4+36` —
  `+` is legal in git refs and GitHub release tags.
- Repos: ours is `ShiroiKuma0/shiroikuma-shoruikanri`; upstream is `zhanghai/MaterialFiles`
  (GPL-3.0). Style reference: `ShiroiKuma0/shiroikuma-futokxkb`.

## Steps

### 1. Determine the version and locate the APK

- `ls -t ~/tmp/shiroikuma-shoruikanri_*_arm64-v8a.apk | head -1` → newest APK; parse `<VERSION>`.
- `grep -E 'VERSION_NAME|BUILD_NUMBER' gradle.properties` → sanity-check `<VERSION>` ==
  `<VERSION_NAME>+<BUILD_NUMBER − 1>`.
- If `~/tmp` has **no** matching APK (it was cleaned), build it first via the **build-apk** skill
  (note: that produces the *next* number) — or ask 白い熊 which APK to publish. Do not invent a tag
  for a build that has no artifact.

### 2. Regenerate the changelog from history

`git log master..custom --no-merges --reverse --format='===== %h %s%n%b'` — read the **full bodies**,
not just subjects. Skip the `Bump BUILD_NUMBER …` commits as content (build bookkeeping). Write a
**grouped, very specific** changelog (this becomes the release notes) — every real behaviour, not a
one-liner per commit. Group by area, roughly:

- **Encrypted volumes (gocryptfs)** · **Tabs & navigation** · **Theme system (skui)** ·
  **Listing views & per-folder styling** · **Open-with** · **Sharing & Termux** · **Sorting** ·
  **File operations** · **Build, identity & FOSS strip**.

Write it to `CHANGELOG.md` at the repo root, structured so it doubles as the release notes:

```markdown
# Changelog — 白い熊 書類管理

All notable fork changes on top of [Material Files](https://github.com/zhanghai/MaterialFiles).
Versions are `<upstream version>+<fork build>` (e.g. `1.7.4+36`).

## <VERSION>

### Encryption — gocryptfs volumes, in-app
- … specific bullets …

### Tabs & navigation
- …

### … (one section per area, every feature listed specifically) …
```

When a **previous tag exists** (`git tag --sort=-v:refname | head`), lead the new version's section
with a short **"New since `<prev>`"** list (diff the commit set), then keep the full grouped detail
below so each release page stays self-contained.

### 3. Rewrite the README on `custom` (futokxkb style)

`README.md` on `custom` is **fork-owned** — it replaces upstream's. (Expect a rebase conflict on the
next `upstream-new-version`; just keep ours.) Model it on `shiroikuma-futokxkb`'s README: a centred
header, a "fork of X with major additions" pitch, then a handful of `## emoji Feature` sections
picking the **most important** changes against stock, each with a short, vivid description, then
attribution + build instructions.

Pull the icon from the **rebranded** raster `app/src/main/res/mipmap-xxxhdpi/launcher_icon.png`
(black field, yellow folder). **Do not** use `art/launcher_icon-play.png` — it is still upstream's
blue icon. We have no fork screenshots/videos yet; omit them (leave an HTML comment placeholder) —
do not fabricate `user-attachments` URLs.

Skeleton (fill the feature sections from the current history; keep 6–9 majors):

```markdown
<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/launcher_icon.png" width="120" alt="白い熊 書類管理 icon" />

# 白い熊 書類管理

**A black-and-yellow, tab-driven file manager — with encrypted gocryptfs volumes built in.**

A fork of [Material Files](https://github.com/zhanghai/MaterialFiles) with **major additions**:
in-app gocryptfs encrypted volumes, multi-folder tabs, a full black/yellow theme system, six
listing views with per-folder styling, a custom open-with chooser, and deep Termux/share
integration.

Installs **side-by-side** with upstream Material Files (app ID `shiroikuma.shoruikanri`).

**📥 Latest release: [`<VERSION>`](https://github.com/ShiroiKuma0/shiroikuma-shoruikanri/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-shoruikanri/releases)

</div>

<!-- README screenshots/videos go here once captured. -->

---

## 🔒 Encrypted gocryptfs volumes, in-app
… short description …

## 🗂 Multi-folder tabs
…

## 🎨 Black & yellow, themed to the pixel
…

## 🔭 Six listing views + per-folder styling
…

## 📤 Open-with & share, your way (Termux-friendly)
…

## 🔤 Lexicographic sort, top-bar paste, and more
…

---

## Built on Material Files

This project is a fork of [Material Files](https://github.com/zhanghai/MaterialFiles) by Hai Zhang
(package `shiroikuma.shoruikanri`, so it coexists with the official build). All upstream work — the
NIO2 file layer, archive/FTP/SFTP/SMB/WebDAV support and the Material Design base — belongs to the
Material Files authors; see the [upstream repository](https://github.com/zhanghai/MaterialFiles) for
issues, contributing and the canonical source. The code remains under the [GNU GPL v3](LICENSE).

The fork drops Firebase Analytics/Crashlytics (FOSS build), targets arm64-v8a, and is signed and
released independently.

## Building

Requires JDK 17+ and the Android SDK. On a machine whose default `java` is older:

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildApk
```

See `CLAUDE.md` for the full fork workflow (remotes, versioning, rebasing onto new upstream releases).
```

The major-features set to pick from (rank the most impactful vs stock; refresh from history):
gocryptfs encrypted volumes · multi-folder tabs (per-tab view, drag-reorder, swipe, favorite,
persistent set) · skui black/yellow theme system (UI page, color/font/size slots, themed dialogs,
toasts, menus, speed-dial) · six listing views + per-folder grid/separator styling · custom
open-with dialog (remembered per-MIME defaults) · custom share dialog + Termux script targets +
inbound system-share→Termux · lexicographic ("Name (literal)") sort · paste in the top toolbar ·
FOSS strip + side-by-side app ID + rebranded launcher icon + 白い熊 書類管理 name everywhere.

### 4. Confirm, then publish

Show 白い熊 the plan in one `AskUserQuestion`: **publish `<VERSION>` now?** — list that it will commit
+ push the README/CHANGELOG to `origin custom`, switch the GitHub default branch to `custom`, and
create a **public** release + tag `<VERSION>` with the APK attached. Options: "Publish now" /
"Stop". (This is the single confirmation; the README/changelog were generated locally with no
prompt.) Only on confirmation:

1. **Commit + push the docs:**
   - `git add README.md CHANGELOG.md`
   - `git commit -m "Publish <VERSION>: fork README + changelog"` (no attribution trailer)
   - `git push origin custom`
2. **Ensure the default branch is `custom`** (idempotent — the landing page must show our README):
   - `gh repo edit ShiroiKuma0/shiroikuma-shoruikanri --default-branch custom`
   - Optional: set the repo description to the fork tagline
     (`gh repo edit … --description "白い熊 書類管理 — a black/yellow, tabbed, gocryptfs-capable fork of Material Files"`).
3. **Create the tag + release with the APK:**
   - `gh release create '<VERSION>' '/home/<user>/tmp/shiroikuma-shoruikanri_<VERSION>_arm64-v8a.apk' -R ShiroiKuma0/shiroikuma-shoruikanri --target custom --title '白い熊 書類管理 <VERSION>' --notes-file CHANGELOG.md`
   - **Always pass `-R ShiroiKuma0/shiroikuma-shoruikanri` on every `gh` call.** This repo has an
     `upstream` remote (`zhanghai/MaterialFiles`), and `gh` otherwise resolves the *parent* repo and
     fails with `HTTP 404 … /repos/zhanghai/MaterialFiles/releases`. (`gh repo edit` already names the
     repo, so it's fine.)
   - `gh release create` makes the tag at `custom`'s HEAD (the docs commit). Docs don't change the
     binary, so the attached `1.7.4+36` APK is the build we shipped + tested.
   - If the tag/release already exists, `gh release edit '<VERSION>' -R ShiroiKuma0/shiroikuma-shoruikanri --notes-file CHANGELOG.md` and
     `gh release upload '<VERSION>' <apk> -R ShiroiKuma0/shiroikuma-shoruikanri --clobber` instead.

### 5. Report

Print the release URL (`gh release view '<VERSION>' -R ShiroiKuma0/shiroikuma-shoruikanri --web` or
the URL from create), confirm the default branch
(`gh repo view ShiroiKuma0/shiroikuma-shoruikanri --json defaultBranchRef`), and the asset name/size
(`gh release view '<VERSION>' -R ShiroiKuma0/shiroikuma-shoruikanri --json assets`).

## Notes

- **Quote the `+`.** Always single-quote `<VERSION>` in shell (`'1.7.4+36'`) so the shell/glob never
  mangles it.
- The README/CHANGELOG are docs — they are **not** bundled into the APK, so regenerating and
  committing them after the build does not invalidate the artifact you attach.
- Keep the README a **legible fork-landing page**, not a feature dump: a few strong sections with
  nice descriptions. Push the exhaustive detail into `CHANGELOG.md` / the release notes.
- This skill does not build. If there is no fresh APK, run **build-apk** first.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated
with Claude" trailer to commit messages, release notes or PR bodies; end the message at the last
line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
