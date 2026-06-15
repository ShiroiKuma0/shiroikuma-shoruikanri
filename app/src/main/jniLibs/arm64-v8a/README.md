# jniLibs/arm64-v8a

Drop the prebuilt gocryptfs native libraries here (arm64-v8a), built in the
`shiroikuma-gcfs` repo:

- **`libgocryptfs.so`** — the engine (`gcf_*`, c-shared). This is exactly what gcfs's
  existing `build.sh` already produces (`build/arm64-v8a/libgocryptfs.so`).
- **`libgocryptfs_jni.so`** — the JNI bridge: DroidFS's `gocryptfs_jni.c` lifted and
  re-pointed at this fork's package/types, compiled against the engine. Exports
  `Java_me_zhanghai_android_files_provider_gocryptfs_client_GocryptfsVolume_native_*`,
  constructs `…GocryptfsStat` (ctor `(IJJ)V`) and list elements via
  `…GocryptfsEntry.new(Ljava/lang/String;IJJLjava/lang/String;)…`.

`GocryptfsVolume` loads the engine then the bridge (`System.loadLibrary("gocryptfs")` then
`"gocryptfs_jni"`); if gcfs instead static-links the engine into a single
`libgocryptfs_jni.so`, the first load fails harmlessly and only that one `.so` is needed.

Only `*.so` files here are packaged into the APK; this README is ignored by the build.
This directory is checked in (with this placeholder) so the `src/main/jniLibs` source set
exists before the binaries are vendored.

See `~/tmp/shoruikanri-gocryptfs-jni-handoff.md` for the gcfs-side build spec.
