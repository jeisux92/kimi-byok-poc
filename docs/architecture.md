# Architecture

How the POC patch works. Written before implementation; updated as milestones land.

## Target

`com.moonshot.kimichat` 3.1.8 (versionCode 388), distributed as an APKMirror `.apkm` bundle
(base + arm64-v8a + dpi config splits), Kotlin Multiplatform + Compose UI, R8-obfuscated,
Google PairIP licensing wrapper, v3-signed with a Play-managed key.

## Pipeline

1. **Merge** — `APKEditor m` merges the split bundle into one universal APK (arm64 + all densities).
2. **Decode** — `apktool d` produces smali + decoded resources/manifest.
3. **BYOK module** — original Java sources in `src/`, compiled with `javac` against `android.jar`
   and dexed with `d8` into an **additional `classes5.dex`**. Existing dexes are never recompiled.
4. **Smali edits** — exactly two, both idempotent, reviewed as diffs:
   - `com/pairip/application/Application.smali`: the single `LicenseClient.checkLicense(...)` call in
     `attachBaseContext` is removed. PairIP otherwise force-exits any non-Play-installed copy within
     ~30 seconds, which would kill the POC regardless of our changes. Nothing else in PairIP is touched.
   - `h40/n.smali` (`f(Intent, m)` router): one branch prepended that delegates to the module's
     `ByokRouter.handleDeepLink` so `kimi://byok` opens the BYOK experience.
5. **Manifest** — one `<activity-alias>` ("Kimi BYOK", MAIN/LAUNCHER → `ByokMainActivity`) and two
   `<activity>` entries. No permissions added, no other attributes changed.
6. **Build & sign** — `apktool b`, zip-inject `classes5.dex`, 16 KB page alignment, fresh keystore,
   `apksigner`. Re-signing intentionally breaks the upstream Google Sign-In (certificate-bound);
   the BYOK module never uses upstream authentication.

## BYOK module design

- Package `com.moonshot.kimichat.byok`; plain Java; only `android.*` and `org.json` — no new
  dependencies, so no changes to the app's dependency graph.
- Settings persist in `SharedPreferences("byok_prefs")`; keys/tokens are device-private and are sent
  only to the provider endpoints the user configures and to `api.github.com` for publishing.
- Providers are fixed slots (DeepSeek / Moonshot OpenPlatform / OpenAI / Custom), each with an
  enable switch, editable base URL, API key, and a model spinner auto-populated from
  `GET {baseUrl}/models` when a key is entered.
- Artifacts: fenced code blocks and full HTML documents are extracted from assistant replies,
  previewed in a sandboxed local WebView, and published via the GitHub REST API
  (create repo → `contents/index.html` → enable Pages → poll → return the `github.io` URL).

## Known limitations (POC scope)

- Single-file HTML artifacts; multi-file projects, SSE streaming, and markdown rendering are follow-ups.
- Re-signing breaks upstream Google Sign-In and Play-managed updates.
- Two methods jadx could not decompile in the upstream app are an unverifiable residual; runtime
  behavior is confirmed on-device.
