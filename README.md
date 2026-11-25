# kimi-byok-poc

A personal **research POC** that patches the Kimi Android app (`com.moonshot.kimichat` 3.1.8) so it
gains a second, fully independent launcher entry — **"Kimi BYOK"** — that opens a
**login-free, signup-free chat** with any OpenAI-compatible AI provider using **your own API keys**,
plus **artifact publishing**: chat-generated HTML artifacts can be published to the web with one tap
(the app creates a GitHub repository under your account and enables GitHub Pages).

## Features

- **Two launcher icons**: the original Kimi entry stays untouched; a new "Kimi BYOK" icon opens the
  BYOK experience directly — **no Kimi sign-in, no signup, no Kimi servers involved**.
- **Per-provider switches** (enable/disable): DeepSeek, Moonshot OpenPlatform, OpenAI, and a Custom
  (editable base URL) slot.
- **Auto model fetch**: paste an API key → the app queries `GET {baseUrl}/models` (OpenAI-style) and
  fills a model picker automatically; falls back to manual entry on error.
- **Chat**: plain OpenAI-compatible `/chat/completions` (non-streaming), clean 401/429/network errors.
- **Artifacts**: HTML artifacts produced in chat are extracted, previewable in-app (WebView), and
  publishable to GitHub Pages in one tap (`https://<you>.github.io/<repo>/`).

## What is in this repository

**Source code and patch tooling only.** This repository never contains, and must never receive:

- any files from the original application (`.apkm`, `.apk`, split APKs, resources, dex);
- any built or signed artifact of the patched application;
- any API keys or tokens.

Everything here is original code written for the POC. The patch pipeline expects the operator to
supply their own copy of the application bundle locally; see `docs/architecture.md`.

## Legal / intended use

Personal-use security-research POC. It modifies a third-party application for local experimentation
and redistributes nothing. Do not distribute the resulting APK. The upstream application remains the
property of its publisher; its terms of service still apply to any use of their services.

## Repository layout (as milestones land)

```
docs/architecture.md   — how the patch works (merge, smali edits, manifest, signing)
patcher/               — idempotent smali/manifest patch scripts
src/                   — BYOK module Java sources (compiled to an extra classes5.dex)
build.sh               — reproducible build/sign pipeline from a locally supplied .apkm
```

## Status

- [x] Repository scaffold
- [ ] BYOK module sources
- [ ] Patch scripts
- [ ] Build pipeline
- [ ] Verified patched APK (built locally)
