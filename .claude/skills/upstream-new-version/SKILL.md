---
name: upstream-new-version
description: Rebase our fork onto a new upstream release of zhanghai/MaterialFiles. Use when the user says a new upstream version is out, asks to update/sync to upstream, bump to the new Material Files release, or rebase custom onto the latest upstream.
---

# Rebase the fork onto a new upstream release

This codifies the "new upstream version" half of the fork workflow. The goal: move `master` to the
new upstream release, replay our `custom` customizations on top of it, and produce a fresh `+1` build.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** Same hard rules as everyday
> development (see CLAUDE.md). After the rebase + build you stop and let the user test; you only
> `git push` when they explicitly say **"Push"**.

## Background — how versioning works here

- `VERSION_NAME` / `VERSION_CODE` in `gradle.properties` **track upstream** (upstream hardcodes
  `versionName` / `versionCode` in `app/build.gradle`; our `custom` commit replaces those with the
  fork logic reading `gradle.properties`).
- `BUILD_NUMBER` is **our** fork increment. It **resets to `1`** on each new upstream version.
- Fork `versionName` = `"<VERSION_NAME>+<BUILD_NUMBER>"`, `versionCode` = `VERSION_CODE * 10000 + BUILD_NUMBER`.

So when upstream's `versionCode` climbs (e.g. 39 → 40), our fork's codes for the new line
(`400001`, `400002`, …) all exceed the previous line's (`390001`, …), keeping upgrades monotonic.

## Steps

1. **Fetch upstream:**
   - `git fetch upstream --tags`
   - Identify the new release tag/commit, e.g. `git tag --sort=-creatordate | head` or check
     `upstream/master`. Confirm the new `versionName` / `versionCode` from upstream's
     `app/build.gradle` at that point:
     `git show upstream/master:app/build.gradle | grep -E 'versionCode|versionName'`
     (note: upstream sometimes advances `master` past the release tag with dependency bumps — we
     track `master`, matching how this fork was originally cut).

2. **Advance `master` to the new upstream state** (it mirrors upstream, no fork work lives there):
   - `git checkout master`
   - `git merge --ff-only upstream/master` (or `git reset --hard <tag>` if tracking an exact tag).

3. **Rebase `custom` onto the new `master`:**
   - `git checkout custom`
   - `git rebase master`
   - Resolve conflicts so **all** our customizations survive (see the table below). The conflict-prone
     files are `app/build.gradle` (fork version logic, NONFREE strip, abiFilters, buildApk task),
     `gradle.properties`, `app/src/main/AndroidManifest.xml` (label), and `values/strings.xml`.
     If upstream re-adds or moves `//#ifdef NONFREE` blocks, re-strip them (delete from each
     `//#ifdef NONFREE` line through its `//#endif` line inclusive, and keep `google-services.json`
     and `nonfree/` deleted).

4. **Update versioning in `gradle.properties`:**
   - Set `VERSION_NAME` / `VERSION_CODE` to the **new upstream** values (from upstream's
     `app/build.gradle`).
   - **Reset `BUILD_NUMBER` to `1`.**

5. **Verify our customizations are intact** (after resolving the rebase):

   | What | Expected value | Where |
   | --- | --- | --- |
   | NONFREE strip | no `NONFREE`/firebase/google-services references | `git grep -i 'NONFREE\|firebase\|google-services' -- app/build.gradle app/src/main` is empty; `app/google-services.json` and `nonfree/` absent |
   | Installed app ID | `shiroikuma.shoruikanri` | `gradle.properties` → `APP_ID`, read in `app/build.gradle` |
   | Code namespace | `me.zhanghai.android.files` (unchanged from upstream) | `app/build.gradle` |
   | App launcher label | `白い熊 書類管理` | `sk_launcher_name` in `values/strings.xml`; manifest `android:label="@string/sk_launcher_name"` |
   | Fork version logic | `forkVersionName` / `forkVersionCode` + `buildApk` task | `app/build.gradle` |
   | ABI | `abiFilters 'arm64-v8a'` | `app/build.gradle` defaultConfig |

   Sanity check: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleRelease --dry-run < /dev/null`
   to confirm the build script still evaluates.

6. **Build the new `+1`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildApk < /dev/null`), then **ask** before
   any `adb push`. This is the first build of the new upstream line (`<newVersion>+1`).

7. **Stop.** Let the user test. Commit/push only on their explicit **"Push"** (force-push may be needed
   for `custom` since rebasing rewrites history: `git push --force-with-lease origin custom`; `master`
   is a fast-forward).

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear history) over
  merging, so the customization set stays easy to audit and replay.
- If upstream restructures a file we customize, port our change to the new structure rather than forcing
  the old diff.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
