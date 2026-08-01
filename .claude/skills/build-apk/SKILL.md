---
name: build-apk
description: Build the signed release APK with the buildApk Gradle task, then deliver it AUTOMATICALLY via the global /after-build skill (adb-push to the phone if connected, else scp to skhw) — no transfer prompt. Always build first without asking for permission to build, and never ask how to transfer the APK. Use whenever the user asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the release APK and optionally send to phone

> **Never ask whether to build — just build.** When this skill applies (the user
> asked to build, or you've made changes that are ready to test), run the build
> immediately. Do **not** ask "shall I build?" / "want me to run buildApk?" — that
> question is wrong. And do **not** ask how to transfer the APK either: after a
> successful build, delivery is automatic via `/after-build` (see below). So:
> always build, *then* deliver — no questions.

> **The push destination is ALWAYS `/sdcard/tmp/`.** Every `adb push` of the APK
> goes to `/sdcard/tmp/<apk name>` — **never** `/sdcard/Download/` or anywhere
> else. Create `/sdcard/tmp` if needed and push there.

> **Never run `adb install` (or `pm install`).** The build step copies the APK
> to the phone with `adb push` automatically (via `/after-build`), but
> **the user installs the APK themselves** from the phone's file manager. Do not
> install it for them under any circumstances.

> **Never `git commit` or `git push` on your own.** Building does not include
> committing. After building (and the optional `adb push`), the user tests the
> build themselves. **Only when the user explicitly says "Push"** do you then
> `git commit` the changes and `git push origin custom`. The user's **"Push"**
> means *commit-and-push-to-the-fork* — it is unrelated to the `adb push` file
> copy in step 4.

> **ALWAYS end every build by delivering the APK via `/after-build` — never ask.**
> Once the signed APK is in `~/tmp/`, invoke the global **`/after-build`** skill:
> it runs **`/adb-check`** (UNSANDBOXED — a sandboxed check falsely reports no
> device), then **`/adb-push`** to `/sdcard/tmp/` if a phone is connected,
> otherwise **`/scp`** to `skhw:~/tmp/`, and announces the filename that landed.
> Mandatory for *every* successful build. Do **not** prompt "scp or adb push?" or
> "is the phone connected?" — `/after-build` decides and announces on its own.

## Steps

1. **Note the output filename.** Read the current version and build number:
   - `grep -E 'VERSION_NAME|VERSION_CODE|BUILD_NUMBER' gradle.properties`
   - The APK will be `shiroikuma-shoruikanri_<VERSION_NAME>+<NNN>_arm64-v8a.apk`, using the `BUILD_NUMBER` value **before** the build (the task bumps it afterward).
   - **`<NNN>` is `BUILD_NUMBER` zero-padded to three digits** (`+001`, `+014`, `+050`) — the global `/after-build` rule, so APKs sort in build order. `gradle.properties` stores the number unpadded; `app/build.gradle` does the padding.
   - versionCode for that build = `VERSION_CODE * 10000 + BUILD_NUMBER` (a number — never padded).

2. **Build** (needs JDK 17+ — the default `java` on this machine is JDK 11):
   - `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildApk < /dev/null`
     (the `< /dev/null` guarantees it never blocks on stdin — important because upstream's
     `signing.gradle` falls back to *prompting on the console* for signing credentials if
     `signing.properties` is missing; with stdin closed it fails fast instead of hanging)
   - This runs `assembleRelease`, copies the signed APK to `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - The task prints `>>> <path>` and `>>> versionCode <n>`; use those to confirm the exact filename and code, and confirm `BUILD SUCCESSFUL`.

3. **Deliver via `/after-build` — no prompt.** As soon as the build reports `BUILD SUCCESSFUL` with the signed APK in `~/tmp/`, invoke the global **`/after-build`** skill. It runs **`/adb-check`** UNSANDBOXED (a sandboxed check falsely reports no device), then:
   - **phone connected** → **`/adb-push`** the newest `~/tmp/*.apk` to `/sdcard/tmp/<apk name>` (creating `/sdcard/tmp` if needed), and announce it. Never `adb install` — the user installs manually from `/sdcard/tmp/`.
   - **no phone** → **`/scp`** the newest `~/tmp/*.apk` to `skhw:~/tmp/`, and announce it.

   Do this for every successful build, without asking.

## Signing

Release signing is non-interactive when configured: upstream's `signing.gradle` reads credentials
from the gitignored `signing.properties` at the repo root (falling back to `STORE_FILE` /
`STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` env vars, then to a console prompt — hence the
`< /dev/null`). This fork signs with `~/.android-keystores/shiroikuma-shoruikanri.jks`
(alias `shoruikanri`). If `signing.properties` is missing, recreate it with the four keys above.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
